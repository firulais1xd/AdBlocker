package com.example.adblocker

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lista de dominios de publicidad/rastreo.
 * Un dominio queda bloqueado si él o cualquiera de sus dominios padre está en la lista
 * (p. ej. "pagead2.googlesyndication.com" se bloquea por "googlesyndication.com").
 */
object Blocklist {
    private const val TAG = "Blocklist"
    private const val CACHE_FILE = "hosts.txt"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 días

    // Lista pública y mantenida de anuncios + malware (formato hosts)
    const val SOURCE_URL =
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"

    // Lista mínima incluida en la app, usada hasta que se descargue la completa
    private val BUILT_IN = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "googletagservices.com", "admob.com", "app-measurement.com",
        "adservice.google.com", "applovin.com", "applvn.com", "unityads.unity3d.com",
        "ads.mopub.com", "adcolony.com", "vungle.com", "inmobi.com", "chartboost.com",
        "supersonicads.com", "startappservice.com", "an.facebook.com",
        "ads.tiktok.com", "ads-api.tiktok.com", "adnxs.com", "taboola.com",
        "outbrain.com", "criteo.com", "pubmatic.com", "rubiconproject.com",
        "moatads.com", "amazon-adsystem.com", "mintegral.com", "pangle.io",
        "pangolin-sdk-toutiao.com", "bytedance-ads.com"
    )

    @Volatile
    private var domains: Set<String> = BUILT_IN

    val size: Int get() = domains.size

    fun isBlocked(host: String): Boolean {
        var d = host.lowercase().trimEnd('.')
        val set = domains
        while (true) {
            if (d in set) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
        }
    }

    /** Carga la lista guardada; si no existe o es vieja, la descarga. Llamar fuera del hilo principal. */
    fun loadOrUpdate(context: Context, forceDownload: Boolean = false) {
        val file = File(context.filesDir, CACHE_FILE)
        val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS
        if (forceDownload || stale) {
            try {
                download(file)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo descargar la lista: ${e.message}")
            }
        }
        if (file.exists()) {
            val parsed = parse(file.readLines())
            if (parsed.isNotEmpty()) domains = parsed + BUILT_IN
        }
        Log.i(TAG, "Dominios bloqueados cargados: ${domains.size}")
    }

    private fun download(target: File) {
        val conn = URL(SOURCE_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val tmp = File(target.parentFile, "$CACHE_FILE.tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        } finally {
            conn.disconnect()
        }
    }

    private val IGNORED = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "0.0.0.0"
    )

    private fun parse(lines: List<String>): Set<String> {
        val result = HashSet<String>(lines.size)
        for (raw in lines) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            val host = when {
                parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
                parts.size == 1 -> parts[0]
                else -> continue
            }.lowercase()
            if (host !in IGNORED && host.contains('.')) result.add(host)
        }
        return result
    }
}
