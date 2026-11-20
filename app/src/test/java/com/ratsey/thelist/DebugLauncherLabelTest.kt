package com.ratsey.thelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DebugLauncherLabelTest {

    @Test
    fun `debug build has its own launcher label`() {
        val release = appNameFrom("src/main/res/values/strings.xml")
        val debugFile = File("src/debug/res/values/strings.xml")

        assertTrue("debug strings.xml must exist", debugFile.exists())
        val debug = appNameFrom(debugFile.path)

        assertEquals("The List", release)
        assertTrue("debug label must name the variant: $debug", debug.contains("Debug", ignoreCase = true))
        assertTrue("debug label must differ from release: $debug", debug != release)
    }

    private fun appNameFrom(path: String): String {
        val xml = File(path).readText()
        val match = Regex("""<string\s+name="app_name"[^>]*>(.*?)</string>""").find(xml)
            ?: throw AssertionError("app_name is missing from $path")
        return match.groupValues[1]
    }
}
