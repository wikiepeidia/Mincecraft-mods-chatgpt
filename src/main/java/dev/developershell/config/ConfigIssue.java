package dev.developershell.config;

import java.util.Objects;
import java.util.Set;

/** Public-safe, bounded diagnostic for one rejected configuration field. */
public record ConfigIssue(String path, String rejectedValue, String expected) {
	public static final int MAX_FIELD_LENGTH = 96;
	private static final String REDACTED = "<redacted>";
	private static final Set<String> SAFE_ROOT_PATHS = Set.of(
			"file",
			"schemaVersion",
			"campaignEnabled",
			"difficulty",
			"bossBlockDamage",
			"reducedFlashing",
			"reducedEffects",
			"lecture",
			"metadataRouletteSchedule",
			"threeDayDeadlineSchedule",
			"modules"
	);
	private static final Set<String> SAFE_LECTURE_PATHS = Set.of(
			"professorHealth",
			"missDamage",
			"maxAdds",
			"slideDeckTelegraphTicks",
			"quizTelegraphTicks",
			"attendanceTelegraphTicks",
			"vulnerabilityTicks",
			"actionBarUpdateTicks",
			"particleRefreshTicks",
			"particlesPerRefresh",
			"maxParticleBurstsPerEncounter",
			"maxTransitionSoundsPerEncounter",
			"arenaSearchRadius"
	);
	private static final Set<String> SAFE_MODULE_PATHS = Set.of(
			"graduation_anyfail",
			"metadata_roulette",
			"python_tools",
			"codex_rich_kid_terminal",
			"git_happens",
			"stack_overflow_totem",
			"rubber_duck_engineering",
			"three_day_deadline"
	);
	private static final Set<String> SAFE_REJECTED_SENTINELS = Set.of(
			"<symbolic-link>",
			"<non-regular>",
			"<metadata-failed>",
			"<read-failed>",
			"<write-failed>",
			"<trailing-data>",
			"<malformed>",
			"<object>",
			"<array>",
			"<null>",
			"<string>",
			"<number>",
			"<boolean>",
			"<missing>",
			"<duplicate>",
			"<oversize>",
			"<invalid-number>",
			"<below-minimum>",
			"<above-maximum>",
			"<invalid-enum>",
			"<unknown-property>"
	);

	public ConfigIssue {
		path = sanitizePath(path);
		rejectedValue = sanitizeRejectedValue(rejectedValue);
		expected = sanitizeExpected(expected);
	}

	private static String sanitizePath(String value) {
		String candidate = singleLine(value);
		if (candidate.equals("$")) {
			return candidate;
		}
		if (!candidate.startsWith("$.") || candidate.length() > MAX_FIELD_LENGTH) {
			return "$";
		}
		String[] segments = candidate.substring(2).split("\\.", -1);
		if (segments.length == 1 && SAFE_ROOT_PATHS.contains(segments[0])) {
			return candidate;
		}
		if (segments.length == 2
				&& ((segments[0].equals("lecture") && SAFE_LECTURE_PATHS.contains(segments[1]))
				|| (segments[0].equals("modules") && SAFE_MODULE_PATHS.contains(segments[1])))) {
			return candidate;
		}
		return "$";
	}

	private static String sanitizeRejectedValue(String value) {
		String candidate = singleLine(value);
		if (SAFE_REJECTED_SENTINELS.contains(candidate)
				|| candidate.equals("true")
				|| candidate.equals("false")) {
			return candidate;
		}
		return REDACTED;
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
