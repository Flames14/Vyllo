package com.vyllo.music.core.security

import java.net.URI

/**
 * Input Validation and Sanitization Utility
 * 
 * Prevents:
 * - Injection attacks (SQL, command)
 * - XSS attempts
 * - Path traversal
 * - DoS via oversized input
 */
object InputSanitizer {

    // Maximum lengths to prevent DoS
    private const val MAX_SEARCH_LENGTH = 100
    private const val MAX_PLAYLIST_NAME_LENGTH = 50
    private const val MAX_URL_LENGTH = 2048

    // Dangerous characters that could be used for injection
    private val DANGEROUS_CHARS = Regex("[<>\"'`;\\\\|&\$()]")

    // SQL injection patterns
    private val SQL_INJECTION_PATTERN = Regex(
        "(?i)(union\\s+select|insert\\s+into|update\\s+.*set|delete\\s+from|drop\\s+table|exec\\s*\\(|execute\\s*\\(|xp_)"
    )

    // Command injection patterns
    private val COMMAND_INJECTION_PATTERN = Regex(
        "(?i)(;|\\||\\$\\(|`|&&|\\|\\|)"
    )

    // Path traversal patterns
    private val PATH_TRAVERSAL_PATTERN = Regex("(\\.\\./|\\.\\.\\\\|%2e%2e%2f|%2e%2e/|\\.\\.%2f)")

    /**
     * Sanitizes search queries
     */
    fun sanitizeSearchQuery(input: String): String {
        if (input.isBlank()) return ""

        // Trim and limit length
        val trimmed = input.trim().take(MAX_SEARCH_LENGTH)

        // Remove dangerous characters
        val sanitized = trimmed.replace(DANGEROUS_CHARS, "")

        // Normalize whitespace
        return sanitized.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Sanitizes playlist names
     */
    fun sanitizePlaylistName(input: String): String {
        if (input.isBlank()) return "Untitled Playlist"

        return input.trim()
            .take(MAX_PLAYLIST_NAME_LENGTH)
            .replace(DANGEROUS_CHARS, "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Untitled Playlist" }
    }

    /**
     * Validates URL format (for stream URLs)
     */
    fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.length > MAX_URL_LENGTH) return false

        return try {
            val uri = URI(url)
            uri.scheme == "https" &&
                    uri.host.isNotBlank() &&
                    !uri.host.contains("..") && // Prevent path traversal
                    !uri.path.contains("..") // Prevent path traversal in path
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detects potential SQL injection attempts
     */
    fun isPotentialSqlInjection(input: String): Boolean {
        return SQL_INJECTION_PATTERN.containsMatchIn(input)
    }

    /**
     * Detects potential command injection attempts
     */
    fun isPotentialCommandInjection(input: String): Boolean {
        return COMMAND_INJECTION_PATTERN.containsMatchIn(input)
    }

    /**
     * Detects path traversal attempts
     */
    fun isPotentialPathTraversal(input: String): Boolean {
        return PATH_TRAVERSAL_PATTERN.containsMatchIn(input)
    }

    /**
     * Comprehensive security check for user input
     */
    fun validateInput(input: String, type: InputType): ValidationResult {
        if (input.isBlank()) {
            return ValidationResult(false, "Input cannot be empty")
        }

        when (type) {
            InputType.SEARCH -> {
                if (input.length > MAX_SEARCH_LENGTH) {
                    return ValidationResult(false, "Search query too long")
                }
                if (isPotentialSqlInjection(input)) {
                    return ValidationResult(false, "Invalid characters in search query")
                }
            }
            InputType.PLAYLIST_NAME -> {
                if (input.length > MAX_PLAYLIST_NAME_LENGTH) {
                    return ValidationResult(false, "Playlist name too long")
                }
            }
            InputType.URL -> {
                if (!isValidUrl(input)) {
                    return ValidationResult(false, "Invalid URL format")
                }
            }
        }

        return ValidationResult(true, null)
    }

    enum class InputType {
        SEARCH,
        PLAYLIST_NAME,
        URL
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String?
    )
}
