package dev.nk.musicplayer.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistResponseParserTest {

    private fun success(raw: String): LlmPlaylist {
        val result = PlaylistResponseParser.parse(raw)
        assertTrue("expected success, got $result", result is ParseResult.Success)
        return (result as ParseResult.Success).playlist
    }

    private fun failure(raw: String): String {
        val result = PlaylistResponseParser.parse(raw)
        assertTrue("expected failure, got $result", result is ParseResult.Failure)
        return (result as ParseResult.Failure).reason
    }

    @Test
    fun `parses a clean response`() {
        val playlist = success(
            """{"name":"Long Road","reasoning":"Gentle build.","indices":[12,47,3]}"""
        )
        assertEquals("Long Road", playlist.name)
        assertEquals("Gentle build.", playlist.reasoning)
        assertEquals(listOf(12, 47, 3), playlist.indices)
    }

    @Test
    fun `strips markdown fences`() {
        val playlist = success(
            """
            ```json
            {"name":"Calm","reasoning":"Soft.","indices":[1,2]}
            ```
            """.trimIndent()
        )
        assertEquals(listOf(1, 2), playlist.indices)
    }

    @Test
    fun `strips bare fences and surrounding commentary`() {
        val playlist = success(
            """
            Sure! Here is the playlist you asked for:
            ```
            {"name":"Drive","reasoning":"Steady.","indices":[5,6,7]}
            ```
            Enjoy.
            """.trimIndent()
        )
        assertEquals(listOf(5, 6, 7), playlist.indices)
    }

    @Test
    fun `handles an unterminated fence`() {
        val playlist = success("""```json {"name":"X","reasoning":"","indices":[9]}""")
        assertEquals(listOf(9), playlist.indices)
    }

    @Test
    fun `accepts indices sent as strings`() {
        val playlist = success("""{"name":"X","reasoning":"","indices":["12","47"]}""")
        assertEquals(listOf(12, 47), playlist.indices)
    }

    @Test
    fun `drops non-numeric index entries`() {
        val playlist = success("""{"name":"X","reasoning":"","indices":[1,"nope",2,null,3.5]}""")
        assertEquals(listOf(1, 2), playlist.indices)
    }

    @Test
    fun `defaults a missing name`() {
        val playlist = success("""{"indices":[1,2,3]}""")
        assertEquals("AI playlist", playlist.name)
        assertEquals("", playlist.reasoning)
    }

    @Test
    fun `fails on prose with no JSON`() {
        assertTrue(failure("I'm sorry, I can't help with that.").isNotEmpty())
    }

    @Test
    fun `fails on truncated JSON`() {
        assertTrue(failure("""{"name":"X","indices":[1,2""").isNotEmpty())
    }

    @Test
    fun `fails when indices are missing`() {
        assertEquals("Response had no \"indices\" field", failure("""{"name":"X"}"""))
    }

    @Test
    fun `fails when indices is not a list`() {
        assertEquals("\"indices\" was not a list", failure("""{"name":"X","indices":"1,2,3"}"""))
    }

    @Test
    fun `fails when indices holds nothing usable`() {
        assertTrue(failure("""{"name":"X","indices":["a","b"]}""").isNotEmpty())
    }

    @Test
    fun `fails on an empty response`() {
        assertTrue(failure("").isNotEmpty())
        assertTrue(failure("   ").isNotEmpty())
    }

    // --- sanitize: the guarantee that the model can never introduce a song I don't own ---

    @Test
    fun `sanitize drops out of range indices`() {
        assertEquals(
            listOf(0, 5, 9),
            PlaylistResponseParser.sanitize(listOf(-1, 0, 5, 10, 9, 4000), 0..9)
        )
    }

    @Test
    fun `sanitize drops duplicates but keeps the model's order`() {
        assertEquals(
            listOf(7, 2, 9),
            PlaylistResponseParser.sanitize(listOf(7, 2, 7, 9, 2), 0..9)
        )
    }

    @Test
    fun `sanitize on an empty library keeps nothing`() {
        assertEquals(emptyList<Int>(), PlaylistResponseParser.sanitize(listOf(0, 1, 2), IntRange.EMPTY))
    }

    @Test
    fun `end to end - a hallucinating model contributes nothing but valid indices`() {
        // The model invented titles and indices past the end of a 20-track library.
        val raw = """
            {"name":"Made Up","reasoning":"...",
             "indices":[3, 999, 7, 3, -2, 19, 20, "11"],
             "tracks":["Some Song That Does Not Exist - Nobody"]}
        """.trimIndent()
        val playlist = success(raw)
        assertEquals(
            listOf(3, 7, 19, 11),
            PlaylistResponseParser.sanitize(playlist.indices, 0..19)
        )
    }
}
