package dev.nk.musicplayer.data.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongAnalysisParserTest {

    private fun success(raw: String): SongVerdict {
        val result = SongAnalysisParser.parse(raw)
        assertTrue("expected success, got $result", result is AnalysisParseResult.Success)
        return (result as AnalysisParseResult.Success).verdict
    }

    private fun failure(raw: String): String {
        val result = SongAnalysisParser.parse(raw)
        assertTrue("expected failure, got $result", result is AnalysisParseResult.Failure)
        return (result as AnalysisParseResult.Failure).reason
    }

    @Test
    fun `parses a clean verdict`() {
        val verdict = success(
            """
            {"language":"en","sentiment":"positive","mood":"hopeful","category":"driving",
             "genre":"indie rock","valence":0.8,"energy":0.7,
             "themes":["road","freedom"],"summary":"A long drive out of town.","explicit":false}
            """.trimIndent()
        )
        assertEquals("en", verdict.language)
        assertEquals("positive", verdict.sentiment)
        assertEquals("hopeful", verdict.mood)
        assertEquals("driving", verdict.category)
        assertEquals("indie rock", verdict.genre)
        assertEquals(0.8f, verdict.valence, 0.001f)
        assertEquals(listOf("road", "freedom"), verdict.themes)
        assertFalse(verdict.explicit)
    }

    @Test
    fun `reads through markdown fences and chatter`() {
        val verdict = success(
            """
            Here is the analysis:
            ```json
            {"sentiment":"negative","mood":"melancholic","category":"heartbreak"}
            ```
            """.trimIndent()
        )
        assertEquals("melancholic", verdict.mood)
        assertEquals("heartbreak", verdict.category)
    }

    @Test
    fun `folds synonyms onto the vocabulary`() {
        assertEquals("melancholic", success("""{"mood":"Melancholy."}""").mood)
        assertEquals("happy", success("""{"mood":"joyful"}""").mood)
        assertEquals("workout", success("""{"category":"gym"}""").category)
        assertEquals("driving", success("""{"category":"road trip"}""").category)
        assertEquals("focus", success("""{"category":"studying"}""").category)
    }

    @Test
    fun `finds a vocabulary word inside a phrase`() {
        assertEquals("hopeful", success("""{"mood":"quietly hopeful"}""").mood)
        assertEquals("party", success("""{"category":"late-night party"}""").category)
    }

    @Test
    fun `keeps an unknown label rather than dropping it`() {
        assertEquals("yearning", success("""{"mood":"yearning"}""").mood)
    }

    @Test
    fun `accepts numbers on the 0-100 scale and as strings`() {
        val verdict = success("""{"mood":"calm","valence":"72","energy":25}""")
        assertEquals(0.72f, verdict.valence, 0.001f)
        assertEquals(0.25f, verdict.energy, 0.001f)
    }

    @Test
    fun `clamps numbers into range`() {
        val verdict = success("""{"mood":"angry","valence":-3,"energy":900}""")
        assertEquals(0f, verdict.valence, 0.001f)
        assertEquals(1f, verdict.energy, 0.001f)
    }

    @Test
    fun `derives missing numbers from the classification`() {
        val verdict = success("""{"mood":"calm","sentiment":"negative","category":"sleep"}""")
        assertEquals(0.2f, verdict.valence, 0.001f)
        assertEquals(0.3f, verdict.energy, 0.001f)
    }

    @Test
    fun `accepts themes as a comma separated string`() {
        val verdict = success("""{"mood":"sad","themes":"Loss, Memory , home"}""")
        assertEquals(listOf("loss", "memory", "home"), verdict.themes)
    }

    @Test
    fun `caps and de-duplicates themes`() {
        val verdict = success(
            """{"mood":"sad","themes":["a","a","b","c","d","e","f","g"]}"""
        )
        assertEquals(listOf("a", "b", "c", "d", "e"), verdict.themes)
    }

    @Test
    fun `reads explicit in the several shapes models use`() {
        assertTrue(success("""{"mood":"angry","explicit":true}""").explicit)
        assertTrue(success("""{"mood":"angry","explicit":"yes"}""").explicit)
        assertFalse(success("""{"mood":"angry","explicit":"no"}""").explicit)
        assertFalse(success("""{"mood":"angry"}""").explicit)
    }

    @Test
    fun `fills in defaults for a sparse verdict`() {
        val verdict = success("""{"mood":"dark"}""")
        assertEquals("neutral", verdict.sentiment)
        assertEquals("chill", verdict.category)
        assertEquals("unknown", verdict.language)
        assertEquals("unknown", verdict.genre)
        assertTrue(verdict.themes.isEmpty())
    }

    @Test
    fun `rejects a reply about something else`() {
        assertTrue(failure("""{"title":"Some Song","artist":"Someone"}""").isNotEmpty())
        assertTrue(failure("I could not analyse that song.").isNotEmpty())
        assertTrue(failure("").isNotEmpty())
    }
}
