package app.agcodespace.runtime

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Keeps incomplete UTF-8 sequences across arbitrary PTY read boundaries. */
class Utf8StreamDecoder {
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    @Synchronized fun reset() {
        pending = ByteArray(0)
        decoder.reset()
    }

    @Synchronized fun decode(bytes: ByteArray): String {
        val input = ByteBuffer.allocate(pending.size + bytes.size)
        input.put(pending)
        input.put(bytes)
        input.flip()
        val output = CharBuffer.allocate(input.remaining() * 2 + 4)
        decoder.decode(input, output, false)
        pending = ByteArray(input.remaining())
        input.get(pending)
        output.flip()
        return output.toString()
    }
}
