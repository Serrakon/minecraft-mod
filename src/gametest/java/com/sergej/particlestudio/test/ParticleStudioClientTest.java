package com.sergej.particlestudio.test;

import com.mojang.brigadier.ParseResults;
import com.sergej.particlestudio.client.ParticleStudioClient;
import com.sergej.particlestudio.client.studio.EffectCompiler;
import com.sergej.particlestudio.client.studio.EffectRunner;
import com.sergej.particlestudio.client.studio.ParticleEffect;
import com.sergej.particlestudio.client.studio.ParticleLayer;
import com.sergej.particlestudio.client.studio.ParticleOptionsHelper;
import com.sergej.particlestudio.client.studio.gui.ParticleStudioScreen;
import com.sergej.particlestudio.client.studio.gui.PickListScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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

		context.setScreen(() -> new PickListScreen(new ParticleStudioScreen(), Component.literal("Pick"),
			new ArrayList<>(ParticleOptionsHelper.allIds()), Component::literal, id -> { }, null));
		context.waitTicks(2);
		context.takeScreenshot("picker");

		context.setScreen(() -> null);
	}
}
