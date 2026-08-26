package dev.developershell.registry;

import dev.developershell.DevelopersHell;
import dev.developershell.item.CursedInternshipContractItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item FOUNDATION_TOKEN =
			register(ModItemIds.FOUNDATION_TOKEN, Item::new, new Item.Properties());
	public static final ResourceKey<Item> CURSED_UNPAID_INTERNSHIP_CONTRACT_KEY =
			itemKey("cursed_unpaid_internship_contract");
	public static final ResourceKey<Item> ATTENDANCE_SHEET_KEY = itemKey("attendance_sheet");
	public static final ResourceKey<Item> INFINITE_SLIDES_REMOTE_KEY = itemKey("infinite_slides_remote");

	public static final CursedInternshipContractItem CURSED_UNPAID_INTERNSHIP_CONTRACT = register(
			CURSED_UNPAID_INTERNSHIP_CONTRACT_KEY,
			CursedInternshipContractItem::new,
			new Item.Properties().stacksTo(16)
	);
	public static final Item ATTENDANCE_SHEET =
			register(ATTENDANCE_SHEET_KEY, Item::new, new Item.Properties());
	public static final Item INFINITE_SLIDES_REMOTE =
			register(INFINITE_SLIDES_REMOTE_KEY, Item::new, new Item.Properties().stacksTo(1));

	private static <T extends Item> T register(
			ResourceKey<Item> key,
			Function<Item.Properties, T> factory,
			Item.Properties properties
	) {
		T item = factory.apply(properties.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static ResourceKey<Item> itemKey(String path) {
		return ResourceKey.create(Registries.ITEM, DevelopersHell.id(path));
	}

	public static void initialize() {
		// Class loading performs unconditional stable registration before behavior hooks.
	}

	private ModItems() {
	}
}
