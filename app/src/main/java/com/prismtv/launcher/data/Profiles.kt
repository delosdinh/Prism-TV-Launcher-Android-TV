package com.prismtv.launcher.data

import java.security.MessageDigest

/**
 * A person using the TV: their name and picture, an optional passcode (needed to switch to them) and
 * the apps they can't see (tvOS 27's per-profile app blocking). The Home Screen layout is shared.
 */
data class ProfileSpec(
    val id: String,
    val name: String,
    /** "preset:<n>" or "photo:<content uri>". */
    val avatar: String,
    /** Hash of the 4-digit passcode; empty = none. */
    val passcode: String = "",
    /** App ids hidden from this profile. */
    val blocked: Set<String> = emptySet(),
) {
    val locked: Boolean get() = passcode.isNotEmpty()
    val restricted: Boolean get() = blocked.isNotEmpty()
}

object Profiles {
    /** The profile everyone starts with (the original single profile). */
    const val MainId = "main"

    fun hash(code: String): String =
        MessageDigest.getInstance("SHA-256").digest("prism-profile:$code".toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun matches(p: ProfileSpec, code: String): Boolean = p.locked && p.passcode == hash(code)

    fun newId(): String = "p" + System.currentTimeMillis().toString(36)

    /** One line per profile: id, name, avatar, passcode hash, blocked app ids (tab-separated). */
    fun encode(list: List<ProfileSpec>): String = list.joinToString("\n") { p ->
        listOf(p.id, p.name, p.avatar, p.passcode, p.blocked.joinToString(","))
            .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
    }

    fun decode(raw: String?): List<ProfileSpec> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 3 || f[0].isBlank()) return@mapNotNull null
            ProfileSpec(
                id = f[0],
                name = f[1],
                avatar = f[2].ifBlank { "preset:0" },
                passcode = f.getOrNull(3).orEmpty(),
                blocked = f.getOrNull(4).orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
            )
        }
    }
}
