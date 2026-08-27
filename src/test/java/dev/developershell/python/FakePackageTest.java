package dev.developershell.python;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

final class FakePackageTest {
	@Test void hasExactlyFourFinitePackages() { assertEquals(4, FakePackage.values().length); }
	@Test void numpyHasHasteEffect() { assertEquals(FakePackage.Effect.HASTE, FakePackage.NUMPY_OF_DESPAIR.effect()); }
	@Test void flaskHasSpeedEffect() { assertEquals(FakePackage.Effect.SPEED, FakePackage.FLASK_OVERFLOW.effect()); }
	@Test void djangoHasResistanceEffect() { assertEquals(FakePackage.Effect.RESISTANCE, FakePackage.DJANGO_UNCHAINED.effect()); }
	@Test void pandasHasJumpEffect() { assertEquals(FakePackage.Effect.JUMP_BOOST, FakePackage.PANDAS_IN_PRODUCTION.effect()); }
	@Test void everyPackageHasPositiveBoundedCostAndDuration() {
		for (FakePackage fakePackage : FakePackage.values()) {
			assertTrue(fakePackage.xpCost() > 0 && fakePackage.xpCost() <= 5);
			assertTrue(fakePackage.durationTicks() > 0 && fakePackage.durationTicks() <= 240);
		}
	}
	@Test void selectionWrapsForward() { assertEquals(FakePackage.NUMPY_OF_DESPAIR, FakePackage.fromIndex(4)); }
	@Test void selectionWrapsBackward() { assertEquals(FakePackage.PANDAS_IN_PRODUCTION, FakePackage.fromIndex(-1)); }
	@Test void idsAreCaseAndWhitespaceTolerant() { assertEquals(FakePackage.FLASK_OVERFLOW, FakePackage.fromId("  FLASK-OVERFLOW ")); }
	@Test void unknownIdIsRejected() { assertThrows(IllegalArgumentException.class, () -> FakePackage.fromId("real-pip")); }
	@Test void codecRoundTripsStableId() {
		var encoded = FakePackage.CODEC.encodeStart(JsonOps.INSTANCE, FakePackage.DJANGO_UNCHAINED).getOrThrow();
		assertEquals(FakePackage.DJANGO_UNCHAINED, FakePackage.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
	}
}
