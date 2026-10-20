package com.sergej.particlestudio.client.studio;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.sergej.particlestudio.ParticleStudio;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Параметры частиц хранятся как CompoundTag — ровно то, что пишется в фигурных скобках
 * команды /particle. Проверка и превью идут через кодек типа частицы, как в ParticleArgument.
 */
public final class ParticleOptionsHelper {
	/** Стартовые значения для ванильных типов с обязательными параметрами. */
	private static final Map<String, String> TEMPLATES = Map.ofEntries(
		Map.entry("minecraft:dust", "{color:[1.0f,0.2f,0.2f],scale:1.0f}"),
		Map.entry("minecraft:dust_color_transition", "{from_color:[1.0f,0.2f,0.2f],to_color:[0.2f,0.4f,1.0f],scale:1.0f}"),
		Map.entry("minecraft:entity_effect", "{color:[1.0f,1.0f,1.0f,1.0f]}"),
		Map.entry("minecraft:tinted_leaves", "{color:[0.4f,0.8f,0.2f,1.0f]}"),
		Map.entry("minecraft:flash", "{color:[1.0f,1.0f,1.0f,1.0f]}"),
		Map.entry("minecraft:effect", "{color:[0.6f,0.3f,1.0f],power:1.0f}"),
		Map.entry("minecraft:instant_effect", "{color:[1.0f,1.0f,1.0f],power:1.0f}"),
		Map.entry("minecraft:dragon_breath", "{power:1.0f}"),
		Map.entry("minecraft:block", "{block_state:{id:\"minecraft:stone\"}}"),
		Map.entry("minecraft:block_marker", "{block_state:{id:\"minecraft:barrier\"}}"),
		Map.entry("minecraft:falling_dust", "{block_state:{id:\"minecraft:sand\"}}"),
		Map.entry("minecraft:dust_pillar", "{block_state:{id:\"minecraft:stone\"}}"),
		Map.entry("minecraft:block_crumble", "{block_state:{id:\"minecraft:stone\"}}"),
		Map.entry("minecraft:item", "{item:{id:\"minecraft:diamond\"}}"),
		Map.entry("minecraft:vibration", "{destination:{type:\"minecraft:block\",pos:[I;0,0,0]},arrival_in_ticks:20}"),
		Map.entry("minecraft:trail", "{target:[0.0d,0.0d,0.0d],color:[1.0f,0.8f,0.2f],duration:20}"),
		Map.entry("minecraft:sculk_charge", "{roll:0.0f}"),
		Map.entry("minecraft:shriek", "{delay:0}"),
		Map.entry("minecraft:geyser", "{water_blocks:3}"),
		Map.entry("minecraft:geyser_plume", "{water_blocks:3}"),
		Map.entry("minecraft:geyser_base", "{water_blocks:3,burst_impulse_base:1.0f}"),
		Map.entry("minecraft:geyser_poof", "{water_blocks:3,burst_impulse_base:1.0f}")
	);

	private ParticleOptionsHelper() {
	}

	public static @Nullable ParticleType<?> type(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? null : BuiltInRegistries.PARTICLE_TYPE.getValue(key);
	}

	public static List<String> allIds() {
		return BuiltInRegistries.PARTICLE_TYPE.keySet().stream().map(Identifier::toString).sorted().toList();
	}

	public static boolean hasOptions(ParticleType<?> type) {
		return !(type instanceof SimpleParticleType);
	}

	/**
	 * Параметры по умолчанию для типа. Координаты внутри (цель trail, позиция vibration)
	 * ставятся в anchor, чтобы эффект сразу был виден рядом с игроком.
	 * null — тип неизвестен, параметры придётся вписать в SNBT руками.
	 */
	public static @Nullable CompoundTag defaultOptions(ParticleType<?> type, HolderLookup.Provider registries, Vec3 anchor) {
		if (!hasOptions(type)) {
			return new CompoundTag();
		}

		String id = BuiltInRegistries.PARTICLE_TYPE.getKey(type).toString();
		String template = TEMPLATES.get(id);
		if (template != null) {
			CompoundTag tag = parse(template);
			DataResult<ParticleOptions> decoded = tag == null ? DataResult.error(() -> "invalid SNBT") : decode(type, tag, registries);
			if (decoded.isSuccess()) {
				moveAnchors(tag, anchor);
				return tag;
			}

			// Шаблон устарел после обновления Minecraft — смотри формат в исходниках частицы.
			ParticleStudio.LOGGER.warn("Particle template for {} is invalid: {}", id, decoded.error().map(DataResult.Error::message).orElse("?"));
		}

		// Типы с необязательными полями (в том числе из других модов) кодируются из пустого набора.
		return decode(type, new CompoundTag(), registries)
			.flatMap(options -> encode(type, options, registries))
			.result()
			.filter(CompoundTag.class::isInstance)
			.map(CompoundTag.class::cast)
			.orElse(null);
	}

	@SuppressWarnings("unchecked")
	public static DataResult<ParticleOptions> decode(ParticleType<?> type, CompoundTag tag, HolderLookup.Provider registries) {
		MapCodec<ParticleOptions> codec = (MapCodec<ParticleOptions>) type.codec();
		return codec.codec().parse(registries.createSerializationContext(NbtOps.INSTANCE), tag);
	}

	@SuppressWarnings("unchecked")
	private static DataResult<Tag> encode(ParticleType<?> type, ParticleOptions options, HolderLookup.Provider registries) {
		MapCodec<ParticleOptions> codec = (MapCodec<ParticleOptions>) type.codec();
		return codec.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), options);
	}

	public static @Nullable CompoundTag parse(String snbt) {
		if (snbt.isBlank()) {
			return new CompoundTag();
		}

		try {
			return TagParser.parseCompoundFully(snbt);
		} catch (CommandSyntaxException e) {
			return null;
		}
	}

	/** SNBT для команды: пустой набор параметров не пишется вовсе. */
	public static String toSnbt(CompoundTag tag) {
		return tag.isEmpty() ? "" : tag.toString();
	}

	private static void moveAnchors(CompoundTag tag, Vec3 anchor) {
		for (String key : tag.keySet()) {
			Tag value = tag.get(key);
			if (value instanceof CompoundTag nested) {
				moveAnchors(nested, anchor);
			} else if (key.equals("target") && value instanceof ListTag) {
				ListTag list = new ListTag();
				list.add(DoubleTag.valueOf(anchor.x));
				list.add(DoubleTag.valueOf(anchor.y));
				list.add(DoubleTag.valueOf(anchor.z));
				tag.put(key, list);
			} else if (key.equals("pos") && value instanceof IntArrayTag) {
				BlockPos pos = BlockPos.containing(anchor);
				tag.put(key, new IntArrayTag(new int[] {pos.getX(), pos.getY(), pos.getZ()}));
			}
		}
	}
}
