package dev.developershell.campaign;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Final immutable schema-v2 campaign record for one player.
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
		UUID retakeEncounterUuid,
		UUID retakeFallbackReservationUuid,
		UUID retakeFallbackEntityUuid,
		long remoteCooldownUntilGameTime,
		long sheetRecoverySequence,
		long remoteReadyNoticeForDeadlineGameTime,
		boolean sheetProjectionPending,
		boolean remoteProjectionPending,
		UUID remoteProjectionUuid,
		boolean legacyRemoteAdoptionPending,
		RewardFallbackRef sheetFallback,
		RewardFallbackRef remoteFallback
) implements CampaignSavedData.PlayerProgress {
	public static final String OVERWORLD_DIMENSION = "minecraft:overworld";

	public PlayerCampaignState {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(chapter, "chapter");
		Objects.requireNonNull(status, "status");
		if (attemptCount < 0) {
			throw new IllegalArgumentException("attemptCount must be non-negative");
		}
		if (status != LectureStatus.READY && attemptCount < 1) {
			throw new IllegalArgumentException("non-READY campaign status requires a positive attempt");
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
		if ((status == LectureStatus.ACTIVE) != (activeEncounterRef != null)) {
			throw new IllegalArgumentException("ACTIVE status and active encounter identity must be present together");
		}
		if ((status == LectureStatus.RETAKE_READY) != retakeEntitled) {
			throw new IllegalArgumentException("RETAKE_READY status and Retake entitlement must be present together");
		}
		if ((status == LectureStatus.PASSED) != (chapter == CampaignChapter.LECTURE_PASSED)) {
			throw new IllegalArgumentException("PASSED status and LECTURE_PASSED chapter must be present together");
		}
		if ((status == LectureStatus.PASSED) != sheetEntitled
				|| (status == LectureStatus.PASSED) != remoteIssued) {
			throw new IllegalArgumentException("PASSED status requires exactly its Sheet and Remote reward ledgers");
		}
		if (sheetProjectionPending && (status != LectureStatus.PASSED || !sheetEntitled)) {
			throw new IllegalArgumentException("Pending Sheet projection requires a passed Sheet entitlement");
		}
		if (remoteProjectionPending && (status != LectureStatus.PASSED || !remoteIssued)) {
			throw new IllegalArgumentException("Pending Remote projection requires a passed Remote ledger");
		}
		if (remoteIssued != (remoteProjectionUuid != null)) {
			throw new IllegalArgumentException("Remote issued ledger and projection identity must be present together");
		}
		if (legacyRemoteAdoptionPending
				&& (status != LectureStatus.PASSED
					|| !remoteProjectionPending
					|| remoteFallback != null
					|| !legacyRemoteProjectionUuid(ownerUuid, deskPos, attemptCount).equals(remoteProjectionUuid))) {
			throw new IllegalArgumentException("Legacy Remote adoption requires a pending passed projection");
		}
		if (sheetFallback != null && (status != LectureStatus.PASSED || !sheetEntitled)) {
			throw new IllegalArgumentException("Sheet fallback requires a passed Sheet entitlement");
		}
		if (remoteFallback != null && (status != LectureStatus.PASSED || !remoteIssued)) {
			throw new IllegalArgumentException("Remote fallback requires a passed Remote entitlement");
		}
		if (sheetFallback != null && !sheetFallback.materialized() && !sheetProjectionPending) {
			throw new IllegalArgumentException("Sheet fallback reservation requires a pending projection");
		}
		if (remoteFallback != null && !remoteFallback.materialized() && !remoteProjectionPending) {
			throw new IllegalArgumentException("Remote fallback reservation requires a pending projection");
		}
		if (sheetFallback != null && remoteFallback != null
				&& sheetFallback.entityUuid().equals(remoteFallback.entityUuid())) {
			throw new IllegalArgumentException("Sheet and Remote fallbacks require distinct entity UUIDs");
		}
		if (!retakeEntitled && (retakeEncounterUuid != null
				|| retakeFallbackReservationUuid != null
				|| retakeFallbackEntityUuid != null)) {
			throw new IllegalArgumentException("Retake identity and fallbacks require a Retake entitlement");
		}
		if (retakeEntitled && retakeEncounterUuid == null) {
			throw new IllegalArgumentException("Retake entitlement requires its failed encounter UUID");
		}
		if (retakeEntitled && attemptCount < 1) {
			throw new IllegalArgumentException("Retake entitlement requires a positive failed attempt");
		}
		if (retakeFallbackReservationUuid != null && retakeFallbackEntityUuid != null) {
			throw new IllegalArgumentException("Retake fallback cannot be reserved and materialized at once");
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

	/** Source-compatible schema-v1 projection constructor; new records are never legacy migrations. */
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
			UUID retakeEncounterUuid,
			UUID retakeFallbackReservationUuid,
			UUID retakeFallbackEntityUuid,
			long remoteCooldownUntilGameTime,
			long sheetRecoverySequence,
			long remoteReadyNoticeForDeadlineGameTime,
			boolean sheetProjectionPending,
			boolean remoteProjectionPending,
			UUID remoteProjectionUuid
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
				retakeEncounterUuid,
				retakeFallbackReservationUuid,
				retakeFallbackEntityUuid,
				remoteCooldownUntilGameTime,
				sheetRecoverySequence,
				remoteReadyNoticeForDeadlineGameTime,
				sheetProjectionPending,
				remoteProjectionPending,
				remoteProjectionUuid,
				false,
				null,
				null
		);
	}

	/** Source-compatible schema-v2 constructor for records without reward fallback reservations. */
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
			UUID retakeEncounterUuid,
			UUID retakeFallbackReservationUuid,
			UUID retakeFallbackEntityUuid,
			long remoteCooldownUntilGameTime,
			long sheetRecoverySequence,
			long remoteReadyNoticeForDeadlineGameTime,
			boolean sheetProjectionPending,
			boolean remoteProjectionPending,
			UUID remoteProjectionUuid,
			boolean legacyRemoteAdoptionPending
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
				retakeEncounterUuid,
				retakeFallbackReservationUuid,
				retakeFallbackEntityUuid,
				remoteCooldownUntilGameTime,
				sheetRecoverySequence,
				remoteReadyNoticeForDeadlineGameTime,
				sheetProjectionPending,
				remoteProjectionPending,
				remoteProjectionUuid,
				legacyRemoteAdoptionPending,
				null,
				null
		);
	}

	/**
	 * Schema-v1 compatibility constructor. Historical records had no independent pending markers;
	 * a deterministic owner projection identity preserves their no-reissue semantics.
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
			UUID retakeEncounterUuid,
			UUID retakeFallbackReservationUuid,
			UUID retakeFallbackEntityUuid,
			long remoteCooldownUntilGameTime,
			long sheetRecoverySequence,
			long remoteReadyNoticeForDeadlineGameTime
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
				retakeEncounterUuid,
				retakeFallbackReservationUuid,
				retakeFallbackEntityUuid,
				remoteCooldownUntilGameTime,
				sheetRecoverySequence,
				remoteReadyNoticeForDeadlineGameTime,
				false,
				false,
				remoteIssued ? legacyRemoteProjectionUuid(ownerUuid, deskPos, attemptCount) : null
		);
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
			long remoteCooldownUntilGameTime,
			long sheetRecoverySequence,
			long remoteReadyNoticeForDeadlineGameTime
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
				retakeEntitled ? legacyRetakeEncounterUuid(ownerUuid, deskPos, attemptCount) : null,
				null,
				retakeFallbackEntityUuid,
				remoteCooldownUntilGameTime,
				sheetRecoverySequence,
				remoteReadyNoticeForDeadlineGameTime
		);
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

	public Optional<RetakeKey> retakeKey() {
		return retakeEntitled
				? Optional.of(new RetakeKey(ownerUuid, retakeEncounterUuid))
				: Optional.empty();
	}

	static UUID legacyRetakeEncounterUuid(UUID ownerUuid, BlockPos deskPos, int attemptCount) {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(deskPos, "deskPos");
		String value = "encounter:" + ownerUuid + ":" + deskPos.getX() + ":" + deskPos.getY()
				+ ":" + deskPos.getZ() + ":" + attemptCount;
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	public static UUID legacyRemoteProjectionUuid(UUID ownerUuid, BlockPos deskPos, int attemptCount) {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(deskPos, "deskPos");
		String value = "remote:" + ownerUuid + ":" + deskPos.getX() + ":" + deskPos.getY()
				+ ":" + deskPos.getZ() + ":" + attemptCount;
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
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

	/** Durable logical Retake identity, independent of inventory or entity representation. */
	public record RetakeKey(UUID ownerUuid, UUID failedEncounterUuid) {
		public RetakeKey {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(failedEncounterUuid, "failedEncounterUuid");
		}
	}

	/** Exact durable identity and last known chunk context for a loose reward representation. */
	public record RewardFallbackRef(
			UUID entityUuid,
			String dimension,
			BlockPos position,
			boolean materialized
	) {
		public RewardFallbackRef {
			Objects.requireNonNull(entityUuid, "entityUuid");
			if (Objects.requireNonNull(dimension, "dimension").isBlank()) {
				throw new IllegalArgumentException("fallback dimension must not be blank");
			}
			position = Objects.requireNonNull(position, "position").immutable();
		}

		public RewardFallbackRef at(String nextDimension, BlockPos nextPosition, boolean nextMaterialized) {
			return new RewardFallbackRef(entityUuid, nextDimension, nextPosition, nextMaterialized);
		}
	}
}
