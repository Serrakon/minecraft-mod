package com.sergej.particlestudio.client.studio.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.sergej.particlestudio.client.ParticleStudioClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/**
 * Настройки студии: клавиши мода. Как в ванильном «Управлении»: нажми кнопку, потом нужную клавишу
 * или кнопку мыши; Esc снимает назначение. Изменения сохраняются в options.txt при закрытии.
 */
public class StudioSettingsScreen extends Screen {
	private static final int ROW_HEIGHT = 24;

	private final Screen parent;
	private final List<Button> keyButtons = new ArrayList<>();
	private final List<Button> resetButtons = new ArrayList<>();
	private @Nullable KeyMapping selected;

	public StudioSettingsScreen(Screen parent) {
		super(Component.translatable("particlestudio.settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.keyButtons.clear();
		this.resetButtons.clear();
		int w = Math.min(320, this.width - 20);
		int left = (this.width - w) / 2;
		int y = 40;

		for (KeyMapping key : ParticleStudioClient.KEYS) {
			Button change = Button.builder(Component.empty(), b -> {
				this.selected = key;
				this.refresh();
			}).bounds(left + w - 160, y, 100, 20).build();
			Button reset = Button.builder(Component.translatable("controls.reset"), b -> {
				key.setKey(key.getDefaultKey());
				KeyMapping.resetMapping();
				this.refresh();
			}).bounds(left + w - 56, y, 56, 20).build();
			this.keyButtons.add(this.addRenderableWidget(change));
			this.resetButtons.add(this.addRenderableWidget(reset));
			y += ROW_HEIGHT;
		}

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
			.bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
		this.refresh();
	}

	private void refresh() {
		for (int i = 0; i < ParticleStudioClient.KEYS.size(); i++) {
			KeyMapping key = ParticleStudioClient.KEYS.get(i);
			Button change = this.keyButtons.get(i);
			List<String> conflicts = this.conflicts(key);

			MutableComponent label = key.getTranslatedKeyMessage().copy();
			if (key == this.selected) {
				label = Component.literal("> ").append(label.withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE)).append(" <").withStyle(ChatFormatting.YELLOW);
			} else if (!conflicts.isEmpty()) {
				label = label.withStyle(ChatFormatting.RED);
			}

			change.setMessage(label);
			change.setTooltip(conflicts.isEmpty() ? null
				: Tooltip.create(Component.translatable("particlestudio.settings.conflict", String.join(", ", conflicts))));
			this.resetButtons.get(i).active = !key.isDefault();
		}
	}

	/** Другие привязки Minecraft и модов на той же клавише. */
	private List<String> conflicts(KeyMapping key) {
		List<String> names = new ArrayList<>();
		if (key.isUnbound()) {
			return names;
		}

		for (KeyMapping other : this.minecraft.options.keyMappings) {
			if (other != key && key.same(other)) {
				names.add(Component.translatable(other.getName()).getString());
			}
		}

		return names;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.selected == null) {
			return super.keyPressed(event);
		}

		this.selected.setKey(event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
		this.finishSelection();
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.selected == null) {
			return super.mouseClicked(event, doubleClick);
		}

		this.selected.setKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
		this.finishSelection();
		return true;
	}

	private void finishSelection() {
		this.selected = null;
		KeyMapping.resetMapping();
		this.refresh();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.translatable("particlestudio.settings.keys_hint"), this.width / 2, 25, 0xFFA0A0A0);

		int w = Math.min(320, this.width - 20);
		int left = (this.width - w) / 2;
		int y = 46;
		for (KeyMapping key : ParticleStudioClient.KEYS) {
			graphics.text(this.font, Component.translatable(key.getName()), left, y, 0xFFFFFFFF);
			y += ROW_HEIGHT;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		this.minecraft.options.save();
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
