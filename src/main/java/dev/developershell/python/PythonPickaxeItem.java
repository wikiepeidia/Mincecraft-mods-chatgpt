package dev.developershell.python;

import net.minecraft.world.item.Item;

/** Vanilla-tool adapter; the bounded server break callback lives in {@link PythonToolsRuntime}. */
public final class PythonPickaxeItem extends Item {
	public PythonPickaxeItem(Properties properties) {
		super(properties);
	}
}
