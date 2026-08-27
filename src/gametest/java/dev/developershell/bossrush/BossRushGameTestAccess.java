package dev.developershell.bossrush;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** GameTest-only bridge for package-private runtime inspection and durable fixtures. */
public final class BossRushGameTestAccess {
	public static Optional<RuntimeView> snapshot(BossRushManager manager, UUID ownerUuid) {
		return manager.snapshotForGameTest(ownerUuid).map(snapshot -> {
			Map<String, Set<UUID>> entitiesByRole = new LinkedHashMap<>();
			snapshot.roles().forEach((entityUuid, role) -> entitiesByRole
					.computeIfAbsent(role.name(), ignored -> new LinkedHashSet<>())
					.add(entityUuid));
			entitiesByRole.replaceAll((ignored, uuids) -> Set.copyOf(uuids));
			return new RuntimeView(
					snapshot.stage(),
					snapshot.replay(),
					Set.copyOf(snapshot.ownedEntities()),
					Collections.unmodifiableMap(entitiesByRole),
					Set.copyOf(snapshot.bossBarPlayers())
			);
		});
	}

	public static void tick(BossRushManager manager, MinecraftServer server) {
		manager.tickForGameTest(server);
	}

	public static BossRushProgress progress(ServerLevel level, UUID ownerUuid) {
		return BossRushSavedData.get(level).snapshot(ownerUuid);
	}

	public static void replaceProgress(ServerLevel level, BossRushProgress progress) {
		BossRushSavedData.get(level).replaceForGameTest(progress);
	}

	public record RuntimeView(
			BossRushStage stage,
			boolean replay,
			Set<UUID> ownedEntities,
			Map<String, Set<UUID>> entitiesByRole,
			Set<UUID> bossBarPlayers
	) {
		public Set<UUID> entities(String role) {
			return entitiesByRole.getOrDefault(role, Set.of());
		}
	}

	private BossRushGameTestAccess() {
	}
}
