package dev.developershell.config;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.developershell.module.ModuleId;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;

/** Strict fixed-child adapter that accepts one complete document or the complete defaults. */
public final class DevHellConfigLoader {
	public static final String FILE_NAME = "developers-hell.json";
	public static final int MAX_FILE_BYTES = 65_536;
	private static final List<String> ROOT_PROPERTIES = List.of(
			"schemaVersion",
			"campaignEnabled",
			"difficulty",
			"bossBlockDamage",
			"reducedFlashing",
			"reducedEffects",
			"lecture",
			"metadataRouletteSchedule",
			"threeDayDeadlineSchedule",
			"modules"
	);
	private static final List<String> LECTURE_PROPERTIES = List.of(
			"professorHealth",
			"missDamage",
			"maxAdds",
			"slideDeckTelegraphTicks",
			"quizTelegraphTicks",
			"attendanceTelegraphTicks",
			"vulnerabilityTicks",
			"actionBarUpdateTicks",
			"particleRefreshTicks",
			"particlesPerRefresh",
			"maxParticleBurstsPerEncounter",
			"maxTransitionSoundsPerEncounter",
			"arenaSearchRadius"
	);
	private static final Map<String, ModuleId> MODULES_BY_NAME = moduleNames();
	private static final byte[] DEFAULT_DOCUMENT = defaultDocument().getBytes(StandardCharsets.UTF_8);
	private static final FileFacts SYSTEM_FILE_FACTS = new SystemFileFacts();

	public static LoadResult loadFromConfigDirectory() {
		return load(FabricLoader.getInstance().getConfigDir(), SYSTEM_FILE_FACTS);
	}

	/**
	 * Loads only {@code developers-hell.json} beneath the supplied config directory.
	 * FileFacts is an explicit seam for deterministic Windows guard and I/O tests.
	 */
	public static LoadResult load(Path configDirectory, FileFacts facts) {
		Objects.requireNonNull(configDirectory, "configDirectory");
		Objects.requireNonNull(facts, "facts");
		Path configPath = configDirectory.normalize().resolve(FILE_NAME).normalize();

		if (!facts.exists(configPath)) {
			return missingFile(configPath, facts);
		}
		if (facts.isSymbolicLink(configPath)) {
			return invalidFile(new ConfigIssue(
					"$.file",
					"<symbolic-link>",
					"regular file at the fixed config child"
			));
		}
		if (!facts.isRegularFile(configPath)) {
			return invalidFile(new ConfigIssue(
					"$.file",
					"<non-regular>",
					"regular file at the fixed config child"
			));
		}

		long reportedSize;
		try {
			reportedSize = facts.size(configPath);
		}
		catch (IOException exception) {
			return invalidFile(new ConfigIssue("$.file", "<metadata-failed>", "readable local JSON"));
		}
		if (reportedSize < 0L || reportedSize > MAX_FILE_BYTES) {
			return invalidFile(oversizeIssue());
		}

		byte[] bytes;
		try {
			bytes = Objects.requireNonNull(facts.read(configPath), "read bytes");
		}
		catch (IOException | RuntimeException exception) {
			return invalidFile(new ConfigIssue("$.file", "<read-failed>", "readable local JSON"));
		}
		if (bytes.length > MAX_FILE_BYTES) {
			return invalidFile(oversizeIssue());
		}
		return parse(new String(bytes, StandardCharsets.UTF_8));
	}

	private static LoadResult missingFile(Path configPath, FileFacts facts) {
		List<ConfigIssue> issues = new ArrayList<>();
		boolean written = false;
		try {
			facts.writeNew(configPath, DEFAULT_DOCUMENT.clone());
			written = true;
		}
		catch (IOException | RuntimeException exception) {
			issues.add(new ConfigIssue("$.file", "<write-failed>", "optional default template"));
		}
		return new LoadResult(DevHellConfig.defaults(), SourceStatus.MISSING_DEFAULT, issues, written);
	}

	private static LoadResult parse(String document) {
		List<ConfigIssue> issues = new ArrayList<>();
		JsonNode root;
		try (JsonReader reader = new JsonReader(new StringReader(document))) {
			reader.setStrictness(Strictness.STRICT);
			root = readNode(reader, "$", issues);
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				issues.add(new ConfigIssue("$", "<trailing-data>", "one schema-v1 JSON object"));
			}
		}
		catch (IOException | IllegalStateException exception) {
			issues.add(new ConfigIssue("$", "<malformed>", "strict schema-v1 JSON document"));
			return invalidFile(issues);
		}

		if (!(root instanceof ObjectNode object)) {
			issues.add(new ConfigIssue("$", describe(root), "schema-v1 JSON object"));
			return invalidFile(issues);
		}
		DevHellConfig candidate = validate(object, issues);
		if (!issues.isEmpty() || candidate == null) {
			return invalidFile(issues);
		}
		return new LoadResult(candidate, SourceStatus.VALID, List.of(), false);
	}

	private static DevHellConfig validate(ObjectNode root, List<ConfigIssue> issues) {
		rejectUnknown(root, ROOT_PROPERTIES, "$", "known schema-v1 property", issues);
		Integer schemaVersion = requiredInteger(root, "schemaVersion", "$", 1, 1, issues);
		Boolean campaignEnabled = requiredBoolean(root, "campaignEnabled", "$", issues);
		DevHellConfig.Difficulty difficulty = requiredEnum(
				root,
				"difficulty",
				"$",
				"story|relaxed|standard|intense",
				DevHellConfig.Difficulty::fromSerializedName,
				issues
		);
		Boolean bossBlockDamage = requiredBoolean(root, "bossBlockDamage", "$", issues);
		Boolean reducedFlashing = requiredBoolean(root, "reducedFlashing", "$", issues);
		Boolean reducedEffects = requiredBoolean(root, "reducedEffects", "$", issues);
		ObjectNode lectureNode = requiredObject(root, "lecture", "$", issues);
		DevHellConfig.LectureTuning lecture = validateLecture(lectureNode, issues);
		DevHellConfig.ScheduleMode metadataSchedule = requiredEnum(
				root,
				"metadataRouletteSchedule",
				"$",
				"manual|automatic",
				DevHellConfig.ScheduleMode::fromSerializedName,
				issues
		);
		DevHellConfig.ScheduleMode deadlineSchedule = requiredEnum(
				root,
				"threeDayDeadlineSchedule",
				"$",
				"manual|automatic",
				DevHellConfig.ScheduleMode::fromSerializedName,
				issues
		);
		ObjectNode modulesNode = requiredObject(root, "modules", "$", issues);
		Set<ModuleId> enabledModules = validateModules(modulesNode, issues);

		if (!issues.isEmpty()) {
			return null;
		}
		return new DevHellConfig(
				schemaVersion,
				campaignEnabled,
				difficulty,
				bossBlockDamage,
				reducedFlashing,
				reducedEffects,
				lecture,
				enabledModules,
				metadataSchedule,
				deadlineSchedule
		);
	}

	private static DevHellConfig.LectureTuning validateLecture(
			ObjectNode lecture,
			List<ConfigIssue> issues
	) {
		if (lecture == null) {
			return null;
		}
		rejectUnknown(lecture, LECTURE_PROPERTIES, "$.lecture", "known lecture tuning property", issues);
		Integer professorHealth = requiredInteger(lecture, "professorHealth", "$.lecture", 81, 400, issues);
		Integer missDamage = requiredInteger(lecture, "missDamage", "$.lecture", 1, 20, issues);
		Integer maxAdds = requiredInteger(lecture, "maxAdds", "$.lecture", 0, 8, issues);
		Integer slideTicks = requiredInteger(lecture, "slideDeckTelegraphTicks", "$.lecture", 60, 200, issues);
		Integer quizTicks = requiredInteger(lecture, "quizTelegraphTicks", "$.lecture", 80, 300, issues);
		Integer attendanceTicks = requiredInteger(
				lecture,
				"attendanceTelegraphTicks",
				"$.lecture",
				80,
				240,
				issues
		);
		Integer vulnerabilityTicks = requiredInteger(lecture, "vulnerabilityTicks", "$.lecture", 20, 200, issues);
		Integer actionTicks = requiredInteger(lecture, "actionBarUpdateTicks", "$.lecture", 20, 20, issues);
		Integer particleTicks = requiredInteger(lecture, "particleRefreshTicks", "$.lecture", 4, 20, issues);
		Integer particles = requiredInteger(lecture, "particlesPerRefresh", "$.lecture", 1, 12, issues);
		Integer particleBursts = requiredInteger(
				lecture,
				"maxParticleBurstsPerEncounter",
				"$.lecture",
				1,
				40,
				issues
		);
		Integer sounds = requiredInteger(
				lecture,
				"maxTransitionSoundsPerEncounter",
				"$.lecture",
				1,
				12,
				issues
		);
		Integer searchRadius = requiredInteger(lecture, "arenaSearchRadius", "$.lecture", 1, 8, issues);
		if (Arrays.asList(
				professorHealth,
				missDamage,
				maxAdds,
				slideTicks,
				quizTicks,
				attendanceTicks,
				vulnerabilityTicks,
				actionTicks,
				particleTicks,
				particles,
				particleBursts,
				sounds,
				searchRadius
		).stream().anyMatch(Objects::isNull)) {
			return null;
		}
		return new DevHellConfig.LectureTuning(
				professorHealth,
				missDamage,
				maxAdds,
				slideTicks,
				quizTicks,
				attendanceTicks,
				vulnerabilityTicks,
				actionTicks,
				particleTicks,
				particles,
				particleBursts,
				sounds,
				searchRadius
		);
	}

	private static Set<ModuleId> validateModules(ObjectNode modules, List<ConfigIssue> issues) {
		EnumSet<ModuleId> enabled = EnumSet.noneOf(ModuleId.class);
		if (modules == null) {
			return enabled;
		}
		rejectUnknown(
				modules,
				List.copyOf(MODULES_BY_NAME.keySet()),
				"$.modules",
				"known module name",
				issues
		);
		for (ModuleId module : ModuleId.values()) {
			Boolean moduleEnabled = requiredBoolean(modules, module.serializedName(), "$.modules", issues);
			if (Boolean.TRUE.equals(moduleEnabled)) {
				enabled.add(module);
			}
		}
		return enabled;
	}

	private static ObjectNode requiredObject(
			ObjectNode parent,
			String name,
			String parentPath,
			List<ConfigIssue> issues
	) {
		JsonNode node = requiredNode(parent, name, parentPath, "object", issues);
		if (node == null) {
			return null;
		}
		if (!(node instanceof ObjectNode object)) {
			issues.add(new ConfigIssue(childPath(parentPath, name), describe(node), "object"));
			return null;
		}
		return object;
	}

	private static Boolean requiredBoolean(
			ObjectNode parent,
			String name,
			String parentPath,
			List<ConfigIssue> issues
	) {
		JsonNode node = requiredNode(parent, name, parentPath, "boolean", issues);
		if (node == null) {
			return null;
		}
		if (node instanceof ScalarNode scalar && scalar.type == ScalarType.BOOLEAN) {
			return Boolean.valueOf(scalar.value);
		}
		issues.add(new ConfigIssue(childPath(parentPath, name), describe(node), "boolean"));
		return null;
	}

	private static Integer requiredInteger(
			ObjectNode parent,
			String name,
			String parentPath,
			int minimum,
			int maximum,
			List<ConfigIssue> issues
	) {
		String expected = minimum == maximum ? "integer " + minimum : "integer " + minimum + ".." + maximum;
		JsonNode node = requiredNode(parent, name, parentPath, expected, issues);
		if (node == null) {
			return null;
		}
		String path = childPath(parentPath, name);
		if (!(node instanceof ScalarNode scalar) || scalar.type != ScalarType.NUMBER) {
			issues.add(new ConfigIssue(path, describe(node), expected));
			return null;
		}
		if (!scalar.value.matches("-?(?:0|[1-9][0-9]*)")) {
			issues.add(new ConfigIssue(path, "<invalid-number>", expected));
			return null;
		}
		long value;
		try {
			value = Long.parseLong(scalar.value);
		}
		catch (NumberFormatException exception) {
			issues.add(new ConfigIssue(path, "<invalid-number>", expected));
			return null;
		}
		if (value < minimum) {
			issues.add(new ConfigIssue(path, "<below-minimum>", expected));
			return null;
		}
		if (value > maximum) {
			issues.add(new ConfigIssue(path, "<above-maximum>", expected));
			return null;
		}
		return (int) value;
	}

	private static <T> T requiredEnum(
			ObjectNode parent,
			String name,
			String parentPath,
			String expected,
			Function<String, T> parser,
			List<ConfigIssue> issues
	) {
		JsonNode node = requiredNode(parent, name, parentPath, expected, issues);
		if (node == null) {
			return null;
		}
		String path = childPath(parentPath, name);
		if (!(node instanceof ScalarNode scalar) || scalar.type != ScalarType.STRING) {
			issues.add(new ConfigIssue(path, describe(node), expected));
			return null;
		}
		T parsed = parser.apply(scalar.value);
		if (parsed == null) {
			issues.add(new ConfigIssue(path, "<invalid-enum>", expected));
		}
		return parsed;
	}

	private static JsonNode requiredNode(
			ObjectNode parent,
			String name,
			String parentPath,
			String expected,
			List<ConfigIssue> issues
	) {
		JsonNode node = parent.values.get(name);
		if (node == null) {
			issues.add(new ConfigIssue(childPath(parentPath, name), "<missing>", expected));
		}
		return node;
	}

	private static void rejectUnknown(
			ObjectNode object,
			List<String> expectedNames,
			String path,
			String expected,
			List<ConfigIssue> issues
	) {
		Set<String> expectedSet = Set.copyOf(expectedNames);
		for (String name : object.values.keySet()) {
			if (!expectedSet.contains(name)) {
				issues.add(new ConfigIssue(path, "<unknown-property>", expected));
			}
		}
	}

	private static JsonNode readNode(JsonReader reader, String path, List<ConfigIssue> issues) throws IOException {
		JsonToken token = reader.peek();
		return switch (token) {
			case BEGIN_OBJECT -> readObject(reader, path, issues);
			case BEGIN_ARRAY -> readArray(reader, path, issues);
			case STRING -> new ScalarNode(ScalarType.STRING, reader.nextString());
			case NUMBER -> new ScalarNode(ScalarType.NUMBER, reader.nextString());
			case BOOLEAN -> new ScalarNode(ScalarType.BOOLEAN, Boolean.toString(reader.nextBoolean()));
			case NULL -> {
				reader.nextNull();
				yield NullNode.INSTANCE;
			}
			default -> throw new IOException("unexpected JSON token");
		};
	}

	private static ObjectNode readObject(JsonReader reader, String path, List<ConfigIssue> issues) throws IOException {
		reader.beginObject();
		Map<String, JsonNode> values = new LinkedHashMap<>();
		while (reader.hasNext()) {
			String name = reader.nextName();
			String propertyPath = childPath(path, name);
			JsonNode value = readNode(reader, propertyPath, issues);
			if (values.containsKey(name)) {
				issues.add(new ConfigIssue(propertyPath, "<duplicate>", "property appears exactly once"));
			}
			else {
				values.put(name, value);
			}
		}
		reader.endObject();
		return new ObjectNode(values);
	}

	private static ArrayNode readArray(JsonReader reader, String path, List<ConfigIssue> issues) throws IOException {
		reader.beginArray();
		int values = 0;
		while (reader.hasNext()) {
			readNode(reader, path, issues);
			values++;
		}
		reader.endArray();
		return new ArrayNode(values);
	}

	private static String childPath(String parent, String child) {
		return parent + "." + child;
	}

	private static String describe(JsonNode node) {
		if (node instanceof ScalarNode scalar) {
			return switch (scalar.type) {
				case STRING -> "<string>";
				case NUMBER -> "<number>";
				case BOOLEAN -> "<boolean>";
			};
		}
		if (node instanceof ObjectNode) {
			return "<object>";
		}
		if (node instanceof ArrayNode) {
			return "<array>";
		}
		return "<null>";
	}

	private static ConfigIssue oversizeIssue() {
		return new ConfigIssue("$.file", "<oversize>", "at most 65536 UTF-8 bytes");
	}

	private static LoadResult invalidFile(ConfigIssue issue) {
		return invalidFile(List.of(issue));
	}

	private static LoadResult invalidFile(List<ConfigIssue> issues) {
		return new LoadResult(DevHellConfig.defaults(), SourceStatus.INVALID_DEFAULTED, issues, false);
	}

	private static Map<String, ModuleId> moduleNames() {
		Map<String, ModuleId> modules = new LinkedHashMap<>();
		for (ModuleId module : ModuleId.values()) {
			modules.put(module.serializedName(), module);
		}
		return Collections.unmodifiableMap(modules);
	}

	private static String defaultDocument() {
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

	public enum SourceStatus {
		VALID("valid"),
		MISSING_DEFAULT("missing-default"),
		INVALID_DEFAULTED("invalid-defaulted");

		private final String serializedName;

		SourceStatus(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return serializedName;
		}
	}

	public record LoadResult(
			DevHellConfig config,
			SourceStatus sourceStatus,
			List<ConfigIssue> issues,
			boolean defaultTemplateWritten
	) {
		public LoadResult {
			config = Objects.requireNonNull(config, "config");
			sourceStatus = Objects.requireNonNull(sourceStatus, "sourceStatus");
			issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
		}
	}

	public interface FileFacts {
		boolean exists(Path path);

		boolean isSymbolicLink(Path path);

		boolean isRegularFile(Path path);

		long size(Path path) throws IOException;

		byte[] read(Path path) throws IOException;

		void writeNew(Path path, byte[] content) throws IOException;
	}

	private sealed interface JsonNode permits ObjectNode, ScalarNode, ArrayNode, NullNode {
	}

	private record ObjectNode(Map<String, JsonNode> values) implements JsonNode {
		private ObjectNode {
			values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
		}
	}

	private record ScalarNode(ScalarType type, String value) implements JsonNode {
	}

	private record ArrayNode(int size) implements JsonNode {
	}

	private enum NullNode implements JsonNode {
		INSTANCE
	}

	private enum ScalarType {
		STRING,
		NUMBER,
		BOOLEAN
	}

	private static final class SystemFileFacts implements FileFacts {
		@Override
		public boolean exists(Path path) {
			return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
		}

		@Override
		public boolean isSymbolicLink(Path path) {
			return Files.isSymbolicLink(path);
		}

		@Override
		public boolean isRegularFile(Path path) {
			return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
		}

		@Override
		public long size(Path path) throws IOException {
			return Files.size(path);
		}

		@Override
		public byte[] read(Path path) throws IOException {
			try (InputStream stream = Files.newInputStream(path, StandardOpenOption.READ)) {
				return stream.readNBytes(MAX_FILE_BYTES + 1);
			}
		}

		@Override
		public void writeNew(Path path, byte[] content) throws IOException {
			Path parent = Objects.requireNonNull(path.getParent(), "config parent");
			Files.createDirectories(parent);
			Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		}
	}

	private DevHellConfigLoader() {
	}
}
