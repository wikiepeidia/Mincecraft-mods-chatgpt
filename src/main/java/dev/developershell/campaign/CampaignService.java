package dev.developershell.campaign;

import dev.developershell.lecture.ArenaRejection;
import dev.developershell.lecture.ArenaValidationResult;
import dev.developershell.lecture.ArenaValidator;
import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.item.InfiniteSlidesRemoteItem;
import dev.developershell.registry.ModItems;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Logical-server transaction boundary for accepted campaign events and their effects. */
public final class CampaignService {
	private static final String CONTRACT_SIGNED_KEY = "message.developers_hell.contract.signed";

	/**
	 * Compatibility seam for server-side lifecycle callers. It delegates to the sole arena
	 * validator and passes its immutable acceptance into the transaction overload below.
	 */
	public static boolean start(
			ServerPlayer player,
			BlockPos deskPos,
			Direction deskFacing,
			ItemStack contract
	) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		ArenaValidationResult validation = ArenaValidator.validate(level, player, deskPos, deskFacing);
		return validation instanceof ArenaValidationResult.Accepted accepted
				&& start(player, accepted, contract).accepted();
	}

	/**
	 * Commits one already-validated arena exactly once. Geometry is never probed again here: the
	 * accepted value is the transaction's immutable input, so validation and persistence cannot
	 * disagree about retry coordinates or interior headroom.
	 */
	public static ArenaValidationResult start(
			ServerPlayer player,
			ArenaValidationResult.Accepted arena,
			ItemStack contract
	) {
		java.util.Objects.requireNonNull(player, "player");
		java.util.Objects.requireNonNull(arena, "arena");
		java.util.Objects.requireNonNull(contract, "contract");
		if (!(player.level() instanceof ServerLevel level)
				|| !level.getServer().isSameThread()
				|| player.isSpectator()
				|| contract.isEmpty()
				|| contract.getItem() != ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT) {
			return rejected(ArenaRejection.SPAWN_CAPACITY);
		}

		CampaignSavedData data = CampaignSavedData.get(level);
		int nextAttempt;
		try {
			nextAttempt = data.player(player.getUUID())
					.map(state -> Math.addExact(state.attemptCount(), 1))
					.orElse(1);
		}
		catch (ArithmeticException exception) {
			return rejected(ArenaRejection.SPAWN_CAPACITY);
		}
		UUID ownerUuid = player.getUUID();
		var layout = arena.layout();
		var deskPos = layout.deskPos();
		var deskFacing = layout.forward();
		UUID encounterUuid = CampaignSavedData.deterministicUuid("encounter", ownerUuid, deskPos, nextAttempt);
		UUID professorUuid = CampaignSavedData.deterministicUuid("professor", ownerUuid, deskPos, nextAttempt);
		if (level.getEntityInAnyDimension(professorUuid) != null) {
			return rejected(ArenaRejection.SPAWN_CAPACITY);
		}
		CampaignEvent.Start start = new CampaignEvent.Start(
				ownerUuid,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				deskPos,
				deskFacing,
				arena.retryPos(),
				encounterUuid,
				professorUuid
		);
		boolean[] runtimeStarted = {false};
		CampaignTransition transition = apply(data, start, effect -> {
			if (effect instanceof CampaignTransition.EffectIntent.StartEncounter) {
				player.sendSystemMessage(Component.translatable(CONTRACT_SIGNED_KEY));
				runtimeStarted[0] = LectureEncounterManager.start(
						level,
						player,
						data.player(ownerUuid).orElseThrow()
				);
			}
		});
		if (!transition.accepted()) {
			return rejected(isActiveRejection(transition.reason())
					? ArenaRejection.ACTIVE_ENCOUNTER
					: ArenaRejection.SPAWN_CAPACITY);
		}
		if (!runtimeStarted[0]) {
			apply(
					data,
					new CampaignEvent.Terminal(ownerUuid, encounterUuid, CampaignEvent.TerminalReason.ABORT),
					effect -> {
						if (effect instanceof CampaignTransition.EffectIntent.CleanupEncounter cleanup) {
							LectureEncounterManager.cleanup(cleanup.encounterUuid());
						}
					}
			);
			return rejected(ArenaRejection.SPAWN_CAPACITY);
		}
		contract.shrink(1);
		return arena;
	}

	/**
	 * Deauthorized source-compatibility seam. Victory must flow from the live encounter manager
	 * through {@link #commitVictory}; calling this method never writes state or dispatches effects.
	 *
	 * @deprecated use {@link #commitVictory} only from the manager-owned final-window transition
	 */
	@Deprecated(forRemoval = true)
	public static boolean victory(ServerLevel level, UUID ownerUuid, UUID encounterUuid) {
		java.util.Objects.requireNonNull(level, "level");
		java.util.Objects.requireNonNull(ownerUuid, "ownerUuid");
		java.util.Objects.requireNonNull(encounterUuid, "encounterUuid");
		return false;
	}

	/**
	 * Commits one matching victory and returns its complete accepted/no-op result. The durable
	 * PASSED, Sheet-entitlement, and Remote-ledger state is dirty before the caller observes any
	 * cleanup, reward, or presentation intent.
	 */
	public static CampaignTransition commitVictory(
			ServerLevel level,
			UUID ownerUuid,
			UUID encounterUuid,
			Consumer<CampaignTransition.EffectIntent> effectConsumer
	) {
		java.util.Objects.requireNonNull(level, "level");
		java.util.Objects.requireNonNull(ownerUuid, "ownerUuid");
		java.util.Objects.requireNonNull(encounterUuid, "encounterUuid");
		java.util.Objects.requireNonNull(effectConsumer, "effectConsumer");
		if (!level.getServer().isSameThread()) {
			return CampaignTransition.noOp(Optional.empty(), "wrong_thread");
		}

		CampaignSavedData data = CampaignSavedData.get(level);
		Optional<ServerPlayer> participant = LectureEncounterManager.participant(encounterUuid);
		if (participant.isEmpty()) {
			return CampaignTransition.noOp(data.player(ownerUuid), "missing_participant");
		}
		if (!participant.get().getUUID().equals(ownerUuid) || participant.get().level() != level) {
			return CampaignTransition.noOp(data.player(ownerUuid), "wrong_participant");
		}
		return applyTerminal(
				data,
				new CampaignEvent.Victory(ownerUuid, encounterUuid),
				effectConsumer
		);
	}

	/**
	 * Sole stateful campaign seam: reduce once, replace the durable record, mark it dirty, and only
	 * then dispatch immutable effects. Rejected events never write and never dispatch.
	 */
	public static CampaignTransition apply(
			ServerLevel level,
			CampaignEvent event,
			Consumer<CampaignTransition.EffectIntent> effectConsumer
	) {
		java.util.Objects.requireNonNull(level, "level");
		java.util.Objects.requireNonNull(event, "event");
		java.util.Objects.requireNonNull(effectConsumer, "effectConsumer");
		if (!level.getServer().isSameThread()) {
			return CampaignTransition.noOp(Optional.empty(), "wrong_thread");
		}
		return apply(CampaignSavedData.get(level), event, effectConsumer);
	}

	/** Read-only state view for bounded server-side adapters such as Retake reconciliation. */
	public static Optional<PlayerCampaignState> snapshot(ServerLevel level, UUID ownerUuid) {
		java.util.Objects.requireNonNull(level, "level");
		java.util.Objects.requireNonNull(ownerUuid, "ownerUuid");
		if (!level.getServer().isSameThread()) {
			return Optional.empty();
		}
		return CampaignSavedData.get(level).player(ownerUuid);
	}

	/**
	 * Commits the exact server-time Remote deadline before exposing its bounded effect intent.
	 * The physical held stack and target scan remain item concerns; this method owns authority,
	 * persistence ordering, and the single permitted clock.
	 */
	public static CampaignTransition commitRemoteCooldown(
			ServerPlayer player,
			Consumer<CampaignTransition.EffectIntent> effectConsumer
	) {
		java.util.Objects.requireNonNull(player, "player");
		java.util.Objects.requireNonNull(effectConsumer, "effectConsumer");
		ServerLevel level = player.level();
		if (!level.getServer().isSameThread()) {
			return CampaignTransition.noOp(Optional.empty(), "wrong_thread");
		}

		CampaignSavedData data = CampaignSavedData.get(level);
		Optional<PlayerCampaignState> current = data.player(player.getUUID());
		if (player.isSpectator()) {
			return CampaignTransition.noOp(current, "spectator");
		}
		long observedGameTime = level.getGameTime();
		long deadlineGameTime;
		try {
			deadlineGameTime = InfiniteSlidesRemoteItem.Cooldown.deadline(observedGameTime);
		}
		catch (ArithmeticException exception) {
			return CampaignTransition.noOp(current, "remote_clock_overflow");
		}
		return apply(
				data,
				new CampaignEvent.StartRemoteCooldown(
						player.getUUID(),
						observedGameTime,
						deadlineGameTime
				),
				effectConsumer
		);
	}

	static CampaignTransition apply(
			CampaignSavedData data,
			CampaignEvent event,
			Consumer<CampaignTransition.EffectIntent> effectConsumer
	) {
		java.util.Objects.requireNonNull(data, "data");
		java.util.Objects.requireNonNull(event, "event");
		java.util.Objects.requireNonNull(effectConsumer, "effectConsumer");
		Optional<PlayerCampaignState> current = data.player(event.ownerUuid());
		if (!data.isWritableSchema()) {
			return CampaignTransition.noOp(current, "read_only_data");
		}
		if (event instanceof CampaignEvent.Start start
				&& data.hasActiveDeskForOther(start.ownerUuid(), start.deskDimension(), start.deskPos())) {
			return CampaignTransition.noOp(current, "desk_occupied");
		}

		CampaignTransition transition = CampaignReducer.reduce(current, event);
		if (!transition.accepted()) {
			return transition;
		}
		if (!data.replace(transition.nextState().orElseThrow())) {
			return CampaignTransition.noOp(current, "persistence_rejected");
		}
		data.setDirty();
		transition.intents().forEach(effectConsumer);
		return transition;
	}

	/** Package-private pure-data seam for exhaustive terminal ordering tests. */
	static CampaignTransition applyTerminal(
			CampaignSavedData data,
			CampaignEvent.EncounterTerminal event,
			Consumer<CampaignTransition.EffectIntent> effectConsumer
	) {
		return apply(data, event, effectConsumer);
	}

	/**
	 * Admits Professor damage only while entity, runtime participant, attacker, and durable
	 * encounter identity all describe the same active owner window.
	 */
	public static boolean canDamageProfessor(
			ServerLevel level,
			UUID ownerUuid,
			UUID encounterUuid,
			UUID attackerUuid
	) {
		if (!level.getServer().isSameThread()
				|| ownerUuid == null
				|| encounterUuid == null
				|| !ownerUuid.equals(attackerUuid)) {
			return false;
		}
		Optional<ServerPlayer> participant = LectureEncounterManager.participant(encounterUuid);
		if (participant.isEmpty()
				|| !participant.get().getUUID().equals(ownerUuid)
				|| participant.get().level() != level) {
			return false;
		}
		CampaignSavedData data = CampaignSavedData.get(level);
		return data.isWritableSchema()
				&& data.player(ownerUuid)
						.map(state -> state.matchesActiveEncounter(ownerUuid, encounterUuid))
						.orElse(false);
	}

	private static boolean isActiveRejection(String reason) {
		return reason.equals("start_not_ready")
				|| reason.equals("desk_occupied")
				|| reason.equals("wrong_owner");
	}

	private static ArenaValidationResult.Rejected rejected(ArenaRejection rejection) {
		return new ArenaValidationResult.Rejected(rejection);
	}

	private CampaignService() {
	}
}
