package dev.developershell.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.developershell.module.ModuleGate;
import dev.developershell.module.ModuleId;
import dev.developershell.registry.ModItemIds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DevHellConfigTest {
	private static final Path CONFIG_DIRECTORY = Path.of("safe-config-root");
	private static final Path CONFIG_PATH = CONFIG_DIRECTORY.resolve(DevHellConfigLoader.FILE_NAME);

	@Test
	void defaultsAreExactSafeBoundedAndImmutable() {
		DevHellConfig config = DevHellConfig.defaults();

		assertEquals(1, config.schemaVersion());
		assertTrue(config.campaignEnabled());
		assertEquals(DevHellConfig.Difficulty.STANDARD, config.difficulty());
		assertFalse(config.bossBlockDamage());
		assertTrue(config.reducedFlashing());
		assertFalse(config.reducedEffects());
		assertEquals(120, config.lecture().professorHealth());
		assertEquals(4, config.lecture().missDamage());
		assertEquals(3, config.lecture().maxAdds());
		assertEquals(100, config.lecture().slideDeckTelegraphTicks());
		assertEquals(160, config.lecture().quizTelegraphTicks());
		assertEquals(120, config.lecture().attendanceTelegraphTicks());
		assertEquals(80, config.lecture().vulnerabilityTicks());
		assertEquals(20, config.lecture().actionBarUpdateTicks());
		assertEquals(10, config.lecture().particleRefreshTicks());
		assertEquals(6, config.lecture().particlesPerRefresh());
		assertEquals(24, config.lecture().maxParticleBurstsPerEncounter());
		assertEquals(8, config.lecture().maxTransitionSoundsPerEncounter());
		assertEquals(5, config.lecture().arenaSearchRadius());
		assertEquals(EnumSet.allOf(ModuleId.class), config.enabledModules());
		assertEquals(DevHellConfig.ScheduleMode.MANUAL, config.metadataRouletteSchedule());
		assertEquals(DevHellConfig.ScheduleMode.MANUAL, config.threeDayDeadlineSchedule());
		assertThrows(UnsupportedOperationException.class,
				() -> config.enabledModules().remove(ModuleId.METADATA_ROULETTE));

		ModuleGate gate = config.moduleGate();
		for (ModuleId module : ModuleId.values()) {
			assertTrue(gate.isEnabled(module), () -> "safe defaults disabled " + module.serializedName());
		}
	}

	@Test
	void missingFileReturnsDefaultsAndWritesOneReusableTemplate() {
		MemoryFileFacts facts = MemoryFileFacts.missing();

		DevHellConfigLoader.LoadResult result = DevHellConfigLoader.load(CONFIG_DIRECTORY, facts);

		assertEquals(DevHellConfig.defaults(), result.config());
		assertEquals(DevHellConfigLoader.SourceStatus.MISSING_DEFAULT, result.sourceStatus());
		assertTrue(result.issues().isEmpty());
		assertTrue(result.defaultTemplateWritten());
		assertEquals(List.of(CONFIG_PATH), facts.writePaths);
		assertTrue(facts.bytes.length > 0);

		MemoryFileFacts reloaded = MemoryFileFacts.regular(facts.bytes);
		DevHellConfigLoader.LoadResult valid = DevHellConfigLoader.load(CONFIG_DIRECTORY, reloaded);
		assertEquals(DevHellConfigLoader.SourceStatus.VALID, valid.sourceStatus());
		assertEquals(DevHellConfig.defaults(), valid.config());
		assertTrue(valid.issues().isEmpty());
	}

	@Test
	void defaultTemplateWriteFailureKeepsSameSafeInMemorySnapshot() {
		MemoryFileFacts facts = MemoryFileFacts.missing();
		facts.writeFailure = new IOException("C:\\private\\person\\secret-token.txt");

		DevHellConfigLoader.LoadResult result = DevHellConfigLoader.load(CONFIG_DIRECTORY, facts);

		assertEquals(DevHellConfig.defaults(), result.config());
		assertEquals(DevHellConfigLoader.SourceStatus.MISSING_DEFAULT, result.sourceStatus());
		assertFalse(result.defaultTemplateWritten());
		assertIssue(result, "$.file", "<write-failed>", "optional default template");
		assertPublicSafe(result.issues());
	}

	@Test
	void validCompleteDocumentAcceptsEveryBoundedValueAndExplicitModule() {
		String document = validDocument()
				.replace("\"campaignEnabled\": true", "\"campaignEnabled\": false")
				.replace("\"difficulty\": \"standard\"", "\"difficulty\": \"intense\"")
				.replace("\"bossBlockDamage\": false", "\"bossBlockDamage\": true")
				.replace("\"reducedFlashing\": true", "\"reducedFlashing\": false")
				.replace("\"reducedEffects\": false", "\"reducedEffects\": true")
				.replace("\"professorHealth\": 120", "\"professorHealth\": 240")
				.replace("\"missDamage\": 4", "\"missDamage\": 9")
				.replace("\"maxAdds\": 3", "\"maxAdds\": 6")
				.replace("\"slideDeckTelegraphTicks\": 100", "\"slideDeckTelegraphTicks\": 140")
				.replace("\"quizTelegraphTicks\": 160", "\"quizTelegraphTicks\": 200")
				.replace("\"attendanceTelegraphTicks\": 120", "\"attendanceTelegraphTicks\": 180")
				.replace("\"vulnerabilityTicks\": 80", "\"vulnerabilityTicks\": 120")
				.replace("\"particleRefreshTicks\": 10", "\"particleRefreshTicks\": 15")
				.replace("\"particlesPerRefresh\": 6", "\"particlesPerRefresh\": 10")
				.replace("\"maxParticleBurstsPerEncounter\": 24", "\"maxParticleBurstsPerEncounter\": 30")
				.replace("\"maxTransitionSoundsPerEncounter\": 8", "\"maxTransitionSoundsPerEncounter\": 10")
				.replace("\"arenaSearchRadius\": 5", "\"arenaSearchRadius\": 7")
				.replace("\"metadataRouletteSchedule\": \"manual\"", "\"metadataRouletteSchedule\": \"automatic\"")
				.replace("\"threeDayDeadlineSchedule\": \"manual\"", "\"threeDayDeadlineSchedule\": \"automatic\"")
				.replace("\"metadata_roulette\": true", "\"metadata_roulette\": false");

		DevHellConfigLoader.LoadResult result = loadDocument(document);

		assertEquals(DevHellConfigLoader.SourceStatus.VALID, result.sourceStatus());
		assertTrue(result.issues().isEmpty());
		assertFalse(result.defaultTemplateWritten());
		assertFalse(result.config().campaignEnabled());
		assertEquals(DevHellConfig.Difficulty.INTENSE, result.config().difficulty());
		assertTrue(result.config().bossBlockDamage());
		assertFalse(result.config().reducedFlashing());
		assertTrue(result.config().reducedEffects());
		assertEquals(240, result.config().lecture().professorHealth());
		assertEquals(9, result.config().lecture().missDamage());
		assertEquals(6, result.config().lecture().maxAdds());
		assertEquals(140, result.config().lecture().slideDeckTelegraphTicks());
		assertEquals(200, result.config().lecture().quizTelegraphTicks());
		assertEquals(180, result.config().lecture().attendanceTelegraphTicks());
		assertEquals(120, result.config().lecture().vulnerabilityTicks());
		assertEquals(15, result.config().lecture().particleRefreshTicks());
		assertEquals(10, result.config().lecture().particlesPerRefresh());
		assertEquals(30, result.config().lecture().maxParticleBurstsPerEncounter());
		assertEquals(10, result.config().lecture().maxTransitionSoundsPerEncounter());
		assertEquals(7, result.config().lecture().arenaSearchRadius());
		assertEquals(DevHellConfig.ScheduleMode.AUTOMATIC, result.config().metadataRouletteSchedule());
		assertEquals(DevHellConfig.ScheduleMode.AUTOMATIC, result.config().threeDayDeadlineSchedule());
		assertFalse(result.config().moduleGate().isEnabled(ModuleId.METADATA_ROULETTE));
		assertEquals(ModuleId.values().length - 1, result.config().enabledModules().size());
	}

	@Test
	void malformedAndNonStrictJsonFailClosedWithoutWriting() {
		List<String> malformedDocuments = List.of(
				"{\"schemaVersion\": 1",
				validDocument().replaceFirst("\\{", "{/* comment */"),
				validDocument().replace("\"professorHealth\": 120", "\"professorHealth\": NaN"),
				validDocument() + " true"
		);

		for (String document : malformedDocuments) {
			MemoryFileFacts facts = MemoryFileFacts.regular(document.getBytes(StandardCharsets.UTF_8));
			byte[] original = facts.bytes.clone();

			DevHellConfigLoader.LoadResult result = DevHellConfigLoader.load(CONFIG_DIRECTORY, facts);

			assertEquals(DevHellConfig.defaults(), result.config());
			assertEquals(DevHellConfigLoader.SourceStatus.INVALID_DEFAULTED, result.sourceStatus());
			assertFalse(result.issues().isEmpty());
			assertTrue(facts.writePaths.isEmpty());
			assertArrayEquals(original, facts.bytes);
			assertPublicSafe(result.issues());
		}
	}

	@Test
	void duplicateUnknownAndMissingPropertiesAreAggregated() {
		String document = validDocument()
				.replace("\"campaignEnabled\": true,", "\"campaignEnabled\": true,\n  \"campaignEnabled\": false,")
				.replace("\"difficulty\": \"standard\",", "")
				.replace("\"modules\": {", "\"mystery/private/path\": \"secret-token-value\",\n  \"modules\": {")
				.replace("\"python_tools\": true,", "\"python_tools\": true,\n    \"python_tools\": false,")
				.replace("\"git_happens\": true,", "\"git_happens\": true,\n    \"unknown_module\": true,");

		DevHellConfigLoader.LoadResult result = loadDocument(document);

		assertEquals(DevHellConfig.defaults(), result.config());
		assertEquals(DevHellConfigLoader.SourceStatus.INVALID_DEFAULTED, result.sourceStatus());
		assertPath(result, "$.campaignEnabled");
		assertPath(result, "$.difficulty");
		assertPath(result, "$.modules.python_tools");
		assertTrue(result.issues().stream().anyMatch(issue -> issue.expected().contains("known schema-v1 property")));
		assertTrue(result.issues().stream().anyMatch(issue -> issue.expected().contains("known module name")));
		assertPublicSafe(result.issues());
	}

	@Test
	void unsupportedSchemaWrongTypesInvalidEnumsAndRangesAreAggregated() {
		String document = validDocument()
				.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
				.replace("\"campaignEnabled\": true", "\"campaignEnabled\": \"yes\"")
				.replace("\"difficulty\": \"standard\"", "\"difficulty\": \"nightmare\"")
				.replace("\"professorHealth\": 120", "\"professorHealth\": 9999")
				.replace("\"missDamage\": 4", "\"missDamage\": -1")
				.replace("\"maxAdds\": 3", "\"maxAdds\": 99")
				.replace("\"particlesPerRefresh\": 6", "\"particlesPerRefresh\": 0")
				.replace("\"slideDeckTelegraphTicks\": 100", "\"slideDeckTelegraphTicks\": 1")
				.replace("\"arenaSearchRadius\": 5", "\"arenaSearchRadius\": 100")
				.replace("\"metadataRouletteSchedule\": \"manual\"", "\"metadataRouletteSchedule\": \"daily\"")
				.replace("\"three_day_deadline\": true", "\"three_day_deadline\": 1");

		DevHellConfigLoader.LoadResult result = loadDocument(document);

		assertEquals(DevHellConfig.defaults(), result.config());
		assertEquals(DevHellConfigLoader.SourceStatus.INVALID_DEFAULTED, result.sourceStatus());
		for (String path : List.of(
				"$.schemaVersion",
				"$.campaignEnabled",
				"$.difficulty",
				"$.lecture.professorHealth",
				"$.lecture.missDamage",
				"$.lecture.maxAdds",
				"$.lecture.particlesPerRefresh",
				"$.lecture.slideDeckTelegraphTicks",
				"$.lecture.arenaSearchRadius",
				"$.metadataRouletteSchedule",
				"$.modules.three_day_deadline"
		)) {
			assertPath(result, path);
		}
		assertTrue(result.issues().stream().anyMatch(issue -> issue.expected().contains("40..400")));
		assertTrue(result.issues().stream().anyMatch(issue -> issue.expected().contains("manual|automatic")));
		assertPublicSafe(result.issues());
	}

	@Test
	void symbolicLinkNonRegularAndOversizeInputsFailBeforeRead() {
		List<MemoryFileFacts> guarded = new ArrayList<>();
		MemoryFileFacts symbolicLink = MemoryFileFacts.regular(validBytes());
		symbolicLink.symbolicLink = true;
		guarded.add(symbolicLink);
		MemoryFileFacts nonRegular = MemoryFileFacts.regular(validBytes());
		nonRegular.regularFile = false;
		guarded.add(nonRegular);
		MemoryFileFacts oversized = MemoryFileFacts.regular(validBytes());
		oversized.reportedSize = DevHellConfigLoader.MAX_FILE_BYTES + 1L;
		guarded.add(oversized);

		for (MemoryFileFacts facts : guarded) {
			byte[] original = facts.bytes.clone();
			DevHellConfigLoader.LoadResult result = DevHellConfigLoader.load(CONFIG_DIRECTORY, facts);

			assertEquals(DevHellConfig.defaults(), result.config());
			assertEquals(DevHellConfigLoader.SourceStatus.INVALID_DEFAULTED, result.sourceStatus());
			assertEquals(0, facts.readCount);
			assertTrue(facts.writePaths.isEmpty());
			assertArrayEquals(original, facts.bytes);
			assertPublicSafe(result.issues());
		}
	}

	@Test
	void sizeRaceAndReadFailureFailClosedWithoutRawIoDetails() {
		MemoryFileFacts raced = MemoryFileFacts.regular(new byte[DevHellConfigLoader.MAX_FILE_BYTES + 1]);
		raced.reportedSize = 20;
		DevHellConfigLoader.LoadResult racedResult = DevHellConfigLoader.load(CONFIG_DIRECTORY, raced);
		assertEquals(DevHellConfig.defaults(), racedResult.config());
		assertIssue(racedResult, "$.file", "<oversize>", "at most 65536 UTF-8 bytes");

		MemoryFileFacts unreadable = MemoryFileFacts.regular(validBytes());
		unreadable.readFailure = new IOException("C:\\Users\\Real Person\\token=top-secret");
		DevHellConfigLoader.LoadResult unreadableResult = DevHellConfigLoader.load(CONFIG_DIRECTORY, unreadable);
		assertEquals(DevHellConfig.defaults(), unreadableResult.config());
		assertIssue(unreadableResult, "$.file", "<read-failed>", "readable local JSON");
		assertPublicSafe(unreadableResult.issues());
	}

	@Test
	void invalidInputRemainsByteForByteUnchangedAndNeverGetsARecoveryWrite() {
		byte[] original = validDocument()
				.replace("\"schemaVersion\": 1", "\"schemaVersion\": 999")
				.getBytes(StandardCharsets.UTF_8);
		MemoryFileFacts facts = MemoryFileFacts.regular(original);

		DevHellConfigLoader.LoadResult result = DevHellConfigLoader.load(CONFIG_DIRECTORY, facts);

		assertEquals(DevHellConfigLoader.SourceStatus.INVALID_DEFAULTED, result.sourceStatus());
		assertArrayEquals(original, facts.bytes);
		assertTrue(facts.writePaths.isEmpty());
	}

	@Test
	void fileAdapterReceivesOnlyTheFixedChildPath() {
		MemoryFileFacts facts = MemoryFileFacts.regular(validBytes());

		DevHellConfigLoader.LoadResult result = DevHellConfigLoader.load(CONFIG_DIRECTORY, facts);

		assertEquals(DevHellConfigLoader.SourceStatus.VALID, result.sourceStatus());
		assertEquals(Set.of(CONFIG_PATH), Set.copyOf(facts.observedPaths));
		assertFalse(facts.observedPaths.stream().anyMatch(path -> path.isAbsolute()));
	}

	@Test
	void allOffAllOnAndEverySingleGateLeaveStableCatalogIdentityUntouched() {
		List<?> itemCatalog = List.copyOf(ModItemIds.phaseTwo());
		List<String> expectedModuleNames = Arrays.stream(ModuleId.values()).map(ModuleId::serializedName).toList();
		List<ModuleGate> gates = new ArrayList<>();
		gates.add(ModuleGate.allDisabled());
		gates.add(ModuleGate.allEnabled());
		for (ModuleId module : ModuleId.values()) {
			gates.add(ModuleGate.of(Set.of(module)));
		}

		assertEquals(8, expectedModuleNames.size());
		assertEquals(5, itemCatalog.size());
		for (ModuleGate gate : gates) {
			for (ModuleId module : ModuleId.values()) {
				gate.isEnabled(module);
			}
			assertEquals(itemCatalog, ModItemIds.phaseTwo());
		}
	}

	@Test
	void configAndLoadResultDefensivelySnapshotMutableInputs() {
		EnumSet<ModuleId> source = EnumSet.of(ModuleId.PYTHON_TOOLS);
		DevHellConfig config = new DevHellConfig(
				1,
				true,
				DevHellConfig.Difficulty.RELAXED,
				false,
				true,
				false,
				DevHellConfig.LectureTuning.standard(),
				source,
				DevHellConfig.ScheduleMode.MANUAL,
				DevHellConfig.ScheduleMode.MANUAL
		);
		source.clear();
		assertEquals(Set.of(ModuleId.PYTHON_TOOLS), config.enabledModules());

		List<ConfigIssue> issues = new ArrayList<>();
		issues.add(new ConfigIssue("$.difficulty", "nightmare", "story|relaxed|standard|intense"));
		DevHellConfigLoader.LoadResult result = new DevHellConfigLoader.LoadResult(
				config,
				DevHellConfigLoader.SourceStatus.INVALID_DEFAULTED,
				issues,
				false
		);
		issues.clear();
		assertEquals(1, result.issues().size());
		assertThrows(UnsupportedOperationException.class,
				() -> result.issues().add(new ConfigIssue("$", "x", "y")));
		assertNotSame(source, config.enabledModules());
	}

	@Test
	void issueFieldsAreBoundedSanitizedAndNeverExposePrivateValues() {
		ConfigIssue issue = new ConfigIssue(
				"C:\\Users\\Real Person\\developers-hell.json",
				"authorization=Bearer top-secret https://private.example",
				"a".repeat(400)
		);

		assertTrue(issue.path().length() <= ConfigIssue.MAX_FIELD_LENGTH);
		assertTrue(issue.rejectedValue().length() <= ConfigIssue.MAX_FIELD_LENGTH);
		assertTrue(issue.expected().length() <= ConfigIssue.MAX_FIELD_LENGTH);
		assertPublicSafe(List.of(issue));
	}

	private static DevHellConfigLoader.LoadResult loadDocument(String document) {
		return DevHellConfigLoader.load(
				CONFIG_DIRECTORY,
				MemoryFileFacts.regular(document.getBytes(StandardCharsets.UTF_8))
		);
	}

	private static byte[] validBytes() {
		return validDocument().getBytes(StandardCharsets.UTF_8);
	}

	private static String validDocument() {
		return """
				{
				  "schemaVersion": 1,
				  "campaignEnabled": true,
				  "difficulty": "standard",
				  "bossBlockDamage": false,
				  "reducedFlashing": true,
				  "reducedEffects": false,
				  "lecture": {
				    "professorHealth": 120,
				    "missDamage": 4,
				    "maxAdds": 3,
				    "slideDeckTelegraphTicks": 100,
				    "quizTelegraphTicks": 160,
				    "attendanceTelegraphTicks": 120,
				    "vulnerabilityTicks": 80,
				    "actionBarUpdateTicks": 20,
				    "particleRefreshTicks": 10,
				    "particlesPerRefresh": 6,
				    "maxParticleBurstsPerEncounter": 24,
				    "maxTransitionSoundsPerEncounter": 8,
				    "arenaSearchRadius": 5
				  },
				  "metadataRouletteSchedule": "manual",
				  "threeDayDeadlineSchedule": "manual",
				  "modules": {
				    "graduation_anyfail": true,
				    "metadata_roulette": true,
				    "python_tools": true,
				    "codex_rich_kid_terminal": true,
				    "git_happens": true,
				    "stack_overflow_totem": true,
				    "rubber_duck_engineering": true,
				    "three_day_deadline": true
				  }
				}
				""";
	}

	private static void assertPath(DevHellConfigLoader.LoadResult result, String path) {
		assertTrue(result.issues().stream().anyMatch(issue -> issue.path().equals(path)),
				() -> "missing issue for " + path + ": " + result.issues());
	}

	private static void assertIssue(
			DevHellConfigLoader.LoadResult result,
			String path,
			String rejectedValue,
			String expectedFragment
	) {
		assertTrue(result.issues().stream().anyMatch(issue -> issue.path().equals(path)
				&& issue.rejectedValue().equals(rejectedValue)
				&& issue.expected().contains(expectedFragment)), () -> "missing issue: " + result.issues());
	}

	private static void assertPublicSafe(List<ConfigIssue> issues) {
		String joined = issues.toString().toLowerCase();
		for (String forbidden : List.of(
				"real person",
				"top-secret",
				"secret-token",
				"bearer",
				"private.example",
				"c:\\users",
				"https://",
				"authorization="
		)) {
			assertFalse(joined.contains(forbidden), () -> "issue leaked " + forbidden + ": " + issues);
		}
	}

	private static final class MemoryFileFacts implements DevHellConfigLoader.FileFacts {
		private boolean exists;
		private boolean symbolicLink;
		private boolean regularFile;
		private long reportedSize;
		private byte[] bytes;
		private IOException readFailure;
		private IOException writeFailure;
		private int readCount;
		private final List<Path> observedPaths = new ArrayList<>();
		private final List<Path> writePaths = new ArrayList<>();

		private static MemoryFileFacts missing() {
			MemoryFileFacts facts = new MemoryFileFacts();
			facts.bytes = new byte[0];
			return facts;
		}

		private static MemoryFileFacts regular(byte[] bytes) {
			MemoryFileFacts facts = new MemoryFileFacts();
			facts.exists = true;
			facts.regularFile = true;
			facts.reportedSize = bytes.length;
			facts.bytes = bytes.clone();
			return facts;
		}

		@Override
		public boolean exists(Path path) {
			observe(path);
			return exists;
		}

		@Override
		public boolean isSymbolicLink(Path path) {
			observe(path);
			return symbolicLink;
		}

		@Override
		public boolean isRegularFile(Path path) {
			observe(path);
			return regularFile;
		}

		@Override
		public long size(Path path) {
			observe(path);
			return reportedSize;
		}

		@Override
		public byte[] read(Path path) throws IOException {
			observe(path);
			readCount++;
			if (readFailure != null) {
				throw readFailure;
			}
			return bytes.clone();
		}

		@Override
		public void writeNew(Path path, byte[] content) throws IOException {
			observe(path);
			writePaths.add(path);
			if (writeFailure != null) {
				throw writeFailure;
			}
			exists = true;
			regularFile = true;
			bytes = content.clone();
			reportedSize = bytes.length;
		}

		private void observe(Path path) {
			observedPaths.add(path);
			assertEquals(CONFIG_PATH, path);
		}
	}
}
