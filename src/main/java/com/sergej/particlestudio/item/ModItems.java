package com.sergej.particlestudio.item;

import com.sergej.particlestudio.ParticleStudio;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
	// Пример: public static final Item RUBY = register("ruby", Item::new, new Item.Properties());

	private ModItems() {
	}

	public static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ParticleStudio.id(name));
		Item item = factory.apply(properties.setId(key));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}

		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		// Вызов из ParticleStudio.onInitialize загружает класс и регистрирует его поля.
	}
}
