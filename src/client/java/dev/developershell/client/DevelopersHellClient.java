package dev.developershell.client;

import dev.developershell.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.VindicatorRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;

public final class DevelopersHellClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.PROFESSOR, VindicatorRenderer::new);
		EntityRenderers.register(ModEntities.HOMEWORK_ADD, ZombieRenderer::new);
	}
}
