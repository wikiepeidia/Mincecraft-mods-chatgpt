package dev.developershell.python;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Arrays;
import java.util.Locale;

/** Four finite joke packages. Nothing here invokes Python or any external process. */
public enum FakePackage {
	NUMPY_OF_DESPAIR("numpy-of-despair", 2, 120, Effect.HASTE),
	FLASK_OVERFLOW("flask-overflow", 3, 160, Effect.SPEED),
	DJANGO_UNCHAINED("django-unchained", 4, 200, Effect.RESISTANCE),
	PANDAS_IN_PRODUCTION("pandas-in-production", 5, 240, Effect.JUMP_BOOST);

	public static final Codec<FakePackage> CODEC = Codec.STRING.comapFlatMap(
			value -> {
				try {
					return DataResult.success(fromId(value));
				}
				catch (IllegalArgumentException exception) {
					return DataResult.error(exception::getMessage);
				}
			},
			FakePackage::id
	);

	private final String id;
	private final int xpCost;
	private final int durationTicks;
	private final Effect effect;

	FakePackage(String id, int xpCost, int durationTicks, Effect effect) {
		if (xpCost < 0 || durationTicks <= 0) {
			throw new IllegalArgumentException("Package cost and duration must be bounded and positive");
		}
		this.id = id;
		this.xpCost = xpCost;
		this.durationTicks = durationTicks;
		this.effect = effect;
	}

	public String id() {
		return id;
	}

	public int xpCost() {
		return xpCost;
	}

	public int durationTicks() {
		return durationTicks;
	}

	public Effect effect() {
		return effect;
	}

	public static FakePackage fromIndex(int index) {
		FakePackage[] packages = values();
		return packages[Math.floorMod(index, packages.length)];
	}

	public static FakePackage fromId(String id) {
		if (id == null) {
			throw new IllegalArgumentException("Unknown fake package: null");
		}
		String normalized = id.trim().toLowerCase(Locale.ROOT);
		return Arrays.stream(values())
				.filter(candidate -> candidate.id.equals(normalized))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown fake package: " + id));
	}

	public enum Effect {
		HASTE,
		SPEED,
		RESISTANCE,
		JUMP_BOOST
	}
}
