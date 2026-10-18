package com.masselis.tpmsadvanced.ressource

import java.security.MessageDigest

/** Keep combined task invocations within filesystem component limits without truncation collisions. */
internal fun resourceReportName(tasks: List<String>): String {
    val name = tasks.joinToString("_")
    val prefix = name.replace("[^a-zA-Z0-9.\\-]".toRegex(), "_").take(100).ifBlank { "build" }
    val hash = MessageDigest.getInstance("SHA-256").digest(name.toByteArray(Charsets.UTF_8))
        .take(8).joinToString("") { "%02x".format(it) }
    return "$prefix-$hash"
}
