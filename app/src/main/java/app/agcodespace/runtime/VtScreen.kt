package app.agcodespace.runtime

/** Small, dependency-free VT screen model for interactive TUI applications. */
class VtScreen(private val columns: Int = 48, private val rows: Int = 24) {
    private data class Cell(var ch: Char = ' ')
    private fun blank() = Array(rows) { Array(columns) { Cell() } }
    private var primary = blank()
    private var alternate = blank()
    private var usingAlternate = false
    private var cursorVisible = true
    private val screen get() = if (usingAlternate) alternate else primary
    private var row = 0
    private var col = 0
    private var savedRow = 0
    private var savedCol = 0
    private var scrollTop = 0
    private var scrollBottom = rows - 1
    private var parser = 0 // 0=text, 1=ESC, 2=CSI, 3=OSC, 4=OSC-after-ESC
    private val sequence = StringBuilder()

    fun reset() {
        primary = blank(); alternate = blank(); usingAlternate = false
        row = 0; col = 0; savedRow = 0; savedCol = 0; cursorVisible = true
        scrollTop = 0; scrollBottom = rows - 1; parser = 0; sequence.setLength(0)
    }

    @Synchronized fun feed(text: String) {
        text.forEach { ch ->
            when (parser) {
                0 -> when (ch) {
                    '\u001b' -> { parser = 1; sequence.setLength(0) }
                    '\r' -> col = 0
                    '\n' -> lineFeed()
                    '\b' -> col = (col - 1).coerceAtLeast(0)
                    '\t' -> col = ((col / 8 + 1) * 8).coerceAtMost(columns - 1)
                    '\u0007' -> Unit
                    else -> if (ch >= ' ' && ch != '\u007f') put(ch)
                }
                1 -> when (ch) {
                    '[' -> { parser = 2; sequence.setLength(0) }
                    ']' -> { parser = 3; sequence.setLength(0) }
                    '7' -> { savedRow = row; savedCol = col; parser = 0 }
                    '8' -> { row = savedRow.coerceIn(0, rows - 1); col = savedCol.coerceIn(0, columns - 1); parser = 0 }
                    'D' -> { lineFeed(); parser = 0 }
                    'E' -> { col = 0; lineFeed(); parser = 0 }
                    'M' -> { reverseIndex(); parser = 0 }
                    'c' -> { reset() }
                    else -> parser = 0
                }
                2 -> {
                    if (ch in '@'..'~') { executeCsi(sequence.toString(), ch); parser = 0; sequence.setLength(0) }
                    else if (sequence.length < 128) sequence.append(ch)
                }
                3 -> when (ch) {
                    '\u0007' -> parser = 0
                    '\u001b' -> parser = 4
                    else -> if (sequence.length < 4096) sequence.append(ch)
                }
                4 -> parser = if (ch == '\\') 0 else if (ch == '\u001b') 4 else 3
            }
        }
    }

    @Synchronized fun render(): String = buildString(rows * (columns + 1)) {
        for (r in 0 until rows) {
            for (c in 0 until columns) append(if (cursorVisible && r == row && c == col) '█' else screen[r][c].ch)
            if (r != rows - 1) append('\n')
        }
    }

    private fun put(ch: Char) {
        if (col >= columns) { col = 0; lineFeed() }
        screen[row][col].ch = ch
        col++
    }
    private fun lineFeed() {
        if (row == scrollBottom) scrollUp(scrollTop, scrollBottom, 1) else row = (row + 1).coerceAtMost(rows - 1)
    }
    private fun reverseIndex() {
        if (row == scrollTop) scrollDown(scrollTop, scrollBottom, 1) else row = (row - 1).coerceAtLeast(0)
    }
    private fun scrollUp(top: Int, bottom: Int, amount: Int) {
        repeat(amount.coerceAtMost(rows)) {
            for (r in top until bottom) screen[r] = screen[r + 1]
            screen[bottom] = Array(columns) { Cell() }
        }
    }
    private fun scrollDown(top: Int, bottom: Int, amount: Int) {
        repeat(amount.coerceAtMost(rows)) {
            for (r in bottom downTo top + 1) screen[r] = screen[r - 1]
            screen[top] = Array(columns) { Cell() }
        }
    }
    private fun eraseLine(mode: Int) {
        val start = when (mode) { 1 -> 0; 2 -> 0; else -> col }
        val end = when (mode) { 0 -> columns - 1; 1 -> col; else -> columns - 1 }
        for (c in start..end) screen[row][c].ch = ' '
    }
    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> { eraseLine(0); for (r in row + 1 until rows) for (c in 0 until columns) screen[r][c].ch = ' ' }
            1 -> { for (r in 0 until row) for (c in 0 until columns) screen[r][c].ch = ' '; eraseLine(1) }
            2, 3 -> for (r in 0 until rows) for (c in 0 until columns) screen[r][c].ch = ' '
        }
    }
    private fun executeCsi(raw: String, final: Char) {
        val privateMode = raw.startsWith("?") || raw.startsWith(">")
        val cleaned = raw.trimStart('?', '>')
        val p = cleaned.split(';').map { it.toIntOrNull() ?: 0 }
        fun arg(i: Int, default: Int = 1) = (p.getOrNull(i) ?: 0).let { if (it == 0) default else it }
        when (final) {
            'A' -> row = (row - arg(0)).coerceAtLeast(0)
            'B', 'e' -> row = (row + arg(0)).coerceAtMost(rows - 1)
            'C', 'a' -> col = (col + arg(0)).coerceAtMost(columns - 1)
            'D' -> col = (col - arg(0)).coerceAtLeast(0)
            'E' -> { row = (row + arg(0)).coerceAtMost(rows - 1); col = 0 }
            'F' -> { row = (row - arg(0)).coerceAtLeast(0); col = 0 }
            'G', '`' -> col = (arg(0) - 1).coerceIn(0, columns - 1)
            'd' -> row = (arg(0) - 1).coerceIn(0, rows - 1)
            'H', 'f' -> { row = (arg(0) - 1).coerceIn(0, rows - 1); col = (arg(1) - 1).coerceIn(0, columns - 1) }
            'J' -> eraseDisplay(p.getOrNull(0) ?: 0)
            'K' -> eraseLine(p.getOrNull(0) ?: 0)
            'm' -> Unit // Color/style state is ignored by this text renderer.
            's' -> { savedRow = row; savedCol = col }
            'u' -> { row = savedRow.coerceIn(0, rows - 1); col = savedCol.coerceIn(0, columns - 1) }
            'r' -> { scrollTop = (arg(0) - 1).coerceIn(0, rows - 1); scrollBottom = ((p.getOrNull(1) ?: rows) - 1).coerceIn(scrollTop, rows - 1); row = 0; col = 0 }
            'S' -> scrollUp(scrollTop, scrollBottom, arg(0))
            'T' -> scrollDown(scrollTop, scrollBottom, arg(0))
            '@' -> { val n = arg(0).coerceAtMost(columns - col); for (c in columns - 1 downTo col + n) screen[row][c] = screen[row][c - n]; for (c in col until col + n) screen[row][c] = Cell() }
            'P' -> { val n = arg(0).coerceAtMost(columns - col); for (c in col until columns - n) screen[row][c] = screen[row][c + n]; for (c in columns - n until columns) screen[row][c] = Cell() }
            'L' -> scrollDown(row, scrollBottom, arg(0))
            'M' -> scrollUp(row, scrollBottom, arg(0))
            'h', 'l' -> if (privateMode) {
                if (cleaned.split(';').contains("25")) cursorVisible = final == 'h'
                if (cleaned.split(';').contains("1049")) {
                    if (final == 'h' && !usingAlternate) { savedRow = row; savedCol = col; alternate = blank(); usingAlternate = true; row = 0; col = 0 }
                    if (final == 'l' && usingAlternate) { usingAlternate = false; row = savedRow.coerceIn(0, rows - 1); col = savedCol.coerceIn(0, columns - 1) }
                }
            }
        }
    }
}
