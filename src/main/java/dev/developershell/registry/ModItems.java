package dev.developershell.registry;

import dev.developershell.item.CursedInternshipContractItem;
import dev.developershell.item.AttendanceSheetItem;
import dev.developershell.item.InfiniteSlidesRemoteItem;
import dev.developershell.item.RetakeFormItem;
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
	public static final RetakeFormItem RETAKE_FORM = register(
			ModItemIds.RETAKE_FORM,
			RetakeFormItem::new,
			new Item.Properties().stacksTo(1)
	);
	public static final AttendanceSheetItem ATTENDANCE_SHEET = register(
			ModItemIds.ATTENDANCE_SHEET,
			AttendanceSheetItem::new,
			new Item.Properties().stacksTo(1)
	);
	public static final InfiniteSlidesRemoteItem INFINITE_SLIDES_REMOTE = register(
			ModItemIds.INFINITE_SLIDES_REMOTE,
			InfiniteSlidesRemoteItem::new,
			new Item.Properties().stacksTo(1)
	);
	public static final Item SIGNED_DEFENSE_MINUTES = register(
			ModItemIds.SIGNED_DEFENSE_MINUTES,
			Item::new,
			new Item.Properties().stacksTo(1)
	);
	public static final Item EVIDENCE_BINDER = register(
			ModItemIds.EVIDENCE_BINDER,
			Item::new,
			new Item.Properties().stacksTo(1)
	);
	public static final Item APPROVED_REVISION_STAMP = register(
			ModItemIds.APPROVED_REVISION_STAMP,
			Item::new,
			new Item.Properties().stacksTo(1)
	);
	public static final Item RED_PEN = register(
			ModItemIds.RED_PEN,
			Item::new,
			new Item.Properties().stacksTo(1)
	);
	public static final Item DEFINITELY_LEGITIMATE_DIPLOMA = register(
			ModItemIds.DEFINITELY_LEGITIMATE_DIPLOMA,
			Item::new,
			new Item.Properties().stacksTo(1)
	);

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
		INFINITE_SLIDES_REMOTE.registerUseCallback();
	}

	private ModItems() {
	}
}
