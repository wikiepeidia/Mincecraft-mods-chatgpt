package dev.developershell.campaign;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.developershell.DevelopersHell;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Durable, Overworld-owned state for the campaign's first lecture slice. */
public final class CampaignSavedData extends SavedData {
	public static final int SCHEMA_VERSION = 2;
	private static final int LEGACY_SCHEMA_VERSION = 1;

	private static final Codec<PlayerCampaignState.LectureStatus> STATUS_CODEC = Codec.STRING.comapFlatMap(
			value -> decodeName("lecture status", value, PlayerCampaignState.LectureStatus::fromSerializedName),
			PlayerCampaignState.LectureStatus::serializedName
	);
	private static final Codec<PlayerCampaignState.CampaignChapter> CHAPTER_CODEC = Codec.STRING.comapFlatMap(
			value -> decodeName("campaign chapter", value, PlayerCampaignState.CampaignChapter::fromSerializedName),
			PlayerCampaignState.CampaignChapter::serializedName
	);

	/* MapCodec groups keep persisted fields flat while avoiding RecordCodecBuilder's 16-field ceiling. */
	private static final MapCodec<IdentityFields> IDENTITY_FIELDS_CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(
					UUIDUtil.CODEC.optionalFieldOf("map_key_uuid").forGetter(IdentityFields::mapKeyUuid),
					UUIDUtil.CODEC.fieldOf("owner_uuid").forGetter(IdentityFields::ownerUuid),
					CHAPTER_CODEC.optionalFieldOf("chapter").forGetter(IdentityFields::chapter),
					STATUS_CODEC.fieldOf("lecture_status").forGetter(IdentityFields::status),
					Codec.INT.fieldOf("attempt_count").forGetter(IdentityFields::attemptCount),
					Codec.STRING.optionalFieldOf("desk_dimension", PlayerCampaignState.OVERWORLD_DIMENSION)
							.forGetter(IdentityFields::deskDimension),
					BlockPos.CODEC.fieldOf("desk_pos").forGetter(IdentityFields::deskPos),
					Direction.CODEC.fieldOf("desk_facing").forGetter(IdentityFields::deskFacing),
					BlockPos.CODEC.fieldOf("retry_pos").forGetter(IdentityFields::retryPos)
			).apply(instance, IdentityFields::new)
	);

	private static final MapCodec<DurableFields> DURABLE_FIELDS_CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(
					UUIDUtil.CODEC.optionalFieldOf("encounter_uuid").forGetter(DurableFields::encounterUuid),
					UUIDUtil.CODEC.optionalFieldOf("professor_uuid").forGetter(DurableFields::professorUuid),
					Codec.BOOL.fieldOf("sheet_entitled").forGetter(DurableFields::sheetEntitled),
					Codec.BOOL.fieldOf("remote_issued").forGetter(DurableFields::remoteIssued),
					Codec.BOOL.optionalFieldOf("retake_entitled", false).forGetter(DurableFields::retakeEntitled),
					UUIDUtil.CODEC.optionalFieldOf("retake_encounter_uuid")
							.forGetter(DurableFields::retakeEncounterUuid),
					UUIDUtil.CODEC.optionalFieldOf("retake_fallback_reservation_uuid")
							.forGetter(DurableFields::retakeFallbackReservationUuid),
					UUIDUtil.CODEC.optionalFieldOf("retake_fallback_entity_uuid")
							.forGetter(DurableFields::retakeFallbackEntityUuid),
					Codec.LONG.optionalFieldOf("remote_cooldown_until_game_time", 0L)
							.forGetter(DurableFields::remoteCooldownUntilGameTime),
					Codec.LONG.optionalFieldOf("sheet_recovery_sequence", 0L)
							.forGetter(DurableFields::sheetRecoverySequence),
					Codec.LONG.optionalFieldOf("remote_ready_notice_for_deadline_game_time", 0L)
							.forGetter(DurableFields::remoteReadyNoticeForDeadlineGameTime),
					Codec.BOOL.optionalFieldOf("sheet_projection_pending")
							.forGetter(DurableFields::sheetProjectionPending),
					Codec.BOOL.optionalFieldOf("remote_projection_pending")
							.forGetter(DurableFields::remoteProjectionPending),
					UUIDUtil.CODEC.optionalFieldOf("remote_projection_uuid")
							.forGetter(DurableFields::remoteProjectionUuid),
					Codec.BOOL.optionalFieldOf("legacy_remote_adoption_pending")
							.forGetter(DurableFields::legacyRemoteAdoptionPending)
			).apply(instance, DurableFields::new)
	);
	private static final Codec<PlayerCampaignState.RewardFallbackRef> REWARD_FALLBACK_CODEC =
			RecordCodecBuilder.create(instance -> instance.group(
					UUIDUtil.CODEC.fieldOf("entity_uuid")
							.forGetter(PlayerCampaignState.RewardFallbackRef::entityUuid),
					Codec.STRING.fieldOf("dimension")
							.forGetter(PlayerCampaignState.RewardFallbackRef::dimension),
					BlockPos.CODEC.fieldOf("position")
							.forGetter(PlayerCampaignState.RewardFallbackRef::position),
					Codec.BOOL.fieldOf("materialized")
							.forGetter(PlayerCampaignState.RewardFallbackRef::materialized)
			).apply(instance, PlayerCampaignState.RewardFallbackRef::new));
	private static final MapCodec<RewardFallbackFields> REWARD_FALLBACK_FIELDS_CODEC =
			RecordCodecBuilder.mapCodec(instance -> instance.group(
					REWARD_FALLBACK_CODEC.optionalFieldOf("sheet_fallback")
							.forGetter(RewardFallbackFields::sheetFallback),
					REWARD_FALLBACK_CODEC.optionalFieldOf("remote_fallback")
							.forGetter(RewardFallbackFields::remoteFallback)
			).apply(instance, RewardFallbackFields::new));

	private static final Codec<RawPlayer> RAW_PLAYER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			IDENTITY_FIELDS_CODEC.forGetter(RawPlayer::identity),
			DURABLE_FIELDS_CODEC.forGetter(RawPlayer::durable),
			REWARD_FALLBACK_FIELDS_CODEC.forGetter(RawPlayer::rewardFallbacks)
	).apply(instance, RawPlayer::new));

	private static final Codec<RawDocument> RAW_DOCUMENT_CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					Codec.intRange(LEGACY_SCHEMA_VERSION, SCHEMA_VERSION).fieldOf("schema")
							.forGetter(RawDocument::schemaVersion),
					RAW_PLAYER_CODEC.listOf().fieldOf("players").forGetter(RawDocument::players)
			).apply(instance, RawDocument::new)
	);
	private static final Codec<CampaignSavedData> WRITABLE_DOCUMENT_CODEC = RAW_DOCUMENT_CODEC.comapFlatMap(
			CampaignSavedData::decodeDocument,
			CampaignSavedData::encodeDocument
	);

	/**
	 * The fallback side deliberately succeeds for unknown or malformed documents. SavedDataStorage
	 * therefore returns a visible read-only value instead of silently replacing user data with a
	 * fresh schema-1 object. Its original Dynamic is retained byte-for-byte at the data-model level.
	 */
	private static final Codec<Either<CampaignSavedData, Dynamic<?>>> ENVELOPE_CODEC = Codec.either(
			WRITABLE_DOCUMENT_CODEC,
			Codec.PASSTHROUGH
	);
	private static final Codec<CampaignSavedData> CODEC = ENVELOPE_CODEC.xmap(
			either -> either.map(data -> data, CampaignSavedData::readOnlyRaw),
			data -> data.rawDocument == null ? Either.left(data) : Either.right(data.rawDocument)
	);

	public static final SavedDataType<CampaignSavedData> TYPE = new SavedDataType<>(
			DevelopersHell.id("campaign"),
			CampaignSavedData::empty,
			CODEC,
			null
	);

	private final int schemaVersion;
	private final Map<UUID, PlayerCampaignState> players;
	private final boolean ownerKeysValid;
	private final ReadDisposition readDisposition;
	private final Dynamic<?> rawDocument;

	private CampaignSavedData(
			int schemaVersion,
			Map<UUID, PlayerCampaignState> players,
			boolean ownerKeysValid,
			ReadDisposition readDisposition,
			Dynamic<?> rawDocument
	) {
		this.schemaVersion = schemaVersion;
		this.players = new LinkedHashMap<>(players);
		this.ownerKeysValid = ownerKeysValid;
		this.readDisposition = Objects.requireNonNull(readDisposition, "readDisposition");
		this.rawDocument = rawDocument;
	}

	private static CampaignSavedData empty() {
		return new CampaignSavedData(SCHEMA_VERSION, Map.of(), true, ReadDisposition.WRITABLE, null);
	}

	static CampaignSavedData createForTesting(Map<UUID, PlayerCampaignState> players) {
		Objects.requireNonNull(players, "players");
		boolean valid = players.entrySet().stream()
				.allMatch(entry -> entry.getKey().equals(entry.getValue().ownerUuid()));
		return new CampaignSavedData(
				SCHEMA_VERSION,
				players,
				valid,
				valid ? ReadDisposition.WRITABLE : ReadDisposition.INVALID_OWNER_KEYS,
				null
		);
	}

	private static DataResult<CampaignSavedData> decodeDocument(RawDocument document) {
		Map<UUID, PlayerCampaignState> players = new LinkedHashMap<>();
		boolean valid = true;
		for (RawPlayer rawPlayer : document.players()) {
			DataResult<EncodedPlayer> decoded = decodePlayer(rawPlayer, document.schemaVersion());
			if (decoded.error().isPresent()) {
				String message = decoded.error().orElseThrow().message();
				return DataResult.error(() -> message);
			}
			EncodedPlayer encodedPlayer = decoded.result().orElseThrow();
			UUID mapKeyUuid = encodedPlayer.mapKeyUuid();
			PlayerCampaignState state = encodedPlayer.state();
			boolean duplicateKey = players.putIfAbsent(mapKeyUuid, state) != null;
			if (!mapKeyUuid.equals(state.ownerUuid()) || duplicateKey) {
				valid = false;
			}
		}
		CampaignSavedData data = new CampaignSavedData(
				valid ? SCHEMA_VERSION : document.schemaVersion(),
				players,
				valid,
				valid ? ReadDisposition.WRITABLE : ReadDisposition.INVALID_OWNER_KEYS,
				null
		);
		if (valid && document.schemaVersion() < SCHEMA_VERSION) {
			data.setDirty();
		}
		return DataResult.success(data);
	}

	private static RawDocument encodeDocument(CampaignSavedData data) {
		return new RawDocument(data.schemaVersion(), data.rawPlayers());
	}

	private static CampaignSavedData readOnlyRaw(Dynamic<?> rawDocument) {
		int discoveredSchema = rawDocument.get("schema").asInt(-1);
		ReadDisposition disposition = discoveredSchema > SCHEMA_VERSION
				? ReadDisposition.FUTURE_SCHEMA
				: ReadDisposition.CORRUPT_DATA;
		return new CampaignSavedData(discoveredSchema, Map.of(), true, disposition, rawDocument);
	}

	private static DataResult<EncodedPlayer> decodePlayer(RawPlayer raw, int schemaVersion) {
		IdentityFields identity = raw.identity();
		DurableFields durable = raw.durable();
		RewardFallbackFields rewardFallbacks = raw.rewardFallbacks();
		if (schemaVersion == SCHEMA_VERSION
				&& (durable.sheetProjectionPending().isEmpty()
					|| durable.remoteProjectionPending().isEmpty()
					|| durable.legacyRemoteAdoptionPending().isEmpty())) {
			return DataResult.error(() -> "schema-2 reward projection markers must be present");
		}
		boolean hasEncounter = durable.encounterUuid().isPresent();
		boolean hasProfessor = durable.professorUuid().isPresent();
		if (identity.status() != PlayerCampaignState.LectureStatus.READY && identity.attemptCount() < 1) {
			return DataResult.error(() -> "non-READY campaign status requires a positive attempt");
		}
		if (hasEncounter != hasProfessor) {
			return DataResult.error(() -> "encounter_uuid and professor_uuid must be present together");
		}
		if (identity.status() == PlayerCampaignState.LectureStatus.ACTIVE && !hasEncounter) {
			return DataResult.error(() -> "active lecture state requires encounter identity");
		}
		if (identity.status() != PlayerCampaignState.LectureStatus.ACTIVE && hasEncounter) {
			return DataResult.error(() -> "inactive lecture state must not retain encounter identity");
		}
		if ((identity.status() == PlayerCampaignState.LectureStatus.RETAKE_READY)
				!= durable.retakeEntitled()) {
			return DataResult.error(() -> "RETAKE_READY status and Retake entitlement must be present together");
		}
		boolean passed = identity.status() == PlayerCampaignState.LectureStatus.PASSED;
		if (passed != durable.sheetEntitled() || passed != durable.remoteIssued()) {
			return DataResult.error(() -> "PASSED status requires exactly its Sheet and Remote reward ledgers");
		}

		PlayerCampaignState.CampaignChapter chapter = identity.chapter().orElseGet(() ->
				identity.status() == PlayerCampaignState.LectureStatus.PASSED
						? PlayerCampaignState.CampaignChapter.LECTURE_PASSED
						: PlayerCampaignState.CampaignChapter.PRE_LECTURE
		);
		if ((identity.status() == PlayerCampaignState.LectureStatus.PASSED)
				!= (chapter == PlayerCampaignState.CampaignChapter.LECTURE_PASSED)) {
			return DataResult.error(() -> "campaign chapter and lecture status disagree");
		}

		try {
			UUID retakeEncounterUuid = durable.retakeEncounterUuid().orElseGet(() ->
					durable.retakeEntitled()
							? PlayerCampaignState.legacyRetakeEncounterUuid(
									identity.ownerUuid(),
									identity.deskPos(),
									identity.attemptCount()
							)
							: null
			);
			UUID legacyProjectionUuid = durable.remoteIssued()
					? PlayerCampaignState.legacyRemoteProjectionUuid(
							identity.ownerUuid(), identity.deskPos(), identity.attemptCount()
					)
					: null;
			UUID remoteProjectionUuid = durable.remoteProjectionUuid().orElseGet(() ->
					durable.remoteIssued()
							? legacyProjectionUuid
							: null
			);
			boolean legacyRemoteAdoptionPending = schemaVersion == LEGACY_SCHEMA_VERSION
					&& passed
					&& (durable.legacyRemoteAdoptionPending().orElse(false)
							|| (durable.remoteProjectionUuid().isEmpty()
									&& durable.remoteProjectionPending().isEmpty())
							|| durable.remoteProjectionUuid().filter(legacyProjectionUuid::equals).isPresent());
			boolean remoteProjectionPending = legacyRemoteAdoptionPending
					|| durable.remoteProjectionPending().orElse(false);
			PlayerCampaignState.EncounterRef encounter = hasEncounter
					? new PlayerCampaignState.EncounterRef(
							identity.ownerUuid(),
							durable.encounterUuid().orElseThrow(),
							durable.professorUuid().orElseThrow(),
							identity.attemptCount()
					)
					: null;
			PlayerCampaignState state = new PlayerCampaignState(
					identity.ownerUuid(),
					chapter,
					identity.status(),
					identity.attemptCount(),
					identity.deskDimension(),
					identity.deskPos(),
					identity.deskFacing(),
					identity.retryPos(),
					encounter,
					durable.sheetEntitled(),
					durable.remoteIssued(),
					durable.retakeEntitled(),
					retakeEncounterUuid,
					durable.retakeFallbackReservationUuid().orElse(null),
					durable.retakeFallbackEntityUuid().orElse(null),
					durable.remoteCooldownUntilGameTime(),
					durable.sheetRecoverySequence(),
					durable.remoteReadyNoticeForDeadlineGameTime(),
					durable.sheetProjectionPending().orElse(false),
					remoteProjectionPending,
					remoteProjectionUuid,
					legacyRemoteAdoptionPending,
					rewardFallbacks.sheetFallback().orElse(null),
					rewardFallbacks.remoteFallback().orElse(null)
			);
			return DataResult.success(new EncodedPlayer(identity.mapKeyUuid().orElse(identity.ownerUuid()), state));
		}
		catch (IllegalArgumentException exception) {
			return DataResult.error(exception::getMessage);
		}
	}

	private static RawPlayer encodePlayer(EncodedPlayer encodedPlayer) {
		PlayerCampaignState state = encodedPlayer.state();
		return new RawPlayer(
				new IdentityFields(
						Optional.of(encodedPlayer.mapKeyUuid()),
						state.ownerUuid(),
						Optional.of(state.chapter()),
						state.status(),
						state.attemptCount(),
						state.deskDimension(),
						state.deskPos(),
						state.deskFacing(),
						state.retryPos()
				),
				new DurableFields(
						Optional.ofNullable(state.encounterUuid()),
						Optional.ofNullable(state.professorUuid()),
						state.sheetEntitled(),
						state.remoteIssued(),
						state.retakeEntitled(),
						Optional.ofNullable(state.retakeEncounterUuid()),
						Optional.ofNullable(state.retakeFallbackReservationUuid()),
						Optional.ofNullable(state.retakeFallbackEntityUuid()),
						state.remoteCooldownUntilGameTime(),
						state.sheetRecoverySequence(),
						state.remoteReadyNoticeForDeadlineGameTime(),
						Optional.of(state.sheetProjectionPending()),
						Optional.of(state.remoteProjectionPending()),
						Optional.ofNullable(state.remoteProjectionUuid()),
						Optional.of(state.legacyRemoteAdoptionPending())
				),
				new RewardFallbackFields(
						Optional.ofNullable(state.sheetFallback()),
						Optional.ofNullable(state.remoteFallback())
				)
		);
	}

	private static <T> DataResult<T> decodeName(
			String field,
			String value,
			java.util.function.Function<String, T> decoder
	) {
		try {
			return DataResult.success(decoder.apply(value));
		}
		catch (IllegalArgumentException exception) {
			return DataResult.error(() -> "Unknown " + field + ": " + value);
		}
	}

	public static CampaignSavedData get(ServerLevel level) {
		ServerLevel overworld = Objects.requireNonNull(
				level.getServer().getLevel(Level.OVERWORLD),
				"Developer's Hell requires an Overworld"
		);
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	public synchronized Optional<PlayerCampaignState> player(UUID ownerUuid) {
		return Optional.ofNullable(players.get(ownerUuid));
	}

	public synchronized Optional<PlayerCampaignState> playerByMapKey(UUID mapKeyUuid) {
		return Optional.ofNullable(players.get(mapKeyUuid));
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	public boolean hasValidOwnerKeys() {
		return ownerKeysValid;
	}

	public ReadDisposition readDisposition() {
		return readDisposition;
	}

	public boolean isWritableSchema() {
		return readDisposition == ReadDisposition.WRITABLE
				&& schemaVersion == SCHEMA_VERSION
				&& ownerKeysValid;
	}

	synchronized boolean replace(PlayerCampaignState nextState) {
		Objects.requireNonNull(nextState, "nextState");
		if (!isWritableSchema()) {
			return false;
		}
		players.put(nextState.ownerUuid(), nextState);
		return true;
	}

	/** Restores the exact pre-start record only while the failed START is still current. */
	synchronized boolean restoreAfterFailedStart(
			UUID ownerUuid,
			PlayerCampaignState failedStartState,
			Optional<PlayerCampaignState> previousState
	) {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(failedStartState, "failedStartState");
		Objects.requireNonNull(previousState, "previousState");
		if (!isWritableSchema() || !Objects.equals(players.get(ownerUuid), failedStartState)) {
			return false;
		}
		if (previousState.isPresent()) {
			players.put(ownerUuid, previousState.orElseThrow());
		}
		else {
			players.remove(ownerUuid);
		}
		setDirty();
		return true;
	}

	synchronized boolean hasActiveDeskForOther(
			UUID ownerUuid,
			String deskDimension,
			BlockPos deskPos
	) {
		return players.values().stream().anyMatch(state ->
				!state.ownerUuid().equals(ownerUuid)
						&& state.status() == PlayerCampaignState.LectureStatus.ACTIVE
						&& state.deskDimension().equals(deskDimension)
						&& state.deskPos().equals(deskPos)
		);
	}

	private synchronized List<RawPlayer> rawPlayers() {
		List<RawPlayer> rawPlayers = new ArrayList<>(players.size());
		players.forEach((mapKeyUuid, state) ->
				rawPlayers.add(encodePlayer(new EncodedPlayer(mapKeyUuid, state))));
		return rawPlayers;
	}

	static UUID deterministicUuid(String kind, UUID ownerUuid, BlockPos deskPos, int attemptCount) {
		String value = kind + ":" + ownerUuid + ":" + deskPos.getX() + ":" + deskPos.getY() + ":"
				+ deskPos.getZ() + ":" + attemptCount;
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private record IdentityFields(
			Optional<UUID> mapKeyUuid,
			UUID ownerUuid,
			Optional<PlayerCampaignState.CampaignChapter> chapter,
			PlayerCampaignState.LectureStatus status,
			int attemptCount,
			String deskDimension,
			BlockPos deskPos,
			Direction deskFacing,
			BlockPos retryPos
	) {
	}

	private record DurableFields(
			Optional<UUID> encounterUuid,
			Optional<UUID> professorUuid,
			boolean sheetEntitled,
			boolean remoteIssued,
			boolean retakeEntitled,
			Optional<UUID> retakeEncounterUuid,
			Optional<UUID> retakeFallbackReservationUuid,
			Optional<UUID> retakeFallbackEntityUuid,
			long remoteCooldownUntilGameTime,
			long sheetRecoverySequence,
			long remoteReadyNoticeForDeadlineGameTime,
			Optional<Boolean> sheetProjectionPending,
			Optional<Boolean> remoteProjectionPending,
			Optional<UUID> remoteProjectionUuid,
			Optional<Boolean> legacyRemoteAdoptionPending
	) {
	}

	private record RewardFallbackFields(
			Optional<PlayerCampaignState.RewardFallbackRef> sheetFallback,
			Optional<PlayerCampaignState.RewardFallbackRef> remoteFallback
	) {
	}

	private record RawPlayer(
			IdentityFields identity,
			DurableFields durable,
			RewardFallbackFields rewardFallbacks
	) {
	}

	private record RawDocument(int schemaVersion, List<RawPlayer> players) {
		private RawDocument {
			players = List.copyOf(players);
		}
	}

	private record EncodedPlayer(UUID mapKeyUuid, PlayerCampaignState state) {
		private EncodedPlayer {
			Objects.requireNonNull(mapKeyUuid, "mapKeyUuid");
			Objects.requireNonNull(state, "state");
		}
	}

	public enum ReadDisposition {
		WRITABLE("writable"),
		FUTURE_SCHEMA("future_schema"),
		CORRUPT_DATA("corrupt_data"),
		INVALID_OWNER_KEYS("invalid_owner_keys");

		private final String serializedName;

		ReadDisposition(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return serializedName;
		}
	}

	/** Narrow source-compatibility view used by the retained Plan 01 encounter manager. */
	public interface PlayerProgress {
		UUID ownerUuid();

		PlayerCampaignState.LectureStatus status();

		int attemptCount();

		BlockPos deskPos();

		Direction deskFacing();

		BlockPos retryPos();

		UUID encounterUuid();

		UUID professorUuid();

		boolean sheetEntitled();

		boolean remoteIssued();
	}

	/** Stable aliases retained for the green Plan 01 GameTest source. */
	public static final class LectureStatus {
		public static final PlayerCampaignState.LectureStatus READY = PlayerCampaignState.LectureStatus.READY;
		public static final PlayerCampaignState.LectureStatus ACTIVE = PlayerCampaignState.LectureStatus.ACTIVE;
		public static final PlayerCampaignState.LectureStatus RETAKE_READY =
				PlayerCampaignState.LectureStatus.RETAKE_READY;
		public static final PlayerCampaignState.LectureStatus PASSED = PlayerCampaignState.LectureStatus.PASSED;

		private LectureStatus() {
		}
	}
}
