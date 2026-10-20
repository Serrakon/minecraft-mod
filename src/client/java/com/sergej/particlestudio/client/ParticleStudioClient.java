package com.sergej.particlestudio.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sergej.particlestudio.ParticleStudio;
import com.sergej.particlestudio.client.studio.EffectRunner;
import com.sergej.particlestudio.client.studio.gui.ParticleStudioScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;

public class ParticleStudioClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ParticleStudio.id("studio"));
	public static final KeyMapping OPEN_STUDIO = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.particlestudio.open", InputConstants.KEY_J, CATEGORY));

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_STUDIO.consumeClick()) {
				if (client.gui.screen() == null && client.player != null) {
					client.gui.setScreen(new ParticleStudioScreen());
				}
			}

			EffectRunner.tick(client);
		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || EffectRunner.allowGameMessage(message));
	}
}
