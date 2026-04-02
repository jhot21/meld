package me.jhot.meld.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyReceiverTest {

    @Test
    fun parse_meldTagAbsent_returnsNull() {
        assertNull(parseNtfyIntent("add"))
    }

    @Test
    fun parse_tagsNull_returnsNull() {
        assertNull(parseNtfyIntent(null))
    }

    @Test
    fun parse_tagsEmpty_returnsNull() {
        assertNull(parseNtfyIntent(""))
    }

    @Test
    fun parse_meldPlusTruthy_returnsTrue() {
        assertEquals(true, parseNtfyIntent("meld,add"))
    }

    @Test
    fun parse_meldPlusFalsy_returnsFalse() {
        assertEquals(false, parseNtfyIntent("meld,remove"))
    }

    @Test
    fun parse_meldPlusBothTruthyAndFalsy_returnsNull() {
        assertNull(parseNtfyIntent("meld,add,remove"))
    }

    @Test
    fun parse_meldNoActionTag_returnsNull() {
        assertNull(parseNtfyIntent("meld,custom-tag"))
    }

    @Test
    fun parse_meldTagCaseInsensitive() {
        assertEquals(true, parseNtfyIntent("MELD,add"))
    }

    @Test
    fun parse_actionTagCaseInsensitive() {
        assertEquals(true, parseNtfyIntent("meld,ADD"))
    }

    @Test
    fun parse_tagsWithWhitespace_trimmed() {
        assertEquals(true, parseNtfyIntent(" meld , add "))
    }

    @Test
    fun parse_extraUnrecognizedTagsIgnored() {
        assertEquals(true, parseNtfyIntent("meld,add,weather,home"))
    }

    @Test
    fun parse_allTruthyValues() {
        for (tag in NtfyReceiver.TRUTHY_TAGS) {
            assertEquals("Expected true for '$tag'", true, parseNtfyIntent("meld,$tag"))
        }
    }

    @Test
    fun parse_allFalsyValues() {
        for (tag in NtfyReceiver.FALSY_TAGS) {
            assertEquals("Expected false for '$tag'", false, parseNtfyIntent("meld,$tag"))
        }
    }
}
