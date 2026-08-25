package dev.developershell.registry;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item FOUNDATION_TOKEN =
			register(ModItemIds.FOUNDATION_TOKEN, Item::new, new Item.Properties());

	private static Item register(
			ResourceKey<Item> key,
			Function<Item.Properties, Item> factory,
			Item.Properties properties
	) {
		Item item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void initialize() {
		// Class loading performs unconditional stable registration before behavior hooks.
	}

	private ModItems() {
	}
}
