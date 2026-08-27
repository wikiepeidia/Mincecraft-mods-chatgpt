package dev.developershell.bossrush;

import java.util.Objects;
import java.util.UUID;

/** Immutable per-player authority. Physical reward stacks never create campaign progress. */
public record BossRushProgress(
		UUID ownerUuid,
		BossRushStage stage,
		boolean juryCleared,
		boolean chairmanCleared,
		boolean diplomaGranted
) {
	public BossRushProgress {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(stage, "stage");
		if (chairmanCleared && !juryCleared) {
			throw new IllegalArgumentException("Chairman clear requires Jury clear");
		}
		if (diplomaGranted && !chairmanCleared) {
			throw new IllegalArgumentException("Diploma requires Chairman clear");
		}
		if (stage == BossRushStage.READY_CHAIRMAN && !juryCleared) {
			throw new IllegalArgumentException("Chairman checkpoint requires Jury clear");
		}
		if ((stage == BossRushStage.SPONSOR || stage == BossRushStage.CODEX) && !chairmanCleared) {
			throw new IllegalArgumentException("Sponsor/Codex checkpoint requires Chairman clear");
		}
		if (stage == BossRushStage.GRADUATED && !diplomaGranted) {
			throw new IllegalArgumentException("Graduated checkpoint requires Diploma");
		}
	}

	public static BossRushProgress initial(UUID ownerUuid) {
		return new BossRushProgress(ownerUuid, BossRushStage.READY_JURY, false, false, false);
	}

	public BossRushProgress normalizeRestart() {
		BossRushStage normalized = stage.restartCheckpoint();
		return normalized == stage ? this : withStage(normalized);
	}

	public BossRushProgress begin(BossRushStage liveStage) {
		Objects.requireNonNull(liveStage, "liveStage");
		boolean legal = (stage == BossRushStage.READY_JURY && liveStage == BossRushStage.JURY)
				|| (stage == BossRushStage.READY_CHAIRMAN && liveStage == BossRushStage.CHAIRMAN)
				|| (stage == BossRushStage.SPONSOR && liveStage == BossRushStage.CODEX);
		if (!legal) {
			throw new IllegalStateException("Illegal boss-rush start: " + stage + " -> " + liveStage);
		}
		return withStage(liveStage);
	}

	public Completion complete(BossRushStage completedStage) {
		Objects.requireNonNull(completedStage, "completedStage");
		if (stage != completedStage) {
			throw new IllegalStateException("Cannot complete " + completedStage + " from " + stage);
		}
		return switch (completedStage) {
			case JURY -> new Completion(
					new BossRushProgress(ownerUuid, BossRushStage.READY_CHAIRMAN, true,
							chairmanCleared, diplomaGranted),
					!juryCleared
			);
			case CHAIRMAN -> new Completion(
					new BossRushProgress(ownerUuid, BossRushStage.SPONSOR, true, true,
							diplomaGranted),
					!chairmanCleared
			);
			case CODEX -> new Completion(
					new BossRushProgress(ownerUuid, BossRushStage.GRADUATED, true, true, true),
					!diplomaGranted
			);
			default -> throw new IllegalStateException("Stage is not a completable fight: " + completedStage);
		};
	}

	public BossRushProgress withStage(BossRushStage nextStage) {
		return new BossRushProgress(ownerUuid, nextStage, juryCleared, chairmanCleared, diplomaGranted);
	}

	public record Completion(BossRushProgress progress, boolean firstClear) {
		public Completion {
			Objects.requireNonNull(progress, "progress");
		}
	}
}
