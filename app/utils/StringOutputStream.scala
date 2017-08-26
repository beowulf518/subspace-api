package utils

import java.io.OutputStream

class StringOutputStream extends OutputStream {
  private val bytes = collection.mutable.ArrayBuffer[Byte]()

  override def write(b: Int): Unit = {
    bytes += b.toByte
  }

  def s: String = new String(bytes.toArray)
}
