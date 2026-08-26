package dev.developershell.registry;

import dev.developershell.DevelopersHell;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItemIds {
	public static final ResourceKey<Item> FOUNDATION_TOKEN = itemKey("foundation_token");
	public static final ResourceKey<Item> CURSED_UNPAID_INTERNSHIP_CONTRACT =
			itemKey("cursed_unpaid_internship_contract");
	public static final ResourceKey<Item> RETAKE_FORM = itemKey("retake_form");
	public static final ResourceKey<Item> ATTENDANCE_SHEET = itemKey("attendance_sheet");
	public static final ResourceKey<Item> INFINITE_SLIDES_REMOTE = itemKey("infinite_slides_remote");

	private static final List<ResourceKey<Item>> FOUNDATION_CATALOG = List.of(FOUNDATION_TOKEN);
	private static final List<ResourceKey<Item>> PHASE_TWO_CATALOG = List.of(
			FOUNDATION_TOKEN,
			CURSED_UNPAID_INTERNSHIP_CONTRACT,
			RETAKE_FORM,
			ATTENDANCE_SHEET,
			INFINITE_SLIDES_REMOTE
	);

	/** Retained Phase 1 catalog view for source compatibility. */
	public static List<ResourceKey<Item>> all() {
		return FOUNDATION_CATALOG;
	}

	/** Every stable item identity registered by the complete Phase 2 catalog. */
	public static List<ResourceKey<Item>> phaseTwo() {
		return PHASE_TWO_CATALOG;
	}

	private static ResourceKey<Item> itemKey(String path) {
		return ResourceKey.create(Registries.ITEM, DevelopersHell.id(path));
	}

	private ModItemIds() {
	}
}
