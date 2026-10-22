package com.example.adblocker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var counter: TextView
    private lateinit var listInfo: TextView
    private lateinit var toggle: Button
    private val handler = Handler(Looper.getMainLooper())

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) startVpn()
            else Toast.makeText(this, R.string.vpn_denied, Toast.LENGTH_LONG).show()
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val refresher = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        counter = findViewById(R.id.counter)
        listInfo = findViewById(R.id.listInfo)
        toggle = findViewById(R.id.toggle)

        toggle.setOnClickListener {
            if (AdBlockVpnService.isRunning) stopVpn() else requestVpn()
        }
        findViewById<Button>(R.id.updateList).setOnClickListener { updateList() }

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun requestVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermission.launch(intent) else startVpn()
    }

    private fun startVpn() {
        ContextCompat.startForegroundService(
            this, Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START)
        )
    }

    private fun stopVpn() {
        startService(Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP))
    }

    private fun updateList() {
        Toast.makeText(this, R.string.updating, Toast.LENGTH_SHORT).show()
        Thread {
            Blocklist.loadOrUpdate(applicationContext, forceDownload = true)
            runOnUiThread {
                Toast.makeText(this, getString(R.string.list_updated, Blocklist.size), Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun refreshUi() {
        val on = AdBlockVpnService.isRunning
        status.setText(if (on) R.string.status_on else R.string.status_off)
        status.setTextColor(ContextCompat.getColor(this, if (on) R.color.on else R.color.off))
        toggle.setText(if (on) R.string.stop else R.string.start)
        counter.text = getString(R.string.blocked_count, AdBlockVpnService.blockedCount.get())
        listInfo.text = getString(R.string.list_size, Blocklist.size)
    }
}
