package com.prismtv.launcher.aerial

import android.content.Context
import android.os.Build

/**
 * Whether this TV reaches Apple's aerial host over HTTPS. Some Android TV trust stores lack the anchor
 * for sylvan.apple.com (the SHIELD logs "Trust anchor for certification path not found" on every
 * start), so the screensaver and the Home wallpaper retry over plain HTTP (allowed for that one host in
 * network_security_config). Once a TLS failure has been seen on this Android build, both start on HTTP
 * straight away instead of failing first every time; an OS update (new fingerprint) tries HTTPS again.
 */
internal object AerialNet {
    private const val PREFS = "prism_aerial_net"
    @Volatile private var cached: Boolean? = null

    fun preferHttp(context: Context): Boolean = cached ?: run {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        (sp.getString("http", null) == Build.FINGERPRINT).also { cached = it }
    }

    /** [error] made the player fall back to HTTP; remember that only if it was a TLS failure. */
    fun fellBack(context: Context, error: Throwable?) {
        if (cached == true || !isTls(error)) return
        cached = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("http", Build.FINGERPRINT).apply()
        android.util.Log.i("PrismAerial", "HTTPS to the aerial host fails on this TV: using HTTP from now on")
    }

    private fun isTls(error: Throwable?): Boolean {
        var e = error
        var depth = 0
        while (e != null && depth++ < 12) {
            if (e is javax.net.ssl.SSLException || e is java.security.cert.CertificateException) return true
            e = e.cause
        }
        return false
    }
}
