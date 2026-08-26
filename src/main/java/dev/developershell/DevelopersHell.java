package dev.developershell;

import dev.developershell.lecture.LectureEncounterManager;
import dev.developershell.lecture.LectureRules;
import dev.developershell.registry.ModEntities;
import dev.developershell.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DevelopersHell implements ModInitializer {
	public static final String MOD_ID = "developers_hell";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModItems.initialize();
		ModEntities.initialize();
		LectureEncounterManager.initialize(LectureRules.standard());
		ModItems.CURSED_UNPAID_INTERNSHIP_CONTRACT.registerInteraction();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			LectureEncounterManager.tick(server);
			if (server.getTickCount() == 1) {
				LOGGER.info("DEVELOPERS_HELL_SERVER_FIRST_TICK_READY");
			}
		});
		LOGGER.info("Developer's Hell foundation initialized");
	}
}
