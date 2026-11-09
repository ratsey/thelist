package com.ratsey.thelist.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun `newer patch wins`() {
        assertTrue("0.1.2".isNewerThan("0.1.1"))
    }

    @Test
    fun `tag prefix is ignored`() {
        assertTrue("v0.2.0".isNewerThan("0.1.9"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse("1.0.0".isNewerThan("1.0.0"))
    }

    @Test
    fun `older release is not newer`() {
        assertFalse("0.9.0".isNewerThan("1.0.0"))
    }

    @Test
    fun `debug suffix keeps the same base version`() {
        assertFalse("0.1.0".isNewerThan("0.1.0-debug"))
    }

    @Test
    fun `segments compare numerically not lexically`() {
        assertTrue("1.10.0".isNewerThan("1.9.9"))
    }

    @Test
    fun `missing segments are zero`() {
        assertTrue("1.1".isNewerThan("1.0.9"))
    }
}
