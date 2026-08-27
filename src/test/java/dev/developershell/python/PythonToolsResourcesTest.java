package dev.developershell.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.developershell.registry.ModItemIds;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

final class PythonToolsResourcesTest {
	private static final List<String> ITEM_IDS = List.of(
			"pip_wand", "venv_flask", "python_pickaxe", "dependency_conflict");

	@Test
	void stableCatalogAndVanillaModelChainsResolve() throws Exception {
		assertEquals(4, ModItemIds.pythonTools().size());
		assertEquals(4, ModItemIds.pythonTools().stream().distinct().count());
		for (String itemId : ITEM_IDS) {
			JsonObject definition = resource("assets/developers_hell/items/" + itemId + ".json");
			JsonObject model = resource("assets/developers_hell/models/item/" + itemId + ".json");
			assertTrue(definition.has("model"), itemId + " item definition must select a model");
			assertTrue(model.has("parent"), itemId + " model must inherit a vanilla model");
		}
	}

	@Test
	void everyPythonToolCueAndItemIsTranslated() throws Exception {
		JsonObject language = resource("assets/developers_hell/lang/en_us.json");
		for (String key : List.of(
				"item.developers_hell.pip_wand",
				"item.developers_hell.venv_flask",
				"item.developers_hell.python_pickaxe",
				"item.developers_hell.dependency_conflict",
				"message.developers_hell.python.demo",
				"message.developers_hell.python.selected",
				"message.developers_hell.python.installed",
				"message.developers_hell.python.conflict",
				"message.developers_hell.python.venv.cleared",
				"message.developers_hell.python.pickaxe.recursion",
				"message.developers_hell.python.retry"
		)) {
			assertTrue(language.has(key), "missing translation: " + key);
		}
	}

	@Test
	void runtimeCeilingsStaySmallAndExplicit() {
		assertEquals(16, PythonToolsRuntime.MAX_ORE_BLOCKS);
		assertEquals(128, PythonToolsRuntime.MAX_VISITED_NODES);
		assertEquals(8, PythonToolsRuntime.MAX_ORE_RADIUS);
		assertEquals(200L, PythonToolsRuntime.RECURSION_COOLDOWN_TICKS);
	}

	@Test
	void productionPythonSourcesExposeNoExecutionOrFileApi() throws Exception {
		Path root = Path.of("src/main/java/dev/developershell/python");
		try (var paths = Files.list(root)) {
			for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
				String source = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
				for (String forbidden : List.of(
						"processbuilder", "runtime.getruntime", "java.io.", "java.nio.file",
						"javax.script", "org.python", "java.net.", "httpclient", "class.forname")) {
					assertTrue(!source.contains(forbidden), path + " contains forbidden surface: " + forbidden);
				}
			}
		}
	}

	@Test
	void verifierPinsPythonArchiveAndDeadlineReceiptFloor() throws Exception {
		String verifier = Files.readString(Path.of("scripts/verify-lecture.ps1"), StandardCharsets.UTF_8);
		assertTrue(verifier.contains("dev/developershell/python/PythonToolsRuntime.class"));
		assertTrue(verifier.contains("assets/developers_hell/items/python_pickaxe.json"));
		assertTrue(verifier.contains("$testReceipts.UnitCount -lt 136"));
		assertTrue(verifier.contains("Python comedy class links a forbidden process/file/script execution surface"));
	}

	@Test
	void readmeDocumentsDemoOfflineContractAndPendingUat() throws Exception {
		String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
		assertTrue(readme.contains("/devhell python demo"));
		assertTrue(readme.contains("never runs Python or pip"));
		assertTrue(readme.contains("manual in-world item feel"));
	}

	private static JsonObject resource(String path) throws Exception {
		var stream = PythonToolsResourcesTest.class.getClassLoader().getResourceAsStream(path);
		assertNotNull(stream, "missing resource: " + path);
		try (stream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}
}
