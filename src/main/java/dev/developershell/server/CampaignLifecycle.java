package dev.developershell.server;

import dev.developershell.DevelopersHell;
import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignSavedData;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.entity.ProfessorInfiniteSlidesEntity;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.RewardService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * One-time Fabric callback adapter for campaign lifecycle events.
 *
 * <p>This class translates typed runtime notifications into closed {@link CampaignEvent}
 * values. Durable mutation and accepted-effect ordering remain inside the runtime's campaign
 * adapter.</p>
 */
public final class CampaignLifecycle {
	private static final String SERVER_STOPPING_MARKER =
			"DEVELOPERS_HELL_SERVER_STOPPING_CLEANUP_COMPLETE";
	private static DevelopersHellRuntime.LifecycleAdapter adapter;

	/** Registers the production callbacks once after immutable runtime composition. */
	public static synchronized void register(DevelopersHellRuntime runtime) {
		Objects.requireNonNull(runtime, "runtime");
		DevelopersHellRuntime.LifecycleAdapter candidate = runtime.lifecycle();
		if (adapter != null) {
			if (adapter != candidate) {
				throw new IllegalStateException("Campaign lifecycle already registered for another runtime");
			}
			return;
		}
		adapter = candidate;

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onPlayerTerminal(player, CampaignEvent.TerminalReason.DEATH);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			onPlayerTerminal(newPlayer, CampaignEvent.TerminalReason.DEATH);
			RewardService.reconcilePending(newPlayer);
			RewardService.restoreRemoteCooldown(newPlayer);
		});
		ServerPlayerEvents.JOIN.register(CampaignLifecycle::onJoin);
		ServerPlayerEvents.LEAVE.register(player ->
				onPlayerTerminal(player, CampaignEvent.TerminalReason.DISCONNECT));
		ServerEntityEvents.ALLOW_LOAD.register(CampaignLifecycle::onAllowLoad);
		ServerEntityEvents.ENTITY_LOAD.register(CampaignLifecycle::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(CampaignLifecycle::onEntityUnload);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			onServerStopping(server);
			DevelopersHell.LOGGER.info(SERVER_STOPPING_MARKER);
		});
	}

	/**
	 * Runs the same bounded, state-first stop cleanup used by the production callback.
	 * This method never stops the supplied server, so GameTest can verify it in-process.
	 */
	public static int onServerStopping(MinecraftServer server) {
		Objects.requireNonNull(server, "server");
		int accepted = 0;
		for (LectureEncounterManager.RuntimeSnapshot runtime
				: LectureEncounterManager.activeRuntimeSnapshots(server)) {
			if (onRuntimeExit(runtime, CampaignEvent.TerminalReason.SERVER_STOP)) {
				accepted++;
			}
		}
		return accepted;
	}

	/** Testable explicit-abort seam used by the later command adapter. */
	public static boolean onAbort(ServerPlayer player) {
		return onPlayerTerminal(
				Objects.requireNonNull(player, "player"),
				CampaignEvent.TerminalReason.ABORT
		);
	}

	/** Receives one bounded manager exit without scanning entities or persisted player records. */
	public static boolean onRuntimeExit(
			LectureEncounterManager.RuntimeSnapshot runtime,
			CampaignEvent.TerminalReason reason
	) {
		Objects.requireNonNull(runtime, "runtime");
		Objects.requireNonNull(reason, "reason");
		DevelopersHellRuntime.LifecycleAdapter current = requireAdapter();
		if (!matchesActiveRuntime(runtime)) {
			current.cleanupStaleRuntime(runtime);
			return false;
		}
		return current.submit(
				runtime.level(),
				new CampaignEvent.Terminal(runtime.ownerUuid(), runtime.encounterUuid(), reason),
				null
		);
	}

	private static void onJoin(ServerPlayer player) {
		DevelopersHellRuntime.LifecycleAdapter current = requireAdapter();
		Optional<PlayerCampaignState.EncounterRef> active = activeEncounter(player.level(), player.getUUID());
		if (active.isPresent()) {
			PlayerCampaignState.EncounterRef encounter = active.get();
			current.submit(
					player.level(),
					new CampaignEvent.NormalizeReload(player.getUUID(), encounter.encounterUuid()),
					player
			);
		}
		else {
			current.deliverPendingReloadNotice(player);
		}
		RewardService.reconcilePending(player);
		RewardService.restoreRemoteCooldown(player);
	}

	private static boolean onPlayerTerminal(ServerPlayer player, CampaignEvent.TerminalReason reason) {
		Optional<PlayerCampaignState.EncounterRef> active = activeEncounter(player.level(), player.getUUID());
		if (active.isEmpty()) {
			return false;
		}
		return requireAdapter().submit(
				player.level(),
				new CampaignEvent.Terminal(player.getUUID(), active.get().encounterUuid(), reason),
				player
		);
	}

	private static boolean onAllowLoad(
			Entity entity,
			ServerLevel level,
			EntitySpawnReason spawnReason,
			boolean loadedFromDisk
	) {
		if (!(entity instanceof ProfessorInfiniteSlidesEntity professor)) {
			return true;
		}
		Optional<PlayerCampaignState.EncounterRef> matching = matchingProfessor(level, professor);
		if (matching.isEmpty()) {
			return false;
		}
		if (!loadedFromDisk) {
			return true;
		}

		PlayerCampaignState.EncounterRef encounter = matching.get();
		ServerPlayer feedback = level.getServer().getPlayerList().getPlayer(encounter.ownerUuid());
		requireAdapter().submit(
				level,
				new CampaignEvent.NormalizeReload(encounter.ownerUuid(), encounter.encounterUuid()),
				feedback
		);
		return false;
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (!(entity instanceof ProfessorInfiniteSlidesEntity professor)) {
			return;
		}
		Optional<PlayerCampaignState.EncounterRef> matching = matchingProfessor(level, professor);
		if (matching.isEmpty()) {
			professor.discard();
			return;
		}
		if (!professor.wasLoadedFromDisk()) {
			return;
		}

		PlayerCampaignState.EncounterRef encounter = matching.get();
		ServerPlayer feedback = level.getServer().getPlayerList().getPlayer(encounter.ownerUuid());
		requireAdapter().submit(
				level,
				new CampaignEvent.NormalizeReload(encounter.ownerUuid(), encounter.encounterUuid()),
				feedback
		);
		professor.discard();
	}

	private static void onEntityUnload(Entity entity, ServerLevel level) {
		if (!(entity instanceof ProfessorInfiniteSlidesEntity professor)) {
			return;
		}
		Optional<PlayerCampaignState.EncounterRef> matching = matchingProfessor(level, professor);
		if (matching.isEmpty()) {
			return;
		}
		PlayerCampaignState.EncounterRef encounter = matching.get();
		requireAdapter().submit(
				level,
				new CampaignEvent.Terminal(
						encounter.ownerUuid(),
						encounter.encounterUuid(),
						CampaignEvent.TerminalReason.ENTITY_UNLOAD
				),
				null
		);
	}

	private static Optional<PlayerCampaignState.EncounterRef> activeEncounter(ServerLevel level, UUID ownerUuid) {
		return CampaignSavedData.get(level).player(ownerUuid)
				.filter(state -> state.status() == PlayerCampaignState.LectureStatus.ACTIVE)
				.flatMap(PlayerCampaignState::activeEncounter);
	}

	private static Optional<PlayerCampaignState.EncounterRef> matchingProfessor(
			ServerLevel level,
			ProfessorInfiniteSlidesEntity professor
	) {
		UUID ownerUuid = professor.ownerUuid();
		UUID encounterUuid = professor.encounterUuid();
		if (ownerUuid == null || encounterUuid == null) {
			return Optional.empty();
		}
		return activeEncounter(level, ownerUuid).filter(encounter ->
				encounter.encounterUuid().equals(encounterUuid)
						&& encounter.professorUuid().equals(professor.getUUID())
		);
	}

	private static boolean matchesActiveRuntime(LectureEncounterManager.RuntimeSnapshot runtime) {
		return activeEncounter(runtime.level(), runtime.ownerUuid()).filter(encounter ->
				encounter.encounterUuid().equals(runtime.encounterUuid())
						&& encounter.professorUuid().equals(runtime.professorUuid())
						&& encounter.attemptNumber() == runtime.attemptNumber()
		).isPresent();
	}

	private static DevelopersHellRuntime.LifecycleAdapter requireAdapter() {
		DevelopersHellRuntime.LifecycleAdapter current = adapter;
		if (current == null) {
			throw new IllegalStateException("Campaign lifecycle has not been registered");
		}
		return current;
	}

	private CampaignLifecycle() {
	}
}
