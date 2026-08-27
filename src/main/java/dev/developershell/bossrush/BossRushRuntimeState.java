package dev.developershell.bossrush;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure bounded ownership ledger shared by production cleanup and unit tests. */
final class BossRushRuntimeState {
	static final int MAX_RUNTIME_TICKS = 20 * 60 * 12;

	private final UUID ownerUuid;
	private final UUID encounterUuid;
	private final Set<UUID> ownedEntityUuids = new LinkedHashSet<>();
	private int elapsedTicks;
	private boolean closed;

	BossRushRuntimeState(UUID ownerUuid, UUID encounterUuid) {
		this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
		this.encounterUuid = Objects.requireNonNull(encounterUuid, "encounterUuid");
	}

	UUID ownerUuid() {
		return ownerUuid;
	}

	UUID encounterUuid() {
		return encounterUuid;
	}

	boolean isOwner(UUID candidate) {
		return ownerUuid.equals(candidate);
	}

	void track(UUID entityUuid) {
		if (closed) {
			throw new IllegalStateException("Cannot track an entity after cleanup");
		}
		ownedEntityUuids.add(Objects.requireNonNull(entityUuid, "entityUuid"));
	}

	Set<UUID> ownedEntityUuids() {
		return Set.copyOf(ownedEntityUuids);
	}

	boolean advance(int ticks) {
		if (ticks < 0) {
			throw new IllegalArgumentException("ticks must be non-negative");
		}
		if (closed) {
			return true;
		}
		elapsedTicks = Math.min(MAX_RUNTIME_TICKS, Math.addExact(elapsedTicks, ticks));
		return elapsedTicks >= MAX_RUNTIME_TICKS;
	}

	Set<UUID> close() {
		if (closed) {
			return Set.of();
		}
		closed = true;
		Set<UUID> cleanup = Set.copyOf(ownedEntityUuids);
		ownedEntityUuids.clear();
		return cleanup;
	}

	boolean closed() {
		return closed;
	}
}
