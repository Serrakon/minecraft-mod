package com.sergej.particlestudio.client.studio;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Текущий эффект студии, его запуск (превью или команды) и повтор по тикам. */
public final class EffectRunner {
	/** Больше команд за один запуск не шлём, чтобы не завалить сервер. */
	public static final int MAX_COMMANDS = 512;

	public enum RepeatMode {
		OFF, PREVIEW, COMMANDS
	}

	private static final long FEEDBACK_WINDOW_MS = 5_000;

	private static ParticleEffect effect = ParticleEffect.createDefault();
	private static int pendingFeedback;
	private static long feedbackDeadline;
	private static RepeatMode repeatMode = RepeatMode.OFF;
	private static int ticksUntilRepeat;
	private static boolean loaded;

	private EffectRunner() {
	}

	public static ParticleEffect effect() {
		if (!loaded) {
			loaded = true;
			ParticleEffect last = PresetStore.loadLastSession();
			if (last != null) {
				effect = last;
			}
		}

		return effect;
	}

	public static void setEffect(ParticleEffect newEffect) {
		loaded = true;
		effect = newEffect;
	}

	public static RepeatMode repeatMode() {
		return repeatMode;
	}

	public static void setRepeatMode(RepeatMode mode) {
		repeatMode = mode;
		ticksUntilRepeat = 0;
	}

	public static void tick(Minecraft minecraft) {
		if (repeatMode == RepeatMode.OFF) {
			return;
		}

		if (minecraft.level == null || minecraft.player == null) {
			repeatMode = RepeatMode.OFF;
			return;
		}

		if (--ticksUntilRepeat > 0) {
			return;
		}

		ticksUntilRepeat = Math.max(1, effect.repeatTicks);
		if (repeatMode == RepeatMode.PREVIEW) {
			preview(minecraft);
		} else {
			execute(minecraft);
		}
	}

	/** Частицы только у себя: права не нужны, другие игроки ничего не видят. */
	public static void preview(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			return;
		}

		Vec3 crosshair = crosshair(minecraft);
		int budget = EffectCompiler.previewBudget();
		for (ParticleLayer layer : effect.layers) {
			if (layer.enabled && budget > 0) {
				budget -= EffectCompiler.preview(EffectCompiler.compile(layer, minecraft.level.registryAccess()), minecraft.level, player, crosshair, budget);
			}
		}
	}

	/** Настоящие команды /particle: нужны права оператора (или читы в одиночной игре). */
	public static void execute(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}

		if (!canRunCommands(minecraft)) {
			player.sendOverlayMessage(Component.translatable("particlestudio.no_permission"));
			if (repeatMode == RepeatMode.COMMANDS) {
				setRepeatMode(RepeatMode.OFF);
			}

			return;
		}

		List<String> commands = commands(minecraft);
		if (commands.size() > MAX_COMMANDS) {
			player.sendOverlayMessage(Component.translatable("particlestudio.too_many_commands", commands.size(), MAX_COMMANDS));
			setRepeatMode(RepeatMode.OFF);
			return;
		}

		pendingFeedback += commands.size();
		feedbackDeadline = System.currentTimeMillis() + FEEDBACK_WINDOW_MS;
		for (String command : commands) {
			player.connection.sendCommand(command);
		}
	}

	/**
	 * Сервер отвечает «Displaying particle …» на каждую команду — на эффект из сотни команд
	 * это сотня строк в чате. Глушим только столько ответов, сколько команд отправила студия.
	 */
	public static boolean allowGameMessage(Component message) {
		if (pendingFeedback <= 0 || System.currentTimeMillis() > feedbackDeadline) {
			pendingFeedback = 0;
			return true;
		}

		if (message.getContents() instanceof TranslatableContents contents && contents.getKey().equals("commands.particle.success")) {
			pendingFeedback--;
			return false;
		}

		return true;
	}

	/** Сервер присылает клиенту только доступные ему команды: нет узла particle — нет прав. */
	public static boolean canRunCommands(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && player.connection.getCommands().getRoot().getChild("particle") != null;
	}

	/** Команды всех включённых слоёв с валидными параметрами. */
	public static List<String> commands(Minecraft minecraft) {
		Vec3 crosshair = crosshair(minecraft);
		List<String> commands = new ArrayList<>();
		for (ParticleLayer layer : effect.layers) {
			if (layer.enabled && minecraft.level != null
				&& EffectCompiler.compile(layer, minecraft.level.registryAccess()).error() == null) {
				commands.addAll(EffectCompiler.commands(layer, crosshair));
			}
		}

		return commands;
	}

	/** Точка под прицелом; если прицел смотрит в небо — 5 блоков перед глазами. */
	public static Vec3 crosshair(Minecraft minecraft) {
		HitResult hit = minecraft.hitResult;
		if (hit != null && hit.getType() != HitResult.Type.MISS) {
			return hit.getLocation();
		}

		LocalPlayer player = minecraft.player;
		return player == null ? Vec3.ZERO : player.getEyePosition().add(player.getLookAngle().scale(5.0));
	}
}
