package dev.developershell.campaign;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Closed, immutable inputs accepted by the campaign reducer. */
public sealed interface CampaignEvent permits
		CampaignEvent.Start,
		CampaignEvent.EncounterTerminal,
		CampaignEvent.ReconcileRetake,
		CampaignEvent.RetakeFallback,
		CampaignEvent.RecoverSheet,
		CampaignEvent.ConfirmSheetProjection,
		CampaignEvent.ConfirmRemoteProjection,
		CampaignEvent.ResolveLegacyRemoteAbsence,
		CampaignEvent.RewardFallback,
		CampaignEvent.StartRemoteCooldown,
		CampaignEvent.RemoteReadyNotice {
	UUID ownerUuid();

	/**
	 * Closed encounter-ending inputs that compete for the same persisted active reference.
	 * The reducer admits exactly one matching instance; every later instance observes the
	 * already-cleared reference and becomes an intent-free no-op.
	 */
	sealed interface EncounterTerminal extends CampaignEvent permits Terminal, NormalizeReload, Victory {
		UUID encounterUuid();
	}

	record Start(
			UUID ownerUuid,
			String deskDimension,
			BlockPos deskPos,
			Direction deskFacing,
			BlockPos retryPos,
			UUID encounterUuid,
			UUID professorUuid,
			PlayerCampaignState.RetakeKey expectedRetakeKey
	) implements CampaignEvent {
		public Start {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			if (Objects.requireNonNull(deskDimension, "deskDimension").isBlank()) {
				throw new IllegalArgumentException("deskDimension must not be blank");
			}
			deskPos = Objects.requireNonNull(deskPos, "deskPos").immutable();
			Objects.requireNonNull(deskFacing, "deskFacing");
			if (!deskFacing.getAxis().isHorizontal()) {
				throw new IllegalArgumentException("deskFacing must be horizontal");
			}
			retryPos = Objects.requireNonNull(retryPos, "retryPos").immutable();
			Objects.requireNonNull(encounterUuid, "encounterUuid");
			Objects.requireNonNull(professorUuid, "professorUuid");
			if (expectedRetakeKey != null && !ownerUuid.equals(expectedRetakeKey.ownerUuid())) {
				throw new IllegalArgumentException("Retake key owner must match start owner");
			}
		}

		/** Compatibility constructor for an initial Contract start. */
		public Start(
				UUID ownerUuid,
				String deskDimension,
				BlockPos deskPos,
				Direction deskFacing,
				BlockPos retryPos,
				UUID encounterUuid,
				UUID professorUuid
		) {
			this(
					ownerUuid,
					deskDimension,
					deskPos,
					deskFacing,
					retryPos,
					encounterUuid,
					professorUuid,
					null
			);
		}
	}

	record Terminal(UUID ownerUuid, UUID encounterUuid, TerminalReason reason) implements EncounterTerminal {
		public Terminal {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(encounterUuid, "encounterUuid");
			Objects.requireNonNull(reason, "reason");
		}
	}

	record NormalizeReload(UUID ownerUuid, UUID encounterUuid) implements EncounterTerminal {
		public NormalizeReload {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(encounterUuid, "encounterUuid");
		}
	}

	record Victory(UUID ownerUuid, UUID encounterUuid) implements EncounterTerminal {
		public Victory {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(encounterUuid, "encounterUuid");
		}
	}

	record ReconcileRetake(
			UUID ownerUuid,
			PlayerCampaignState.RetakeKey retakeKey,
			UUID fallbackEntityUuid
	) implements CampaignEvent {
		public ReconcileRetake {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(retakeKey, "retakeKey");
			Objects.requireNonNull(fallbackEntityUuid, "fallbackEntityUuid");
			if (!ownerUuid.equals(retakeKey.ownerUuid())) {
				throw new IllegalArgumentException("Retake key owner must match event owner");
			}
		}
	}

	record RetakeFallback(
			UUID ownerUuid,
			PlayerCampaignState.RetakeKey retakeKey,
			UUID fallbackEntityUuid,
			FallbackOperation operation
	) implements CampaignEvent {
		public RetakeFallback {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(retakeKey, "retakeKey");
			Objects.requireNonNull(fallbackEntityUuid, "fallbackEntityUuid");
			Objects.requireNonNull(operation, "operation");
			if (!ownerUuid.equals(retakeKey.ownerUuid())) {
				throw new IllegalArgumentException("Retake key owner must match event owner");
			}
		}
	}

	record RecoverSheet(UUID ownerUuid, long expectedSequence) implements CampaignEvent {
		public RecoverSheet {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			if (expectedSequence < 0L) {
				throw new IllegalArgumentException("expectedSequence must be non-negative");
			}
		}
	}

	record ConfirmSheetProjection(UUID ownerUuid, long recoverySequence) implements CampaignEvent {
		public ConfirmSheetProjection {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			if (recoverySequence < 0L) {
				throw new IllegalArgumentException("recoverySequence must be non-negative");
			}
		}
	}

	record ConfirmRemoteProjection(UUID ownerUuid, UUID projectionUuid) implements CampaignEvent {
		public ConfirmRemoteProjection {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(projectionUuid, "projectionUuid");
		}
	}

	record ResolveLegacyRemoteAbsence(UUID ownerUuid, UUID projectionUuid) implements CampaignEvent {
		public ResolveLegacyRemoteAbsence {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(projectionUuid, "projectionUuid");
		}
	}

	/** One state-first lifecycle change for an exact Sheet or Remote fallback entity. */
	record RewardFallback(
			RewardProjectionKey key,
			UUID entityUuid,
			String dimension,
			BlockPos position,
			RewardFallbackOperation operation
	) implements CampaignEvent {
		public RewardFallback {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(entityUuid, "entityUuid");
			if (Objects.requireNonNull(dimension, "dimension").isBlank()) {
				throw new IllegalArgumentException("fallback dimension must not be blank");
			}
			position = Objects.requireNonNull(position, "position").immutable();
			Objects.requireNonNull(operation, "operation");
		}

		@Override
		public UUID ownerUuid() {
			return key.ownerUuid();
		}
	}

	sealed interface RewardProjectionKey permits SheetProjectionKey, RemoteProjectionKey {
		UUID ownerUuid();
	}

	record SheetProjectionKey(UUID ownerUuid, long recoverySequence) implements RewardProjectionKey {
		public SheetProjectionKey {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			if (recoverySequence < 0L) {
				throw new IllegalArgumentException("recoverySequence must be non-negative");
			}
		}
	}

	record RemoteProjectionKey(UUID ownerUuid, UUID projectionUuid) implements RewardProjectionKey {
		public RemoteProjectionKey {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(projectionUuid, "projectionUuid");
		}
	}

	record StartRemoteCooldown(
			UUID ownerUuid,
			long observedGameTime,
			long cooldownDeadlineGameTime
	) implements CampaignEvent {
		public StartRemoteCooldown {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			if (observedGameTime < 0L || cooldownDeadlineGameTime < 0L) {
				throw new IllegalArgumentException("Remote times must be non-negative");
			}
		}
	}

	record RemoteReadyNotice(
			UUID ownerUuid,
			long cooldownDeadlineGameTime,
			long observedGameTime
	) implements CampaignEvent {
		public RemoteReadyNotice {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			if (cooldownDeadlineGameTime < 0L || observedGameTime < 0L) {
				throw new IllegalArgumentException("Remote times must be non-negative");
			}
		}
	}

	enum TerminalReason {
		DEATH("death"),
		ESCAPE("escape"),
		TIMEOUT("timeout"),
		DIMENSION_CHANGE("dimension_change"),
		DISCONNECT("disconnect"),
		ABORT("abort"),
		SERVER_STOP("server_stop"),
		ENTITY_UNLOAD("entity_unload");

		private final String serializedName;

		TerminalReason(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return serializedName;
		}
	}

	enum FallbackOperation {
		MATERIALIZED("materialized"),
		MATERIALIZATION_FAILED("materialization_failed"),
		LOST("lost"),
		CLEARED("cleared");

		private final String serializedName;

		FallbackOperation(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return serializedName;
		}
	}

	enum RewardFallbackOperation {
		RESERVE("reserved"),
		MATERIALIZED("materialized"),
		RELOCATED("relocated"),
		LOST("lost"),
		CLEARED("cleared");

		private final String serializedName;

		RewardFallbackOperation(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return serializedName;
		}
	}
}
