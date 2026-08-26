package dev.developershell.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.developershell.campaign.CampaignEvent;
import dev.developershell.campaign.CampaignReducer;
import dev.developershell.campaign.CampaignTransition;
import dev.developershell.campaign.PlayerCampaignState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

final class RemoteCooldownTest {
	private static final UUID OWNER = UUID.fromString("4bf47b9d-490b-4eab-b913-4d1792b945f7");
	private static final UUID OTHER_OWNER = UUID.fromString("97f49658-4e50-4145-89fc-c68a657a5b15");

	@Test
	void exactDeadlineAndCeilingSecondsUseServerTickArithmetic() {
		assertEquals(400, InfiniteSlidesRemoteItem.COOLDOWN_TICKS);
		assertEquals(400L, InfiniteSlidesRemoteItem.cooldownDeadline(0L));
		assertEquals(1_634L, InfiniteSlidesRemoteItem.cooldownDeadline(1_234L));
		assertThrows(
				ArithmeticException.class,
				() -> InfiniteSlidesRemoteItem.cooldownDeadline(Long.MAX_VALUE - 399L)
		);

		long deadline = 1_400L;
		assertEquals(20, InfiniteSlidesRemoteItem.remainingSeconds(deadline, 1_000L));
		assertEquals(20, InfiniteSlidesRemoteItem.remainingSeconds(deadline, 1_001L));
		assertEquals(19, InfiniteSlidesRemoteItem.remainingSeconds(deadline, 1_020L));
		assertEquals(1, InfiniteSlidesRemoteItem.remainingSeconds(deadline, 1_399L));
		assertEquals(0, InfiniteSlidesRemoteItem.remainingSeconds(deadline, deadline));
		assertEquals(0, InfiniteSlidesRemoteItem.remainingSeconds(deadline, deadline + 1L));
	}

	@Test
	void overlayRestoreIsClampedAndReadyNoticeIsOneExactDeadlineEdge() {
		assertEquals(400, InfiniteSlidesRemoteItem.restoredOverlayTicks(1_500L, 1_000L));
		assertEquals(399, InfiniteSlidesRemoteItem.restoredOverlayTicks(1_399L, 1_000L));
		assertEquals(1, InfiniteSlidesRemoteItem.restoredOverlayTicks(1_001L, 1_000L));
		assertEquals(0, InfiniteSlidesRemoteItem.restoredOverlayTicks(1_000L, 1_000L));
		assertEquals(0, InfiniteSlidesRemoteItem.restoredOverlayTicks(999L, 1_000L));

		assertFalse(InfiniteSlidesRemoteItem.readyNoticeDue(0L, 0L, 10_000L));
		assertFalse(InfiniteSlidesRemoteItem.readyNoticeDue(1_400L, 0L, 1_399L));
		assertTrue(InfiniteSlidesRemoteItem.readyNoticeDue(1_400L, 0L, 1_400L));
		assertTrue(InfiniteSlidesRemoteItem.readyNoticeDue(1_400L, 1_000L, 1_401L));
		assertFalse(InfiniteSlidesRemoteItem.readyNoticeDue(1_400L, 1_400L, 1_400L));
		assertFalse(InfiniteSlidesRemoteItem.readyNoticeDue(1_400L, 1_500L, 1_500L));
	}

	@Test
	void acceptedDeadlineAndReadyEventsAreOwnerBoundAndReplaySafe() {
		PlayerCampaignState passed = passedState();
		long observed = 1_000L;
		long deadline = InfiniteSlidesRemoteItem.cooldownDeadline(observed);
		CampaignEvent.StartRemoteCooldown start =
				new CampaignEvent.StartRemoteCooldown(OWNER, observed, deadline);

		CampaignTransition accepted = CampaignReducer.reduce(Optional.of(passed), start);
		assertTrue(accepted.accepted());
		assertEquals("remote_cooldown_started", accepted.reason());
		PlayerCampaignState coolingDown = accepted.nextState().orElseThrow();
		assertEquals(deadline, coolingDown.remoteCooldownUntilGameTime());
		assertEquals(0L, coolingDown.remoteReadyNoticeForDeadlineGameTime());

		assertNoOp(CampaignReducer.reduce(accepted.nextState(), start), "remote_on_cooldown");
		assertNoOp(
				CampaignReducer.reduce(
						accepted.nextState(),
						new CampaignEvent.StartRemoteCooldown(OTHER_OWNER, observed, deadline)
				),
				"wrong_owner"
		);
		assertNoOp(
				CampaignReducer.reduce(
						accepted.nextState(),
						new CampaignEvent.RemoteReadyNotice(OWNER, deadline - 1L, deadline)
				),
				"stale_cooldown_deadline"
		);
		assertNoOp(
				CampaignReducer.reduce(
						accepted.nextState(),
						new CampaignEvent.RemoteReadyNotice(OWNER, deadline, deadline - 1L)
				),
				"remote_not_ready"
		);

		CampaignEvent.RemoteReadyNotice ready =
				new CampaignEvent.RemoteReadyNotice(OWNER, deadline, deadline);
		CampaignTransition noticed = CampaignReducer.reduce(accepted.nextState(), ready);
		assertTrue(noticed.accepted());
		assertEquals(deadline, noticed.nextState().orElseThrow().remoteReadyNoticeForDeadlineGameTime());
		assertNoOp(CampaignReducer.reduce(noticed.nextState(), ready), "remote_ready_already_noticed");
	}

	@Test
	void slideCeilingsStayExplicitAndSmall() {
		assertEquals(6.0D, InfiniteSlidesRemoteItem.EFFECT_RANGE_BLOCKS);
		assertEquals(6, InfiniteSlidesRemoteItem.MAX_TARGETS);
		assertTrue(InfiniteSlidesRemoteItem.HORIZONTAL_IMPULSE > 0.0D);
		assertTrue(InfiniteSlidesRemoteItem.HORIZONTAL_IMPULSE <= 1.0D);
		assertTrue(InfiniteSlidesRemoteItem.VERTICAL_IMPULSE >= 0.0D);
		assertTrue(InfiniteSlidesRemoteItem.VERTICAL_IMPULSE <= 0.25D);
		assertTrue(InfiniteSlidesRemoteItem.MAX_CUE_PARTICLES > 0);
		assertTrue(InfiniteSlidesRemoteItem.MAX_CUE_PARTICLES <= 16);
	}

	private static PlayerCampaignState passedState() {
		return new PlayerCampaignState(
				OWNER,
				PlayerCampaignState.CampaignChapter.LECTURE_PASSED,
				PlayerCampaignState.LectureStatus.PASSED,
				1,
				PlayerCampaignState.OVERWORLD_DIMENSION,
				new BlockPos(0, 64, 0),
				Direction.NORTH,
				new BlockPos(0, 64, 2),
				null,
				true,
				true,
				false,
				null,
				0L
		);
	}

	private static void assertNoOp(CampaignTransition transition, String reason) {
		assertFalse(transition.accepted());
		assertEquals(reason, transition.reason());
		assertTrue(transition.intents().isEmpty());
	}
}
