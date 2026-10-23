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
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000 // 3 días

    /** Listas públicas y mantenidas. Se aceptan formato hosts ("0.0.0.0 dominio") o un dominio por línea. */
    private val SOURCES = listOf(
        // HaGeZi Pro: muy completa para anuncios dentro de apps móviles (bloquea también subdominios)
        "hagezi.txt" to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/pro-onlydomains.txt",
        // StevenBlack: anuncios + malware
        "stevenblack.txt" to "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    )

    // Redes de anuncios de apps móviles; siempre activas aunque no se haya descargado nada
    private val BUILT_IN = setOf(
        // Google
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
        "admob.com", "app-measurement.com", "adservice.google.com", "pagead2.googlesyndication.com",
        "googleads.g.doubleclick.net", "imasdk.googleapis.com",
        // AppLovin / MAX
        "applovin.com", "applvn.com", "applovin.net",
        // Unity Ads
        "unityads.unity3d.com", "unityads.unity.com", "ads.prd.ie.internal.unity3d.com",
        // IronSource / LevelPlay
        "supersonicads.com", "ironsrc.com", "ironsrc.mobi", "ironsrc.net",
        // Mintegral
        "mintegral.com", "mintegral.net", "rayjump.com", "mtgglobals.com",
        // Pangle / TikTok / ByteDance
        "pangle.io", "pangle.cn", "pangolin-sdk-toutiao.com", "pangolin-sdk-toutiao-b.com",
        "pglstatp-toutiao.com", "byteoversea.com", "bytedance-ads.com", "ads.tiktok.com",
        "ads-api.tiktok.com", "analytics.tiktok.com",
        // Meta Audience Network
        "an.facebook.com",
        // Vungle / Liftoff, Moloco, InMobi, Chartboost, AdColony, Fyber, StartApp, Bigo
        "vungle.com", "liftoff.io", "liftoff-creatives.io", "moloco.com", "inmobi.com",
        "inmobicdn.net", "chartboost.com", "adcolony.com", "fyber.com", "inner-active.mobi",
        "startappservice.com", "startapp.com", "bigossp.com", "bigo.sg",
        // Otras redes comunes en apps de manga/anime
        "adsterra.com", "propellerads.com", "popads.net", "popcash.net", "exoclick.com",
        "juicyads.com", "trafficjunky.net", "onclickads.net", "adnxs.com", "taboola.com",
        "outbrain.com", "criteo.com", "pubmatic.com", "rubiconproject.com", "openx.net",
        "moatads.com", "amazon-adsystem.com", "smaato.net", "yandexadexchange.net",
        "mobfox.com", "tapjoy.com"
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

    /** Carga rápida desde los archivos ya descargados (sin red). */
    fun loadCached(context: Context) {
        val files = SOURCES.map { File(context.filesDir, it.first) }.filter { it.exists() }
        if (files.isEmpty()) return
        val result = HashSet<String>(300_000)
        files.forEach { parseInto(it, result) }
        result.addAll(BUILT_IN)
        domains = result
        Log.i(TAG, "Dominios cargados desde caché: ${result.size}")
    }

    /** Descarga las listas viejas o faltantes y recarga. Llamar fuera del hilo principal. */
    fun loadOrUpdate(context: Context, forceDownload: Boolean = false) {
        var changed = false
        for ((name, url) in SOURCES) {
            val file = File(context.filesDir, name)
            val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS
            if (forceDownload || stale) {
                try {
                    download(url, file); changed = true
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo descargar $name: ${e.message}")
                }
            }
        }
        if (changed || domains === BUILT_IN) loadCached(context)
    }

    private fun download(url: String, target: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val tmp = File(target.parentFile, "${target.name}.tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (tmp.length() < 1000) throw IllegalStateException("archivo vacío")
            tmp.renameTo(target)
        } finally {
            conn.disconnect()
        }
    }

    private val IGNORED = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "0.0.0.0"
    )
    private val SPACES = Regex("\\s+")

    private fun parseInto(file: File, out: MutableSet<String>) {
        file.useLines { lines ->
            for (raw in lines) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val parts = line.split(SPACES)
                val host = when {
                    parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1") -> parts[1]
                    parts.size == 1 -> parts[0].removePrefix("*.")
                    else -> continue
                }.lowercase()
                if (host !in IGNORED && host.contains('.')) out.add(host)
            }
        }
    }
}
