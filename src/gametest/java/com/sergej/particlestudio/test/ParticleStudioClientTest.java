package com.sergej.particlestudio.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.ParseResults;
import com.sergej.particlestudio.client.ParticleStudioClient;
import com.sergej.particlestudio.client.studio.EffectCompiler;
import com.sergej.particlestudio.client.studio.EffectRunner;
import com.sergej.particlestudio.client.studio.ParticleEffect;
import com.sergej.particlestudio.client.studio.ParticleLayer;
import com.sergej.particlestudio.client.studio.ParticleOptionsHelper;
import com.sergej.particlestudio.client.studio.gui.ParticleStudioScreen;
import com.sergej.particlestudio.client.studio.gui.PickListScreen;
import com.sergej.particlestudio.client.studio.gui.StudioSettingsScreen;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec3;

/** ./scripts/test.sh — скриншоты кладутся в build/run/clientGameTest/screenshots. */
public class ParticleStudioClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// Без читов у игрока нет прав на /particle: «Выполнить» должен не слать команды, а подсказать.
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(20);
			if (context.computeOnClient(EffectRunner::canRunCommands)) {
				throw new AssertionError("Player without cheats must not be able to run /particle");
			}
		}

		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(s -> s.setAllowCommands(true)).create()) {
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			context.waitTicks(40);
			if (!context.computeOnClient(EffectRunner::canRunCommands)) {
				throw new AssertionError("Player with cheats must be able to run /particle");
			}

			checkEveryParticleCommandParses(context, world);
			checkShapes(context, world);
			checkScreen(context);
		}
	}

	/** Для каждого типа частиц команда с параметрами по умолчанию должна разбираться сервером без ошибок. */
	private static void checkEveryParticleCommandParses(ClientGameTestContext context, TestSingleplayerContext world) {
		List<String> commands = context.computeOnClient(mc -> {
			List<String> result = new ArrayList<>();
			for (String id : ParticleOptionsHelper.allIds()) {
				ParticleType<?> type = ParticleOptionsHelper.type(id);
				CompoundTag defaults = ParticleOptionsHelper.defaultOptions(type, mc.level.registryAccess(), new Vec3(0.0, 80.0, 0.0));
				if (defaults == null) {
					throw new AssertionError("No default options for " + id);
				}

				ParticleLayer layer = new ParticleLayer();
				layer.particle = id;
				layer.options = ParticleOptionsHelper.toSnbt(defaults);
				EffectCompiler.CompiledLayer compiled = EffectCompiler.compile(layer, mc.level.registryAccess());
				if (compiled.error() != null) {
					throw new AssertionError(id + ": " + compiled.error());
				}

				result.addAll(EffectCompiler.commands(layer, Vec3.ZERO));
			}

			return result;
		});

		List<String> failures = world.getServer().computeOnServer(server -> {
			List<String> bad = new ArrayList<>();
			CommandSourceStack source = server.createCommandSourceStack();
			for (String command : commands) {
				ParseResults<CommandSourceStack> parse = server.getCommands().getDispatcher().parse(command, source);
				if (parse.getReader().canRead() || !parse.getExceptions().isEmpty() || parse.getContext().getCommand() == null) {
					bad.add(command + " -> " + parse.getExceptions().values());
				}
			}

			return bad;
		});

		if (!failures.isEmpty()) {
			throw new AssertionError("Commands rejected by server:\n" + String.join("\n", failures));
		}
	}

	/** Формы дают нужное число команд, и все они разбираются сервером. */
	private static void checkShapes(ClientGameTestContext context, TestSingleplayerContext world) {
		List<String> commands = context.computeOnClient(mc -> {
			List<String> result = new ArrayList<>();
			for (ParticleLayer.Shape shape : ParticleLayer.Shape.values()) {
				for (ParticleLayer.PositionMode mode : ParticleLayer.PositionMode.values()) {
					ParticleLayer layer = new ParticleLayer();
					layer.shape = shape;
					layer.positionMode = mode;
					layer.motion = ParticleLayer.Motion.OUTWARD;
					layer.points = 12;
					List<String> layerCommands = EffectCompiler.commands(layer, new Vec3(10.5, 70.0, -3.25));
					int expected = shape == ParticleLayer.Shape.POINT ? 1 : 12;
					if (layerCommands.size() != expected) {
						throw new AssertionError(shape + " gave " + layerCommands.size() + " commands");
					}

					result.addAll(layerCommands);
				}
			}

			return result;
		});

		List<String> failures = world.getServer().computeOnServer(server -> {
			List<String> bad = new ArrayList<>();
			CommandSourceStack source = server.createCommandSourceStack();
			for (String command : commands) {
				ParseResults<CommandSourceStack> parse = server.getCommands().getDispatcher().parse(command, source);
				if (parse.getReader().canRead() || !parse.getExceptions().isEmpty()) {
					bad.add(command);
				}
			}

			return bad;
		});

		if (!failures.isEmpty()) {
			throw new AssertionError("Shape commands rejected:\n" + String.join("\n", failures));
		}
	}

	/** Переназначение клавиши в настройках студии и быстрая клавиша повтора без открытой панели. */
	private static void checkSettings(ClientGameTestContext context) {
		context.clickScreenButton("⚙");
		context.waitForScreen(StudioSettingsScreen.class);
		context.takeScreenshot("settings");

		// Подпись клавиши зависит от раскладки системы (J в русской — «О»), поэтому берём её у игры.
		context.clickScreenButton(context.computeOnClient(mc -> ParticleStudioClient.OPEN_STUDIO.getTranslatedKeyMessage().getString()));
		context.getInput().pressKey(InputConstants.KEY_K);
		context.waitTicks(1);
		context.takeScreenshot("settings-rebound");
		InputConstants.Key bound = context.computeOnClient(mc -> KeyMappingHelper.getBoundKeyOf(ParticleStudioClient.OPEN_STUDIO));
		if (bound.getValue() != InputConstants.KEY_K) {
			throw new AssertionError("Open key was not rebound, got " + bound);
		}

		// Свободная клавиша: многие буквы в 26.3 заняты (O — «Друзья»).
		context.runOnClient(mc -> {
			ParticleStudioClient.OPEN_STUDIO.setKey(ParticleStudioClient.OPEN_STUDIO.getDefaultKey());
			ParticleStudioClient.TOGGLE_REPEAT.setKey(freeKey(mc));
			KeyMapping.resetMapping();
		});
		context.setScreen(() -> null);
		context.waitTicks(1);
		context.getInput().pressKey(ParticleStudioClient.TOGGLE_REPEAT);
		context.waitTicks(2);
		if (context.computeOnClient(mc -> EffectRunner.repeatMode()) != EffectRunner.RepeatMode.PREVIEW) {
			throw new AssertionError("Toggle loop key did not start the preview loop");
		}

		context.waitTicks(10);
		context.takeScreenshot("loop-hotkey");
		context.getInput().pressKey(ParticleStudioClient.TOGGLE_REPEAT);
		context.waitTicks(2);
		if (context.computeOnClient(mc -> EffectRunner.repeatMode()) != EffectRunner.RepeatMode.OFF) {
			throw new AssertionError("Toggle loop key did not stop the loop");
		}

		context.setScreen(ParticleStudioScreen::new);
	}

	private static InputConstants.Key freeKey(Minecraft mc) {
		int[] candidates = {InputConstants.KEY_N, InputConstants.KEY_M, InputConstants.KEY_U, InputConstants.KEY_Y, InputConstants.KEY_H, InputConstants.KEY_G, InputConstants.KEY_V, InputConstants.KEY_Z};
		for (int code : candidates) {
			InputConstants.Key key = InputConstants.Type.KEYBOARD.getOrCreate(code);
			boolean used = Arrays.stream(mc.options.keyMappings).anyMatch(m -> KeyMappingHelper.getBoundKeyOf(m).equals(key));
			if (!used) {
				return key;
			}
		}

		throw new AssertionError("No free key for the test");
	}

	private static void checkScreen(ClientGameTestContext context) {
		context.getInput().pressKey(ParticleStudioClient.OPEN_STUDIO);
		context.waitForScreen(ParticleStudioScreen.class);
		context.takeScreenshot("studio-default");

		context.runOnClient(mc -> {
			ParticleEffect effect = new ParticleEffect();
			effect.name = "test_ring";

			ParticleLayer ring = new ParticleLayer();
			ring.particle = "minecraft:dust";
			ring.options = "{color:[1.0f,0.2f,0.2f],scale:1.5f}";
			ring.positionMode = ParticleLayer.PositionMode.LOCAL;
			ring.x = 0.0;
			ring.y = 1.5;
			ring.z = 6.0;
			ring.shape = ParticleLayer.Shape.CIRCLE;
			ring.plane = ParticleLayer.Plane.VERTICAL_X;
			ring.radius = 2.0;
			ring.points = 32;
			ring.count = 3;
			ring.dx = 0.05;
			ring.dy = 0.05;
			ring.dz = 0.05;
			ring.speed = 0.0;
			effect.layers.add(ring);

			ParticleLayer helix = new ParticleLayer();
			helix.particle = "minecraft:end_rod";
			helix.positionMode = ParticleLayer.PositionMode.LOCAL;
			helix.y = 0.0;
			helix.z = 6.0;
			helix.shape = ParticleLayer.Shape.HELIX;
			helix.radius = 1.0;
			helix.points = 40;
			helix.height = 3.0;
			helix.turns = 3.0;
			helix.count = 1;
			helix.dx = 0.0;
			helix.dy = 0.0;
			helix.dz = 0.0;
			helix.speed = 0.0;
			effect.layers.add(helix);

			EffectRunner.setEffect(effect);
		});

		context.setScreen(ParticleStudioScreen::new);
		context.waitTicks(2);
		context.takeScreenshot("studio-dust-ring");

		context.clickScreenButton("particlestudio.preview");
		context.waitTicks(2);
		context.takeScreenshot("studio-preview");

		AtomicInteger feedbackShown = new AtomicInteger();
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (message.getContents() instanceof TranslatableContents contents && contents.getKey().equals("commands.particle.success")) {
				feedbackShown.incrementAndGet();
			}
		});
		context.clickScreenButton("particlestudio.run");
		context.waitTicks(4);
		context.takeScreenshot("studio-run");
		if (feedbackShown.get() != 0) {
			throw new AssertionError("Command feedback leaked into chat: " + feedbackShown.get());
		}

		context.clickScreenButton("particlestudio.copy");
		String clipboard = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
		if (!clipboard.startsWith("/particle minecraft:dust{") || clipboard.lines().count() != 72) {
			throw new AssertionError("Unexpected clipboard: " + clipboard.lines().limit(3).toList() + " lines=" + clipboard.lines().count());
		}

		context.clickScreenButton("«");
		context.clickScreenButton("particlestudio.preview");
		context.waitTicks(2);
		context.takeScreenshot("studio-collapsed");
		context.clickScreenButton("» Particle Studio");
		context.waitTicks(1);

		// Второй слой — частица без параметров, редактор должен показать подсказку.
		context.runOnClient(mc -> EffectRunner.effect().layers.get(1).particle = "minecraft:flame");
		context.setScreen(ParticleStudioScreen::new);
		context.waitTicks(2);
		context.takeScreenshot("studio-flame");

		checkSettings(context);

		context.setScreen(() -> new PickListScreen(new ParticleStudioScreen(), Component.literal("Pick"),
			new ArrayList<>(ParticleOptionsHelper.allIds()), Component::literal, id -> { }, null));
		context.waitTicks(2);
		context.takeScreenshot("picker");

		context.setScreen(() -> null);
	}
}
