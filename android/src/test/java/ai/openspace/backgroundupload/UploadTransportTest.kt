package ai.openspace.backgroundupload

import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class UploadTransportTest {
  private fun slowServer(delayMs: Long): ServerSocket {
    val server = ServerSocket(0)
    thread(isDaemon = true) {
      server.accept().use { socket ->
        val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val headers = generateSequence { input.readLine() }.takeWhile { it.isNotEmpty() }.toList()
        val length = headers.first { it.startsWith("Content-Length:", ignoreCase = true) }
          .substringAfter(':').trim().toInt()
        repeat(length) { input.read() }
        Thread.sleep(delayMs)
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
        out.flush()
      }
    }
    return server
  }

  @Test
  fun `a part whose response comes after OkHttp's 10 s default still completes`() {
    slowServer(delayMs = 11_000).use { server ->
      val request = TransferRequest(
        url = "http://127.0.0.1:${server.localPort}/part",
        method = "PUT",
        headers = mapOf(),
        body = ByteArray(64 * 1024).toRequestBody(null),
      )
      val response = runBlocking { okhttpSend(uploadHttpClient, request) {} }
      assertEquals(200, response.code)
      assertEquals("ok", response.body)
    }
  }
}
