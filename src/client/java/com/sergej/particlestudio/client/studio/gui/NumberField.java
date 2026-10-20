package com.sergej.particlestudio.client.studio.gui;

import com.sergej.particlestudio.client.studio.EffectCompiler;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Числовое поле: ввод с клавиатуры, запятая тоже годится. Неверное значение подсвечивается красным и не применяется. */
public class NumberField extends EditBox {
	private static final int TEXT_COLOR = 0xFFE0E0E0;
	private static final int ERROR_COLOR = 0xFFFF5555;

	private final boolean integer;
	private final double min;

	public NumberField(Font font, double value, boolean integer, double min, DoubleConsumer onChange) {
		super(font, 40, 20, Component.empty());
		this.integer = integer;
		this.min = min;
		this.setMaxLength(16);
		this.setValue(format(value));
		this.setResponder(text -> {
			Double parsed = parse(text);
			if (parsed == null) {
				this.setTextColor(ERROR_COLOR);
				return;
			}

			this.setTextColor(TEXT_COLOR);
			onChange.accept(parsed);
		});
	}

	public void setNumber(double number) {
		this.setValue(format(number));
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
