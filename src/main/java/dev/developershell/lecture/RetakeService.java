package dev.developershell.lecture;

import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.campaign.PlayerCampaignState;
import dev.developershell.registry.ModItems;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Sole ordering seam between the durable Retake entitlement and its lossy inventory/entity
 * projections. Concrete item and entity adapters arrive with the interaction layer; this class
 * owns the state-first protocol they must follow.
 */
public final class RetakeService {
	private static final String FORM_OWNER_TAG = "developers_hell_retake_owner";
	private static final String FORM_ENCOUNTER_TAG = "developers_hell_retake_encounter";
	private static final Consumer<CampaignTransition.EffectIntent> IGNORE_EFFECT = effect -> {
	};
	private static boolean fallbackLifecycleRegistered;

	private final CampaignPort campaign;
	private final RepresentationPort representations;
	private final Supplier<UUID> fallbackIds;

	public RetakeService(
			CampaignPort campaign,
			RepresentationPort representations,
			Supplier<UUID> fallbackIds
	) {
		this.campaign = Objects.requireNonNull(campaign, "campaign");
		this.representations = Objects.requireNonNull(representations, "representations");
		this.fallbackIds = Objects.requireNonNull(fallbackIds, "fallbackIds");
	}

	/** Production adapter; all writes still pass through {@link CampaignService#apply}. */
	public static RetakeService forLevel(
			ServerLevel level,
			RepresentationPort representations,
			Supplier<UUID> fallbackIds
	) {
		Objects.requireNonNull(level, "level");
		return new RetakeService(new CampaignPort() {
			@Override
			public Optional<PlayerCampaignState> state(UUID ownerUuid) {
				return CampaignService.snapshot(level, ownerUuid);
			}

			@Override
			public CampaignTransition apply(
					CampaignEvent event,
					Consumer<CampaignTransition.EffectIntent> effectConsumer
			) {
				return CampaignService.apply(level, event, effectConsumer);
			}
		}, representations, fallbackIds);
	}

	/** Production composition over the real player inventory and owner-targeted ItemEntity. */
	public static RetakeService forLevel(ServerLevel level) {
		Objects.requireNonNull(level, "level");
		return forLevel(level, new MinecraftRepresentationPort(level), UUID::randomUUID);
	}

	/** Explicit lifecycle effect adapter used only after a persisted ReconcileRetake intent. */
	public static Outcome reconcile(ServerLevel level, UUID ownerUuid) {
		return forLevel(Objects.requireNonNull(level, "level"))
				.reconcile(Objects.requireNonNull(ownerUuid, "ownerUuid"));
	}

	/**
	 * Installs one fence for tracked fallback unloads and stale bound entity reloads. Durable
	 * authority is cleared before a lost entity can be replaced, and an old chunk copy cannot
	 * later become a second representation.
	 */
	public static synchronized void registerFallbackLifecycle() {
		if (fallbackLifecycleRegistered) {
			return;
		}
		ServerEntityEvents.ALLOW_LOAD.register(RetakeService::allowFallbackLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(RetakeService::onFallbackUnload);
		fallbackLifecycleRegistered = true;
	}

	/** Reads the fail-closed owner/failed-encounter binding from one production Form stack. */
	public static Optional<PlayerCampaignState.RetakeKey> formKey(ItemStack stack) {
		Objects.requireNonNull(stack, "stack");
		if (stack.isEmpty() || stack.getItem() != ModItems.RETAKE_FORM) {
			return Optional.empty();
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return Optional.empty();
		}
		CompoundTag tag = customData.copyTag();
		Optional<UUID> ownerUuid = tag.read(FORM_OWNER_TAG, UUIDUtil.CODEC);
		Optional<UUID> encounterUuid = tag.read(FORM_ENCOUNTER_TAG, UUIDUtil.CODEC);
		if (ownerUuid.isEmpty() || encounterUuid.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new PlayerCampaignState.RetakeKey(ownerUuid.get(), encounterUuid.get()));
	}

	/** Creates one non-stackable Form bound to the exact durable Retake key. */
	public static ItemStack boundForm(PlayerCampaignState.RetakeKey key) {
		Objects.requireNonNull(key, "key");
		ItemStack stack = new ItemStack(ModItems.RETAKE_FORM);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.store(FORM_OWNER_TAG, UUIDUtil.CODEC, key.ownerUuid());
			tag.store(FORM_ENCOUNTER_TAG, UUIDUtil.CODEC, key.failedEncounterUuid());
		});
		return stack;
	}

	private static boolean allowFallbackLoad(
			Entity entity,
			ServerLevel level,
			net.minecraft.world.entity.EntitySpawnReason spawnReason,
			boolean loadedFromDisk
	) {
		if (!(entity instanceof ItemEntity item)) {
			return true;
		}
		Optional<PlayerCampaignState.RetakeKey> key = formKey(item.getItem());
		return key.isEmpty() || isTrackedFallback(level, key.get(), item.getUUID());
	}

	private static void onFallbackUnload(Entity entity, ServerLevel level) {
		if (!(entity instanceof ItemEntity item)) {
			return;
		}
		Optional<PlayerCampaignState.RetakeKey> keyView = formKey(item.getItem());
		if (keyView.isEmpty()) {
			return;
		}
		PlayerCampaignState.RetakeKey key = keyView.get();
		CampaignService.snapshot(level, key.ownerUuid()).ifPresent(state -> {
			if (state.status() != PlayerCampaignState.LectureStatus.RETAKE_READY
					|| state.retakeKey().filter(key::equals).isEmpty()) {
				return;
			}
			UUID entityUuid = item.getUUID();
			CampaignEvent.FallbackOperation operation;
			if (entityUuid.equals(state.retakeFallbackReservationUuid())) {
				operation = CampaignEvent.FallbackOperation.MATERIALIZATION_FAILED;
			}
			else if (entityUuid.equals(state.retakeFallbackEntityUuid())) {
				operation = CampaignEvent.FallbackOperation.LOST;
			}
			else {
				return;
			}
			CampaignService.apply(
					level,
					new CampaignEvent.RetakeFallback(key.ownerUuid(), key, entityUuid, operation),
					IGNORE_EFFECT
			);
		});
	}

	private static boolean isTrackedFallback(
			ServerLevel level,
			PlayerCampaignState.RetakeKey key,
			UUID fallbackUuid
	) {
		return CampaignService.snapshot(level, key.ownerUuid()).filter(state ->
				state.status() == PlayerCampaignState.LectureStatus.RETAKE_READY
						&& state.retakeKey().filter(key::equals).isPresent()
						&& (fallbackUuid.equals(state.retakeFallbackReservationUuid())
						|| fallbackUuid.equals(state.retakeFallbackEntityUuid()))
		).isPresent();
	}

	/** Ensures exactly one recoverable inventory Form or tracked fallback projection. */
	public Outcome reconcile(UUID ownerUuid) {
		return ensureRepresentation(Objects.requireNonNull(ownerUuid, "ownerUuid"));
	}

	/** Manual empty-hand recovery follows the exact same idempotent authority path. */
	public Outcome recover(UUID ownerUuid) {
		return ensureRepresentation(Objects.requireNonNull(ownerUuid, "ownerUuid"));
	}

	/**
	 * Production retry boundary. The clicked Form, persisted entitlement, accepted arena, and
	 * owning player must all describe the same attempt before deterministic runtime identities are
	 * allocated. The lower transaction persists START before this method starts the runtime and
	 * consumes the Form.
	 */
	public Outcome startRetake(
			ServerPlayer player,
			ArenaValidationResult.Accepted arena,
			ItemStack presentedForm
	) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(arena, "arena");
		Objects.requireNonNull(presentedForm, "presentedForm");
		ServerLevel level = player.level();
		if (!level.getServer().isSameThread()
				|| player.isSpectator()
				|| (player.getMainHandItem() != presentedForm
				&& player.getOffhandItem() != presentedForm)) {
			return Outcome.RETRY_REJECTED;
		}

		UUID ownerUuid = player.getUUID();
		Optional<PlayerCampaignState.RetakeKey> presentedKey = formKey(presentedForm);
		Optional<PlayerCampaignState> current = campaign.state(ownerUuid);
		if (presentedKey.isEmpty() || current.isEmpty()) {
			return Outcome.RETRY_REJECTED;
		}
		PlayerCampaignState state = current.get();
		var layout = arena.layout();
		boolean matchingAuthority = state.status() == PlayerCampaignState.LectureStatus.RETAKE_READY
				&& state.retakeKey().filter(presentedKey.get()::equals).isPresent()
				&& presentedKey.get().ownerUuid().equals(ownerUuid)
				&& state.deskDimension().equals(PlayerCampaignState.OVERWORLD_DIMENSION)
				&& level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
				&& state.deskPos().equals(layout.deskPos())
				&& state.deskFacing() == layout.forward()
				&& state.retryPos().equals(arena.retryPos());
		if (!matchingAuthority) {
			return Outcome.RETRY_REJECTED;
		}

		int nextAttempt;
		try {
			nextAttempt = Math.addExact(state.attemptCount(), 1);
		}
		catch (ArithmeticException exception) {
			return Outcome.RETRY_REJECTED;
		}
		UUID encounterUuid = deterministicUuid("encounter", ownerUuid, layout.deskPos(), nextAttempt);
		UUID professorUuid = deterministicUuid("professor", ownerUuid, layout.deskPos(), nextAttempt);
		if (level.getEntityInAnyDimension(professorUuid) != null
				|| LectureEncounterManager.runtimeSnapshot(encounterUuid).isPresent()) {
			return Outcome.RETRY_REJECTED;
		}

		return startRetake(
				ownerUuid,
				arena,
				encounterUuid,
				professorUuid,
				intent -> campaign.state(ownerUuid)
						.filter(persisted -> persisted.matchesActiveEncounter(ownerUuid, intent.encounter().encounterUuid()))
						.map(persisted -> LectureEncounterManager.start(level, player, persisted))
						.orElse(false)
		);
	}

	/**
	 * Starts one retry from an already-accepted arena. The keyed START is persisted before its
	 * runtime intent is dispatched; only an accepted transition can consume or discard the old
	 * physical representation.
	 */
	public Outcome startRetake(
			UUID ownerUuid,
			ArenaValidationResult.Accepted arena,
			UUID encounterUuid,
			UUID professorUuid,
			StartRuntimePort runtime
	) {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(arena, "arena");
		Objects.requireNonNull(encounterUuid, "encounterUuid");
		Objects.requireNonNull(professorUuid, "professorUuid");
		Objects.requireNonNull(runtime, "runtime");

		Optional<PlayerCampaignState> current = campaign.state(ownerUuid);
		if (current.isEmpty()) {
			return Outcome.RETRY_REJECTED;
		}
		PlayerCampaignState state = current.get();
		Optional<PlayerCampaignState.RetakeKey> keyView = state.retakeKey();
		if (state.status() != PlayerCampaignState.LectureStatus.RETAKE_READY || keyView.isEmpty()) {
			return Outcome.RETRY_REJECTED;
		}
		PlayerCampaignState.RetakeKey key = keyView.get();
		boolean inventoryPresent = representations.hasInventoryForm(key);
		UUID reservationUuid = state.retakeFallbackReservationUuid();
		boolean reservationPresent = reservationUuid != null
				&& representations.hasFallback(key, reservationUuid);
		UUID fallbackUuid = state.retakeFallbackEntityUuid();
		boolean fallbackPresent = fallbackUuid != null
				&& representations.hasFallback(key, fallbackUuid);
		if (!inventoryPresent && !reservationPresent && !fallbackPresent) {
			return Outcome.REPRESENTATION_MISSING;
		}

		var layout = arena.layout();
		CampaignEvent.Start start = new CampaignEvent.Start(
				ownerUuid,
				state.deskDimension(),
				layout.deskPos(),
				layout.forward(),
				arena.retryPos(),
				encounterUuid,
				professorUuid,
				key
		);
		boolean[] runtimeIntentSeen = {false};
		boolean[] runtimeStarted = {false};
		CampaignTransition transition = campaign.apply(start, effect -> {
			if (effect instanceof CampaignTransition.EffectIntent.StartEncounter startIntent
					&& startIntent.encounter().ownerUuid().equals(ownerUuid)
					&& startIntent.encounter().encounterUuid().equals(encounterUuid)) {
				runtimeIntentSeen[0] = true;
				runtimeStarted[0] = runtime.start(startIntent);
			}
		});
		if (!transition.accepted()) {
			return Outcome.RETRY_REJECTED;
		}
		if (!runtimeIntentSeen[0] || !runtimeStarted[0]) {
			CampaignTransition compensated = campaign.apply(
					new CampaignEvent.Terminal(
							ownerUuid,
							encounterUuid,
							CampaignEvent.TerminalReason.ABORT
					),
					IGNORE_EFFECT
			);
			if (!compensated.accepted()) {
				return Outcome.STALE_STATE;
			}
			clearPhysicalRepresentations(
					key,
					inventoryPresent,
					reservationPresent,
					reservationUuid,
					fallbackPresent,
					fallbackUuid
			);
			ensureRepresentation(ownerUuid);
			return Outcome.RUNTIME_START_FAILED;
		}
		clearPhysicalRepresentations(
				key,
				inventoryPresent,
				reservationPresent,
				reservationUuid,
				fallbackPresent,
				fallbackUuid
		);
		return Outcome.RETRY_ACCEPTED;
	}

	private void clearPhysicalRepresentations(
			PlayerCampaignState.RetakeKey key,
			boolean inventoryPresent,
			boolean reservationPresent,
			UUID reservationUuid,
			boolean fallbackPresent,
			UUID fallbackUuid
	) {
		if (inventoryPresent) {
			representations.consumeInventoryForm(key);
		}
		if (reservationPresent) {
			representations.discardFallback(key, reservationUuid);
		}
		if (fallbackPresent && !Objects.equals(fallbackUuid, reservationUuid)) {
			representations.discardFallback(key, fallbackUuid);
		}
	}

	private static UUID deterministicUuid(
			String kind,
			UUID ownerUuid,
			BlockPos deskPos,
			int attemptCount
	) {
		String value = kind + ":" + ownerUuid + ":" + deskPos.getX() + ":" + deskPos.getY()
				+ ":" + deskPos.getZ() + ":" + attemptCount;
		return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
	}

	private Outcome ensureRepresentation(UUID ownerUuid) {
		Optional<PlayerCampaignState> current = campaign.state(ownerUuid);
		if (current.isEmpty()) {
			return Outcome.NOT_ENTITLED;
		}
		PlayerCampaignState state = current.get();
		Optional<PlayerCampaignState.RetakeKey> keyView = state.retakeKey();
		if (state.status() != PlayerCampaignState.LectureStatus.RETAKE_READY || keyView.isEmpty()) {
			return Outcome.NOT_ENTITLED;
		}
		PlayerCampaignState.RetakeKey key = keyView.get();

		if (representations.hasInventoryForm(key)) {
			Outcome duplicateResult = clearDuplicateFallback(state, key);
			return duplicateResult == null ? Outcome.ALREADY_PRESENT : duplicateResult;
		}

		UUID reservationUuid = state.retakeFallbackReservationUuid();
		if (reservationUuid != null) {
			if (representations.hasFallback(key, reservationUuid)) {
				CampaignTransition committed = campaign.apply(
						new CampaignEvent.RetakeFallback(
								ownerUuid,
								key,
								reservationUuid,
								CampaignEvent.FallbackOperation.MATERIALIZED
						),
						IGNORE_EFFECT
				);
				return committed.accepted() ? Outcome.ALREADY_PRESENT : Outcome.STALE_STATE;
			}
			CampaignTransition cleared = clearFallback(
					key,
					reservationUuid,
					CampaignEvent.FallbackOperation.MATERIALIZATION_FAILED
			);
			if (!cleared.accepted()) {
				return Outcome.STALE_STATE;
			}
		}

		UUID fallbackUuid = state.retakeFallbackEntityUuid();
		if (fallbackUuid != null) {
			if (representations.hasFallback(key, fallbackUuid)) {
				return Outcome.ALREADY_PRESENT;
			}
			CampaignTransition cleared = clearFallback(
					key,
					fallbackUuid,
					CampaignEvent.FallbackOperation.LOST
			);
			if (!cleared.accepted()) {
				return Outcome.STALE_STATE;
			}
		}

		if (!campaign.state(ownerUuid).flatMap(PlayerCampaignState::retakeKey).filter(key::equals).isPresent()) {
			return Outcome.STALE_STATE;
		}
		if (representations.tryInsertInventoryForm(key)) {
			return Outcome.INVENTORY_ISSUED;
		}
		return materializeFallback(state, key);
	}

	/** Returns null when the inventory Form was already the only representation. */
	private Outcome clearDuplicateFallback(
			PlayerCampaignState state,
			PlayerCampaignState.RetakeKey key
	) {
		UUID reservationUuid = state.retakeFallbackReservationUuid();
		UUID fallbackUuid = state.retakeFallbackEntityUuid();
		UUID duplicateUuid = reservationUuid != null ? reservationUuid : fallbackUuid;
		if (duplicateUuid == null) {
			return null;
		}
		boolean physicalDuplicate = representations.hasFallback(key, duplicateUuid);
		CampaignEvent.FallbackOperation operation = reservationUuid != null
				? CampaignEvent.FallbackOperation.MATERIALIZATION_FAILED
				: CampaignEvent.FallbackOperation.CLEARED;
		CampaignTransition cleared = clearFallback(key, duplicateUuid, operation);
		if (!cleared.accepted()) {
			return Outcome.STALE_STATE;
		}
		if (physicalDuplicate) {
			representations.discardFallback(key, duplicateUuid);
		}
		return Outcome.ALREADY_PRESENT;
	}

	private CampaignTransition clearFallback(
			PlayerCampaignState.RetakeKey key,
			UUID fallbackUuid,
			CampaignEvent.FallbackOperation operation
	) {
		return campaign.apply(
				new CampaignEvent.RetakeFallback(key.ownerUuid(), key, fallbackUuid, operation),
				IGNORE_EFFECT
		);
	}

	private Outcome materializeFallback(
			PlayerCampaignState state,
			PlayerCampaignState.RetakeKey key
	) {
		UUID fallbackUuid = Objects.requireNonNull(fallbackIds.get(), "fallbackIds returned null");
		boolean[] materializationIntent = {false};
		boolean[] materialized = {false};
		CampaignTransition reserved = campaign.apply(
				new CampaignEvent.ReconcileRetake(key.ownerUuid(), key, fallbackUuid),
				effect -> {
					if (effect instanceof CampaignTransition.EffectIntent.MaterializeRetakeFallback intent
							&& intent.ownerUuid().equals(key.ownerUuid())
							&& intent.fallbackEntityUuid().equals(fallbackUuid)) {
						materializationIntent[0] = true;
						materialized[0] = representations.materializeFallback(
								key,
								fallbackUuid,
								state.retryPos()
						);
					}
				}
		);
		if (!reserved.accepted()) {
			return Outcome.STALE_STATE;
		}
		if (!materializationIntent[0] || !materialized[0]) {
			CampaignTransition cleared = clearFallback(
					key,
					fallbackUuid,
					CampaignEvent.FallbackOperation.MATERIALIZATION_FAILED
			);
			return cleared.accepted() ? Outcome.MATERIALIZATION_FAILED : Outcome.STALE_STATE;
		}

		CampaignTransition committed = campaign.apply(
				new CampaignEvent.RetakeFallback(
						key.ownerUuid(),
						key,
						fallbackUuid,
						CampaignEvent.FallbackOperation.MATERIALIZED
				),
				IGNORE_EFFECT
		);
		if (committed.accepted()) {
			return Outcome.FALLBACK_ISSUED;
		}
		boolean stillTracked = campaign.state(key.ownerUuid())
				.map(current -> fallbackUuid.equals(current.retakeFallbackReservationUuid())
						|| fallbackUuid.equals(current.retakeFallbackEntityUuid()))
				.orElse(false);
		if (!stillTracked) {
			representations.discardFallback(key, fallbackUuid);
		}
		return Outcome.STALE_STATE;
	}

	public enum Outcome {
		NOT_ENTITLED,
		ALREADY_PRESENT,
		INVENTORY_ISSUED,
		FALLBACK_ISSUED,
		MATERIALIZATION_FAILED,
		STALE_STATE,
		REPRESENTATION_MISSING,
		RETRY_ACCEPTED,
		RETRY_REJECTED,
		RUNTIME_START_FAILED
	}

	/** Concrete lossy projection adapter; all durable decisions remain in RetakeService. */
	private static final class MinecraftRepresentationPort implements RepresentationPort {
		private final ServerLevel level;

		private MinecraftRepresentationPort(ServerLevel level) {
			this.level = Objects.requireNonNull(level, "level");
		}

		@Override
		public boolean hasInventoryForm(PlayerCampaignState.RetakeKey key) {
			ServerPlayer owner = owner(key);
			if (owner == null) {
				return false;
			}
			for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
				if (formKey(owner.getInventory().getItem(slot)).filter(key::equals).isPresent()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean hasFallback(PlayerCampaignState.RetakeKey key, UUID fallbackEntityUuid) {
			Entity entity = level.getEntityInAnyDimension(fallbackEntityUuid);
			return entity instanceof ItemEntity item
					&& !item.isRemoved()
					&& formKey(item.getItem()).filter(key::equals).isPresent();
		}

		@Override
		public boolean tryInsertInventoryForm(PlayerCampaignState.RetakeKey key) {
			ServerPlayer owner = owner(key);
			return owner != null && owner.getInventory().add(boundForm(key));
		}

		@Override
		public boolean materializeFallback(
				PlayerCampaignState.RetakeKey key,
				UUID fallbackEntityUuid,
				BlockPos retryPos
		) {
			if (!isTrackedFallback(level, key, fallbackEntityUuid)
					|| level.getEntityInAnyDimension(fallbackEntityUuid) != null
					|| !level.isLoaded(retryPos)
					|| !level.getWorldBorder().isWithinBounds(retryPos)) {
				return false;
			}
			ItemEntity fallback = new ItemEntity(
					level,
					retryPos.getX() + 0.5D,
					retryPos.getY() + 0.25D,
					retryPos.getZ() + 0.5D,
					boundForm(key)
			);
			fallback.setUUID(fallbackEntityUuid);
			fallback.setTarget(key.ownerUuid());
			fallback.setDefaultPickUpDelay();
			return level.addFreshEntity(fallback);
		}

		@Override
		public void consumeInventoryForm(PlayerCampaignState.RetakeKey key) {
			ServerPlayer owner = owner(key);
			if (owner == null) {
				return;
			}
			for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
				ItemStack stack = owner.getInventory().getItem(slot);
				if (formKey(stack).filter(key::equals).isPresent()) {
					stack.shrink(1);
					owner.getInventory().setChanged();
					return;
				}
			}
		}

		@Override
		public void discardFallback(PlayerCampaignState.RetakeKey key, UUID fallbackEntityUuid) {
			Entity entity = level.getEntityInAnyDimension(fallbackEntityUuid);
			if (entity instanceof ItemEntity item
					&& formKey(item.getItem()).filter(key::equals).isPresent()) {
				item.discard();
			}
		}

		private ServerPlayer owner(PlayerCampaignState.RetakeKey key) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(key.ownerUuid());
			return owner != null && owner.level() == level ? owner : null;
		}
	}

	/** Runtime materialization result for the already-persisted immutable START intent. */
	@FunctionalInterface
	public interface StartRuntimePort {
		boolean start(CampaignTransition.EffectIntent.StartEncounter intent);
	}

	/** Durable campaign access, injected in tests and bound to CampaignService in production. */
	public interface CampaignPort {
		Optional<PlayerCampaignState> state(UUID ownerUuid);

		CampaignTransition apply(
				CampaignEvent event,
				Consumer<CampaignTransition.EffectIntent> effectConsumer
		);
	}

	/** Bounded lossy projection operations supplied by the later item/entity interaction plan. */
	public interface RepresentationPort {
		boolean hasInventoryForm(PlayerCampaignState.RetakeKey key);

		boolean hasFallback(PlayerCampaignState.RetakeKey key, UUID fallbackEntityUuid);

		boolean tryInsertInventoryForm(PlayerCampaignState.RetakeKey key);

		boolean materializeFallback(
				PlayerCampaignState.RetakeKey key,
				UUID fallbackEntityUuid,
				BlockPos retryPos
		);

		void consumeInventoryForm(PlayerCampaignState.RetakeKey key);

		void discardFallback(PlayerCampaignState.RetakeKey key, UUID fallbackEntityUuid);
	}
}
