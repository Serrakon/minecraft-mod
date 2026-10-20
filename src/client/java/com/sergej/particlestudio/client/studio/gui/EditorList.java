package com.sergej.particlestudio.client.studio.gui;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Прокручиваемый список строк редактора: заголовки, подсказки и строки «подпись + виджеты».
 * Ширина виджетов в строке делится поровну, если не задана явно.
 */
public class EditorList extends ContainerObjectSelectionList<EditorList.Row> {
	public static final int LABEL_WIDTH = 64;
	private static final int GAP = 2;

	public EditorList(Minecraft minecraft, int width, int height, int y) {
		super(minecraft, width, height, y, 22);
	}

	public void clear() {
		this.clearEntries();
	}

	public void header(Component text) {
		this.addEntry(new Row(text.copy().withStyle(ChatFormatting.YELLOW), List.of(), false));
	}

	public void hint(Component text) {
		this.addEntry(new Row(text.copy().withStyle(ChatFormatting.GRAY), List.of(), false), 12);
	}

	/** Строка с подписью слева; виджеты делят оставшуюся ширину поровну. */
	public void row(@Nullable Component label, AbstractWidget... widgets) {
		int available = this.getRowWidth() - LABEL_WIDTH;
		int each = (available - GAP * (widgets.length - 1)) / Math.max(1, widgets.length);
		for (AbstractWidget widget : widgets) {
			widget.setWidth(each);
		}

		this.addEntry(new Row(label, List.of(widgets), true));
	}

	/** Строка, где у виджетов уже выставлена ширина; последний растягивается до края. */
	public void rowFixed(@Nullable Component label, AbstractWidget... widgets) {
		int used = 0;
		for (int i = 0; i < widgets.length - 1; i++) {
			used += widgets[i].getWidth() + GAP;
		}

		widgets[widgets.length - 1].setWidth(this.getRowWidth() - LABEL_WIDTH - used);
		this.addEntry(new Row(label, List.of(widgets), true));
	}

	@Override
	public int getRowWidth() {
		return this.width - 14;
	}

	@Override
	public int getRowLeft() {
		return this.getX() + 4;
	}

	@Override
	protected int scrollBarX() {
		return this.getX() + this.width - 6;
	}

	@Override
	protected void extractListBackground(GuiGraphicsExtractor graphics) {
	}

	@Override
	protected void extractListSeparators(GuiGraphicsExtractor graphics) {
	}

	public class Row extends ContainerObjectSelectionList.Entry<Row> {
		private final @Nullable Component label;
		private final List<AbstractWidget> widgets;
		private final boolean indented;

		Row(@Nullable Component label, List<AbstractWidget> widgets, boolean indented) {
			this.label = label;
			this.widgets = widgets;
			this.indented = indented;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
			int x = this.getContentX();
			if (this.label != null) {
				int textY = this.widgets.isEmpty() ? this.getContentYMiddle() - 4 : this.getContentY() + 6;
				String text = EditorList.this.minecraft.font.plainSubstrByWidth(this.label.getString(), this.indented ? LABEL_WIDTH - 4 : EditorList.this.getRowWidth());
				graphics.text(EditorList.this.minecraft.font, Component.literal(text).setStyle(this.label.getStyle()), x, textY, 0xFFFFFFFF);
			}

			x += this.indented ? LABEL_WIDTH : 0;
			for (AbstractWidget widget : this.widgets) {
				widget.setPosition(x, this.getContentY());
				widget.extractRenderState(graphics, mouseX, mouseY, a);
				x += widget.getWidth() + GAP;
			}
		}

		@Override
		public List<? extends AbstractWidget> children() {
			return this.widgets;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.widgets;
		}
	}
}
