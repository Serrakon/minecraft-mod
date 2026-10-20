package com.sergej.particlestudio.client.studio.gui;

import com.sergej.particlestudio.client.ParticleStudioClient;
import com.sergej.particlestudio.client.studio.EffectCompiler;
import com.sergej.particlestudio.client.studio.EffectRunner;
import com.sergej.particlestudio.client.studio.ParticleEffect;
import com.sergej.particlestudio.client.studio.ParticleLayer;
import com.sergej.particlestudio.client.studio.ParticleOptionsHelper;
import com.sergej.particlestudio.client.studio.PresetStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Панель слева, мир справа остаётся видимым — эффект виден прямо во время настройки.
 * Слои сверху, редактор выбранного слоя посередине, запуск и сохранение внизу.
 */
public class ParticleStudioScreen extends Screen {
	private static final int MAX_LAYERS = 8;
	private static final int PANEL_COLOR = 0xC0101018;
	private static final int EDITOR_TOP = 62;
	private static final int BOTTOM_HEIGHT = 80;
	private static final double ANY = -Double.MAX_VALUE;

	/** Выбранный слой и свёрнутость панели запоминаются между открытиями. */
	private static int selected;
	private static boolean collapsed;

	private int panelWidth;
	private EditorList editor;
	private @Nullable EditBox snbtBox;
	private @Nullable CompoundTag optionsTag;
	private boolean syncing;
	private Component statusLine = Component.empty();
	private Component infoLine = Component.empty();
	private @Nullable Component flash;
	private int flashTicks;

	public ParticleStudioScreen() {
		super(t("title"));
	}

	private static Component t(String key, Object... args) {
		return Component.translatable("particlestudio." + key, args);
	}

	private static ParticleEffect effect() {
		return EffectRunner.effect();
	}

	private static ParticleLayer layer() {
		List<ParticleLayer> layers = effect().layers;
		if (layers.isEmpty()) {
			layers.add(new ParticleLayer());
		}

		selected = Math.clamp(selected, 0, layers.size() - 1);
		return layers.get(selected);
	}

	@Override
	protected void init() {
		if (collapsed) {
			this.initCollapsed();
			return;
		}

		this.panelWidth = Math.min(260, Math.max(200, this.width * 2 / 5));
		int left = 4;
		int w = this.panelWidth - 8;

		Button collapse = Button.builder(Component.literal("«"), b -> this.setCollapsed(true))
			.bounds(this.panelWidth - 18, 2, 14, 12).build();
		collapse.setTooltip(Tooltip.create(t("collapse")));
		this.addRenderableWidget(collapse);

		EditBox name = new EditBox(this.font, left + 34, 16, w - 34, 18, t("name"));
		name.setMaxLength(64);
		name.setValue(effect().name);
		name.setResponder(value -> effect().name = value);
		this.addRenderableWidget(name);

		this.initLayerBar(left, w);

		this.editor = new EditorList(this.minecraft, this.panelWidth, this.height - EDITOR_TOP - BOTTOM_HEIGHT, EDITOR_TOP);
		this.addRenderableWidget(this.editor);
		this.buildEditor();

		this.initBottomButtons(left, w);
		this.updateStatus();
	}

	/** Свёрнутая панель: весь экран под эффект, внизу только запуск. */
	private void initCollapsed() {
		this.panelWidth = 0;
		int y = this.height - 24;
		this.addRenderableWidget(Button.builder(Component.literal("» ").append(t("title")), b -> this.setCollapsed(false))
			.bounds(4, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(t("preview"), b -> EffectRunner.preview(this.minecraft)).bounds(106, y, 60, 20).build());
		this.addRenderableWidget(Button.builder(t("run"), b -> this.runCommands()).bounds(168, y, 60, 20).build());
		this.addRenderableWidget(this.repeatButton(230, y, 100));
	}

	private void setCollapsed(boolean value) {
		collapsed = value;
		this.rebuildWidgets();
	}

	private CycleButton<EffectRunner.RepeatMode> repeatButton(int x, int y, int width) {
		CycleButton<EffectRunner.RepeatMode> repeat = CycleButton.<EffectRunner.RepeatMode>builder(
				mode -> t("repeat." + mode.name().toLowerCase(Locale.ROOT)), EffectRunner.repeatMode())
			.withValues(EffectRunner.RepeatMode.values())
			.create(x, y, width, 20, t("repeat"), (b, mode) -> EffectRunner.setRepeatMode(mode));
		repeat.setTooltip(Tooltip.create(t("repeat.tooltip")));
		return repeat;
	}

	private void initLayerBar(int left, int w) {
		List<ParticleLayer> layers = effect().layers;
		ParticleLayer current = layer();
		int x = left;
		for (int i = 0; i < layers.size(); i++) {
			int index = i;
			Component label = i == selected
				? Component.literal(String.valueOf(i + 1)).withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)
				: Component.literal(String.valueOf(i + 1)).withStyle(layers.get(i).enabled ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
			Button button = Button.builder(label, b -> this.selectLayer(index)).bounds(x, 38, 16, 20).build();
			button.setTooltip(Tooltip.create(Component.literal(layers.get(i).particle)));
			this.addRenderableWidget(button);
			x += 17;
		}

		Button add = Button.builder(Component.literal("+"), b -> this.addLayer(new ParticleLayer())).bounds(x, 38, 16, 20).build();
		add.active = layers.size() < MAX_LAYERS;
		add.setTooltip(Tooltip.create(t("layer.add")));
		this.addRenderableWidget(add);
		x += 17;

		Button copy = Button.builder(Component.literal("⧉"), b -> this.addLayer(PresetStore.copy(layer()))).bounds(x, 38, 16, 20).build();
		copy.active = layers.size() < MAX_LAYERS;
		copy.setTooltip(Tooltip.create(t("layer.duplicate")));
		this.addRenderableWidget(copy);
		x += 17;

		Button remove = Button.builder(Component.literal("✕"), b -> this.removeLayer()).bounds(x, 38, 16, 20).build();
		remove.active = layers.size() > 1;
		remove.setTooltip(Tooltip.create(t("layer.delete")));
		this.addRenderableWidget(remove);
		x += 19;

		this.addRenderableWidget(CycleButton.onOffBuilder(current.enabled)
			.create(x, 38, left + w - x, 20, t("layer.enabled"), (b, value) -> {
				layer().enabled = value;
				this.rebuildWidgets();
			}));
	}

	private void initBottomButtons(int left, int w) {
		int y1 = this.height - 48;
		int y2 = this.height - 24;
		int quarter = (w - 6) / 4;

		Button preview = Button.builder(t("preview"), b -> EffectRunner.preview(this.minecraft)).bounds(left, y1, quarter, 20).build();
		preview.setTooltip(Tooltip.create(t("preview.tooltip")));
		this.addRenderableWidget(preview);

		Button run = Button.builder(t("run"), b -> this.runCommands()).bounds(left + quarter + 2, y1, quarter, 20).build();
		run.setTooltip(Tooltip.create(t("run.tooltip")));
		this.addRenderableWidget(run);

		this.addRenderableWidget(this.repeatButton(left + 2 * (quarter + 2), y1, quarter + 20));

		NumberField interval = new NumberField(this.font, effect().repeatTicks, 1, true, 1, v -> effect().repeatTicks = (int) v);
		interval.setRectangle(w - (3 * quarter + 26), 20, left + 3 * quarter + 26, y1);
		interval.setTooltip(Tooltip.create(t("repeat.interval")));
		this.addRenderableWidget(interval);

		this.addRenderableWidget(Button.builder(t("copy"), b -> this.copyCommands()).bounds(left, y2, quarter, 20)
			.tooltip(Tooltip.create(t("copy.tooltip"))).build());
		this.addRenderableWidget(Button.builder(t("save"), b -> this.savePreset()).bounds(left + quarter + 2, y2, quarter, 20).build());
		this.addRenderableWidget(Button.builder(t("load"), b -> this.openPresetPicker()).bounds(left + 2 * (quarter + 2), y2, quarter, 20).build());
		this.addRenderableWidget(Button.builder(t("export"), b -> this.exportFunction()).bounds(left + 3 * (quarter + 2), y2, w - 3 * (quarter + 2), 20)
			.tooltip(Tooltip.create(t("export.tooltip"))).build());
	}

	// ---------- Редактор слоя ----------

	private void rebuildEditor() {
		double scroll = this.editor.scrollAmount();
		this.buildEditor();
		this.editor.setScrollAmount(scroll);
		this.updateStatus();
	}

	private void buildEditor() {
		this.editor.clear();
		this.snbtBox = null;
		ParticleLayer layer = layer();

		this.editor.header(t("section.particle"));
		Button picker = Button.builder(Component.literal(layer.particle.replace("minecraft:", "")), b -> this.openParticlePicker()).build();
		this.editor.row(t("particle"), picker);
		this.buildOptions(layer);

		this.editor.header(t("section.position"));
		this.editor.row(t("position"), CycleButton.<ParticleLayer.PositionMode>builder(
				m -> t("position." + m.name().toLowerCase(Locale.ROOT)), layer.positionMode)
			.withValues(ParticleLayer.PositionMode.values())
			.displayOnlyValue()
			.create(0, 0, 100, 20, t("position"), (b, m) -> {
				layer.positionMode = m;
				this.rebuildEditor();
			}));
		this.editor.row(t(layer.positionMode == ParticleLayer.PositionMode.LOCAL ? "local_xyz" : "xyz"),
			this.number(layer.x, 0.25, ANY, v -> layer.x = v),
			this.number(layer.y, 0.25, ANY, v -> layer.y = v),
			this.number(layer.z, 0.25, ANY, v -> layer.z = v));
		if (layer.positionMode == ParticleLayer.PositionMode.ABSOLUTE) {
			this.editor.row(null,
				Button.builder(t("position.here"), b -> this.setAbsolute(layer, this.minecraft.player.position())).build(),
				Button.builder(t("position.target"), b -> this.setAbsolute(layer, EffectRunner.crosshair(this.minecraft))).build());
		} else {
			this.editor.hint(t("position." + layer.positionMode.name().toLowerCase(Locale.ROOT) + ".hint"));
		}

		this.editor.header(t("section.motion"));
		this.editor.row(t("delta"),
			this.number(layer.dx, 0.1, ANY, v -> layer.dx = v),
			this.number(layer.dy, 0.1, ANY, v -> layer.dy = v),
			this.number(layer.dz, 0.1, ANY, v -> layer.dz = v));
		this.editor.row(t("speed_count"),
			this.number(layer.speed, 0.01, 0.0, v -> layer.speed = v),
			this.integer(layer.count, 0, v -> layer.count = (int) v));
		this.editor.row(t("force"), CycleButton.booleanBuilder(t("force.on"), t("force.off"), layer.force)
			.displayOnlyValue()
			.create(0, 0, 100, 20, t("force"), (b, v) -> {
				layer.force = v;
				this.updateStatus();
			}));
		this.editor.hint(t("hint.count_zero"));

		this.editor.header(t("section.shape"));
		this.editor.row(t("shape"), CycleButton.<ParticleLayer.Shape>builder(
				s -> t("shape." + s.name().toLowerCase(Locale.ROOT)), layer.shape)
			.withValues(ParticleLayer.Shape.values())
			.displayOnlyValue()
			.create(0, 0, 100, 20, t("shape"), (b, s) -> {
				layer.shape = s;
				this.rebuildEditor();
			}));
		this.buildShape(layer);
	}

	private void buildShape(ParticleLayer layer) {
		switch (layer.shape) {
			case POINT -> {
				return;
			}
			case CIRCLE -> {
				this.editor.row(t("plane"), CycleButton.<ParticleLayer.Plane>builder(
						p -> t("plane." + p.name().toLowerCase(Locale.ROOT)), layer.plane)
					.withValues(ParticleLayer.Plane.values())
					.displayOnlyValue()
					.create(0, 0, 100, 20, t("plane"), (b, p) -> {
						layer.plane = p;
						this.updateStatus();
					}));
				this.radiusAndPoints(layer);
			}
			case SPHERE -> this.radiusAndPoints(layer);
			case LINE -> {
				this.editor.row(t("line_end"),
					this.number(layer.lineX, 0.5, ANY, v -> layer.lineX = v),
					this.number(layer.lineY, 0.5, ANY, v -> layer.lineY = v),
					this.number(layer.lineZ, 0.5, ANY, v -> layer.lineZ = v));
				this.editor.row(t("points"), this.integer(layer.points, 1, v -> layer.points = (int) v));
			}
			case HELIX -> {
				this.radiusAndPoints(layer);
				this.editor.row(t("height_turns"),
					this.number(layer.height, 0.25, ANY, v -> layer.height = v),
					this.number(layer.turns, 0.25, ANY, v -> layer.turns = v));
			}
		}

		this.editor.row(t("motion"), CycleButton.<ParticleLayer.Motion>builder(
				m -> t("motion." + m.name().toLowerCase(Locale.ROOT)), layer.motion)
			.withValues(ParticleLayer.Motion.values())
			.displayOnlyValue()
			.create(0, 0, 100, 20, t("motion"), (b, m) -> {
				layer.motion = m;
				this.rebuildEditor();
			}));
		this.editor.hint(t(layer.motion == ParticleLayer.Motion.COMMAND ? "hint.motion_command" : "hint.motion_directed"));
	}

	private void radiusAndPoints(ParticleLayer layer) {
		this.editor.row(t("radius_points"),
			this.number(layer.radius, 0.25, 0.0, v -> layer.radius = v),
			this.integer(layer.points, 1, v -> layer.points = (int) v));
	}

	private void setAbsolute(ParticleLayer layer, Vec3 pos) {
		layer.x = Math.round(pos.x * 100.0) / 100.0;
		layer.y = Math.round(pos.y * 100.0) / 100.0;
		layer.z = Math.round(pos.z * 100.0) / 100.0;
		this.rebuildEditor();
	}

	// ---------- Параметры частицы: строятся по SNBT, каждое поле правит свой тег ----------

	private void buildOptions(ParticleLayer layer) {
		ParticleType<?> type = ParticleOptionsHelper.type(layer.particle);
		if (type == null) {
			this.editor.hint(t("hint.unknown_particle"));
			return;
		}

		if (!ParticleOptionsHelper.hasOptions(type)) {
			this.editor.hint(t("hint.no_options"));
			return;
		}

		this.optionsTag = ParticleOptionsHelper.parse(layer.options);
		if (this.optionsTag != null) {
			this.addOptionRows(this.optionsTag, "");
		}

		EditBox snbt = new EditBox(this.font, 0, 0, 100, 20, Component.literal("SNBT"));
		snbt.setMaxLength(4096);
		snbt.setValue(layer.options);
		snbt.setResponder(text -> {
			if (!this.syncing) {
				layer.options = text;
				this.optionsTag = ParticleOptionsHelper.parse(text);
				this.updateStatus();
			}
		});
		snbt.setWidth(this.editor.getRowWidth() - EditorList.LABEL_WIDTH - 24);
		this.snbtBox = snbt;
		Button apply = Button.builder(Component.literal("↻"), b -> this.rebuildEditor()).build();
		apply.setTooltip(Tooltip.create(t("snbt.apply")));
		this.editor.rowFixed(Component.literal("SNBT"), snbt, apply);

		this.editor.row(null, Button.builder(t("options.reset"), b -> {
			CompoundTag defaults = ParticleOptionsHelper.defaultOptions(type, this.minecraft.level.registryAccess(), EffectRunner.crosshair(this.minecraft));
			layer.options = defaults == null ? "" : ParticleOptionsHelper.toSnbt(defaults);
			this.rebuildEditor();
		}).build());
	}

	private void addOptionRows(CompoundTag tag, String prefix) {
		List<String> keys = new ArrayList<>(tag.keySet());
		Collections.sort(keys);
		for (String key : keys) {
			Tag value = tag.get(key);
			String path = prefix + key;
			Component label = optionLabel(path);
			float[] color = key.contains("color") ? colorComponents(value) : null;
			if (color != null) {
				this.addColorRows(tag, key, label, color);
			} else if (value instanceof NumericTag numeric) {
				boolean whole = !(numeric instanceof FloatTag || numeric instanceof DoubleTag);
				double step = whole ? 1.0 : 0.1;
				NumberField field = new NumberField(this.font, numeric.doubleValue(), step, whole, ANY, v -> {
					tag.put(key, sameKind(numeric, v));
					this.optionsChanged();
				});
				this.editor.row(label, field);
			} else if (value instanceof CompoundTag nested) {
				this.addOptionRows(nested, path + ".");
			} else if (vector(value) != null) {
				this.addVectorRow(tag, key, label, vector(value), value instanceof IntArrayTag);
			} else if (value instanceof StringTag) {
				this.addStringRow(tag, key, label, prefix);
			}
		}
	}

	private void addColorRows(CompoundTag tag, String key, Component label, float[] color) {
		ColorSwatch swatch = new ColorSwatch(argb(color));
		EditBox hex = new EditBox(this.font, 0, 0, 60, 20, label);
		hex.setMaxLength(7);
		hex.setValue(toHex(color));
		ChannelSlider[] sliders = new ChannelSlider[color.length];

		Runnable write = () -> {
			ListTag list = new ListTag();
			for (float c : color) {
				list.add(FloatTag.valueOf(Math.round(c * 1000.0F) / 1000.0F));
			}

			tag.put(key, list);
			swatch.setColor(argb(color));
			this.optionsChanged();
		};

		for (int i = 0; i < color.length; i++) {
			int channel = i;
			sliders[i] = new ChannelSlider(String.valueOf("RGBA".charAt(i)), color[i], v -> {
				color[channel] = (float) v;
				this.syncing = true;
				hex.setValue(toHex(color));
				this.syncing = false;
				write.run();
			});
		}

		hex.setResponder(text -> {
			if (this.syncing) {
				return;
			}

			Integer rgb = parseHex(text);
			hex.setTextColor(rgb == null ? 0xFFFF5555 : 0xFFE0E0E0);
			if (rgb != null) {
				color[0] = ARGB.red(rgb) / 255.0F;
				color[1] = ARGB.green(rgb) / 255.0F;
				color[2] = ARGB.blue(rgb) / 255.0F;
				for (int i = 0; i < 3; i++) {
					sliders[i].setChannel(color[i]);
				}

				write.run();
			}
		});

		this.editor.rowFixed(label, swatch, hex);
		this.editor.row(null, sliders);
	}

	private void addVectorRow(CompoundTag tag, String key, Component label, double[] v, boolean ints) {
		Runnable write = () -> {
			if (ints) {
				tag.put(key, new IntArrayTag(new int[] {(int) Math.floor(v[0]), (int) Math.floor(v[1]), (int) Math.floor(v[2])}));
			} else {
				ListTag list = new ListTag();
				for (double d : v) {
					list.add(DoubleTag.valueOf(d));
				}

				tag.put(key, list);
			}

			this.optionsChanged();
		};

		NumberField[] fields = new NumberField[3];
		int fieldWidth = (this.editor.getRowWidth() - EditorList.LABEL_WIDTH - 20 - 6) / 3;
		for (int i = 0; i < 3; i++) {
			int axis = i;
			fields[i] = new NumberField(this.font, v[i], ints ? 1.0 : 0.25, ints, ANY, d -> {
				v[axis] = d;
				write.run();
			});
			fields[i].setWidth(fieldWidth);
		}

		Button target = Button.builder(Component.literal("⌖"), b -> {
			Vec3 point = EffectRunner.crosshair(this.minecraft);
			if (ints && this.minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
				BlockPos pos = hit.getBlockPos();
				point = new Vec3(pos.getX(), pos.getY(), pos.getZ());
			}

			fields[0].setNumber(ints ? Math.floor(point.x) : point.x);
			fields[1].setNumber(ints ? Math.floor(point.y) : point.y);
			fields[2].setNumber(ints ? Math.floor(point.z) : point.z);
		}).build();
		target.setTooltip(Tooltip.create(t("from_crosshair")));
		this.editor.rowFixed(label, fields[0], fields[1], fields[2], target);
	}

	private void addStringRow(CompoundTag tag, String key, Component label, String prefix) {
		EditBox box = new EditBox(this.font, 0, 0, 100, 20, label);
		box.setMaxLength(256);
		box.setValue(tag.getStringOr(key, ""));
		box.setResponder(text -> {
			tag.put(key, StringTag.valueOf(text));
			this.optionsChanged();
		});

		Button helper = null;
		if (key.equals("id") && prefix.startsWith("block_state")) {
			helper = Button.builder(Component.literal("⌖"), b -> {
				if (this.minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
					box.setValue(BuiltInRegistries.BLOCK.getKey(this.minecraft.level.getBlockState(hit.getBlockPos()).getBlock()).toString());
				}
			}).build();
			helper.setTooltip(Tooltip.create(t("block_from_crosshair")));
		} else if (key.equals("id") && prefix.startsWith("item")) {
			helper = Button.builder(Component.literal("✋"), b -> {
				ItemStack stack = this.minecraft.player.getMainHandItem();
				if (!stack.isEmpty()) {
					box.setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
				}
			}).build();
			helper.setTooltip(Tooltip.create(t("item_from_hand")));
		}

		if (helper == null) {
			this.editor.row(label, box);
		} else {
			box.setWidth(this.editor.getRowWidth() - EditorList.LABEL_WIDTH - 24);
			this.editor.rowFixed(label, box, helper);
		}
	}

	private void optionsChanged() {
		if (this.optionsTag == null) {
			return;
		}

		layer().options = ParticleOptionsHelper.toSnbt(this.optionsTag);
		if (this.snbtBox != null) {
			this.syncing = true;
			this.snbtBox.setValue(layer().options);
			this.syncing = false;
		}

		this.updateStatus();
	}

	private NumberField number(double value, double step, double min, DoubleConsumer setter) {
		return new NumberField(this.font, value, step, false, min, v -> {
			setter.accept(v);
			this.updateStatus();
		});
	}

	private NumberField integer(int value, int min, DoubleConsumer setter) {
		return new NumberField(this.font, value, 1, true, min, v -> {
			setter.accept(v);
			this.updateStatus();
		});
	}

	private static Component optionLabel(String path) {
		String key = "particlestudio.option." + path;
		return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(path);
	}

	private static Tag sameKind(NumericTag original, double value) {
		return switch (original) {
			case FloatTag _ -> FloatTag.valueOf((float) value);
			case DoubleTag _ -> DoubleTag.valueOf(value);
			default -> IntTag.valueOf((int) Math.round(value));
		};
	}

	private static float @Nullable [] colorComponents(@Nullable Tag value) {
		if (!(value instanceof ListTag list) || (list.size() != 3 && list.size() != 4)) {
			return null;
		}

		float[] color = new float[list.size()];
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof NumericTag n)) {
				return null;
			}

			color[i] = Math.clamp(n.floatValue(), 0.0F, 1.0F);
		}

		return color;
	}

	private static double @Nullable [] vector(@Nullable Tag value) {
		if (value instanceof IntArrayTag array && array.getAsIntArray().length == 3) {
			int[] ints = array.getAsIntArray();
			return new double[] {ints[0], ints[1], ints[2]};
		}

		if (value instanceof ListTag list && list.size() == 3) {
			double[] result = new double[3];
			for (int i = 0; i < 3; i++) {
				if (!(list.get(i) instanceof NumericTag n)) {
					return null;
				}

				result[i] = n.doubleValue();
			}

			return result;
		}

		return null;
	}

	private static int argb(float[] color) {
		return ARGB.colorFromFloat(1.0F, color[0], color[1], color[2]);
	}

	private static String toHex(float[] color) {
		return String.format(Locale.ROOT, "#%06X", argb(color) & 0xFFFFFF);
	}

	private static @Nullable Integer parseHex(String text) {
		String clean = text.startsWith("#") ? text.substring(1) : text;
		if (clean.length() != 6) {
			return null;
		}

		try {
			return Integer.parseInt(clean, 16);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// ---------- Действия ----------

	private void selectLayer(int index) {
		selected = index;
		this.rebuildWidgets();
	}

	private void addLayer(ParticleLayer layer) {
		if (effect().layers.size() < MAX_LAYERS) {
			effect().layers.add(layer);
			selected = effect().layers.size() - 1;
			this.rebuildWidgets();
		}
	}

	private void removeLayer() {
		if (effect().layers.size() > 1) {
			effect().layers.remove(selected);
			selected = Math.max(0, selected - 1);
			this.rebuildWidgets();
		}
	}

	private void openParticlePicker() {
		this.minecraft.gui.setScreen(new PickListScreen(this, t("particle.pick"), new ArrayList<>(ParticleOptionsHelper.allIds()),
			id -> {
				ParticleType<?> type = ParticleOptionsHelper.type(id);
				String shortId = id.replace("minecraft:", "");
				return type != null && ParticleOptionsHelper.hasOptions(type)
					? Component.literal(shortId).append(Component.literal("  ⚙").withStyle(ChatFormatting.GOLD))
					: Component.literal(shortId);
			},
			id -> {
				ParticleLayer layer = layer();
				ParticleType<?> type = ParticleOptionsHelper.type(id);
				layer.particle = id;
				CompoundTag defaults = type == null ? null
					: ParticleOptionsHelper.defaultOptions(type, this.minecraft.level.registryAccess(), EffectRunner.crosshair(this.minecraft));
				layer.options = defaults == null ? "" : ParticleOptionsHelper.toSnbt(defaults);
			},
			null));
	}

	private void openPresetPicker() {
		this.minecraft.gui.setScreen(new PickListScreen(this, t("load.title"), new ArrayList<>(PresetStore.list()), Component::literal,
			name -> {
				ParticleEffect loaded = PresetStore.load(name);
				if (loaded != null) {
					EffectRunner.setEffect(loaded);
					selected = 0;
				}
			},
			name -> {
				try {
					PresetStore.delete(name);
				} catch (IOException e) {
					this.showFlash(t("error", e.getMessage()));
				}
			}));
	}

	private void runCommands() {
		List<String> commands = EffectRunner.commands(this.minecraft);
		if (commands.isEmpty()) {
			this.showFlash(t("nothing_to_run"));
			return;
		}

		if (!EffectRunner.canRunCommands(this.minecraft)) {
			this.showFlash(t("no_permission"));
			return;
		}

		EffectRunner.execute(this.minecraft);
		this.showFlash(t("ran", commands.size()));
	}

	private void copyCommands() {
		List<String> commands = EffectRunner.commands(this.minecraft);
		this.minecraft.keyboardHandler.setClipboard(String.join("\n", commands.stream().map(c -> "/" + c).toList()));
		this.showFlash(t("copied", commands.size()));
	}

	private void savePreset() {
		try {
			Path file = PresetStore.save(effect());
			this.showFlash(t("saved", file.getFileName().toString()));
		} catch (IOException e) {
			this.showFlash(t("error", e.getMessage()));
		}
	}

	private void exportFunction() {
		List<String> commands = EffectRunner.commands(this.minecraft);
		try {
			Path file = PresetStore.exportFunction(effect().name, commands);
			this.showFlash(t("exported", file.getFileName().toString()));
			this.minecraft.player.sendSystemMessage(t("exported.chat", commands.size(), file.toAbsolutePath().toString()));
		} catch (IOException e) {
			this.showFlash(t("error", e.getMessage()));
		}
	}

	private void showFlash(Component message) {
		this.flash = message;
		this.flashTicks = 60;
	}

	private void updateStatus() {
		if (this.minecraft == null || this.minecraft.level == null) {
			return;
		}

		ParticleLayer layer = layer();
		EffectCompiler.CompiledLayer compiled = EffectCompiler.compile(layer, this.minecraft.level.registryAccess());
		if (compiled.error() != null) {
			this.statusLine = Component.literal(compiled.error()).withStyle(ChatFormatting.RED);
		} else {
			List<String> commands = EffectCompiler.commands(layer, EffectRunner.crosshair(this.minecraft));
			this.statusLine = Component.literal("/" + commands.getFirst()).withStyle(ChatFormatting.AQUA);
		}

		int layerCommands = EffectCompiler.emissions(layer).size();
		this.infoLine = t("status", layerCommands, EffectRunner.commands(this.minecraft).size()).copy().withStyle(ChatFormatting.GRAY);
	}

	// ---------- Экран ----------

	@Override
	public void tick() {
		if (this.flashTicks > 0 && --this.flashTicks == 0) {
			this.flash = null;
		}

		// Прицел и позиция игрока меняются — команды с абсолютными координатами тоже.
		if (this.minecraft.level != null && this.minecraft.level.getGameTime() % 10 == 0) {
			this.updateStatus();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Без размытия и затемнения: эффект должен быть виден за панелью.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (collapsed) {
			super.extractRenderState(graphics, mouseX, mouseY, a);
			if (this.flash != null) {
				graphics.text(this.font, this.flash.copy().withStyle(ChatFormatting.GREEN), 6, this.height - 36, 0xFFFFFFFF);
			}

			return;
		}

		graphics.fill(0, 0, this.panelWidth, this.height, PANEL_COLOR);
		graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.GOLD), 6, 4, 0xFFFFFFFF);
		graphics.text(this.font, t("name"), 6, 21, 0xFFAAAAAA);
		super.extractRenderState(graphics, mouseX, mouseY, a);

		int textWidth = this.panelWidth - 10;
		int y = this.height - BOTTOM_HEIGHT + 6;
		graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(this.statusLine.getString(), textWidth))
			.setStyle(this.statusLine.getStyle()), 6, y, 0xFFFFFFFF);
		Component second = this.flash != null ? this.flash : this.infoLine;
		graphics.text(this.font, Component.literal(this.font.plainSubstrByWidth(second.getString(), textWidth))
			.setStyle(this.flash != null ? second.getStyle().withColor(ChatFormatting.GREEN) : second.getStyle()), 6, y + 11, 0xFFFFFFFF);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!this.isTyping() && ParticleStudioClient.OPEN_STUDIO.matches(event)) {
			this.onClose();
			return true;
		}

		return super.keyPressed(event);
	}

	private boolean isTyping() {
		GuiEventListener focused = this.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) {
			focused = container.getFocused();
		}

		return focused instanceof EditBox;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		PresetStore.saveLastSession(effect());
		super.onClose();
	}
}
