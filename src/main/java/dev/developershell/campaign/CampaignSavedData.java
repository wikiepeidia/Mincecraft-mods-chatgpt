package dev.developershell.campaign;

import com.mojang.serialization.Codec;
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
	public static final int SCHEMA_VERSION = 1;

	private static final Codec<PlayerCampaignState.LectureStatus> STATUS_CODEC = Codec.STRING.xmap(
			PlayerCampaignState.LectureStatus::fromSerializedName,
			PlayerCampaignState.LectureStatus::serializedName
	);
	private static final Codec<PlayerCampaignState.CampaignChapter> CHAPTER_CODEC = Codec.STRING.xmap(
			PlayerCampaignState.CampaignChapter::fromSerializedName,
			PlayerCampaignState.CampaignChapter::serializedName
	);

	/**
	 * The explicit map key preserves the existing list payload while letting schema 1 reject an
	 * encoded key that does not match the state's owner. All Plan 01 field names remain unchanged,
	 * and newly frozen fields are optional so existing tracer saves decode safely.
	 */
	private static final Codec<EncodedPlayer> PLAYER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.optionalFieldOf("map_key_uuid").forGetter(player -> Optional.of(player.mapKeyUuid())),
			UUIDUtil.CODEC.fieldOf("owner_uuid").forGetter(player -> player.state().ownerUuid()),
			CHAPTER_CODEC.optionalFieldOf("chapter").forGetter(player -> Optional.of(player.state().chapter())),
			STATUS_CODEC.fieldOf("lecture_status").forGetter(player -> player.state().status()),
			Codec.INT.fieldOf("attempt_count").forGetter(player -> player.state().attemptCount()),
			Codec.STRING.optionalFieldOf("desk_dimension", PlayerCampaignState.OVERWORLD_DIMENSION)
					.forGetter(player -> player.state().deskDimension()),
			BlockPos.CODEC.fieldOf("desk_pos").forGetter(player -> player.state().deskPos()),
			Direction.CODEC.fieldOf("desk_facing").forGetter(player -> player.state().deskFacing()),
			BlockPos.CODEC.fieldOf("retry_pos").forGetter(player -> player.state().retryPos()),
			UUIDUtil.CODEC.optionalFieldOf("encounter_uuid")
					.forGetter(player -> Optional.ofNullable(player.state().encounterUuid())),
			UUIDUtil.CODEC.optionalFieldOf("professor_uuid")
					.forGetter(player -> Optional.ofNullable(player.state().professorUuid())),
			Codec.BOOL.fieldOf("sheet_entitled").forGetter(player -> player.state().sheetEntitled()),
			Codec.BOOL.fieldOf("remote_issued").forGetter(player -> player.state().remoteIssued()),
			Codec.BOOL.optionalFieldOf("retake_entitled", false)
					.forGetter(player -> player.state().retakeEntitled()),
			UUIDUtil.CODEC.optionalFieldOf("retake_fallback_entity_uuid")
					.forGetter(player -> Optional.ofNullable(player.state().retakeFallbackEntityUuid())),
			Codec.LONG.optionalFieldOf("remote_cooldown_until_game_time", 0L)
					.forGetter(player -> player.state().remoteCooldownUntilGameTime())
	).apply(instance, (
			mapKeyUuid,
			ownerUuid,
			chapter,
			status,
			attemptCount,
			deskDimension,
			deskPos,
			deskFacing,
			retryPos,
			encounterUuid,
			professorUuid,
			sheetEntitled,
			remoteIssued,
			retakeEntitled,
			retakeFallbackEntityUuid,
			remoteCooldownUntilGameTime
	) -> {
		PlayerCampaignState.EncounterRef activeEncounter = encounterUuid.isPresent() && professorUuid.isPresent()
				? new PlayerCampaignState.EncounterRef(
						ownerUuid,
						encounterUuid.get(),
						professorUuid.get(),
						attemptCount
				)
				: null;
		PlayerCampaignState.CampaignChapter decodedChapter = chapter.orElseGet(() ->
				status == PlayerCampaignState.LectureStatus.PASSED
						? PlayerCampaignState.CampaignChapter.LECTURE_PASSED
						: PlayerCampaignState.CampaignChapter.PRE_LECTURE
		);
		PlayerCampaignState state = new PlayerCampaignState(
				ownerUuid,
				decodedChapter,
				status,
				attemptCount,
				deskDimension,
				deskPos,
				deskFacing,
				retryPos,
				activeEncounter,
				sheetEntitled,
				remoteIssued,
				retakeEntitled,
				retakeFallbackEntityUuid.orElse(null),
				remoteCooldownUntilGameTime
		);
		return new EncodedPlayer(mapKeyUuid.orElse(ownerUuid), state);
	}));

	private static final Codec<CampaignSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("schema").forGetter(CampaignSavedData::schemaVersion),
			PLAYER_CODEC.listOf().fieldOf("players").forGetter(CampaignSavedData::encodedPlayers)
	).apply(instance, CampaignSavedData::decode));

	public static final SavedDataType<CampaignSavedData> TYPE = new SavedDataType<>(
			DevelopersHell.id("campaign"),
			CampaignSavedData::empty,
			CODEC,
			null
	);

	private final int schemaVersion;
	private final Map<UUID, PlayerCampaignState> players;
	private final boolean ownerKeysValid;

	private CampaignSavedData(
			int schemaVersion,
			Map<UUID, PlayerCampaignState> players,
			boolean ownerKeysValid
	) {
		this.schemaVersion = schemaVersion;
		this.players = new LinkedHashMap<>(players);
		this.ownerKeysValid = ownerKeysValid;
	}

	private static CampaignSavedData empty() {
		return new CampaignSavedData(SCHEMA_VERSION, Map.of(), true);
	}

	private static CampaignSavedData decode(int schemaVersion, List<EncodedPlayer> encodedPlayers) {
		Map<UUID, PlayerCampaignState> players = new LinkedHashMap<>();
		boolean ownerKeysValid = true;
		for (EncodedPlayer encodedPlayer : encodedPlayers) {
			UUID mapKeyUuid = encodedPlayer.mapKeyUuid();
			PlayerCampaignState state = encodedPlayer.state();
			if (!mapKeyUuid.equals(state.ownerUuid())) {
				ownerKeysValid = false;
			}
			if (players.putIfAbsent(mapKeyUuid, state) != null) {
				ownerKeysValid = false;
			}
		}
		return new CampaignSavedData(schemaVersion, players, ownerKeysValid);
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

	public int schemaVersion() {
		return schemaVersion;
	}

	public boolean hasValidOwnerKeys() {
		return ownerKeysValid;
	}

	public boolean isWritableSchema() {
		return schemaVersion == SCHEMA_VERSION && ownerKeysValid;
	}

	/**
	 * Atomically accepts one owner/desk start and returns the state needed to materialize it.
	 * The caller must mark this SavedData dirty before applying any side effects.
	 */
	public synchronized Optional<StartCommit> beginEncounter(
			UUID ownerUuid,
			BlockPos deskPos,
			Direction deskFacing,
			BlockPos retryPos
	) {
		if (!isWritableSchema()) {
			return Optional.empty();
		}
		PlayerCampaignState previous = players.get(ownerUuid);
		if (previous != null && (previous.status() == PlayerCampaignState.LectureStatus.ACTIVE
				|| previous.status() == PlayerCampaignState.LectureStatus.PASSED)) {
			return Optional.empty();
		}
		boolean deskBusy = players.values().stream()
				.anyMatch(progress -> progress.status() == PlayerCampaignState.LectureStatus.ACTIVE
						&& progress.deskPos().equals(deskPos));
		if (deskBusy) {
			return Optional.empty();
		}

		int attemptCount = previous == null ? 1 : previous.attemptCount() + 1;
		UUID encounterUuid = deterministicUuid("encounter", ownerUuid, deskPos, attemptCount);
		UUID professorUuid = deterministicUuid("professor", ownerUuid, deskPos, attemptCount);
		PlayerCampaignState current = new PlayerCampaignState(
				ownerUuid,
				previous == null ? PlayerCampaignState.CampaignChapter.PRE_LECTURE : previous.chapter(),
				PlayerCampaignState.LectureStatus.ACTIVE,
				attemptCount,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				deskPos,
				deskFacing,
				retryPos,
				new PlayerCampaignState.EncounterRef(ownerUuid, encounterUuid, professorUuid, attemptCount),
				previous != null && previous.sheetEntitled(),
				previous != null && previous.remoteIssued(),
				false,
				null,
				previous == null ? 0L : previous.remoteCooldownUntilGameTime()
		);
		players.put(ownerUuid, current);
		return Optional.of(new StartCommit(current, previous));
	}

	public synchronized boolean rollbackStart(StartCommit commit) {
		PlayerCampaignState current = players.get(commit.current().ownerUuid());
		if (current == null || !Objects.equals(current.encounterUuid(), commit.current().encounterUuid())) {
			return false;
		}
		if (commit.previous() == null) {
			players.remove(current.ownerUuid());
		}
		else {
			players.put(current.ownerUuid(), commit.previous());
		}
		return true;
	}

	/** Commits a matching victory once; all stale, spoofed, and replayed callbacks are no-ops. */
	public synchronized boolean commitVictory(UUID ownerUuid, UUID encounterUuid) {
		if (!isWritableSchema()) {
			return false;
		}
		PlayerCampaignState active = players.get(ownerUuid);
		if (active == null
				|| active.status() != PlayerCampaignState.LectureStatus.ACTIVE
				|| !active.matchesActiveEncounter(ownerUuid, encounterUuid)) {
			return false;
		}
		players.put(ownerUuid, new PlayerCampaignState(
				ownerUuid,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				active.attemptCount(),
				active.deskDimension(),
				active.deskPos(),
				active.deskFacing(),
				active.retryPos(),
				null,
				true,
				true,
				false,
				null,
				active.remoteCooldownUntilGameTime()
		));
		return true;
	}

	private List<EncodedPlayer> encodedPlayers() {
		List<EncodedPlayer> encodedPlayers = new ArrayList<>(players.size());
		players.forEach((mapKeyUuid, state) -> encodedPlayers.add(new EncodedPlayer(mapKeyUuid, state)));
		return encodedPlayers;
	}

	private static UUID deterministicUuid(String kind, UUID ownerUuid, BlockPos deskPos, int attemptCount) {
		String value = kind + ":" + ownerUuid + ":" + deskPos.getX() + ":" + deskPos.getY() + ":"
				+ deskPos.getZ() + ":" + attemptCount;
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private record EncodedPlayer(UUID mapKeyUuid, PlayerCampaignState state) {
		private EncodedPlayer {
			Objects.requireNonNull(mapKeyUuid, "mapKeyUuid");
			Objects.requireNonNull(state, "state");
		}
	}

	public record StartCommit(PlayerCampaignState current, PlayerCampaignState previous) {
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
