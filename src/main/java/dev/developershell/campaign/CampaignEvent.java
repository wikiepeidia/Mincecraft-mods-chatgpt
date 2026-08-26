package dev.developershell.campaign;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Closed, immutable inputs accepted by the campaign reducer. */
public sealed interface CampaignEvent permits
		CampaignEvent.Start,
		CampaignEvent.Terminal,
		CampaignEvent.NormalizeReload,
		CampaignEvent.Victory,
		CampaignEvent.ReconcileRetake,
		CampaignEvent.RetakeFallback,
		CampaignEvent.RecoverSheet,
		CampaignEvent.StartRemoteCooldown,
		CampaignEvent.RemoteReadyNotice {
	UUID ownerUuid();

	record Start(
			UUID ownerUuid,
			String deskDimension,
			BlockPos deskPos,
			Direction deskFacing,
			BlockPos retryPos,
			UUID encounterUuid,
			UUID professorUuid
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
		}
	}

	record Terminal(UUID ownerUuid, UUID encounterUuid, TerminalReason reason) implements CampaignEvent {
		public Terminal {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(encounterUuid, "encounterUuid");
			Objects.requireNonNull(reason, "reason");
		}
	}

	record NormalizeReload(UUID ownerUuid, UUID encounterUuid) implements CampaignEvent {
		public NormalizeReload {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(encounterUuid, "encounterUuid");
		}
	}

	record Victory(UUID ownerUuid, UUID encounterUuid) implements CampaignEvent {
		public Victory {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(encounterUuid, "encounterUuid");
		}
	}

	record ReconcileRetake(UUID ownerUuid, UUID fallbackEntityUuid) implements CampaignEvent {
		public ReconcileRetake {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(fallbackEntityUuid, "fallbackEntityUuid");
		}
	}

	record RetakeFallback(
			UUID ownerUuid,
			UUID fallbackEntityUuid,
			FallbackOperation operation
	) implements CampaignEvent {
		public RetakeFallback {
			Objects.requireNonNull(ownerUuid, "ownerUuid");
			Objects.requireNonNull(fallbackEntityUuid, "fallbackEntityUuid");
			Objects.requireNonNull(operation, "operation");
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
		LOST("lost");

		private final String serializedName;

		FallbackOperation(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return serializedName;
		}
	}
}
