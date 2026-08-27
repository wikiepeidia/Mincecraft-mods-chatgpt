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
	void schemaOneRoundTripsPassedRewardFieldsWithStableNames() {
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
	void schemaOneRoundTripsMaterializationReservationForReloadRecovery() {
		PlayerCampaignState reserved = new PlayerCampaignState(
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
				FALLBACK,
				null,
				0L,
				0L,
				0L
		);
		JsonObject root = encode(CampaignSavedData.createForTesting(Map.of(OWNER, reserved))).getAsJsonObject();
		JsonObject encodedPlayer = root.getAsJsonArray("players").get(0).getAsJsonObject();

		assertEquals(encodeUuid(FALLBACK), encodedPlayer.get("retake_fallback_reservation_uuid"));
		assertFalse(encodedPlayer.has("retake_fallback_entity_uuid"));
		CampaignSavedData decoded = decode(root);
		assertTrue(decoded.isWritableSchema());
		assertEquals(reserved, decoded.player(OWNER).orElseThrow());
		assertEquals(root, encode(decoded));
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
	void legacyPassedSaveDefaultsToMaterializedAndBackfillsStableRemoteIdentity() {
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
		encoded.remove("sheet_projection_pending");
		encoded.remove("remote_projection_pending");
		encoded.remove("remote_projection_uuid");

		CampaignSavedData decoded = decode(root);
		PlayerCampaignState migrated = decoded.player(OWNER).orElseThrow();
		assertFalse(migrated.sheetProjectionPending());
		assertFalse(migrated.remoteProjectionPending());
		assertEquals(PlayerCampaignState.legacyRemoteProjectionUuid(OWNER, DESK, 7),
				migrated.remoteProjectionUuid());
		assertTrue(encode(decoded).getAsJsonObject().getAsJsonArray("players").get(0).getAsJsonObject()
				.has("remote_projection_uuid"));
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
