package com.sergej.particlestudio.client.studio.gui;

import java.util.function.DoubleConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.AbstractSliderButton;

/** Слайдер одного канала цвета (0..1), подпись вида «R 255». */
public class ChannelSlider extends AbstractSliderButton {
	private final String channel;
	private final DoubleConsumer onChange;

	public ChannelSlider(String channel, double value, DoubleConsumer onChange) {
		super(0, 0, 40, 20, Component.empty(), value);
		this.channel = channel;
		this.onChange = onChange;
		this.updateMessage();
	}

	/** Меняет значение без вызова onChange — для синхронизации с полем HEX. */
	public void setChannel(double value) {
		this.value = Math.clamp(value, 0.0, 1.0);
		this.updateMessage();
	}

	@Override
	protected void updateMessage() {
		this.setMessage(Component.literal(this.channel + " " + Math.round(this.value * 255.0)));
	}

	@Override
	protected void applyValue() {
		this.onChange.accept(this.value);
	}
}
