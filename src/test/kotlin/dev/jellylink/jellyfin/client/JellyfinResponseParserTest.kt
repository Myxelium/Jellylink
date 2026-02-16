package dev.jellylink.jellyfin.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JellyfinResponseParserTest {
    private lateinit var parser: JellyfinResponseParser

    @BeforeEach
    fun setup() {
        parser = JellyfinResponseParser()
    }

    @Test
    fun `should parse valid auth response`() {
        val json =
            """
            {
                "AccessToken": "test-token-123",
                "User": {
                    "Id": "user-456"
                }
            }
            """.trimIndent()

        val result = parser.parseAuthResponse(json)

        assertNotNull(result)
        assertEquals("test-token-123", result?.accessToken)
        assertEquals("user-456", result?.userId)
    }

    @Test
    fun `should return null for auth response missing token`() {
        val json =
            """
            {
                "User": {
                    "Id": "user-456"
                }
            }
            """.trimIndent()

        val result = parser.parseAuthResponse(json)
        assertNull(result)
    }

    @Test
    fun `should return null for auth response missing user ID`() {
        val json =
            """
            {
                "AccessToken": "test-token-123"
            }
            """.trimIndent()

        val result = parser.parseAuthResponse(json)
        assertNull(result)
    }

    @Test
    fun `should parse audio item with all fields`() {
        val json =
            """
            {
                "Items": [
                    {
                        "Id": "audio-123",
                        "Name": "Bohemian Rhapsody",
                        "AlbumArtist": "Queen",
                        "Album": "A Night at the Opera",
                        "RunTimeTicks": 3540000000,
                        "ImageTags": {
                            "Primary": "abc123"
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = parser.parseFirstAudioItem(json, "http://jellyfin.local:8096")

        assertNotNull(result)
        assertEquals("audio-123", result?.id)
        assertEquals("Bohemian Rhapsody", result?.title)
        assertEquals("Queen", result?.artist)
        assertEquals("A Night at the Opera", result?.album)
        assertEquals(354000L, result?.lengthMs)
        assertNotNull(result?.artworkUrl)
    }

    @Test
    fun `should parse audio item with Artists array`() {
        val json =
            """
            {
                "Items": [
                    {
                        "Id": "audio-123",
                        "Name": "Test Song",
                        "Artists": ["Artist 1", "Artist 2"],
                        "RunTimeTicks": 1800000000
                    }
                ]
            }
            """.trimIndent()

        val result = parser.parseFirstAudioItem(json, "http://jellyfin.local:8096")

        assertNotNull(result)
        assertEquals("Artist 1", result?.artist)
    }

    @Test
    fun `should handle negative RunTimeTicks`() {
        val json =
            """
            {
                "Items": [
                    {
                        "Id": "audio-123",
                        "Name": "Test Song",
                        "RunTimeTicks": -1000
                    }
                ]
            }
            """.trimIndent()

        val result = parser.parseFirstAudioItem(json, "http://jellyfin.local:8096")

        assertNotNull(result)
        assertNull(result?.lengthMs)
    }

    @Test
    fun `should handle zero RunTimeTicks`() {
        val json =
            """
            {
                "Items": [
                    {
                        "Id": "audio-123",
                        "Name": "Test Song",
                        "RunTimeTicks": 0
                    }
                ]
            }
            """.trimIndent()

        val result = parser.parseFirstAudioItem(json, "http://jellyfin.local:8096")

        assertNotNull(result)
        assertNull(result?.lengthMs)
    }

    @Test
    fun `should return null for empty Items array`() {
        val json =
            """
            {
                "Items": []
            }
            """.trimIndent()

        val result = parser.parseFirstAudioItem(json, "http://jellyfin.local:8096")
        assertNull(result)
    }

    @Test
    fun `should return null for missing Items field`() {
        val json =
            """
            {
                "TotalRecordCount": 0
            }
            """.trimIndent()

        val result = parser.parseFirstAudioItem(json, "http://jellyfin.local:8096")
        assertNull(result)
    }

    @Test
    fun `should build artwork URL with image tag`() {
        val json =
            """
            {
                "Items": [
                    {
                        "Id": "audio-123",
                        "Name": "Test Song",
                        "ImageTags": {
                            "Primary": "tag123"
                        }
                    }
                ]
            }
            """.trimIndent()

        val result = parser.parseFirstAudioItem(json, "http://jellyfin.local:8096")

        assertNotNull(result)
        assertEquals("http://jellyfin.local:8096/Items/audio-123/Images/Primary?tag=tag123", result?.artworkUrl)
    }

    @Test
    fun `should build artwork URL without image tag`() {
        val json =
            """
            {
                "Items": [
                    {
                        "Id": "audio-123",
                        "Name": "Test Song"
                    }
                ]
            }
            """.trimIndent()

        val result = parser.parseFirstAudioItem(json, "http://jellyfin.local:8096")

        assertNotNull(result)
        assertEquals("http://jellyfin.local:8096/Items/audio-123/Images/Primary", result?.artworkUrl)
    }

    @Test
    fun `should trim trailing slash from base URL`() {
        val json =
            """
            {
                "Items": [
                    {
                        "Id": "audio-123",
                        "Name": "Test Song"
                    }
                ]
            }
            """.trimIndent()

        val result = parser.parseFirstAudioItem(json, "http://jellyfin.local:8096/")

        assertNotNull(result)
        assertEquals("http://jellyfin.local:8096/Items/audio-123/Images/Primary", result?.artworkUrl)
    }
}
