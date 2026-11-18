package com.example.phonecam

import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

class MjpegServer(port: Int, private val frameProvider: () -> ByteArray?) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/stream") return newFixedLengthResponse("Open /stream")
        val input = object : InputStream() {
            private var buf = ByteArray(0)
            private var pos = 0

            private fun refill() {
                while (true) {
                    val f = frameProvider()
                    if (f != null && f.isNotEmpty()) {
                        val head = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${f.size}\r\n\r\n".toByteArray()
                        buf = head + f + "\r\n".toByteArray()
                        pos = 0
                        return
                    }
                    Thread.sleep(10)
                }
            }
            override fun read(): Int {
                if (pos >= buf.size) refill()
                return buf[pos++].toInt() and 0xFF
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= buf.size) refill()
                val n = minOf(len, buf.size - pos)
                System.arraycopy(buf, pos, b, off, n)
                pos += n
                return n
            }
        }
        return newChunkedResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=frame", input)
    }
}
