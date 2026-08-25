package dev.developershell;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DevelopersHell implements ModInitializer {
	public static final String MOD_ID = "developers_hell";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<Item> FOUNDATION_TOKEN_ID =
			ResourceKey.create(Registries.ITEM, id("foundation_token"));
	public static final Item FOUNDATION_TOKEN =
			new Item(new Item.Properties().setId(FOUNDATION_TOKEN_ID));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.ITEM, FOUNDATION_TOKEN_ID, FOUNDATION_TOKEN);
		LOGGER.info("Developer's Hell foundation initialized");
	}
}
