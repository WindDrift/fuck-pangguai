package io.github.winddrift.fuckpangguai.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {

    @Test
    fun parsesPrefixedAndSuffixedVersions() {
        assertEquals(ReleaseVersion(1, 10, 1), parseReleaseVersion("v1.10.1"))
        assertEquals(ReleaseVersion(1, 10, 1), parseReleaseVersion("V1.10.1"))
        assertEquals(ReleaseVersion(1, 10, 1), parseReleaseVersion("1.10.1-7f0c885"))
        assertEquals(ReleaseVersion(2, 0, 0), parseReleaseVersion(" 2.0.0+abc "))
    }

    @Test
    fun rejectsIncompleteOrInvalidVersions() {
        assertNull(parseReleaseVersion(""))
        assertNull(parseReleaseVersion("1.10"))
        assertNull(parseReleaseVersion("v1.x.1"))
        assertNull(parseReleaseVersion("nightly"))
    }

    @Test
    fun comparesBySemverComponents() {
        assertTrue(parseReleaseVersion("1.11.0")!! > parseReleaseVersion("1.10.9")!!)
        assertTrue(parseReleaseVersion("v1.10.1")!! == parseReleaseVersion("1.10.1-abc")!!)
        assertTrue(parseReleaseVersion("1.10.1-abc")!! < parseReleaseVersion("v1.10.2")!!)
    }
}
