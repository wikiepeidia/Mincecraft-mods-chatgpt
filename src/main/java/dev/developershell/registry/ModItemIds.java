package dev.developershell.registry;

import dev.developershell.DevelopersHell;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItemIds {
	public static final ResourceKey<Item> FOUNDATION_TOKEN =
			ResourceKey.create(Registries.ITEM, DevelopersHell.id("foundation_token"));

	private static final List<ResourceKey<Item>> ALL = List.of(FOUNDATION_TOKEN);

	public static List<ResourceKey<Item>> all() {
		return ALL;
	}

	private ModItemIds() {
	}
}
