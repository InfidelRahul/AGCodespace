package app.agcodespace

import app.agcodespace.runtime.VtScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VtScreenTest {
    @Test fun carriageReturnOverwritesCurrentLine() {
        val screen = VtScreen(8, 3)
        screen.feed("hello\rworld")
        assertTrue(screen.render().startsWith("world"))
    }

    @Test fun cursorAddressingWritesAtRequestedCell() {
        val screen = VtScreen(8, 3)
        screen.feed("\u001b[2;3HXY")
        val lines = screen.render().split('\n')
        assertTrue(lines[1].startsWith("  XY"))
    }

    @Test fun eraseDisplayClearsAllCells() {
        val screen = VtScreen(8, 3)
        screen.feed("hello\u001b[?25l\u001b[2J")
        assertEquals("        \n        \n        ", screen.render())
    }
}
