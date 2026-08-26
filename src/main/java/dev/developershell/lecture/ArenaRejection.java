package dev.developershell.lecture;

import java.util.Objects;

/** Closed, localized reasons why a Contract preflight can reject an Internship Desk. */
public enum ArenaRejection {
	WRONG_TARGET("message.developers_hell.contract.find_lectern"),
	WRONG_DIMENSION("message.developers_hell.contract.rejected.dimension"),
	UNLOADED_OR_OUTSIDE_BORDER("message.developers_hell.contract.rejected.loaded_border"),
	NON_SOLID_FLOOR("message.developers_hell.contract.rejected.floor"),
	INSUFFICIENT_HEADROOM("message.developers_hell.contract.rejected.headroom"),
	UNSAFE_RETRY("message.developers_hell.contract.rejected.retry"),
	ACTIVE_ENCOUNTER("message.developers_hell.contract.rejected.active"),
	SPAWN_CAPACITY("message.developers_hell.contract.rejected.spawn");

	private final String translationKey;

	ArenaRejection(String translationKey) {
		this.translationKey = Objects.requireNonNull(translationKey, "translationKey");
	}

	public String translationKey() {
		return translationKey;
	}
}
