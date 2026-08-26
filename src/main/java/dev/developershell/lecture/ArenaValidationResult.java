package dev.developershell.lecture;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/** Immutable accepted geometry or one stable localized rejection. */
public sealed interface ArenaValidationResult permits
		ArenaValidationResult.Accepted,
		ArenaValidationResult.Rejected {
	boolean accepted();

	Optional<ArenaRejection> rejection();

	record Accepted(LectureGeometry.Layout layout, BlockPos retryPos) implements ArenaValidationResult {
		public Accepted {
			Objects.requireNonNull(layout, "layout");
			retryPos = Objects.requireNonNull(retryPos, "retryPos").immutable();
			if (!layout.retryCandidates().contains(retryPos)) {
				throw new IllegalArgumentException("Accepted retry position must come from the frozen retry order");
			}
		}

		@Override
		public boolean accepted() {
			return true;
		}

		@Override
		public Optional<ArenaRejection> rejection() {
			return Optional.empty();
		}

		public BlockPos combatOriginFloor() {
			return layout.combatCenterFloor();
		}
	}

	record Rejected(ArenaRejection reason) implements ArenaValidationResult {
		public Rejected {
			Objects.requireNonNull(reason, "reason");
		}

		@Override
		public boolean accepted() {
			return false;
		}

		@Override
		public Optional<ArenaRejection> rejection() {
			return Optional.of(reason);
		}
	}
}
