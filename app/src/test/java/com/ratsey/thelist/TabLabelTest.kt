package com.ratsey.thelist

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TabLabelTest {

    @Test
    fun `to buy tab uses title case`() {
        val xml = File("src/main/res/values/strings.xml").readText()
        val match = Regex("""<string\s+name="tab_to_buy"[^>]*>(.*?)</string>""").find(xml)
            ?: throw AssertionError("tab_to_buy is missing from strings.xml")
        assertEquals("To Buy", match.groupValues[1])
    }
}
