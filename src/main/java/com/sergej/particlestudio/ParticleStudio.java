package com.sergej.particlestudio;

import com.sergej.particlestudio.block.ModBlocks;
import com.sergej.particlestudio.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParticleStudio implements ModInitializer {
	public static final String MOD_ID = "particlestudio";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModItems.init();

		LOGGER.info("{} loaded", MOD_ID);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
