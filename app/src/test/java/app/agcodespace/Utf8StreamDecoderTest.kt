package app.agcodespace

import app.agcodespace.runtime.Utf8StreamDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Utf8StreamDecoderTest {
    @Test fun preservesCharactersSplitAcrossPtyReads() {
        val decoder = Utf8StreamDecoder()
        val bytes = "你好🌍".toByteArray(Charsets.UTF_8)
        assertTrue(decoder.decode(bytes.copyOfRange(0, 2)).isEmpty())
        assertEquals("你好🌍", decoder.decode(bytes.copyOfRange(2, bytes.size)))
    }
}
