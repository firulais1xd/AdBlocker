package com.example.adblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * VPN local que solo captura el tráfico DNS del teléfono.
 * Las consultas a dominios de publicidad reciben 0.0.0.0; el resto se reenvía a un DNS real.
 * Ningún otro tráfico pasa por la VPN, así que no afecta la velocidad de navegación.
 */
class AdBlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.adblocker.START"
        const val ACTION_STOP = "com.example.adblocker.STOP"

        private const val TAG = "AdBlockVpn"
        private const val CHANNEL_ID = "adblock_vpn"
        private const val NOTIFICATION_ID = 1

        private const val VPN_ADDRESS = "10.111.222.2"
        private const val VPN_DNS = "10.111.222.1"   // DNS "falso" que interceptamos
        private const val UPSTREAM_DNS = "1.1.1.1"     // DNS real (Cloudflare)

        @Volatile var isRunning = false
            private set
        val blockedCount = AtomicLong(0)
    }

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var forwarders: ExecutorService? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isRunning) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32)      // solo el DNS entra a la VPN
            .setBlocking(true)
        try {
            builder.addDisallowedApplication(packageName) // nuestra app sale directo (descarga de listas)
        } catch (_: Exception) { }

        val fd = builder.establish()
        if (fd == null) {
            Log.e(TAG, "No hay permiso de VPN")
            stopSelf()
            return
        }
        tun = fd
        isRunning = true
        forwarders = Executors.newFixedThreadPool(8)

        worker = Thread({ runLoop(fd) }, "dns-loop").also { it.start() }
    }

    private fun runLoop(fd: ParcelFileDescriptor) {
        Blocklist.loadOrUpdate(applicationContext)
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (isRunning) {
                val n = input.read(buf)
                if (n <= 0) continue
                handlePacket(buf.copyOf(n), output)
            }
        } catch (e: Exception) {
            if (isRunning) Log.e(TAG, "Error en el bucle DNS", e)
        }
    }

    private fun handlePacket(pkt: ByteArray, output: FileOutputStream) {
        if (pkt.size < 28) return
        if ((pkt[0].toInt() shr 4) and 0xF != 4) return          // solo IPv4
        val ihl = (pkt[0].toInt() and 0xF) * 4
        if (pkt[9].toInt() and 0xFF != 17) return                 // solo UDP
        if (DnsPackets.u16(pkt, ihl + 2) != 53) return            // solo puerto 53
        val totalLen = minOf(DnsPackets.u16(pkt, 2), pkt.size)
        val dns = pkt.copyOfRange(ihl + 8, totalLen)
        val name = DnsPackets.queryName(dns) ?: return

        if (Blocklist.isBlocked(name)) {
            val resp = DnsPackets.blockedResponse(dns) ?: return
            write(output, DnsPackets.buildIpv4UdpResponse(pkt, ihl, resp))
            blockedCount.incrementAndGet()
            Log.d(TAG, "Bloqueado: $name")
        } else {
            forwarders?.execute { forward(pkt, ihl, dns, output) }
        }
    }

    private fun forward(pkt: ByteArray, ihl: Int, dns: ByteArray, output: FileOutputStream) {
        try {
            DatagramSocket().use { socket ->
                protect(socket) // evita que esta consulta vuelva a entrar en la VPN
                socket.soTimeout = 5000
                socket.send(DatagramPacket(dns, dns.size, InetAddress.getByName(UPSTREAM_DNS), 53))
                val rb = ByteArray(4096)
                val rp = DatagramPacket(rb, rb.size)
                socket.receive(rp)
                write(output, DnsPackets.buildIpv4UdpResponse(pkt, ihl, rb.copyOf(rp.length)))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo reenviando DNS: ${e.message}")
        }
    }

    private fun write(output: FileOutputStream, data: ByteArray) {
        synchronized(output) {
            try { output.write(data) } catch (_: Exception) { }
        }
    }

    private fun stopVpn() {
        isRunning = false
        forwarders?.shutdownNow()
        forwarders = null
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        worker?.interrupt()
        worker = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {   // el usuario desactivó la VPN desde Ajustes u otra VPN tomó el control
        stopVpn()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AdBlockVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .build()
    }
}
