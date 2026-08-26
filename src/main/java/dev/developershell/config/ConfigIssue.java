package dev.developershell.config;

import java.util.Locale;
import java.util.Objects;

/** Public-safe, bounded diagnostic for one rejected configuration field. */
public record ConfigIssue(String path, String rejectedValue, String expected) {
	public static final int MAX_FIELD_LENGTH = 96;
	private static final String REDACTED = "<redacted>";

	public ConfigIssue {
		path = sanitizePath(path);
		rejectedValue = sanitizeRejectedValue(rejectedValue);
		expected = sanitizeExpected(expected);
	}

	private static String sanitizePath(String value) {
		String candidate = singleLine(value);
		if (!candidate.matches("\\$(?:\\.[a-zA-Z0-9_]+)*")) {
			return "$";
		}
		return truncate(candidate);
	}

	private static String sanitizeRejectedValue(String value) {
		String candidate = singleLine(value);
		String lower = candidate.toLowerCase(Locale.ROOT);
		if (candidate.isEmpty()
				|| !candidate.matches("[a-zA-Z0-9_+.<|>-]+")
				|| lower.contains("secret")
				|| lower.contains("token")
				|| lower.contains("credential")
				|| lower.contains("authorization")
				|| lower.contains("endpoint")
				|| lower.contains("http")
				|| lower.contains("openai")
				|| lower.contains("budget")
				|| lower.contains("spend")) {
			return REDACTED;
		}
		return truncate(candidate);
	}

	private static String sanitizeExpected(String value) {
		String candidate = singleLine(value);
		if (candidate.isEmpty()) {
			return "valid schema value";
		}
		return truncate(candidate);
	}

	private static String singleLine(String value) {
		String candidate = Objects.requireNonNull(value, "config issue field");
		return candidate.replaceAll("[\\p{Cntrl}]+", " ").trim();
	}

	private static String truncate(String value) {
		return value.length() <= MAX_FIELD_LENGTH ? value : value.substring(0, MAX_FIELD_LENGTH);
	}
}
