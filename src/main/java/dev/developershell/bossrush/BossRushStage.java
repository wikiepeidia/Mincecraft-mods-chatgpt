package dev.developershell.bossrush;

import java.util.Locale;

/** Stable durable checkpoints and live stages for the emergency boss-rush campaign. */
public enum BossRushStage {
	READY_JURY("ready_jury"),
	JURY("jury"),
	READY_CHAIRMAN("ready_chairman"),
	CHAIRMAN("chairman"),
	SPONSOR("sponsor"),
	CODEX("codex"),
	GRADUATED("graduated");

	private final String serializedName;

	BossRushStage(String serializedName) {
		this.serializedName = serializedName;
	}

	public String serializedName() {
		return serializedName;
	}

	public static BossRushStage fromSerializedName(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Boss-rush stage cannot be null");
		}
		String normalized = value.toLowerCase(Locale.ROOT);
		for (BossRushStage stage : values()) {
			if (stage.serializedName.equals(normalized)) {
				return stage;
			}
		}
		throw new IllegalArgumentException("Unknown boss-rush stage: " + value);
	}

	public boolean isLiveFight() {
		return this == JURY || this == CHAIRMAN || this == CODEX;
	}

	public BossRushStage restartCheckpoint() {
		return switch (this) {
			case JURY -> READY_JURY;
			case CHAIRMAN -> READY_CHAIRMAN;
			case CODEX -> SPONSOR;
			default -> this;
		};
	}
}
