package dev.developershell.config;

import dev.developershell.module.ModuleGate;
import dev.developershell.module.ModuleId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** One complete immutable schema-v1 configuration snapshot for a server session. */
public record DevHellConfig(
		int schemaVersion,
		boolean campaignEnabled,
		Difficulty difficulty,
		boolean bossBlockDamage,
		boolean reducedFlashing,
		boolean reducedEffects,
		LectureTuning lecture,
		Set<ModuleId> enabledModules,
		ScheduleMode metadataRouletteSchedule,
		ScheduleMode threeDayDeadlineSchedule
) {
	public static final int SCHEMA_VERSION = 1;
	private static final DevHellConfig DEFAULTS = new DevHellConfig(
			SCHEMA_VERSION,
			true,
			Difficulty.STANDARD,
			false,
			true,
			false,
			LectureTuning.standard(),
			EnumSet.allOf(ModuleId.class),
			ScheduleMode.MANUAL,
			ScheduleMode.MANUAL
	);

	public DevHellConfig {
		if (schemaVersion != SCHEMA_VERSION) {
			throw new IllegalArgumentException("schemaVersion must be " + SCHEMA_VERSION);
		}
		difficulty = Objects.requireNonNull(difficulty, "difficulty");
		lecture = Objects.requireNonNull(lecture, "lecture");
		Objects.requireNonNull(enabledModules, "enabledModules");
		EnumSet<ModuleId> enabledCopy = EnumSet.noneOf(ModuleId.class);
		for (ModuleId module : enabledModules) {
			enabledCopy.add(Objects.requireNonNull(module, "enabledModules member"));
		}
		enabledModules = Collections.unmodifiableSet(enabledCopy);
		metadataRouletteSchedule = Objects.requireNonNull(metadataRouletteSchedule, "metadataRouletteSchedule");
		threeDayDeadlineSchedule = Objects.requireNonNull(threeDayDeadlineSchedule, "threeDayDeadlineSchedule");
	}

	public static DevHellConfig defaults() {
		return DEFAULTS;
	}

	public ModuleGate moduleGate() {
		return ModuleGate.of(enabledModules);
	}

	public enum Difficulty {
		STORY("story"),
		RELAXED("relaxed"),
		STANDARD("standard"),
		INTENSE("intense");

		private final String serializedName;

		Difficulty(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return serializedName;
		}

		static Difficulty fromSerializedName(String value) {
			for (Difficulty difficulty : values()) {
				if (difficulty.serializedName.equals(value)) {
					return difficulty;
				}
			}
			return null;
		}
	}

	public enum ScheduleMode {
		MANUAL("manual"),
		AUTOMATIC("automatic");

		private final String serializedName;

		ScheduleMode(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return serializedName;
		}

		static ScheduleMode fromSerializedName(String value) {
			for (ScheduleMode mode : values()) {
				if (mode.serializedName.equals(value)) {
					return mode;
				}
			}
			return null;
		}
	}

	/** Bounded values used by the lecture adapters without reopening the config file. */
	public record LectureTuning(
			int professorHealth,
			int missDamage,
			int maxAdds,
			int slideDeckTelegraphTicks,
			int quizTelegraphTicks,
			int attendanceTelegraphTicks,
			int vulnerabilityTicks,
			int actionBarUpdateTicks,
			int particleRefreshTicks,
			int particlesPerRefresh,
			int maxParticleBurstsPerEncounter,
			int maxTransitionSoundsPerEncounter,
			int arenaSearchRadius
	) {
		private static final LectureTuning STANDARD = new LectureTuning(
				120,
				4,
				3,
				100,
				160,
				120,
				80,
				20,
				10,
				6,
				24,
				8,
				5
		);

		public LectureTuning {
			requireRange("professorHealth", professorHealth, 40, 400);
			requireRange("missDamage", missDamage, 1, 20);
			requireRange("maxAdds", maxAdds, 0, 8);
			requireRange("slideDeckTelegraphTicks", slideDeckTelegraphTicks, 60, 200);
			requireRange("quizTelegraphTicks", quizTelegraphTicks, 80, 300);
			requireRange("attendanceTelegraphTicks", attendanceTelegraphTicks, 80, 240);
			requireRange("vulnerabilityTicks", vulnerabilityTicks, 20, 200);
			requireRange("actionBarUpdateTicks", actionBarUpdateTicks, 20, 20);
			requireRange("particleRefreshTicks", particleRefreshTicks, 4, 20);
			requireRange("particlesPerRefresh", particlesPerRefresh, 1, 12);
			requireRange("maxParticleBurstsPerEncounter", maxParticleBurstsPerEncounter, 1, 40);
			requireRange("maxTransitionSoundsPerEncounter", maxTransitionSoundsPerEncounter, 1, 12);
			requireRange("arenaSearchRadius", arenaSearchRadius, 1, 8);
		}

		public static LectureTuning standard() {
			return STANDARD;
		}

		private static void requireRange(String field, int value, int minimum, int maximum) {
			if (value < minimum || value > maximum) {
				throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum);
			}
		}
	}
}
