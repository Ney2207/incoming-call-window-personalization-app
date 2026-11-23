package com.customcall.core.model.util

/**
 * Pure-Kotlin normalization utilities for phone numbers, VoIP handles, and caller display names.
 *
 * Ensures consistent canonical keys between stored contact rules and live incoming call metadata.
 */
object IdentifierNormalizer {

    private val WHITESPACE_REGEX = Regex("\\s+")
    private val PHONE_CANDIDATE_REGEX = Regex("^[+0-9()\\-.\\s]+$")

    /**
     * Normalizes a raw phone number into a compact canonical form.
     *
     * - Strips spaces, dashes, parentheses, dots, and other non-digit separators.
     * - Preserves a single leading `+` if present in the trimmed input (E.164 prefix).
     * - Returns an empty string if the input contains no digits.
     *
     * Example: `"+1 (555) 234-5678"` -> `"+15552345678"`.
     */
    fun normalizePhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val hasLeadingPlus = trimmed.startsWith('+')
        val digitsOnly = buildString(trimmed.length) {
            for (ch in trimmed) {
                if (ch.isDigit()) append(ch)
            }
        }

        if (digitsOnly.isEmpty()) return ""
        return if (hasLeadingPlus) "+$digitsOnly" else digitsOnly
    }

    /**
     * Normalizes a caller display name or VoIP title for case-insensitive matching.
     *
     * - Trims leading and trailing whitespace.
     * - Collapses consecutive internal whitespace characters into a single space.
     * - Converts characters to lowercase.
     *
     * Example: `"  Alice   M.  "` -> `"alice m."`.
     */
    fun normalizeDisplayName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.replace(WHITESPACE_REGEX, " ").lowercase()
    }

    /**
     * Normalizes a generic caller identifier (either a phone number or a VoIP handle/username).
     *
     * If [raw] consists solely of phone formatting characters and at least one digit,
     * delegates to [normalizePhoneNumber]; otherwise delegates to [normalizeDisplayName].
     */
    fun normalizeIdentifier(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val isPhoneLike = PHONE_CANDIDATE_REGEX.matches(trimmed) && trimmed.any { it.isDigit() }
        return if (isPhoneLike) {
            normalizePhoneNumber(trimmed)
        } else {
            normalizeDisplayName(trimmed)
        }
    }

    /**
     * Returns `true` when two raw phone numbers normalize to the same non-empty canonical number.
     */
    fun phoneNumbersMatch(first: String, second: String): Boolean {
        val normalizedFirst = normalizePhoneNumber(first)
        if (normalizedFirst.isEmpty()) return false
        return normalizedFirst == normalizePhoneNumber(second)
    }
}