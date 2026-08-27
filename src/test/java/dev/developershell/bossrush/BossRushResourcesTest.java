package dev.developershell.bossrush;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.developershell.registry.ModItemIds;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BossRushResourcesTest {
	private static final List<String> ITEM_IDS = List.of(
			"signed_defense_minutes",
			"evidence_binder",
			"approved_revision_stamp",
			"red_pen",
			"definitely_legitimate_diploma"
	);

	@Test
	void stableItemCatalogAndVanillaModelChainsResolve() throws Exception {
		assertEquals(5, ModItemIds.bossRush().size());
		assertEquals(5, ModItemIds.bossRush().stream().distinct().count());
		for (String itemId : ITEM_IDS) {
			JsonObject definition = resource("assets/developers_hell/items/" + itemId + ".json");
			JsonObject model = resource("assets/developers_hell/models/item/" + itemId + ".json");
			assertTrue(definition.has("model"), itemId + " item definition must select a model");
			assertTrue(model.has("parent"), itemId + " model must inherit a vanilla model");
		}
	}

	@Test
	void everyPlayerFacingBossRushCueIsTranslated() throws Exception {
		JsonObject language = resource("assets/developers_hell/lang/en_us.json");
		for (String key : List.of(
				"bossbar.developers_hell.jury",
				"bossbar.developers_hell.chairman",
				"bossbar.developers_hell.sponsor",
				"bossbar.developers_hell.codex",
				"actionbar.developers_hell.bossrush.jury.scope_creep",
				"message.developers_hell.bossrush.chairman.major_revisions",
				"actionbar.developers_hell.bossrush.codex.context_overflow",
				"message.developers_hell.bossrush.codex.max_reasoning",
				"item.developers_hell.definitely_legitimate_diploma"
		)) {
			assertTrue(language.has(key), "missing translation: " + key);
		}
	}

	private static JsonObject resource(String path) throws Exception {
		var stream = BossRushResourcesTest.class.getClassLoader().getResourceAsStream(path);
		assertNotNull(stream, "missing resource: " + path);
		try (stream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}
}
