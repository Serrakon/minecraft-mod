package com.sergej.particlestudio.client.studio.gui;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Выбор строки из списка с поиском: тип частицы, пресет. Двойной клик — выбрать. */
public class PickListScreen extends Screen {
	private final Screen parent;
	private final List<String> items;
	private final Function<String, Component> display;
	private final Consumer<String> onPick;
	private final @Nullable Consumer<String> onDelete;
	private EditBox search;
	private ItemList list;

	public PickListScreen(Screen parent, Component title, List<String> items, Function<String, Component> display,
			Consumer<String> onPick, @Nullable Consumer<String> onDelete) {
		super(title);
		this.parent = parent;
		this.items = items;
		this.display = display;
		this.onPick = onPick;
		this.onDelete = onDelete;
	}

	@Override
	protected void init() {
		int w = Math.min(320, this.width - 20);
		int left = (this.width - w) / 2;

		String query = this.search == null ? "" : this.search.getValue();
		this.search = new EditBox(this.font, left, 22, w, 20, Component.translatable("particlestudio.search"));
		this.search.setHint(Component.translatable("particlestudio.search"));
		this.search.setValue(query);
		this.search.setResponder(text -> this.filter());
		this.addRenderableWidget(this.search);

		this.list = new ItemList(left, 46, w, this.height - 46 - 32);
		this.addRenderableWidget(this.list);
		this.filter();

		int buttons = this.onDelete == null ? 2 : 3;
		int bw = (w - 4 * (buttons - 1)) / buttons;
		int x = left;
		this.addRenderableWidget(Button.builder(Component.translatable("particlestudio.pick"), b -> this.pickSelected())
			.bounds(x, this.height - 26, bw, 20).build());
		x += bw + 4;
		if (this.onDelete != null) {
			this.addRenderableWidget(Button.builder(Component.translatable("particlestudio.delete"), b -> this.deleteSelected())
				.bounds(x, this.height - 26, bw, 20).build());
			x += bw + 4;
		}

		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
			.bounds(x, this.height - 26, bw, 20).build());
		this.setInitialFocus(this.search);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	private void filter() {
		String query = this.search.getValue().trim().toLowerCase(Locale.ROOT);
		this.list.show(this.items.stream()
			.filter(item -> query.isEmpty() || item.toLowerCase(Locale.ROOT).contains(query)
				|| this.display.apply(item).getString().toLowerCase(Locale.ROOT).contains(query))
			.toList());
	}

	private void pickSelected() {
		ItemList.Item selected = this.list.getSelected();
		if (selected != null) {
			// Сначала меняем данные, потом возвращаемся: родитель перестроится уже по новым.
			this.onPick.accept(selected.value);
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	private void deleteSelected() {
		ItemList.Item selected = this.list.getSelected();
		if (selected != null && this.onDelete != null) {
			this.onDelete.accept(selected.value);
			this.items.remove(selected.value);
			this.filter();
		}
	}

	private class ItemList extends ObjectSelectionList<ItemList.Item> {
		ItemList(int x, int y, int width, int height) {
			super(PickListScreen.this.minecraft, width, height, y, 14);
			this.setX(x);
		}

		void show(List<String> values) {
			this.clearEntries();
			for (String value : values) {
				this.addEntry(new Item(value));
			}

			this.setScrollAmount(0.0);
		}

		@Override
		public int getRowWidth() {
			return this.width - 16;
		}

		class Item extends ObjectSelectionList.Entry<Item> {
			final String value;

			Item(String value) {
				this.value = value;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				graphics.text(PickListScreen.this.font, PickListScreen.this.display.apply(this.value), this.getContentX() + 2, this.getContentY() + 2, 0xFFFFFFFF);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				ItemList.this.setSelected(this);
				if (doubleClick) {
					PickListScreen.this.pickSelected();
				}

				return true;
			}

			@Override
			public Component getNarration() {
				return PickListScreen.this.display.apply(this.value);
			}
		}
	}
}
