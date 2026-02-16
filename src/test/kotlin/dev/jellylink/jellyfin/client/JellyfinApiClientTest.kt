package dev.jellylink.jellyfin.client

import dev.jellylink.jellyfin.config.JellyfinConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JellyfinApiClientTest {
    private lateinit var config: JellyfinConfig

    @BeforeEach
    fun setup() {
        config = JellyfinConfig().apply {
            baseUrl = "http://localhost:8096"
            username = "testuser"
            password = "testpass"
            searchLimit = 5
            audioQuality = "ORIGINAL"
            audioCodec = "mp3"
            tokenRefreshMinutes = 30
        }
    }

    @Test
    fun `should return false when config is blank`() {
        config.baseUrl = ""
        val client = JellyfinApiClient(config)
        val result = client.ensureAuthenticated()
        assertFalse(result)
    }

    @Test
    fun `should build playback URL for ORIGINAL quality`() {
        val client = JellyfinApiClient(config)
        // Use reflection to set the token for testing
        val tokenField = JellyfinApiClient::class.java.getDeclaredField("accessToken")
        tokenField.isAccessible = true
        tokenField.set(client, "test-token")

        val url = client.buildPlaybackUrl("item-123")

        assertTrue(url.contains("/Audio/item-123/stream"))
        assertTrue(url.contains("static=true"))
        assertTrue(url.contains("api_key=test-token"))
    }

    @Test
    fun `should build playback URL for HIGH quality`() {
        config.audioQuality = "HIGH"
        val client = JellyfinApiClient(config)
        val tokenField = JellyfinApiClient::class.java.getDeclaredField("accessToken")
        tokenField.isAccessible = true
        tokenField.set(client, "test-token")

        val url = client.buildPlaybackUrl("item-123")

        assertTrue(url.contains("/Audio/item-123/stream"))
        assertTrue(url.contains("audioBitRate=320000"))
        assertTrue(url.contains("audioCodec=mp3"))
        assertTrue(url.contains("api_key=test-token"))
    }

    @Test
    fun `should build playback URL for MEDIUM quality`() {
        config.audioQuality = "MEDIUM"
        val client = JellyfinApiClient(config)
        val tokenField = JellyfinApiClient::class.java.getDeclaredField("accessToken")
        tokenField.isAccessible = true
        tokenField.set(client, "test-token")

        val url = client.buildPlaybackUrl("item-123")

        assertTrue(url.contains("audioBitRate=192000"))
    }

    @Test
    fun `should build playback URL for LOW quality`() {
        config.audioQuality = "LOW"
        val client = JellyfinApiClient(config)
        val tokenField = JellyfinApiClient::class.java.getDeclaredField("accessToken")
        tokenField.isAccessible = true
        tokenField.set(client, "test-token")

        val url = client.buildPlaybackUrl("item-123")

        assertTrue(url.contains("audioBitRate=128000"))
    }

    @Test
    fun `should build playback URL for custom bitrate`() {
        config.audioQuality = "256"
        val client = JellyfinApiClient(config)
        val tokenField = JellyfinApiClient::class.java.getDeclaredField("accessToken")
        tokenField.isAccessible = true
        tokenField.set(client, "test-token")

        val url = client.buildPlaybackUrl("item-123")

        assertTrue(url.contains("audioBitRate=256000"))
    }

    @Test
    fun `should use default HIGH quality for invalid quality string`() {
        config.audioQuality = "INVALID"
        val client = JellyfinApiClient(config)
        val tokenField = JellyfinApiClient::class.java.getDeclaredField("accessToken")
        tokenField.isAccessible = true
        tokenField.set(client, "test-token")

        val url = client.buildPlaybackUrl("item-123")

        assertTrue(url.contains("audioBitRate=320000"))
    }

    @Test
    fun `should use custom codec`() {
        config.audioQuality = "HIGH"
        config.audioCodec = "opus"
        val client = JellyfinApiClient(config)
        val tokenField = JellyfinApiClient::class.java.getDeclaredField("accessToken")
        tokenField.isAccessible = true
        tokenField.set(client, "test-token")

        val url = client.buildPlaybackUrl("item-123")

        assertTrue(url.contains("audioCodec=opus"))
    }

    @Test
    fun `should invalidate token`() {
        val client = JellyfinApiClient(config)
        val tokenField = JellyfinApiClient::class.java.getDeclaredField("accessToken")
        tokenField.isAccessible = true
        tokenField.set(client, "old-token")

        assertNotNull(client.accessToken)
        client.invalidateToken()
        assertEquals(null, client.accessToken)
    }

    @Test
    fun `should trim base URL in playback URL`() {
        config.baseUrl = "http://localhost:8096/"
        val client = JellyfinApiClient(config)
        val tokenField = JellyfinApiClient::class.java.getDeclaredField("accessToken")
        tokenField.isAccessible = true
        tokenField.set(client, "test-token")

        val url = client.buildPlaybackUrl("item-123")

        // Should not have double slashes
        assertFalse(url.contains("8096//"))
        assertTrue(url.startsWith("http://localhost:8096/Audio/"))
    }
}
