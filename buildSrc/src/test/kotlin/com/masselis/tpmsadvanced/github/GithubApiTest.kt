package com.masselis.tpmsadvanced.github

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

class GithubApiTest {
    @Test fun `repository rejects URL authority traversal and query injection`() {
        listOf("owner/repo").forEach { assertEquals(it, GithubApi.validateRepository(it)) }
        listOf("https://evil.test/repo", "owner/../repo", "owner/..", "owner/repo?x=y", "owner/repo#x",
            "owner/repo@evil.test/", "owner/repo\r\nX: y", "owner\\repo").forEach {
            assertThrows(IllegalArgumentException::class.java) { GithubApi.validateRepository(it) }
        }
    }

    @Test fun `authorization stays in HTTPS headers and redirects are refused`() {
        val connection = Response(302)
        val api = GithubApi("test-credential", "owner/repo") { connection }
        assertThrows(IllegalStateException::class.java) { api.request("GET", "/releases") }
        assertFalse(connection.instanceFollowRedirects)
        assertEquals("Bearer test-credential", connection.getRequestProperty("Authorization"))
        assertTrue(connection.disconnected)
    }

    @Test fun `API failures fail closed except explicitly handled conflicts`() {
        listOf(401, 403, 429, 500).forEach { code ->
            val api = GithubApi("test", "owner/repo") { Response(code) }
            assertThrows(IllegalStateException::class.java) { api.request("POST", "/releases", "{}") }
        }
        assertEquals("{}", GithubApi("test", "owner/repo") { Response(422) }
            .request("POST", "/pulls", "{}", setOf(422)))
    }

    @Test fun `oversized responses and header injection are rejected`() {
        val oversized = GithubApi("test", "owner/repo") { Response(200, "x".repeat(4_000_001)) }
        assertThrows(IllegalStateException::class.java) { oversized.request("GET", "/releases") }
        val injected = GithubApi("test\r\nX: injected", "owner/repo") { error("Must not connect") }
        assertThrows(IllegalArgumentException::class.java) { injected.request("GET", "/releases") }
    }

    @Test fun `branch and asset names stay within their URL component`() {
        assertEquals("release%2Ftest%3Fx%3Dy%23z%20a", GithubApi.encode("release/test?x=y#z a"))
        var destination: URL? = null
        val api = GithubApi("test", "fork/project") { destination = it; Response(200) }
        assertEquals("{}", api.request("GET", "/releases"))
        assertEquals("https://api.github.com/repos/fork/project/releases", destination.toString())
    }

    private class Response(private val code: Int, private val body: String = "{}") :
        HttpsURLConnection(URL("https://api.github.com/")) {
        var disconnected = false
        override fun getResponseCode(): Int = code
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream() = ByteArrayInputStream(body.toByteArray())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun disconnect() { disconnected = true }
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
        override fun getCipherSuite(): String = "test"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
}
