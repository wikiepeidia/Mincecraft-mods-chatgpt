package dev.developershell.python;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.developershell.DevelopersHell;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Overworld-owned, compare-and-commit authority for Python-tool side effects. */
public final class PythonToolsSavedData extends SavedData {
	public static final int SCHEMA_VERSION = 1;

	private static final Codec<PlayerEntry> PLAYER_CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					UUIDUtil.CODEC.fieldOf("owner_uuid").forGetter(PlayerEntry::ownerUuid),
					PythonToolsState.CODEC.fieldOf("state").forGetter(PlayerEntry::state)
			).apply(instance, PlayerEntry::new)
	);
	private static final Codec<RawDocument> DOCUMENT_CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					Codec.intRange(SCHEMA_VERSION, SCHEMA_VERSION).fieldOf("schema").forGetter(RawDocument::schema),
					PLAYER_CODEC.listOf().fieldOf("players").forGetter(RawDocument::players)
			).apply(instance, RawDocument::new)
	);
	private static final Codec<PythonToolsSavedData> CODEC = DOCUMENT_CODEC.comapFlatMap(
			PythonToolsSavedData::decode,
			PythonToolsSavedData::encode
	);

	public static final SavedDataType<PythonToolsSavedData> TYPE = new SavedDataType<>(
			DevelopersHell.id("python_tools"),
			PythonToolsSavedData::new,
			CODEC,
			null
	);

	private final Map<UUID, PythonToolsState> players;

	private PythonToolsSavedData() {
		this(Map.of());
	}

	private PythonToolsSavedData(Map<UUID, PythonToolsState> players) {
		this.players = new LinkedHashMap<>(players);
	}

	public static PythonToolsSavedData get(ServerLevel level) {
		ServerLevel overworld = Objects.requireNonNull(
				level.getServer().getLevel(Level.OVERWORLD),
				"Developer's Hell requires an Overworld"
		);
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	public synchronized PythonToolsState snapshot(UUID ownerUuid) {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		return players.getOrDefault(ownerUuid, PythonToolsState.initial());
	}

	public synchronized boolean commitIfCurrent(
			UUID ownerUuid,
			PythonToolsState expected,
			PythonToolsState next
	) {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(expected, "expected");
		Objects.requireNonNull(next, "next");
		if (!snapshot(ownerUuid).equals(expected)) {
			return false;
		}
		if (expected.equals(next)) {
			return true;
		}
		players.put(ownerUuid, next);
		setDirty();
		return true;
	}

	static PythonToolsSavedData createForTesting(Map<UUID, PythonToolsState> players) {
		return new PythonToolsSavedData(players);
	}

	private static DataResult<PythonToolsSavedData> decode(RawDocument document) {
		Map<UUID, PythonToolsState> players = new LinkedHashMap<>();
		for (PlayerEntry entry : document.players()) {
			if (players.putIfAbsent(entry.ownerUuid(), entry.state()) != null) {
				return DataResult.error(() -> "Duplicate Python-tools owner UUID");
			}
		}
		return DataResult.success(new PythonToolsSavedData(players));
	}

	private static RawDocument encode(PythonToolsSavedData data) {
		List<PlayerEntry> players = data.players.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> new PlayerEntry(entry.getKey(), entry.getValue()))
				.toList();
		return new RawDocument(SCHEMA_VERSION, players);
	}

	private record PlayerEntry(UUID ownerUuid, PythonToolsState state) {
		private PlayerEntry {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(state, "state");
		}
	}

	private record RawDocument(int schema, List<PlayerEntry> players) {
		private RawDocument {
			players = List.copyOf(players);
		}
	}
}
