package dev.developershell.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.developershell.DevelopersHell;
import dev.developershell.registry.ModItemIds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

final class ModuleGateTest {
	@Test
	void allDisabledDisablesEveryModule() {
		assertFalse(ModuleGate.allDisabled().isEnabled(ModuleId.GRADUATION_ANYFAIL), "allDisabled must disable graduation_anyfail");

		ModuleGate gate = ModuleGate.allDisabled();
		for (ModuleId module : ModuleId.values()) {
			assertFalse(gate.isEnabled(module), () -> "allDisabled enabled " + module.serializedName());
		}
		assertTrue(gate.enabledModules().isEmpty());
	}

	@Test
	void allEnabledEnablesEveryModule() {
		ModuleGate gate = ModuleGate.allEnabled();

		for (ModuleId module : ModuleId.values()) {
			assertTrue(gate.isEnabled(module), () -> "allEnabled disabled " + module.serializedName());
		}
		assertEquals(EnumSet.allOf(ModuleId.class), gate.enabledModules());
	}

	@Test
	void explicitGateEnablesOnlySelectedModule() {
		ModuleGate gate = ModuleGate.of(Set.of(ModuleId.PYTHON_TOOLS));

		for (ModuleId module : ModuleId.values()) {
			assertEquals(module == ModuleId.PYTHON_TOOLS, gate.isEnabled(module));
		}
	}

	@Test
	void ofRejectsNullSetAndMember() {
		assertThrows(NullPointerException.class, () -> ModuleGate.of(null));

		Set<ModuleId> withNull = new HashSet<>();
		withNull.add(null);
		assertThrows(NullPointerException.class, () -> ModuleGate.of(withNull));
	}

	@Test
	void gateDefensivelyCopiesInput() {
		EnumSet<ModuleId> source = EnumSet.of(ModuleId.PYTHON_TOOLS);
		ModuleGate gate = ModuleGate.of(source);

		source.clear();
		source.add(ModuleId.GIT_HAPPENS);

		assertTrue(gate.isEnabled(ModuleId.PYTHON_TOOLS));
		assertFalse(gate.isEnabled(ModuleId.GIT_HAPPENS));
	}

	@Test
	void enabledModuleViewIsImmutableAndNullQueriesAreRejected() {
		ModuleGate gate = ModuleGate.of(Set.of(ModuleId.RUBBER_DUCK_ENGINEERING));

		assertThrows(UnsupportedOperationException.class, () -> gate.enabledModules().add(ModuleId.GIT_HAPPENS));
		assertThrows(NullPointerException.class, () -> gate.isEnabled(null));
	}

	@Test
	void serializedNamesAreExactAndUnique() {
		List<String> names = Arrays.stream(ModuleId.values()).map(ModuleId::serializedName).toList();

		assertEquals(List.of(
				"graduation_anyfail",
				"metadata_roulette",
				"python_tools",
				"codex_rich_kid_terminal",
				"git_happens",
				"stack_overflow_totem",
				"rubber_duck_engineering",
				"three_day_deadline"
		), names);
		assertEquals(names.size(), new HashSet<>(names).size());
	}

	@Test
	void stableCatalogIsExactImmutableAndIndependentOfEveryGate() {
		ResourceKey<Item> expectedFoundationToken =
				ResourceKey.create(Registries.ITEM, DevelopersHell.id("foundation_token"));
		List<ResourceKey<Item>> baseline = List.copyOf(ModItemIds.all());

		assertEquals(List.of(expectedFoundationToken), baseline);
		assertThrows(UnsupportedOperationException.class, () -> ModItemIds.all().add(expectedFoundationToken));

		List<ModuleGate> gates = new ArrayList<>();
		gates.add(ModuleGate.allEnabled());
		gates.add(ModuleGate.allDisabled());
		for (ModuleId module : ModuleId.values()) {
			gates.add(ModuleGate.of(Set.of(module)));
		}

		for (ModuleGate gate : gates) {
			for (ModuleId module : ModuleId.values()) {
				gate.isEnabled(module);
			}
			assertEquals(baseline, ModItemIds.all());
		}
	}
}
