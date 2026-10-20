package com.sergej.particlestudio.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sergej.particlestudio.ParticleStudio;
import com.sergej.particlestudio.client.studio.EffectRunner;
import com.sergej.particlestudio.client.studio.gui.ParticleStudioScreen;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ParticleStudioClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ParticleStudio.id("studio"));
	public static final KeyMapping OPEN_STUDIO = key("open", InputConstants.KEY_J);
	// Быстрые действия без открытия панели. По умолчанию не назначены, чтобы не конфликтовать с другими модами.
	public static final KeyMapping PREVIEW = key("preview", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping RUN = key("run", InputConstants.UNKNOWN.getValue());
	public static final KeyMapping TOGGLE_REPEAT = key("toggle_repeat", InputConstants.UNKNOWN.getValue());

	/** Все клавиши мода — в этом порядке они показаны в настройках. */
	public static final List<KeyMapping> KEYS = List.of(OPEN_STUDIO, PREVIEW, RUN, TOGGLE_REPEAT);

	private static KeyMapping key(String name, int keysym) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + ParticleStudio.MOD_ID + "." + name, keysym, CATEGORY));
	}

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			handleKeys(client);
			EffectRunner.tick(client);
		});
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || EffectRunner.allowGameMessage(message));
	}

	private static void handleKeys(Minecraft client) {
		while (OPEN_STUDIO.consumeClick()) {
			if (client.gui.screen() == null && client.player != null) {
				client.gui.setScreen(new ParticleStudioScreen());
			}
		}

		while (PREVIEW.consumeClick()) {
			EffectRunner.preview(client);
		}

		while (RUN.consumeClick()) {
			EffectRunner.execute(client);
		}

		while (TOGGLE_REPEAT.consumeClick()) {
			EffectRunner.RepeatMode mode = EffectRunner.toggleRepeat();
			if (client.player != null) {
				client.player.sendOverlayMessage(Component.translatable("particlestudio.repeat")
					.append(": ")
					.append(Component.translatable("particlestudio.repeat." + mode.name().toLowerCase(Locale.ROOT))));
			}
		}
	}
}
