package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCsvFieldEscaping() {
    fun escapeCsvField(value: Any?): String {
      val str = value?.toString() ?: ""
      if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
        return "\"" + str.replace("\"", "\"\"") + "\""
      }
      return str
    }

    assertEquals("normal text", escapeCsvField("normal text"))
    assertEquals("\"text, with comma\"", escapeCsvField("text, with comma"))
    assertEquals("\"text with \"\"quotes\"\"\"", escapeCsvField("text with \"quotes\""))
    assertEquals("\"text with\nnewline\"", escapeCsvField("text with\nnewline"))
  }
}
