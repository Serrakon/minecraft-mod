package com.sergej.particlestudio.client.studio.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Квадрат с текущим цветом. Не кликается. */
public class ColorSwatch extends AbstractWidget {
	private int argb;

	public ColorSwatch(int argb) {
		super(0, 0, 20, 20, Component.empty());
		this.argb = argb;
		this.active = false;
	}

	public void setColor(int argb) {
		this.argb = argb;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0xFF000000);
		graphics.fill(this.getX() + 1, this.getY() + 1, this.getRight() - 1, this.getBottom() - 1, this.argb | 0xFF000000);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
