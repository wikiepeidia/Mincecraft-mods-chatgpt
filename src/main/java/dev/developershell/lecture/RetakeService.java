package dev.developershell.lecture;

import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignService;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.campaign.PlayerCampaignState;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Sole ordering seam between the durable Retake entitlement and its lossy inventory/entity
 * projections. Concrete item and entity adapters arrive with the interaction layer; this class
 * owns the state-first protocol they must follow.
 */
public final class RetakeService {
	private static final Consumer<CampaignTransition.EffectIntent> IGNORE_EFFECT = effect -> {
	};

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

	/** Ensures exactly one recoverable inventory Form or tracked fallback projection. */
	public Outcome reconcile(UUID ownerUuid) {
		return ensureRepresentation(Objects.requireNonNull(ownerUuid, "ownerUuid"));
	}

	/** Manual empty-hand recovery follows the exact same idempotent authority path. */
	public Outcome recover(UUID ownerUuid) {
		return ensureRepresentation(Objects.requireNonNull(ownerUuid, "ownerUuid"));
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
			Consumer<CampaignTransition.EffectIntent> effectConsumer
	) {
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		Objects.requireNonNull(arena, "arena");
		Objects.requireNonNull(encounterUuid, "encounterUuid");
		Objects.requireNonNull(professorUuid, "professorUuid");
		Objects.requireNonNull(effectConsumer, "effectConsumer");

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
		UUID fallbackUuid = state.retakeFallbackEntityUuid();
		boolean fallbackPresent = fallbackUuid != null
				&& representations.hasFallback(key, fallbackUuid);
		if (!inventoryPresent && !fallbackPresent) {
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
		CampaignTransition transition = campaign.apply(start, effectConsumer);
		if (!transition.accepted()) {
			return Outcome.RETRY_REJECTED;
		}
		if (inventoryPresent) {
			representations.consumeInventoryForm(key);
		}
		if (fallbackPresent) {
			representations.discardFallback(key, fallbackUuid);
		}
		return Outcome.RETRY_ACCEPTED;
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
		return committed.accepted() ? Outcome.FALLBACK_ISSUED : Outcome.STALE_STATE;
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
		RETRY_REJECTED
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
