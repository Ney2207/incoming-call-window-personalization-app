package com.customcall.core.model.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentifierNormalizerTest {

    @Test
    fun normalizePhoneNumberPreservesLeadingPlusAndStripsFormatting() {
        assertEquals("+15552345678", IdentifierNormalizer.normalizePhoneNumber("  +1 (555) 234-5678  "))
        assertEquals("+584149876543", IdentifierNormalizer.normalizePhoneNumber("+58 414-987.65.43"))
        assertEquals("04125550199", IdentifierNormalizer.normalizePhoneNumber("(0412) 555-0199"))
        assertEquals("", IdentifierNormalizer.normalizePhoneNumber("   "))
        assertEquals("", IdentifierNormalizer.normalizePhoneNumber("+-() ."))
    }

    @Test
    fun normalizeDisplayNameCollapsesWhitespaceAndCaseFolds() {
        assertEquals("alice m.", IdentifierNormalizer.normalizeDisplayName("  Alice   M.  "))
        assertEquals("mom ❤️", IdentifierNormalizer.normalizeDisplayName("\tMOM\n  ❤️ "))
        assertEquals("", IdentifierNormalizer.normalizeDisplayName("   \t\n  "))
    }

    @Test
    fun normalizeIdentifierDispatchesBetweenPhoneAndVoipHandles() {
        assertEquals("+18005550100", IdentifierNormalizer.normalizeIdentifier("+1-800-555-0100"))
        assertEquals("@johndoe_dev", IdentifierNormalizer.normalizeIdentifier("  @JohnDoe_Dev "))
        assertEquals("team standup", IdentifierNormalizer.normalizeIdentifier("Team   Standup"))
        assertEquals("", IdentifierNormalizer.normalizeIdentifier(""))
    }

    @Test
    fun phoneNumbersMatchComparesCanonicalNumbersSafely() {
        assertTrue(IdentifierNormalizer.phoneNumbersMatch("+1 (555) 234-5678", "+15552345678"))
        assertFalse(IdentifierNormalizer.phoneNumbersMatch("+15552345678", "15552345678"))
        assertFalse(IdentifierNormalizer.phoneNumbersMatch("", ""))
        assertFalse(IdentifierNormalizer.phoneNumbersMatch("---", "()"))
    }
}