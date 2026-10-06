package com.agcodespace.terminal

object NativePty {
    init { System.loadLibrary("agpty") }
    external fun spawn(command: String, rows: Int, cols: Int): Int
    external fun write(fd: Int, data: ByteArray): Int
    external fun read(fd: Int, out: ByteArray): Int
    external fun resize(fd: Int, rows: Int, cols: Int)
}
