package jp.assasans.protanki.deploy.extensions

import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun InputStream.pipeTo(output: OutputStream) {
  val reader = reader()
  val writer = output.writer()
  val buffer = CharArray(1024)
  withContext(Dispatchers.IO) {
    while(true) {
      val count = reader.read(buffer)
      if(count == -1) break
      writer.write(buffer, 0, count)
      writer.flush()
    }
  }
}
