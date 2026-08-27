package dev.developershell.campaign;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One immutable accepted or no-op reducer result. */
public record CampaignTransition(
		boolean accepted,
		Optional<PlayerCampaignState> nextState,
		List<EffectIntent> intents,
		String reason
) {
	public CampaignTransition {
		nextState = Objects.requireNonNull(nextState, "nextState");
		intents = List.copyOf(Objects.requireNonNull(intents, "intents"));
		if (Objects.requireNonNull(reason, "reason").isBlank()) {
			throw new IllegalArgumentException("reason must not be blank");
		}
		if (accepted && nextState.isEmpty()) {
			throw new IllegalArgumentException("Accepted transitions require a next state");
		}
		if (!accepted && !intents.isEmpty()) {
			throw new IllegalArgumentException("No-op transitions cannot carry effects");
		}
	}

	public static CampaignTransition accepted(
			PlayerCampaignState nextState,
			String reason,
			List<? extends EffectIntent> intents
	) {
		return new CampaignTransition(true, Optional.of(nextState), List.copyOf(intents), reason);
	}

	public static CampaignTransition accepted(
			PlayerCampaignState nextState,
			String reason,
			EffectIntent... intents
	) {
		return accepted(nextState, reason, List.of(intents));
	}

	public static CampaignTransition noOp(Optional<PlayerCampaignState> currentState, String reason) {
		return new CampaignTransition(false, currentState, List.of(), reason);
	}

	/** Immutable, bounded effects that adapters may interpret only after persistence. */
	public sealed interface EffectIntent permits
			EffectIntent.StartEncounter,
			EffectIntent.CleanupEncounter,
			EffectIntent.ReconcileRetake,
			EffectIntent.GrantFirstRewards,
			EffectIntent.MaterializeRetakeFallback,
			EffectIntent.MaterializeRewardFallback,
			EffectIntent.RecoverAttendanceSheet,
			EffectIntent.ApplyRemoteCooldown,
			EffectIntent.NotifyRemoteReady {
		record StartEncounter(PlayerCampaignState.EncounterRef encounter) implements EffectIntent {
			public StartEncounter {
				Objects.requireNonNull(encounter, "encounter");
			}
		}

		record CleanupEncounter(UUID ownerUuid, UUID encounterUuid, String reason) implements EffectIntent {
			public CleanupEncounter {
				Objects.requireNonNull(ownerUuid, "ownerUuid");
				Objects.requireNonNull(encounterUuid, "encounterUuid");
				if (Objects.requireNonNull(reason, "reason").isBlank()) {
					throw new IllegalArgumentException("reason must not be blank");
				}
			}
		}

		record ReconcileRetake(UUID ownerUuid) implements EffectIntent {
			public ReconcileRetake {
				Objects.requireNonNull(ownerUuid, "ownerUuid");
			}
		}

		record GrantFirstRewards(UUID ownerUuid) implements EffectIntent {
			public GrantFirstRewards {
				Objects.requireNonNull(ownerUuid, "ownerUuid");
			}
		}

		record MaterializeRetakeFallback(UUID ownerUuid, UUID fallbackEntityUuid) implements EffectIntent {
			public MaterializeRetakeFallback {
				Objects.requireNonNull(ownerUuid, "ownerUuid");
				Objects.requireNonNull(fallbackEntityUuid, "fallbackEntityUuid");
			}
		}

		record MaterializeRewardFallback(
				CampaignEvent.RewardProjectionKey key,
				PlayerCampaignState.RewardFallbackRef fallback
		) implements EffectIntent {
			public MaterializeRewardFallback {
				Objects.requireNonNull(key, "key");
				Objects.requireNonNull(fallback, "fallback");
				if (fallback.materialized()) {
					throw new IllegalArgumentException("materialization intent requires a reservation");
				}
			}
		}

		record RecoverAttendanceSheet(UUID ownerUuid, long recoverySequence) implements EffectIntent {
			public RecoverAttendanceSheet {
				Objects.requireNonNull(ownerUuid, "ownerUuid");
				if (recoverySequence < 1L) {
					throw new IllegalArgumentException("recoverySequence must be positive");
				}
			}
		}

		record ApplyRemoteCooldown(UUID ownerUuid, long deadlineGameTime) implements EffectIntent {
			public ApplyRemoteCooldown {
				Objects.requireNonNull(ownerUuid, "ownerUuid");
				if (deadlineGameTime < 1L) {
					throw new IllegalArgumentException("deadlineGameTime must be positive");
				}
			}
		}

		record NotifyRemoteReady(UUID ownerUuid, long deadlineGameTime) implements EffectIntent {
			public NotifyRemoteReady {
				Objects.requireNonNull(ownerUuid, "ownerUuid");
				if (deadlineGameTime < 1L) {
					throw new IllegalArgumentException("deadlineGameTime must be positive");
				}
			}
		}
	}
}
