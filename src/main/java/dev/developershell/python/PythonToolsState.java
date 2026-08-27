package dev.developershell.python;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable per-player authority for the finite Python-tools simulation. */
public record PythonToolsState(
		int selectedIndex,
		Set<FakePackage> installedPackages,
		boolean dependencyConflict,
		long flaskCooldownUntilTick,
		long recursionCooldownUntilTick,
		long revision
) {
	private static final Codec<RawState> RAW_CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					Codec.intRange(0, FakePackage.values().length - 1).fieldOf("selected_index")
							.forGetter(RawState::selectedIndex),
					FakePackage.CODEC.listOf().fieldOf("installed_packages")
							.forGetter(RawState::installedPackages),
					Codec.BOOL.fieldOf("dependency_conflict").forGetter(RawState::dependencyConflict),
					Codec.LONG.fieldOf("flask_cooldown_until_tick").forGetter(RawState::flaskCooldownUntilTick),
					Codec.LONG.fieldOf("recursion_cooldown_until_tick").forGetter(RawState::recursionCooldownUntilTick),
					Codec.LONG.fieldOf("revision").forGetter(RawState::revision)
			).apply(instance, RawState::new)
	);
	public static final Codec<PythonToolsState> CODEC = RAW_CODEC.comapFlatMap(
			PythonToolsState::decode,
			PythonToolsState::encode
	);

	public PythonToolsState {
		if (selectedIndex < 0 || selectedIndex >= FakePackage.values().length) {
			throw new IllegalArgumentException("Selected package index is out of range");
		}
		Objects.requireNonNull(installedPackages, "installedPackages");
		for (FakePackage installedPackage : installedPackages) {
			if (installedPackage == null) {
				throw new IllegalArgumentException("Installed packages cannot contain null");
			}
		}
		installedPackages = Set.copyOf(installedPackages);
		if (flaskCooldownUntilTick < 0 || recursionCooldownUntilTick < 0 || revision < 0) {
			throw new IllegalArgumentException("Persisted ticks and revision cannot be negative");
		}
	}

	public static PythonToolsState initial() {
		return new PythonToolsState(0, Set.of(), false, 0L, 0L, 0L);
	}

	public FakePackage selectedPackage() {
		return FakePackage.fromIndex(selectedIndex);
	}

	public PythonToolsState cycleSelection() {
		return new PythonToolsState(
				(selectedIndex + 1) % FakePackage.values().length,
				installedPackages,
				dependencyConflict,
				flaskCooldownUntilTick,
				recursionCooldownUntilTick,
				revision + 1
		);
	}

	PythonToolsState install(FakePackage fakePackage) {
		EnumSet<FakePackage> next = installedPackages.isEmpty()
				? EnumSet.noneOf(FakePackage.class)
				: EnumSet.copyOf(installedPackages);
		next.add(Objects.requireNonNull(fakePackage, "fakePackage"));
		return changed(next, false, flaskCooldownUntilTick, recursionCooldownUntilTick);
	}

	PythonToolsState conflict() {
		return dependencyConflict
				? this
				: changed(installedPackages, true, flaskCooldownUntilTick, recursionCooldownUntilTick);
	}

	PythonToolsState clearEnvironment(long cooldownUntilTick) {
		if (cooldownUntilTick < 0) {
			throw new IllegalArgumentException("Cooldown cannot be negative");
		}
		Set<FakePackage> isolated = installedPackages.contains(selectedPackage())
				? Set.of(selectedPackage())
				: Set.of();
		return changed(isolated, false, cooldownUntilTick, recursionCooldownUntilTick);
	}

	public PythonToolsState withRecursionCooldown(long cooldownUntilTick) {
		if (cooldownUntilTick < 0) {
			throw new IllegalArgumentException("Cooldown cannot be negative");
		}
		if (cooldownUntilTick == recursionCooldownUntilTick) {
			return this;
		}
		return changed(installedPackages, dependencyConflict, flaskCooldownUntilTick, cooldownUntilTick);
	}

	private PythonToolsState changed(
			Set<FakePackage> installed,
			boolean conflict,
			long flaskCooldown,
			long recursionCooldown
	) {
		return new PythonToolsState(
				selectedIndex,
				installed,
				conflict,
				flaskCooldown,
				recursionCooldown,
				revision + 1
		);
	}

	private static DataResult<PythonToolsState> decode(RawState raw) {
		if (raw.flaskCooldownUntilTick() < 0 || raw.recursionCooldownUntilTick() < 0 || raw.revision() < 0) {
			return DataResult.error(() -> "Python-tools ticks and revision cannot be negative");
		}
		if (Set.copyOf(raw.installedPackages()).size() != raw.installedPackages().size()) {
			return DataResult.error(() -> "Duplicate installed fake package");
		}
		return DataResult.success(new PythonToolsState(
				raw.selectedIndex(),
				Set.copyOf(raw.installedPackages()),
				raw.dependencyConflict(),
				raw.flaskCooldownUntilTick(),
				raw.recursionCooldownUntilTick(),
				raw.revision()
		));
	}

	private static RawState encode(PythonToolsState state) {
		List<FakePackage> ordered = state.installedPackages().stream().sorted().toList();
		return new RawState(
				state.selectedIndex(), ordered, state.dependencyConflict(),
				state.flaskCooldownUntilTick(), state.recursionCooldownUntilTick(), state.revision()
		);
	}

	private record RawState(
			int selectedIndex,
			List<FakePackage> installedPackages,
			boolean dependencyConflict,
			long flaskCooldownUntilTick,
			long recursionCooldownUntilTick,
			long revision
	) {
		private RawState {
			installedPackages = List.copyOf(installedPackages);
		}
	}
}
