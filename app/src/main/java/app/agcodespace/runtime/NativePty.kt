package app.agcodespace.runtime

/** Real POSIX PTY bridge; one foreground terminal session per app process. */
class NativePty {
    external fun start(argv: Array<String>, cwd: String, cols: Int, rows: Int): Int
    external fun read(maxBytes: Int = 8192): ByteArray
    external fun write(data: ByteArray): Int
    external fun resize(cols: Int, rows: Int): Int
    external fun close(): Int
    external fun isAlive(): Boolean
    external fun setEnv(key: String, value: String): Int
    companion object { init { System.loadLibrary("ptybridge") } }
}
