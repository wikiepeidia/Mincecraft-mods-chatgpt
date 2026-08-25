package dev.developershell.module;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class ModuleGate {
	private final Set<ModuleId> enabledModules;

	private ModuleGate(Set<ModuleId> enabledModules) {
		this.enabledModules = Collections.unmodifiableSet(enabledModules);
	}

	public static ModuleGate allEnabled() {
		return new ModuleGate(EnumSet.allOf(ModuleId.class));
	}

	public static ModuleGate allDisabled() {
		return allEnabled();
	}

	public static ModuleGate of(Set<ModuleId> enabledModules) {
		Objects.requireNonNull(enabledModules, "enabledModules");

		EnumSet<ModuleId> copy = EnumSet.noneOf(ModuleId.class);
		for (ModuleId module : enabledModules) {
			copy.add(Objects.requireNonNull(module, "enabledModules member"));
		}
		return new ModuleGate(copy);
	}

	public boolean isEnabled(ModuleId module) {
		return enabledModules.contains(Objects.requireNonNull(module, "module"));
	}

	public Set<ModuleId> enabledModules() {
		return enabledModules;
	}
}
