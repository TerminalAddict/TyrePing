package com.masselis.tpmsadvanced.github

import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import javax.net.ssl.HttpsURLConnection

/** Keep credentials out of process arguments and refuse redirects to another credential destination. */
internal class GithubApi(private val token: String, repository: String,
    private val connect: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }) {
    val repository: String = validateRepository(repository)
    val owner: String get() = repository.substringBefore('/')

    fun request(method: String, path: String, body: String? = null,
        acceptedErrors: Set<Int> = emptySet()): String =
        requestUrl("https://api.github.com/repos/$repository$path", method, body, null, acceptedErrors)

    fun graphql(body: String): String = requestUrl("https://api.github.com/graphql", "POST", body)

    fun upload(releaseId: Long, file: File, assetName: String = file.name): String = requestUrl(
        "https://uploads.github.com/repos/$repository/releases/$releaseId/assets?name=${encode(assetName)}",
        "POST", file = file)

    private fun requestUrl(url: String, method: String, body: String? = null, file: File? = null,
        acceptedErrors: Set<Int> = emptySet()): String {
        require(token.isNotBlank() && token.none { it == '\r' || it == '\n' }) { "Invalid GitHub credential" }
        val connection = connect(URI(url).toURL())
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 120_000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "TyrePing-release-tools")
            if (file != null || body != null) {
                val bytes = body?.toByteArray(UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type",
                    if (file != null) "application/octet-stream" else "application/json")
                connection.setFixedLengthStreamingMode(file?.length() ?: checkNotNull(bytes).size.toLong())
                connection.outputStream.use { output ->
                    if (file != null) file.inputStream().use { it.copyTo(output) }
                    else output.write(checkNotNull(bytes))
                }
            }
            val status = connection.responseCode
            check(status in 200..299 || status in acceptedErrors) { "GitHub request failed: HTTP $status" }
            return (if (status in 200..299) connection.inputStream else connection.errorStream)?.use {
                val bytes = it.readNBytes(MAX_RESPONSE_BYTES + 1)
                check(bytes.size <= MAX_RESPONSE_BYTES) { "GitHub response exceeds the size limit" }
                bytes.toString(UTF_8)
            }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 4_000_000

        fun validateRepository(value: String): String = value.also {
            require(Regex("[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9_.-]+").matches(it) &&
                it.substringAfter('/') !in setOf(".", "..")) { "GitHub repository must be owner/name" }
        }

        fun encode(value: String): String = URLEncoder.encode(value, UTF_8).replace("+", "%20")
    }
}
