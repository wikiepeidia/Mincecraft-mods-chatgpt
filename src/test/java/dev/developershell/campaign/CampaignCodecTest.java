package dev.developershell.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.developershell.campaign.CampaignSavedData.ReadDisposition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import org.junit.jupiter.api.Test;

final class CampaignCodecTest {
	private static final UUID OWNER = UUID.fromString("c0de0000-0000-4000-8000-000000000501");
	private static final UUID OTHER_OWNER = UUID.fromString("c0de0000-0000-4000-8000-000000000502");
	private static final UUID ENCOUNTER = UUID.fromString("c0de0000-0000-4000-8000-000000000511");
	private static final UUID PROFESSOR = UUID.fromString("c0de0000-0000-4000-8000-000000000521");
	private static final UUID FALLBACK = UUID.fromString("c0de0000-0000-4000-8000-000000000531");
	private static final BlockPos DESK = new BlockPos(11, 72, -9);
	private static final BlockPos RETRY = new BlockPos(11, 72, -12);

	@Test
	void schemaTwoRoundTripsPassedRewardFieldsWithStableNames() {
		PlayerCampaignState state = new PlayerCampaignState(
				OWNER,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				7,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				null,
				true,
				true,
				false,
				null,
				null,
				null,
				9_000L,
				6L,
				8_000L,
				true,
				true,
				ENCOUNTER
		);
		CampaignSavedData original = CampaignSavedData.createForTesting(Map.of(OWNER, state));

		JsonObject root = encode(original).getAsJsonObject();
		assertEquals(CampaignSavedData.SCHEMA_VERSION, root.get("schema").getAsInt());
		JsonObject encodedPlayer = root.getAsJsonArray("players").get(0).getAsJsonObject();
		assertEquals(encodeUuid(OWNER), encodedPlayer.get("map_key_uuid"));
		assertEquals("lecture_passed", encodedPlayer.get("chapter").getAsString());
		assertEquals("passed", encodedPlayer.get("lecture_status").getAsString());
		assertFalse(encodedPlayer.has("retake_encounter_uuid"));
		assertFalse(encodedPlayer.has("retake_fallback_reservation_uuid"));
		assertFalse(encodedPlayer.has("retake_fallback_entity_uuid"));
		assertEquals(6L, encodedPlayer.get("sheet_recovery_sequence").getAsLong());
		assertEquals(8_000L, encodedPlayer.get("remote_ready_notice_for_deadline_game_time").getAsLong());
		assertTrue(encodedPlayer.get("sheet_projection_pending").getAsBoolean());
		assertTrue(encodedPlayer.get("remote_projection_pending").getAsBoolean());
		assertFalse(encodedPlayer.get("legacy_remote_adoption_pending").getAsBoolean());
		assertEquals(encodeUuid(ENCOUNTER), encodedPlayer.get("remote_projection_uuid"));

		CampaignSavedData decoded = decode(root);
		assertEquals(ReadDisposition.WRITABLE, decoded.readDisposition());
		assertTrue(decoded.isWritableSchema());
		assertTrue(decoded.hasValidOwnerKeys());
		assertEquals(state, decoded.player(OWNER).orElseThrow());
		assertEquals(root, encode(decoded));
	}

	@Test
	void legacySchemaOneRetakeBackfillsOnlyMissingOptionalIdentity() {
		PlayerCampaignState state = new PlayerCampaignState(
				OWNER,
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.RETAKE_READY,
				3,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				null,
				false,
				false,
				true,
				ENCOUNTER,
				null,
				FALLBACK,
				0L,
				0L,
				0L
		);
		JsonObject root = encode(CampaignSavedData.createForTesting(Map.of(OWNER, state))).getAsJsonObject();
		root.addProperty("schema", 1);
		JsonObject encodedPlayer = root.getAsJsonArray("players").get(0).getAsJsonObject();
		encodedPlayer.remove("retake_encounter_uuid");

		CampaignSavedData decoded = decode(root);
		PlayerCampaignState migrated = decoded.player(OWNER).orElseThrow();
		assertTrue(decoded.isWritableSchema());
		assertTrue(migrated.retakeEntitled());
		assertEquals(OWNER, migrated.retakeKey().orElseThrow().ownerUuid());
		assertEquals(
				PlayerCampaignState.legacyRetakeEncounterUuid(OWNER, DESK, 3),
				migrated.retakeKey().orElseThrow().failedEncounterUuid()
		);
		assertEquals(3, migrated.attemptCount());
		assertEquals(FALLBACK, migrated.retakeFallbackEntityUuid());
		assertTrue(encode(decoded).getAsJsonObject().getAsJsonArray("players").get(0).getAsJsonObject()
				.has("retake_encounter_uuid"));
	}

	@Test
	void schemaTwoRoundTripsMaterializationReservationForReloadRecovery() {
		UUID remoteFallbackUuid = UUID.fromString("c0de0000-0000-4000-8000-000000000532");
		PlayerCampaignState.RewardFallbackRef sheetFallback = new PlayerCampaignState.RewardFallbackRef(
				FALLBACK, PlayerCampaignState.OVERWORLD_DIMENSION, RETRY, false);
		PlayerCampaignState.RewardFallbackRef remoteFallback = new PlayerCampaignState.RewardFallbackRef(
				remoteFallbackUuid, PlayerCampaignState.OVERWORLD_DIMENSION, DESK, true);
		PlayerCampaignState reserved = new PlayerCampaignState(
				OWNER,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				3,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				null,
				true,
				true,
				false,
				null,
				null,
				null,
				0L,
				0L,
				0L,
				true,
				false,
				ENCOUNTER,
				false,
				sheetFallback,
				remoteFallback
		);
		JsonObject root = encode(CampaignSavedData.createForTesting(Map.of(OWNER, reserved))).getAsJsonObject();
		JsonObject encodedPlayer = root.getAsJsonArray("players").get(0).getAsJsonObject();

		JsonObject encodedSheet = encodedPlayer.getAsJsonObject("sheet_fallback");
		assertEquals(encodeUuid(FALLBACK), encodedSheet.get("entity_uuid"));
		assertEquals(PlayerCampaignState.OVERWORLD_DIMENSION, encodedSheet.get("dimension").getAsString());
		assertEquals(BlockPos.CODEC.encodeStart(JsonOps.INSTANCE, RETRY).getOrThrow(),
				encodedSheet.get("position"));
		assertFalse(encodedSheet.get("materialized").getAsBoolean());
		JsonObject encodedRemote = encodedPlayer.getAsJsonObject("remote_fallback");
		assertEquals(encodeUuid(remoteFallbackUuid), encodedRemote.get("entity_uuid"));
		assertTrue(encodedRemote.get("materialized").getAsBoolean());
		CampaignSavedData decoded = decode(root);
		assertTrue(decoded.isWritableSchema());
		assertEquals(reserved, decoded.player(OWNER).orElseThrow());
		assertEquals(root, encode(decoded));

		JsonObject olderSchemaTwo = root.deepCopy();
		JsonObject olderPlayer = olderSchemaTwo.getAsJsonArray("players").get(0).getAsJsonObject();
		olderPlayer.remove("sheet_fallback");
		olderPlayer.remove("remote_fallback");
		PlayerCampaignState older = decode(olderSchemaTwo).player(OWNER).orElseThrow();
		assertEquals(null, older.sheetFallback());
		assertEquals(null, older.remoteFallback());
	}

	@Test
	void missingAndCorruptSchemaOneRemainExplicitReadOnlyAndPreserveRaw() {
		List<JsonElement> malformedDocuments = List.of(
				JsonParser.parseString("{\"players\":[]}"),
				JsonParser.parseString("""
						{\"schema\":1,\"players\":[{
						  \"map_key_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"lecture_status\":\"active\",\"attempt_count\":1,
						  \"desk_pos\":[11,72,-9],\"desk_facing\":\"north\",
						  \"retry_pos\":[11,72,-12],\"sheet_entitled\":false,
						  \"remote_issued\":false
						}]}"""),
				JsonParser.parseString("""
						{\"schema\":1,\"players\":[{
						  \"map_key_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"owner_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"lecture_status\":\"active\",\"attempt_count\":1,
						  \"desk_pos\":[11,72,-9],\"desk_facing\":\"north\",
						  \"retry_pos\":[11,72,-12],
						  \"encounter_uuid\":\"c0de0000-0000-4000-8000-000000000511\",
						  \"sheet_entitled\":false,\"remote_issued\":false
						}]}"""),
				JsonParser.parseString("""
						{\"schema\":1,\"players\":[{
						  \"map_key_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"owner_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"chapter\":\"pre_lecture\",\"lecture_status\":\"retake_ready\",\"attempt_count\":1,
						  \"desk_pos\":[11,72,-9],\"desk_facing\":\"north\",
						  \"retry_pos\":[11,72,-12],\"sheet_entitled\":false,
						  \"remote_issued\":false
						}]}"""),
				JsonParser.parseString("""
						{\"schema\":1,\"players\":[{
						  \"map_key_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"owner_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"chapter\":\"pre_lecture\",\"lecture_status\":\"ready\",\"attempt_count\":1,
						  \"desk_pos\":[11,72,-9],\"desk_facing\":\"north\",
						  \"retry_pos\":[11,72,-12],\"sheet_entitled\":false,
						  \"remote_issued\":false,\"retake_entitled\":true,
						  \"retake_encounter_uuid\":\"c0de0000-0000-4000-8000-000000000511\"
						}]}"""),
				JsonParser.parseString("""
						{\"schema\":1,\"players\":[{
						  \"map_key_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"owner_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"chapter\":\"lecture_passed\",\"lecture_status\":\"passed\",\"attempt_count\":1,
						  \"desk_pos\":[11,72,-9],\"desk_facing\":\"north\",
						  \"retry_pos\":[11,72,-12],\"sheet_entitled\":false,
						  \"remote_issued\":true
						}]}"""),
				JsonParser.parseString("""
						{\"schema\":1,\"players\":[{
						  \"map_key_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"owner_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"chapter\":\"pre_lecture\",\"lecture_status\":\"ready\",\"attempt_count\":1,
						  \"desk_pos\":[11,72,-9],\"desk_facing\":\"north\",
						  \"retry_pos\":[11,72,-12],
						  \"encounter_uuid\":\"c0de0000-0000-4000-8000-000000000511\",
						  \"professor_uuid\":\"c0de0000-0000-4000-8000-000000000521\",
						  \"sheet_entitled\":false,\"remote_issued\":false
						}]}"""),
				JsonParser.parseString("""
						{\"schema\":1,\"players\":[{
						  \"map_key_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"owner_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"chapter\":\"pre_lecture\",\"lecture_status\":\"ready\",\"attempt_count\":1,
						  \"desk_pos\":[11,72,-9],\"desk_facing\":\"north\",
						  \"retry_pos\":[11,72,-12],\"sheet_entitled\":false,
						  \"remote_issued\":false,\"sheet_projection_pending\":true
						}]}"""),
				JsonParser.parseString("""
						{\"schema\":1,\"players\":[{
						  \"map_key_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"owner_uuid\":\"c0de0000-0000-4000-8000-000000000501\",
						  \"chapter\":\"pre_lecture\",\"lecture_status\":\"ready\",\"attempt_count\":1,
						  \"desk_pos\":[11,72,-9],\"desk_facing\":\"north\",
						  \"retry_pos\":[11,72,-12],\"sheet_entitled\":false,
						  \"remote_issued\":false,\"remote_projection_pending\":true
						}]}""")
		);

		for (JsonElement malformed : malformedDocuments) {
			CampaignSavedData decoded = decode(malformed);
			assertEquals(ReadDisposition.CORRUPT_DATA, decoded.readDisposition());
			assertFalse(decoded.isWritableSchema());
			assertFalse(decoded.isDirty());
			assertEquals(malformed, encode(decoded));
			assertFalse(CampaignService.apply(decoded, startEvent(OWNER, ENCOUNTER, PROFESSOR), ignored -> {
				throw new AssertionError("read-only data dispatched an effect");
			}).accepted());
			assertFalse(decoded.isDirty());
		}
	}

	@Test
	void schemaOneRemoteFieldPresenceMigratesLegacyAndExplicitProjectionStates() {
		PlayerCampaignState state = new PlayerCampaignState(
				OWNER,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				7,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				null,
				true,
				true,
				false,
				null,
				null,
				null,
				9_000L,
				6L,
				8_000L
		);
		JsonObject root = encode(CampaignSavedData.createForTesting(Map.of(OWNER, state))).getAsJsonObject();
		JsonObject encoded = root.getAsJsonArray("players").get(0).getAsJsonObject();
		root.addProperty("schema", 1);
		encoded.remove("sheet_projection_pending");
		encoded.remove("remote_projection_pending");
		encoded.remove("remote_projection_uuid");
		encoded.remove("legacy_remote_adoption_pending");

		CampaignSavedData decoded = decode(root);
		PlayerCampaignState migrated = decoded.player(OWNER).orElseThrow();
		assertEquals(CampaignSavedData.SCHEMA_VERSION, decoded.schemaVersion());
		assertTrue(decoded.isDirty());
		assertFalse(migrated.sheetProjectionPending());
		assertTrue(migrated.remoteProjectionPending());
		assertTrue(migrated.legacyRemoteAdoptionPending());
		assertEquals(PlayerCampaignState.legacyRemoteProjectionUuid(OWNER, DESK, 7),
				migrated.remoteProjectionUuid());

		JsonObject migratedRoot = encode(decoded).getAsJsonObject();
		assertEquals(CampaignSavedData.SCHEMA_VERSION, migratedRoot.get("schema").getAsInt());
		assertTrue(migratedRoot.getAsJsonArray("players").get(0).getAsJsonObject()
				.get("legacy_remote_adoption_pending").getAsBoolean());

		JsonObject brokenResave = migratedRoot.deepCopy();
		brokenResave.addProperty("schema", 1);
		JsonObject brokenPlayer = brokenResave.getAsJsonArray("players").get(0).getAsJsonObject();
		brokenPlayer.addProperty("remote_projection_pending", false);
		brokenPlayer.remove("legacy_remote_adoption_pending");
		PlayerCampaignState repaired = decode(brokenResave).player(OWNER).orElseThrow();
		assertTrue(repaired.remoteProjectionPending());
		assertTrue(repaired.legacyRemoteAdoptionPending());

		JsonObject explicitProjection = encode(CampaignSavedData.createForTesting(Map.of(
				OWNER,
				new PlayerCampaignState(
						OWNER,
						PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
						PlayerCampaignState.LectureStatus.PASSED,
						7,
						PlayerCampaignState.OVERWORLD_DIMENSION,
						DESK,
						Direction.NORTH,
						RETRY,
						null,
						true,
						true,
						false,
						null,
						null,
						null,
						9_000L,
						6L,
						8_000L,
						false,
						false,
						ENCOUNTER
				)
		))).getAsJsonObject();
		explicitProjection.addProperty("schema", 1);
		explicitProjection.getAsJsonArray("players").get(0).getAsJsonObject()
				.remove("legacy_remote_adoption_pending");
		PlayerCampaignState explicit = decode(explicitProjection).player(OWNER).orElseThrow();
		assertFalse(explicit.remoteProjectionPending());
		assertFalse(explicit.legacyRemoteAdoptionPending());
		assertEquals(ENCOUNTER, explicit.remoteProjectionUuid());
	}

	@Test
	void recordConstructorRejectsUnreachableCrossFieldStates() {
		PlayerCampaignState.EncounterRef active = new PlayerCampaignState.EncounterRef(
				OWNER, ENCOUNTER, PROFESSOR, 1
		);
		assertThrows(IllegalArgumentException.class, () -> state(
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.ACTIVE, null, false, false, false, null
		));
		assertThrows(IllegalArgumentException.class, () -> state(
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.READY, active, false, false, false, null
		));
		assertThrows(IllegalArgumentException.class, () -> state(
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.RETAKE_READY, null, false, false, false, null
		));
		assertThrows(IllegalArgumentException.class, () -> state(
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.READY, null, false, false, true, ENCOUNTER
		));
		assertThrows(IllegalArgumentException.class, () -> state(
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED, null, false, true, false, null
		));
		assertThrows(IllegalArgumentException.class, () -> state(
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.READY, null, true, true, false, null
		));
		assertThrows(IllegalArgumentException.class, () -> new PlayerCampaignState(
				OWNER,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				0,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				null,
				true,
				true,
				false,
				null,
				null,
				null,
				0L,
				0L,
				0L
		));
		assertThrows(IllegalArgumentException.class, () -> new PlayerCampaignState(
				OWNER,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				1,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				null,
				true,
				true,
				false,
				null,
				null,
				null,
				0L,
				0L,
				0L,
				false,
				false,
				ENCOUNTER,
				true
		));
		PlayerCampaignState.RewardFallbackRef reservation = new PlayerCampaignState.RewardFallbackRef(
				FALLBACK, PlayerCampaignState.OVERWORLD_DIMENSION, RETRY, false);
		assertThrows(IllegalArgumentException.class, () -> new PlayerCampaignState(
				OWNER, PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED, 1,
				PlayerCampaignState.OVERWORLD_DIMENSION, DESK, Direction.NORTH, RETRY,
				null, true, true, false, null, null, null, 0L, 0L, 0L,
				false, false, ENCOUNTER, false, reservation, null
		));
		PlayerCampaignState.RewardFallbackRef materialized = new PlayerCampaignState.RewardFallbackRef(
				FALLBACK, PlayerCampaignState.OVERWORLD_DIMENSION, RETRY, true);
		assertThrows(IllegalArgumentException.class, () -> new PlayerCampaignState(
				OWNER, PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED, 1,
				PlayerCampaignState.OVERWORLD_DIMENSION, DESK, Direction.NORTH, RETRY,
				null, true, true, false, null, null, null, 0L, 0L, 0L,
				false, false, ENCOUNTER, false, materialized, materialized
		));
	}

	@Test
	void mapKeyMismatchIsVisibleAndReadOnly() {
		JsonObject root = encode(CampaignSavedData.createForTesting(Map.of(OWNER, readyState(OWNER)))).getAsJsonObject();
		root.getAsJsonArray("players").get(0).getAsJsonObject()
				.add("map_key_uuid", encodeUuid(OTHER_OWNER));

		CampaignSavedData decoded = decode(root);
		assertEquals(ReadDisposition.INVALID_OWNER_KEYS, decoded.readDisposition());
		assertFalse(decoded.hasValidOwnerKeys());
		assertFalse(decoded.isWritableSchema());
		assertEquals(OWNER, decoded.playerByMapKey(OTHER_OWNER).orElseThrow().ownerUuid());
		assertFalse(CampaignService.apply(decoded, startEvent(OWNER, ENCOUNTER, PROFESSOR), ignored -> {
			throw new AssertionError("invalid-owner data dispatched an effect");
		}).accepted());
		assertFalse(decoded.isDirty());
	}

	@Test
	void futureSchemaIsVisibleReadOnlyAndRoundTripsUnknownPayload() {
		JsonElement future = JsonParser.parseString("""
				{\"schema\":99,\"future_payload\":{\"unknown\":[1,2,3]},\"players\":[]}""");
		CampaignSavedData decoded = decode(future);

		assertEquals(99, decoded.schemaVersion());
		assertEquals(ReadDisposition.FUTURE_SCHEMA, decoded.readDisposition());
		assertFalse(decoded.isWritableSchema());
		assertTrue(decoded.player(OWNER).isEmpty());
		assertEquals(future, encode(decoded));
		assertFalse(CampaignService.apply(decoded, startEvent(OWNER, ENCOUNTER, PROFESSOR), ignored -> {
			throw new AssertionError("future data dispatched an effect");
		}).accepted());
		assertFalse(decoded.isDirty());
	}

	@Test
	void serviceCommitsBeforeEffectsAndNoOpWritesNothing() {
		CampaignSavedData data = CampaignSavedData.createForTesting(Map.of());
		CampaignEvent.Start start = startEvent(OWNER, ENCOUNTER, PROFESSOR);
		List<CampaignTransition.EffectIntent> effects = new ArrayList<>();

		CampaignTransition accepted = CampaignService.apply(data, start, effect -> {
			assertTrue(data.isDirty(), "state must be dirty before effects dispatch");
			assertEquals(PlayerCampaignState.LectureStatus.ACTIVE, data.player(OWNER).orElseThrow().status());
			effects.add(effect);
		});
		assertTrue(accepted.accepted());
		assertTrue(data.isDirty());
		assertEquals(accepted.intents(), effects);

		data.setDirty(false);
		effects.clear();
		CampaignTransition replay = CampaignService.apply(data, start, effects::add);
		assertFalse(replay.accepted());
		assertFalse(data.isDirty());
		assertTrue(effects.isEmpty());

		CampaignEvent.Start conflicting = startEvent(OTHER_OWNER, ENCOUNTER, PROFESSOR);
		CampaignTransition occupied = CampaignService.apply(data, conflicting, effects::add);
		assertFalse(occupied.accepted());
		assertEquals("desk_occupied", occupied.reason());
		assertFalse(data.isDirty());
		assertTrue(data.player(OTHER_OWNER).isEmpty());
	}

	@Test
	void activeReloadNormalizationPersistsOnceAndThenNoOps() {
		PlayerCampaignState active = activeState();
		CampaignSavedData data = decode(encode(CampaignSavedData.createForTesting(Map.of(OWNER, active))));
		List<CampaignTransition.EffectIntent> effects = new ArrayList<>();
		CampaignEvent.NormalizeReload reload = new CampaignEvent.NormalizeReload(OWNER, ENCOUNTER);

		CampaignTransition normalized = CampaignService.apply(data, reload, effect -> {
			assertTrue(data.isDirty());
			assertEquals(PlayerCampaignState.LectureStatus.RETAKE_READY, data.player(OWNER).orElseThrow().status());
			effects.add(effect);
		});
		assertTrue(normalized.accepted());
		assertEquals(normalized.intents(), effects);
		assertEquals(1, data.player(OWNER).orElseThrow().attemptCount());

		data.setDirty(false);
		effects.clear();
		CampaignTransition replay = CampaignService.apply(data, reload, effects::add);
		assertFalse(replay.accepted());
		assertFalse(data.isDirty());
		assertTrue(effects.isEmpty());
	}

	private static CampaignEvent.Start startEvent(UUID ownerUuid, UUID encounterUuid, UUID professorUuid) {
		return new CampaignEvent.Start(
				ownerUuid,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				encounterUuid,
				professorUuid
		);
	}

	private static PlayerCampaignState readyState(UUID ownerUuid) {
		return new PlayerCampaignState(
				ownerUuid,
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.READY,
				0,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				null,
				false,
				false,
				false,
				null,
				0L,
				0L,
				0L
		);
	}

	private static PlayerCampaignState activeState() {
		return new PlayerCampaignState(
				OWNER,
				PlayerCampaignState.CampaignChapter.PRE_LECTURE,
				PlayerCampaignState.LectureStatus.ACTIVE,
				1,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				new PlayerCampaignState.EncounterRef(OWNER, ENCOUNTER, PROFESSOR, 1),
				false,
				false,
				false,
				null,
				0L,
				0L,
				0L
		);
	}

	private static PlayerCampaignState state(
			PlayerCampaignState.CampaignChapter chapter,
			PlayerCampaignState.LectureStatus status,
			PlayerCampaignState.EncounterRef activeEncounter,
			boolean sheetEntitled,
			boolean remoteIssued,
			boolean retakeEntitled,
			UUID retakeEncounterUuid
	) {
		return new PlayerCampaignState(
				OWNER,
				chapter,
				status,
				1,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				DESK,
				Direction.NORTH,
				RETRY,
				activeEncounter,
				sheetEntitled,
				remoteIssued,
				retakeEntitled,
				retakeEncounterUuid,
				null,
				null,
				0L,
				0L,
				0L
		);
	}

	private static CampaignSavedData decode(JsonElement value) {
		return CampaignSavedData.TYPE.codec().parse(JsonOps.INSTANCE, value).getOrThrow();
	}

	private static JsonElement encode(CampaignSavedData data) {
		return CampaignSavedData.TYPE.codec().encodeStart(JsonOps.INSTANCE, data).getOrThrow();
	}

	private static JsonElement encodeUuid(UUID uuid) {
		return UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, uuid).getOrThrow();
	}
}
