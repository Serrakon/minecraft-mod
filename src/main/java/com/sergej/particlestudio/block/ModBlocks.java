package com.sergej.particlestudio.block;

import com.sergej.particlestudio.ParticleStudio;
import com.sergej.particlestudio.item.ModItems;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	// Пример: public static final Block RUBY_BLOCK = register("ruby_block", Block::new,
	//         BlockBehaviour.Properties.of().strength(5.0f).requiresCorrectToolForDrops(), true);

	private ModBlocks() {
	}

	public static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties, boolean withItem) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ParticleStudio.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));

		if (withItem) {
			ModItems.register(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
		}

		return block;
	}

	public static void init() {
		// Вызов из ParticleStudio.onInitialize загружает класс и регистрирует его поля.
	}
}
