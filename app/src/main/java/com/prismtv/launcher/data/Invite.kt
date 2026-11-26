package com.prismtv.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.prismtv.launcher.BuildConfig
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Invite codes for the GitHub edition. Anyone can download it, so Pro needs a code made for this TV:
 * Settings › Prism Pro shows a short TV ID, the friend sends it to whoever runs Prism, and they make a
 * code for that ID with their invite key (kept out of this repository: CI builds it in from the
 * PRISM_INVITE_KEY secret). A code works only on the TV it was made for, and needs no internet.
 *
 * Installs from before invite codes keep Pro without one (see [grandfather]).
 *
 * Kept in Pro's own preferences file, so Reset Launcher leaves it alone. Both the code and the
 * grandfathered mark are tied to the TV ID, so a backup restored on another TV doesn't carry them over.
 */
object Invite {
    private const val Alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ" // Crockford base 32: no I, L, O, U
    private const val IdChars = 8
    private const val CodeChars = 10

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("prism_pro", Context.MODE_PRIVATE)

    /** Codes can be checked: this build has an invite key. */
    val configured: Boolean get() = BuildConfig.INVITE_KEY.isNotEmpty()

    /**
     * This TV's ID, as shown in Settings (XXXX-XXXX). From ANDROID_ID, which stays the same through
     * updates and reinstalls of Prism TV (same signing key) and changes only on a factory reset.
     */
    @SuppressLint("HardwareIds")
    fun deviceId(ctx: Context): String {
        val raw = runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
            ?: "unknown"
        val h = MessageDigest.getInstance("SHA-256").digest("prism-tv-id:$raw".toByteArray())
        return group(base32(h, IdChars))
    }

    /** The code for [id] with [key]: what scripts/invite-code makes on the PC. */
    fun codeFor(key: String, id: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return group(base32(mac.doFinal("prism-invite-v1:${normalise(id)}".toByteArray()), CodeChars))
    }

    /** Letters people mix up read as what they look like; dashes, spaces and case don't matter. */
    fun normalise(s: String): String = s.uppercase()
        .replace('O', '0').replace('I', '1').replace('L', '1')
        .filter { it in Alphabet }

    /** Whether [code] unlocks this TV. */
    fun valid(ctx: Context, code: String): Boolean {
        if (!configured) return false
        val want = normalise(codeFor(BuildConfig.INVITE_KEY, deviceId(ctx)))
        val got = normalise(code)
        return got.length == want.length && MessageDigest.isEqual(got.toByteArray(), want.toByteArray())
    }

    /** Checks and keeps [code]. True when it unlocked Pro. */
    fun redeem(ctx: Context, code: String): Boolean {
        if (!valid(ctx, code)) return false
        sp(ctx).edit().putString("inviteCode", normalise(code)).apply()
        return true
    }

    /** Pro is unlocked here: a code made for this TV, or an install from before invite codes. */
    fun unlocked(ctx: Context): Boolean {
        grandfather(ctx)
        val sp = sp(ctx)
        val id = deviceId(ctx)
        if (sp.getString("inviteGrandfathered", null) == id) return true
        if (BuildConfig.DEBUG && sp.getBoolean("inviteDebug", false)) return true
        return sp.getString("inviteCode", null)?.let { valid(ctx, it) } == true
    }

    /**
     * Decided once, the first time this version runs. An install that has been updated (any install from
     * before invite codes, since only the newest build can be downloaded) keeps Pro without a code. A
     * fresh install needs one.
     */
    private fun grandfather(ctx: Context) {
        val sp = sp(ctx)
        if (sp.getBoolean("inviteDecided", false)) return
        val updated = runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            pi.lastUpdateTime > pi.firstInstallTime
        }.getOrDefault(false)
        sp.edit().apply {
            if (updated) putString("inviteGrandfathered", deviceId(ctx))
            putBoolean("inviteDecided", true)
        }.apply()
    }

    /** First run of a fresh install: ask for a code once. */
    fun shouldPrompt(ctx: Context): Boolean = !unlocked(ctx) && !sp(ctx).getBoolean("invitePrompted", false)

    fun prompted(ctx: Context) {
        sp(ctx).edit().putBoolean("invitePrompted", true).apply()
    }

    /** Debug builds only (CI screenshots): "ok" unlocks, "locked" doesn't. */
    fun debugSet(ctx: Context, state: String) {
        if (!BuildConfig.DEBUG) return
        sp(ctx).edit().putBoolean("inviteDebug", state == "ok").putBoolean("inviteDecided", true)
            .putBoolean("invitePrompted", true).remove("inviteGrandfathered").apply()
    }

    private fun group(s: String): String = s.chunked(if (s.length == IdChars) 4 else 5).joinToString("-")

    private fun base32(bytes: ByteArray, chars: Int): String {
        val sb = StringBuilder()
        var buf = 0
        var bits = 0
        for (b in bytes) {
            buf = (buf shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5 && sb.length < chars) {
                sb.append(Alphabet[(buf shr (bits - 5)) and 31])
                bits -= 5
            }
            buf = buf and ((1 shl bits) - 1)
            if (sb.length >= chars) break
        }
        return sb.toString()
    }
}
