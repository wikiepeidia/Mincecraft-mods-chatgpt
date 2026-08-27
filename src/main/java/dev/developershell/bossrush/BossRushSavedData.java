package dev.developershell.bossrush;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.developershell.DevelopersHell;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Overworld-owned boss-rush checkpoints and first-clear reward authority. */
public final class BossRushSavedData extends SavedData {
	public static final int SCHEMA_VERSION = 1;

	private static final Codec<BossRushStage> STAGE_CODEC = Codec.STRING.comapFlatMap(
			value -> {
				try {
					return DataResult.success(BossRushStage.fromSerializedName(value));
				}
				catch (IllegalArgumentException exception) {
					return DataResult.error(exception::getMessage);
				}
			},
			BossRushStage::serializedName
	);
	private static final Codec<BossRushProgress> PLAYER_CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					UUIDUtil.CODEC.fieldOf("owner_uuid").forGetter(BossRushProgress::ownerUuid),
					STAGE_CODEC.fieldOf("stage").forGetter(BossRushProgress::stage),
					Codec.BOOL.fieldOf("jury_cleared").forGetter(BossRushProgress::juryCleared),
					Codec.BOOL.fieldOf("chairman_cleared").forGetter(BossRushProgress::chairmanCleared),
					Codec.BOOL.fieldOf("diploma_granted").forGetter(BossRushProgress::diplomaGranted)
			).apply(instance, BossRushProgress::new)
	);
	private static final Codec<RawDocument> DOCUMENT_CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					Codec.intRange(SCHEMA_VERSION, SCHEMA_VERSION).fieldOf("schema")
							.forGetter(RawDocument::schema),
					PLAYER_CODEC.listOf().fieldOf("players").forGetter(RawDocument::players)
			).apply(instance, RawDocument::new)
	);
	private static final Codec<BossRushSavedData> CODEC = DOCUMENT_CODEC.comapFlatMap(
			BossRushSavedData::decode,
			BossRushSavedData::encode
	);

	public static final SavedDataType<BossRushSavedData> TYPE = new SavedDataType<>(
			DevelopersHell.id("boss_rush"),
			BossRushSavedData::new,
			CODEC,
			null
	);

	private final Map<UUID, BossRushProgress> players;

	private BossRushSavedData() {
		this(Map.of());
	}

	private BossRushSavedData(Map<UUID, BossRushProgress> players) {
		this.players = new LinkedHashMap<>(players);
	}

	private static DataResult<BossRushSavedData> decode(RawDocument document) {
		Map<UUID, BossRushProgress> players = new LinkedHashMap<>();
		for (BossRushProgress progress : document.players()) {
			if (players.putIfAbsent(progress.ownerUuid(), progress) != null) {
				return DataResult.error(() -> "Duplicate boss-rush owner UUID");
			}
		}
		return DataResult.success(new BossRushSavedData(players));
	}

	private static RawDocument encode(BossRushSavedData data) {
		return new RawDocument(SCHEMA_VERSION, List.copyOf(data.players.values()));
	}

	public static BossRushSavedData get(ServerLevel level) {
		ServerLevel overworld = Objects.requireNonNull(
				level.getServer().getLevel(Level.OVERWORLD),
				"Developer's Hell requires an Overworld"
		);
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	public synchronized BossRushProgress snapshot(UUID ownerUuid) {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		return players.getOrDefault(ownerUuid, BossRushProgress.initial(ownerUuid));
	}

	public synchronized BossRushProgress normalizeRestart(UUID ownerUuid) {
		BossRushProgress current = snapshot(ownerUuid);
		BossRushProgress normalized = current.normalizeRestart();
		if (!normalized.equals(current)) {
			players.put(ownerUuid, normalized);
			setDirty();
		}
		return normalized;
	}

	public synchronized BossRushProgress begin(UUID ownerUuid, BossRushStage liveStage) {
		BossRushProgress current = normalizeRestart(ownerUuid);
		BossRushProgress next = current.begin(liveStage);
		players.put(ownerUuid, next);
		setDirty();
		return next;
	}

	public synchronized BossRushProgress.Completion complete(
			UUID ownerUuid,
			BossRushStage completedStage
	) {
		BossRushProgress.Completion completion = snapshot(ownerUuid).complete(completedStage);
		players.put(ownerUuid, completion.progress());
		setDirty();
		return completion;
	}

	public synchronized boolean restoreIfCurrent(
			UUID ownerUuid,
			BossRushProgress expected,
			BossRushProgress replacement
	) {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(expected, "expected");
		Objects.requireNonNull(replacement, "replacement");
		if (!ownerUuid.equals(expected.ownerUuid())
				|| !ownerUuid.equals(replacement.ownerUuid())
				|| !snapshot(ownerUuid).equals(expected)) {
			return false;
		}
		players.put(ownerUuid, replacement);
		setDirty();
		return true;
	}

	static BossRushSavedData createForTesting(Map<UUID, BossRushProgress> players) {
		return new BossRushSavedData(players);
	}

	synchronized void replaceForGameTest(BossRushProgress progress) {
		players.put(progress.ownerUuid(), progress);
		setDirty();
	}

	private record RawDocument(int schema, List<BossRushProgress> players) {
		private RawDocument {
			players = List.copyOf(players);
		}
	}
}
