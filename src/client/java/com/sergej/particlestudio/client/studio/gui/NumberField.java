package com.sergej.particlestudio.client.studio.gui;

import com.sergej.particlestudio.client.studio.EffectCompiler;
import java.util.function.DoubleConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Числовое поле: ввод с клавиатуры (запятая тоже годится) или колесо мыши.
 * Колесо меняет значение на step, с Shift — на step×10.
 */
public class NumberField extends EditBox {
	private static final int TEXT_COLOR = 0xFFE0E0E0;
	private static final int ERROR_COLOR = 0xFFFF5555;

	private final double step;
	private final boolean integer;
	private final double min;
	private double value;

	public NumberField(Font font, double value, double step, boolean integer, double min, DoubleConsumer onChange) {
		super(font, 40, 20, Component.empty());
		this.step = step;
		this.integer = integer;
		this.min = min;
		this.value = value;
		this.setMaxLength(16);
		this.setValue(format(value));
		this.setTooltip(Tooltip.create(Component.translatable("particlestudio.hint.scroll")));
		this.setResponder(text -> {
			Double parsed = parse(text);
			if (parsed == null) {
				this.setTextColor(ERROR_COLOR);
				return;
			}

			this.setTextColor(TEXT_COLOR);
			this.value = parsed;
			onChange.accept(parsed);
		});
	}

	public double number() {
		return this.value;
	}

	public void setNumber(double number) {
		this.setValue(format(number));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (!this.isMouseOver(x, y) || scrollY == 0.0) {
			return false;
		}

		double delta = this.step * Math.signum(scrollY) * (Minecraft.getInstance().hasShiftDown() ? 10.0 : 1.0);
		this.setNumber(this.value + delta);
		return true;
	}

	private String format(double number) {
		return this.integer ? Long.toString(Math.round(number)) : EffectCompiler.number(number);
	}

	private Double parse(String text) {
		try {
			double parsed = Double.parseDouble(text.trim().replace(',', '.'));
			if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < this.min) {
				return null;
			}

			return this.integer ? Math.rint(parsed) : parsed;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
