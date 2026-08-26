package dev.developershell.registry;

import dev.developershell.item.CursedInternshipContractItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item FOUNDATION_TOKEN =
			register(ModItemIds.FOUNDATION_TOKEN, Item::new, new Item.Properties());

	public static final CursedInternshipContractItem CURSED_UNPAID_INTERNSHIP_CONTRACT = register(
			ModItemIds.CURSED_UNPAID_INTERNSHIP_CONTRACT,
			CursedInternshipContractItem::new,
			new Item.Properties().stacksTo(16)
	);
	public static final Item RETAKE_FORM =
			register(ModItemIds.RETAKE_FORM, Item::new, new Item.Properties().stacksTo(1));
	public static final Item ATTENDANCE_SHEET =
			register(ModItemIds.ATTENDANCE_SHEET, Item::new, new Item.Properties());
	public static final Item INFINITE_SLIDES_REMOTE =
			register(ModItemIds.INFINITE_SLIDES_REMOTE, Item::new, new Item.Properties().stacksTo(1));

	private static <T extends Item> T register(
			ResourceKey<Item> key,
			Function<Item.Properties, T> factory,
			Item.Properties properties
	) {
		T item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void initialize() {
		// Class loading performs unconditional stable registration before behavior hooks.
	}

	private ModItems() {
	}
}
