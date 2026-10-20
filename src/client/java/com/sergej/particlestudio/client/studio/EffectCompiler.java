package com.sergej.particlestudio.client.studio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.coordinates.LocalCoordinates;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Превращает слой в команды /particle и в локальные частицы для превью. */
public final class EffectCompiler {
	private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
	private static final int MAX_PREVIEW_PARTICLES = 20_000;

	/** Одна команда /particle: смещение точки формы + разброс/скорость/количество. */
	public record Emission(Vec3 offset, Vec3 delta, double speed, int count) {
	}

	/** Слой с разобранными параметрами частицы. error != null — слой нельзя запустить. */
	public record CompiledLayer(ParticleLayer layer, @Nullable ParticleOptions options, @Nullable String error) {
	}

	private record ShapePoint(Vec3 offset, Vec3 direction) {
	}

	private EffectCompiler() {
	}

	public static CompiledLayer compile(ParticleLayer layer, HolderLookup.Provider registries) {
		ParticleType<?> type = ParticleOptionsHelper.type(layer.particle);
		if (type == null) {
			return new CompiledLayer(layer, null, "Unknown particle: " + layer.particle);
		}

		CompoundTag tag = ParticleOptionsHelper.parse(layer.options);
		if (tag == null) {
			return new CompiledLayer(layer, null, "Invalid SNBT: " + layer.options);
		}

		return ParticleOptionsHelper.decode(type, tag, registries)
			.mapOrElse(
				options -> new CompiledLayer(layer, options, null),
				error -> new CompiledLayer(layer, null, error.message())
			);
	}

	public static List<Emission> emissions(ParticleLayer layer) {
		Vec3 base = new Vec3(layer.x, layer.y, layer.z);
		List<Emission> result = new ArrayList<>();
		for (ShapePoint point : shapePoints(layer)) {
			Vec3 offset = base.add(point.offset());
			Emission emission = switch (layer.motion) {
				case COMMAND -> new Emission(offset, new Vec3(layer.dx, layer.dy, layer.dz), layer.speed, layer.count);
				case OUTWARD -> new Emission(offset, point.direction(), layer.speed, 0);
				case INWARD -> new Emission(offset, point.direction().scale(-1.0), layer.speed, 0);
				case UP -> new Emission(offset, new Vec3(0.0, 1.0, 0.0), layer.speed, 0);
			};
			result.add(emission);
		}

		return result;
	}

	public static List<String> commands(ParticleLayer layer, Vec3 crosshair) {
		CompoundTag tag = ParticleOptionsHelper.parse(layer.options);
		String head = "particle " + layer.particle + (tag == null ? "" : ParticleOptionsHelper.toSnbt(tag)) + " ";
		String tail = layer.force ? " force" : "";

		List<String> result = new ArrayList<>();
		for (Emission e : emissions(layer)) {
			result.add(head + position(layer.positionMode, e.offset(), crosshair)
				+ " " + number(e.delta().x) + " " + number(e.delta().y) + " " + number(e.delta().z)
				+ " " + number(Math.max(0.0, e.speed())) + " " + Math.max(0, e.count()) + tail);
		}

		return result;
	}

	/** Спавнит частицы локально так же, как клиент обрабатывает пакет от команды /particle. */
	public static int preview(CompiledLayer compiled, Level level, Player player, Vec3 crosshair, int budget) {
		ParticleOptions options = compiled.options();
		if (options == null) {
			return 0;
		}

		ParticleLayer layer = compiled.layer();
		RandomSource random = level.getRandom();
		int spawned = 0;
		for (Emission e : emissions(layer)) {
			Vec3 pos = worldPosition(layer.positionMode, e.offset(), player, crosshair);
			double speed = Math.max(0.0, e.speed());
			if (e.count() <= 0) {
				level.addParticle(options, layer.force, false, pos.x, pos.y, pos.z,
					speed * e.delta().x, speed * e.delta().y, speed * e.delta().z);
				spawned++;
			} else {
				for (int i = 0; i < e.count() && spawned < budget; i++, spawned++) {
					level.addParticle(options, layer.force, false,
						pos.x + random.nextGaussian() * e.delta().x,
						pos.y + random.nextGaussian() * e.delta().y,
						pos.z + random.nextGaussian() * e.delta().z,
						random.nextGaussian() * speed, random.nextGaussian() * speed, random.nextGaussian() * speed);
				}
			}

			if (spawned >= budget) {
				break;
			}
		}

		return spawned;
	}

	public static int previewBudget() {
		return MAX_PREVIEW_PARTICLES;
	}

	private static Vec3 worldPosition(ParticleLayer.PositionMode mode, Vec3 offset, Player player, Vec3 crosshair) {
		return switch (mode) {
			case RELATIVE -> player.position().add(offset);
			case LOCAL -> new LocalCoordinates(offset.x, offset.y, offset.z).apply(player.position(), player.getRotationVector());
			case CROSSHAIR -> crosshair.add(offset);
			case ABSOLUTE -> offset;
		};
	}

	private static String position(ParticleLayer.PositionMode mode, Vec3 offset, Vec3 crosshair) {
		return switch (mode) {
			case RELATIVE -> prefixed('~', offset);
			case LOCAL -> prefixed('^', offset);
			case CROSSHAIR -> {
				Vec3 abs = crosshair.add(offset);
				yield number(abs.x) + " " + number(abs.y) + " " + number(abs.z);
			}
			case ABSOLUTE -> number(offset.x) + " " + number(offset.y) + " " + number(offset.z);
		};
	}

	private static String prefixed(char prefix, Vec3 v) {
		return prefix + relative(v.x) + " " + prefix + relative(v.y) + " " + prefix + relative(v.z);
	}

	private static String relative(double value) {
		String s = number(value);
		return s.equals("0") ? "" : s;
	}

	/** Не больше трёх знаков после точки, без хвостовых нулей: 1.5, 0.125, -2. */
	public static String number(double value) {
		String s = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
		return s.equals("-0") ? "0" : s;
	}

	private static List<ShapePoint> shapePoints(ParticleLayer layer) {
		int n = Math.max(1, layer.points);
		double r = layer.radius;
		List<ShapePoint> points = new ArrayList<>();
		switch (layer.shape) {
			case POINT -> points.add(new ShapePoint(Vec3.ZERO, new Vec3(0.0, 1.0, 0.0)));
			case CIRCLE -> {
				for (int i = 0; i < n; i++) {
					double a = Math.PI * 2.0 * i / n;
					Vec3 unit = inPlane(layer.plane, Math.cos(a), Math.sin(a));
					points.add(new ShapePoint(unit.scale(r), unit));
				}
			}
			case SPHERE -> {
				for (int i = 0; i < n; i++) {
					double y = 1.0 - 2.0 * (i + 0.5) / n;
					double ring = Math.sqrt(1.0 - y * y);
					double phi = GOLDEN_ANGLE * i;
					Vec3 unit = new Vec3(Math.cos(phi) * ring, y, Math.sin(phi) * ring);
					points.add(new ShapePoint(unit.scale(r), unit));
				}
			}
			case LINE -> {
				Vec3 line = new Vec3(layer.lineX, layer.lineY, layer.lineZ);
				Vec3 direction = line.lengthSqr() > 0.0 ? line.normalize() : new Vec3(0.0, 1.0, 0.0);
				for (int i = 0; i < n; i++) {
					double t = n == 1 ? 0.0 : (double) i / (n - 1);
					points.add(new ShapePoint(line.scale(t), direction));
				}
			}
			case HELIX -> {
				for (int i = 0; i < n; i++) {
					double t = n == 1 ? 0.0 : (double) i / (n - 1);
					double a = Math.PI * 2.0 * layer.turns * t;
					Vec3 unit = inPlane(ParticleLayer.Plane.HORIZONTAL, Math.cos(a), Math.sin(a));
					points.add(new ShapePoint(unit.scale(r).add(0.0, layer.height * t, 0.0), unit));
				}
			}
		}

		return points;
	}

	private static Vec3 inPlane(ParticleLayer.Plane plane, double cos, double sin) {
		return switch (plane) {
			case HORIZONTAL -> new Vec3(cos, 0.0, sin);
			case VERTICAL_X -> new Vec3(cos, sin, 0.0);
			case VERTICAL_Z -> new Vec3(0.0, sin, cos);
		};
	}
}
