package com.example.orthodox_prm.util;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Utility class for sanitizing and validating user input.
 * Prevents XSS attacks and ensures data quality.
 */
@Component
public class InputSanitizer {

    // Patterns for validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^[\\d\\s\\-\\(\\)\\+\\.]{7,20}$"
    );

    private static final Pattern ZIP_CODE_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9\\s\\-]{3,10}$"
    );

    private static final Pattern NAME_PATTERN = Pattern.compile(
        "^[a-zA-Z\\s\\-'\\.,]+$"
    );

    // HTML/Script tags to remove
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "(?i)<script[^>]*>.*?</script>|javascript:|on\\w+\\s*=",
        Pattern.DOTALL
    );

    // Max lengths for fields
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 100;
    public static final int MAX_PHONE_LENGTH = 20;
    public static final int MAX_ADDRESS_LENGTH = 255;
    public static final int MAX_CITY_LENGTH = 100;
    public static final int MAX_ZIP_LENGTH = 20;
    public static final int MAX_SUFFIX_LENGTH = 10;
    public static final int MAX_GENERAL_LENGTH = 150;

    /**
     * Sanitize a general string input - removes HTML tags and scripts
     * @param input The raw input string
     * @return Sanitized string, or null if input was null
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String sanitized = input.trim();

        // Remove script tags and javascript
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");

        // Remove HTML tags
        sanitized = HTML_TAGS.matcher(sanitized).replaceAll("");

        // Replace multiple spaces with single space
        sanitized = sanitized.replaceAll("\\s+", " ");

        return sanitized.isEmpty() ? null : sanitized;
    }

    /**
     * Sanitize and validate a name field
     * @param name The raw name input
     * @param maxLength Maximum allowed length
     * @return Sanitized name, or null if invalid/empty
     */
    public String sanitizeName(String name, int maxLength) {
        String sanitized = sanitize(name);
        if (sanitized == null) {
            return null;
        }

        // Truncate if too long
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength).trim();
        }

        return sanitized;
    }

    /**
     * Sanitize and validate an email address
     * @param email The raw email input
     * @return Sanitized email, or null if invalid
     */
    public String sanitizeEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        String sanitized = email.trim().toLowerCase();

        // Remove any HTML/script content
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = HTML_TAGS.matcher(sanitized).replaceAll("");

        // Truncate if too long
        if (sanitized.length() > MAX_EMAIL_LENGTH) {
            return null; // Invalid - too long
        }

        // Validate format
        if (!EMAIL_PATTERN.matcher(sanitized).matches()) {
            return null;
        }

        return sanitized;
    }

    /**
     * Sanitize and validate a phone number
     * @param phone The raw phone input
     * @return Sanitized phone, or null if invalid
     */
    public String sanitizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }

        String sanitized = phone.trim();

        // Remove any HTML/script content
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = HTML_TAGS.matcher(sanitized).replaceAll("");

        // Truncate if too long
        if (sanitized.length() > MAX_PHONE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_PHONE_LENGTH);
        }

        // Validate format (digits, spaces, dashes, parentheses, plus, dots)
        if (!PHONE_PATTERN.matcher(sanitized).matches()) {
            return null;
        }

        return sanitized;
    }

    /**
     * Sanitize and validate a zip/postal code
     * @param zipCode The raw zip code input
     * @return Sanitized zip code, or null if invalid
     */
    public String sanitizeZipCode(String zipCode) {
        if (zipCode == null || zipCode.trim().isEmpty()) {
            return null;
        }

        String sanitized = zipCode.trim().toUpperCase();

        // Remove any HTML/script content
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = HTML_TAGS.matcher(sanitized).replaceAll("");

        // Truncate if too long
        if (sanitized.length() > MAX_ZIP_LENGTH) {
            sanitized = sanitized.substring(0, MAX_ZIP_LENGTH);
        }

        // Basic validation
        if (!ZIP_CODE_PATTERN.matcher(sanitized).matches()) {
            return null;
        }

        return sanitized;
    }

    /**
     * Sanitize an address field
     * @param address The raw address input
     * @return Sanitized address
     */
    public String sanitizeAddress(String address) {
        String sanitized = sanitize(address);
        if (sanitized == null) {
            return null;
        }

        if (sanitized.length() > MAX_ADDRESS_LENGTH) {
            sanitized = sanitized.substring(0, MAX_ADDRESS_LENGTH).trim();
        }

        return sanitized;
    }

    /**
     * Sanitize a city field
     * @param city The raw city input
     * @return Sanitized city
     */
    public String sanitizeCity(String city) {
        return sanitizeName(city, MAX_CITY_LENGTH);
    }

    /**
     * Check if a string contains potentially malicious content
     * @param input The string to check
     * @return true if suspicious content detected
     */
    public boolean containsSuspiciousContent(String input) {
        if (input == null) {
            return false;
        }

        String lower = input.toLowerCase();

        // Check for script injection attempts
        if (lower.contains("<script") ||
            lower.contains("javascript:") ||
            lower.contains("onerror=") ||
            lower.contains("onclick=") ||
            lower.contains("onload=") ||
            lower.contains("onmouseover=")) {
            return true;
        }

        // Check for SQL injection attempts
        if (lower.contains("'; drop") ||
            lower.contains("1=1") ||
            lower.contains("union select") ||
            lower.contains("--")) {
            return true;
        }

        return false;
    }

    /**
     * Validate that a name only contains allowed characters
     * @param name The name to validate
     * @return true if valid
     */
    public boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return NAME_PATTERN.matcher(name.trim()).matches();
    }

    /**
     * Validate email format
     * @param email The email to validate
     * @return true if valid email format
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return true; // Empty is valid (optional field)
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Validate phone format
     * @param phone The phone to validate
     * @return true if valid phone format
     */
    public boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return true; // Empty is valid (optional field)
        }
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }
}
