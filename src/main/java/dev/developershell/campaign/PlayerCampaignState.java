package dev.developershell.campaign;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Final immutable schema-v1 campaign record for one player.
 *
 * <p>The retained Plan 01 tracer still accepts the narrow
 * {@link CampaignSavedData.PlayerProgress} view. This record is the sole implementation of
 * that compatibility interface and owns every durable field that later reducers and adapters
 * extend.</p>
 */
public record PlayerCampaignState(
		UUID ownerUuid,
		CampaignChapter chapter,
		LectureStatus status,
		int attemptCount,
		String deskDimension,
		BlockPos deskPos,
		Direction deskFacing,
		BlockPos retryPos,
		EncounterRef activeEncounterRef,
		boolean sheetEntitled,
		boolean remoteIssued,
		boolean retakeEntitled,
		UUID retakeFallbackEntityUuid,
		long remoteCooldownUntilGameTime,
		long sheetRecoverySequence,
		long remoteReadyNoticeForDeadlineGameTime
) implements CampaignSavedData.PlayerProgress {
	public static final String OVERWORLD_DIMENSION = "minecraft:overworld";

	public PlayerCampaignState {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(chapter, "chapter");
		Objects.requireNonNull(status, "status");
		if (attemptCount < 0) {
			throw new IllegalArgumentException("attemptCount must be non-negative");
		}
		Objects.requireNonNull(deskDimension, "deskDimension");
		deskPos = Objects.requireNonNull(deskPos, "deskPos").immutable();
		Objects.requireNonNull(deskFacing, "deskFacing");
		retryPos = Objects.requireNonNull(retryPos, "retryPos").immutable();
		if (activeEncounterRef != null
				&& (!ownerUuid.equals(activeEncounterRef.ownerUuid())
				|| attemptCount != activeEncounterRef.attemptNumber())) {
			throw new IllegalArgumentException("active encounter identity must match owner and attempt");
		}
		if (!retakeEntitled && retakeFallbackEntityUuid != null) {
			throw new IllegalArgumentException("Retake fallback requires a Retake entitlement");
		}
		if (remoteCooldownUntilGameTime < 0L) {
			throw new IllegalArgumentException("Remote cooldown deadline must be non-negative");
		}
		if (sheetRecoverySequence < 0L) {
			throw new IllegalArgumentException("Sheet recovery sequence must be non-negative");
		}
		if (remoteReadyNoticeForDeadlineGameTime < 0L
				|| remoteReadyNoticeForDeadlineGameTime > remoteCooldownUntilGameTime) {
			throw new IllegalArgumentException("Remote ready notice must identify a committed cooldown deadline");
		}
	}

	/**
	 * Schema-v1 compatibility constructor retained for the frozen Plan 14 call surface.
	 * New monotonic replay markers default to their never-issued value.
	 */
	public PlayerCampaignState(
			UUID ownerUuid,
			CampaignChapter chapter,
			LectureStatus status,
			int attemptCount,
			String deskDimension,
			BlockPos deskPos,
			Direction deskFacing,
			BlockPos retryPos,
			EncounterRef activeEncounterRef,
			boolean sheetEntitled,
			boolean remoteIssued,
			boolean retakeEntitled,
			UUID retakeFallbackEntityUuid,
			long remoteCooldownUntilGameTime
	) {
		this(
				ownerUuid,
				chapter,
				status,
				attemptCount,
				deskDimension,
				deskPos,
				deskFacing,
				retryPos,
				activeEncounterRef,
				sheetEntitled,
				remoteIssued,
				retakeEntitled,
				retakeFallbackEntityUuid,
				remoteCooldownUntilGameTime,
				0L,
				0L
		);
	}

	public Optional<EncounterRef> activeEncounter() {
		return Optional.ofNullable(activeEncounterRef);
	}

	/** Compatibility accessor retained for the green Plan 01 tracer. */
	@Override
	public UUID encounterUuid() {
		return activeEncounterRef == null ? null : activeEncounterRef.encounterUuid();
	}

	/** Compatibility accessor retained for the green Plan 01 tracer. */
	@Override
	public UUID professorUuid() {
		return activeEncounterRef == null ? null : activeEncounterRef.professorUuid();
	}

	public boolean matchesActiveEncounter(UUID ownerUuid, UUID encounterUuid) {
		return activeEncounterRef != null
				&& activeEncounterRef.ownerUuid().equals(ownerUuid)
				&& activeEncounterRef.encounterUuid().equals(encounterUuid);
	}

	public enum CampaignChapter {
		PRE_LECTURE("pre_lecture"),
		LECTURE_PASSED("lecture_passed");

		private final String serializedName;

		CampaignChapter(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return serializedName;
		}

		public static CampaignChapter fromSerializedName(String value) {
			for (CampaignChapter chapter : values()) {
				if (chapter.serializedName.equals(value)) {
					return chapter;
				}
			}
			throw new IllegalArgumentException("Unknown campaign chapter: " + value);
		}
	}

	public enum LectureStatus {
		READY("ready"),
		ACTIVE("active"),
		RETAKE_READY("retake_ready"),
		PASSED("passed");

		private final String serializedName;

		LectureStatus(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return serializedName;
		}

		public static LectureStatus fromSerializedName(String value) {
			for (LectureStatus status : values()) {
				if (status.serializedName.equals(value)) {
					return status;
				}
			}
			throw new IllegalArgumentException("Unknown lecture status: " + value);
		}
	}

	public record EncounterRef(
			UUID ownerUuid,
			UUID encounterUuid,
			UUID professorUuid,
			int attemptNumber
	) {
		public EncounterRef {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(encounterUuid, "encounterUuid");
			Objects.requireNonNull(professorUuid, "professorUuid");
			if (attemptNumber < 1) {
				throw new IllegalArgumentException("attemptNumber must be positive");
			}
		}
	}
}
