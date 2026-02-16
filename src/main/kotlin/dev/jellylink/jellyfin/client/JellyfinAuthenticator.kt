package dev.jellylink.jellyfin.client

import dev.jellylink.jellyfin.config.JellyfinConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID

/**
 * Handles Jellyfin authentication and token lifecycle.
 *
 * Maintains the current access token, user ID, and automatic expiration /
 * re-authentication logic.
 */
@Component
class JellyfinAuthenticator(
    private val config: JellyfinConfig,
    private val responseParser: JellyfinResponseParser = JellyfinResponseParser(),
) {
    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var userId: String? = null
        private set

    @Volatile
    private var tokenObtainedAt: Instant? = null

    /**
     * Ensure a valid access token is available, authenticating if necessary.
     *
     * @return `true` when a valid token is ready for use
     */
    fun ensureAuthenticated(): Boolean {
        if (accessToken != null && userId != null && !isTokenExpired()) {
            return true
        }

        if (accessToken != null && isTokenExpired()) {
            log.info("Jellyfin access token expired after {} minutes, re-authenticating", config.tokenRefreshMinutes)
            invalidateToken()
        }

        if (config.baseUrl.isBlank() || config.username.isBlank() || config.password.isBlank()) {
            return false
        }

        return authenticate()
    }

    /**
     * Invalidate the current token so the next call will re-authenticate.
     */
    fun invalidateToken() {
        log.info("Invalidating Jellyfin access token")
        accessToken = null
        userId = null
        tokenObtainedAt = null
    }

    private fun isTokenExpired(): Boolean {
        val refreshMinutes = config.tokenRefreshMinutes

        if (refreshMinutes <= 0) {
            return false
        }

        val obtainedAt = tokenObtainedAt ?: return true

        return Instant.now().isAfter(obtainedAt.plusSeconds(refreshMinutes * SECONDS_PER_MINUTE))
    }

    private fun authenticate(): Boolean {
        val url = buildString {
            append(config.baseUrl.trimEnd('/'))
            append("/Users/AuthenticateByName")
        }

        val body = """{"Username":"${escape(config.username)}","Pw":"${escape(config.password)}"}"""
        val deviceId = UUID.randomUUID()
        val version = getVersionNumber()

        val authHeader = buildString {
            append("MediaBrowser ")
            append("Client=\"Jellylink\", ")
            append("Device=\"Lavalink\", ")
            append("DeviceId=\"$deviceId\", ")
            append("Version=\"$version\"")
        }

        val request = buildAuthenticatonRequest(url, authHeader, body)

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in HTTP_OK_RANGE) {
            log.error("Jellyfin auth failed with status {}: {}", response.statusCode(), response.body().take(ERROR_BODY_PREVIEW_LENGTH))
            return false
        }

        log.info("Successfully authenticated with Jellyfin")
        val result = responseParser.parseAuthResponse(response.body()) ?: return false

        accessToken = result.accessToken
        userId = result.userId
        tokenObtainedAt = Instant.now()

        return true
    }

    private fun getVersionNumber(): String {
        return this::class.java.getPackage().implementationVersion ?: "unknown"
    }

    private fun buildAuthenticatonRequest(
        url: String,
        authHeader: String,
        body: String
    ): HttpRequest? {
        return HttpRequest
            .newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("X-Emby-Authorization", authHeader)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    companion object {
        private val log = LoggerFactory.getLogger(JellyfinAuthenticator::class.java)
        private val httpClient: HttpClient = HttpClient.newHttpClient()

        private val HTTP_OK_RANGE = 200..299
        private const val ERROR_BODY_PREVIEW_LENGTH = 500
        private const val SECONDS_PER_MINUTE = 60L
    }
}
