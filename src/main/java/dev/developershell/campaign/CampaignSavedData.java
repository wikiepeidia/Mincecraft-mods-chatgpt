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

	private static final Codec<LectureStatus> STATUS_CODEC = Codec.STRING.xmap(
			name -> LectureStatus.valueOf(name.toUpperCase(java.util.Locale.ROOT)),
			status -> status.name().toLowerCase(java.util.Locale.ROOT)
	);

	private static final Codec<PlayerProgress> PLAYER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("owner_uuid").forGetter(PlayerProgress::ownerUuid),
			STATUS_CODEC.fieldOf("lecture_status").forGetter(PlayerProgress::status),
			Codec.INT.fieldOf("attempt_count").forGetter(PlayerProgress::attemptCount),
			BlockPos.CODEC.fieldOf("desk_pos").forGetter(PlayerProgress::deskPos),
			Direction.CODEC.fieldOf("desk_facing").forGetter(PlayerProgress::deskFacing),
			BlockPos.CODEC.fieldOf("retry_pos").forGetter(PlayerProgress::retryPos),
			UUIDUtil.CODEC.optionalFieldOf("encounter_uuid").forGetter(progress -> Optional.ofNullable(progress.encounterUuid())),
			UUIDUtil.CODEC.optionalFieldOf("professor_uuid").forGetter(progress -> Optional.ofNullable(progress.professorUuid())),
			Codec.BOOL.fieldOf("sheet_entitled").forGetter(PlayerProgress::sheetEntitled),
			Codec.BOOL.fieldOf("remote_issued").forGetter(PlayerProgress::remoteIssued)
	).apply(instance, (ownerUuid, status, attemptCount, deskPos, deskFacing, retryPos, encounterUuid, professorUuid,
			sheetEntitled, remoteIssued) -> new PlayerProgress(
				ownerUuid,
				status,
				attemptCount,
				deskPos,
				deskFacing,
				retryPos,
				encounterUuid.orElse(null),
				professorUuid.orElse(null),
				sheetEntitled,
				remoteIssued
		)));

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
	private final Map<UUID, PlayerProgress> players;

	private CampaignSavedData(int schemaVersion, Map<UUID, PlayerProgress> players) {
		this.schemaVersion = schemaVersion;
		this.players = new LinkedHashMap<>(players);
	}

	private static CampaignSavedData empty() {
		return new CampaignSavedData(SCHEMA_VERSION, Map.of());
	}

	private static CampaignSavedData decode(int schemaVersion, List<PlayerProgress> encodedPlayers) {
		Map<UUID, PlayerProgress> players = new LinkedHashMap<>();
		for (PlayerProgress progress : encodedPlayers) {
			players.putIfAbsent(progress.ownerUuid(), progress);
		}
		return new CampaignSavedData(schemaVersion, players);
	}

	public static CampaignSavedData get(ServerLevel level) {
		ServerLevel overworld = Objects.requireNonNull(
				level.getServer().getLevel(Level.OVERWORLD),
				"Developer's Hell requires an Overworld"
		);
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	public synchronized Optional<PlayerProgress> player(UUID ownerUuid) {
		return Optional.ofNullable(players.get(ownerUuid));
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	public boolean isWritableSchema() {
		return schemaVersion == SCHEMA_VERSION;
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
		PlayerProgress previous = players.get(ownerUuid);
		if (previous != null && (previous.status() == LectureStatus.ACTIVE || previous.status() == LectureStatus.PASSED)) {
			return Optional.empty();
		}
		boolean deskBusy = players.values().stream()
				.anyMatch(progress -> progress.status() == LectureStatus.ACTIVE && progress.deskPos().equals(deskPos));
		if (deskBusy) {
			return Optional.empty();
		}

		int attemptCount = previous == null ? 1 : previous.attemptCount() + 1;
		UUID encounterUuid = deterministicUuid("encounter", ownerUuid, deskPos, attemptCount);
		UUID professorUuid = deterministicUuid("professor", ownerUuid, deskPos, attemptCount);
		PlayerProgress current = new PlayerProgress(
				ownerUuid,
				LectureStatus.ACTIVE,
				attemptCount,
				deskPos.immutable(),
				deskFacing,
				retryPos.immutable(),
				encounterUuid,
				professorUuid,
				previous != null && previous.sheetEntitled(),
				previous != null && previous.remoteIssued()
		);
		players.put(ownerUuid, current);
		return Optional.of(new StartCommit(current, previous));
	}

	public synchronized boolean rollbackStart(StartCommit commit) {
		PlayerProgress current = players.get(commit.current().ownerUuid());
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
		PlayerProgress active = players.get(ownerUuid);
		if (active == null
				|| active.status() != LectureStatus.ACTIVE
				|| !Objects.equals(active.encounterUuid(), encounterUuid)) {
			return false;
		}
		players.put(ownerUuid, new PlayerProgress(
				ownerUuid,
				LectureStatus.PASSED,
				active.attemptCount(),
				active.deskPos(),
				active.deskFacing(),
				active.retryPos(),
				null,
				null,
				true,
				true
		));
		return true;
	}

	private List<PlayerProgress> encodedPlayers() {
		return new ArrayList<>(players.values());
	}

	private static UUID deterministicUuid(String kind, UUID ownerUuid, BlockPos deskPos, int attemptCount) {
		String value = kind + ":" + ownerUuid + ":" + deskPos.getX() + ":" + deskPos.getY() + ":"
				+ deskPos.getZ() + ":" + attemptCount;
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	public enum LectureStatus {
		READY,
		ACTIVE,
		RETAKE_READY,
		PASSED
	}

	public record StartCommit(PlayerProgress current, PlayerProgress previous) {
	}

	public static final class PlayerProgress {
		private final UUID ownerUuid;
		private final LectureStatus status;
		private final int attemptCount;
		private final BlockPos deskPos;
		private final Direction deskFacing;
		private final BlockPos retryPos;
		private final UUID encounterUuid;
		private final UUID professorUuid;
		private final boolean sheetEntitled;
		private final boolean remoteIssued;

		private PlayerProgress(
				UUID ownerUuid,
				LectureStatus status,
				int attemptCount,
				BlockPos deskPos,
				Direction deskFacing,
				BlockPos retryPos,
				UUID encounterUuid,
				UUID professorUuid,
				boolean sheetEntitled,
				boolean remoteIssued
		) {
			this.ownerUuid = ownerUuid;
			this.status = status;
			this.attemptCount = attemptCount;
			this.deskPos = deskPos.immutable();
			this.deskFacing = deskFacing;
			this.retryPos = retryPos.immutable();
			this.encounterUuid = encounterUuid;
			this.professorUuid = professorUuid;
			this.sheetEntitled = sheetEntitled;
			this.remoteIssued = remoteIssued;
		}

		public UUID ownerUuid() {
			return ownerUuid;
		}

		public LectureStatus status() {
			return status;
		}

		public int attemptCount() {
			return attemptCount;
		}

		public BlockPos deskPos() {
			return deskPos;
		}

		public Direction deskFacing() {
			return deskFacing;
		}

		public BlockPos retryPos() {
			return retryPos;
		}

		public UUID encounterUuid() {
			return encounterUuid;
		}

		public UUID professorUuid() {
			return professorUuid;
		}

		public boolean sheetEntitled() {
			return sheetEntitled;
		}

		public boolean remoteIssued() {
			return remoteIssued;
		}
	}
}
