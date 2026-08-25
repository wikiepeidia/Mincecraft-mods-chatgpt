package dev.developershell.gametest;

import dev.developershell.DevelopersHell;
import dev.developershell.registry.ModItems;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

public final class FoundationGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void foundationTokenIsRegistered(GameTestHelper context) {
		Identifier actual = BuiltInRegistries.ITEM.getKey(ModItems.FOUNDATION_TOKEN);
		Identifier expected = DevelopersHell.id("foundation_token");
		context.assertValueEqual(actual, expected, "foundation token registry key");
		context.succeed();
	}

	@Override
	public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
		method.invoke(this, context);
	}
}
