package com.sergej.particlestudio.client.studio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.sergej.particlestudio.ParticleStudio;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/** Пресеты эффектов (config/particlestudio/presets/*.json) и экспорт в .mcfunction (config/particlestudio/functions). */
public final class PresetStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve(ParticleStudio.MOD_ID);
	private static final Path PRESETS = ROOT.resolve("presets");
	private static final Path FUNCTIONS = ROOT.resolve("functions");
	private static final Path LAST_SESSION = ROOT.resolve("last_session.json");

	private PresetStore() {
	}

	public static List<String> list() {
		if (!Files.isDirectory(PRESETS)) {
			return List.of();
		}

		try (Stream<Path> files = Files.list(PRESETS)) {
			return files.map(p -> p.getFileName().toString())
				.filter(name -> name.endsWith(".json"))
				.map(name -> name.substring(0, name.length() - ".json".length()))
				.sorted()
				.toList();
		} catch (IOException e) {
			ParticleStudio.LOGGER.warn("Cannot list presets", e);
			return List.of();
		}
	}

	public static Path save(ParticleEffect effect) throws IOException {
		Path file = PRESETS.resolve(fileName(effect.name) + ".json");
		write(file, GSON.toJson(effect));
		return file;
	}

	public static @Nullable ParticleEffect load(String name) {
		return read(PRESETS.resolve(name + ".json"));
	}

	public static void delete(String name) throws IOException {
		Files.deleteIfExists(PRESETS.resolve(name + ".json"));
	}

	public static Path exportFunction(String effectName, List<String> commands) throws IOException {
		Path file = FUNCTIONS.resolve(fileName(effectName) + ".mcfunction");
		write(file, String.join("\n", commands) + "\n");
		return file;
	}

	public static void saveLastSession(ParticleEffect effect) {
		try {
			write(LAST_SESSION, GSON.toJson(effect));
		} catch (IOException e) {
			ParticleStudio.LOGGER.warn("Cannot save last particle studio session", e);
		}
	}

	public static @Nullable ParticleEffect loadLastSession() {
		return read(LAST_SESSION);
	}

	public static ParticleEffect copy(ParticleEffect effect) {
		return GSON.fromJson(GSON.toJson(effect), ParticleEffect.class);
	}

	public static ParticleLayer copy(ParticleLayer layer) {
		return GSON.fromJson(GSON.toJson(layer), ParticleLayer.class);
	}

	/** Имя файла и функции: только a-z0-9_-, как требует Minecraft для путей ресурсов. */
	public static String fileName(String name) {
		String clean = name.toLowerCase().replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("^_+|_+$", "");
		return clean.isEmpty() ? "effect" : clean;
	}

	private static void write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	private static @Nullable ParticleEffect read(Path file) {
		if (!Files.isRegularFile(file)) {
			return null;
		}

		try {
			ParticleEffect effect = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ParticleEffect.class);
			if (effect == null || effect.layers == null || effect.layers.isEmpty()) {
				return null;
			}

			effect.layers.replaceAll(layer -> layer == null ? new ParticleLayer() : layer);
			return effect;
		} catch (IOException | JsonParseException e) {
			ParticleStudio.LOGGER.warn("Cannot read {}", file, e);
			return null;
		}
	}
}
