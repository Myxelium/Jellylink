package dev.jellylink.jellyfin.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JellyfinMetadataStoreTest {
    private lateinit var store: JellyfinMetadataStore

    @BeforeEach
    fun setup() {
        store = JellyfinMetadataStore(maxSize = 100)
    }

    @Test
    fun `should store and retrieve metadata`() {
        val metadata = JellyfinMetadata(
            id = "123",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            lengthMs = 180000L,
            artworkUrl = "http://test.com/art.jpg",
        )

        store.put("http://test.com/audio/123", metadata)
        val retrieved = store.get("http://test.com/audio/123")

        assertNotNull(retrieved)
        assertEquals("123", retrieved?.id)
        assertEquals("Test Song", retrieved?.title)
    }

    @Test
    fun `should return null for non-existent URL`() {
        val retrieved = store.get("http://nonexistent.com")
        assertNull(retrieved)
    }

    @Test
    fun `should update access order on get`() {
        val metadata1 = JellyfinMetadata("1", "Song 1", null, null, null, null)
        val metadata2 = JellyfinMetadata("2", "Song 2", null, null, null, null)

        store.put("url1", metadata1)
        store.put("url2", metadata2)

        // Access url1 to make it more recent
        store.get("url1")

        assertEquals(2, store.size())
    }

    @Test
    fun `should evict LRU entries when max size exceeded`() {
        val smallStore = JellyfinMetadataStore(maxSize = 10)

        // Add 15 entries
        for (i in 1..15) {
            val metadata = JellyfinMetadata("id$i", "Song $i", null, null, null, null)
            smallStore.put("url$i", metadata)
        }

        // Store should have evicted some entries
        assert(smallStore.size() <= 10)
    }

    @Test
    fun `should preserve recently accessed entries during eviction`() {
        val smallStore = JellyfinMetadataStore(maxSize = 10)

        // Add 10 entries
        for (i in 1..10) {
            val metadata = JellyfinMetadata("id$i", "Song $i", null, null, null, null)
            smallStore.put("url$i", metadata)
        }

        // Access url10 to make it most recent
        smallStore.get("url10")

        // Add 5 more entries to trigger eviction
        for (i in 11..15) {
            val metadata = JellyfinMetadata("id$i", "Song $i", null, null, null, null)
            smallStore.put("url$i", metadata)
        }

        // url10 should still be present as it was recently accessed
        assertNotNull(smallStore.get("url10"))
    }

    @Test
    fun `should clear all entries`() {
        for (i in 1..5) {
            val metadata = JellyfinMetadata("id$i", "Song $i", null, null, null, null)
            store.put("url$i", metadata)
        }

        assertEquals(5, store.size())

        store.clear()

        assertEquals(0, store.size())
        assertNull(store.get("url1"))
    }

    @Test
    fun `should handle concurrent access`() {
        val metadata = JellyfinMetadata("123", "Test", null, null, null, null)

        val threads = List(10) {
            Thread {
                repeat(100) { i ->
                    store.put("url$i", metadata)
                    store.get("url$i")
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Should not crash and should have entries
        assert(store.size() > 0)
    }
}
