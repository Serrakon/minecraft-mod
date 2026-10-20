package com.sergej.particlestudio.client.studio;

import java.util.ArrayList;
import java.util.List;

/** Эффект — набор слоёв, которые запускаются вместе. */
public class ParticleEffect {
	public String name = "effect";
	public int repeatTicks = 10;
	public List<ParticleLayer> layers = new ArrayList<>();

	public static ParticleEffect createDefault() {
		ParticleEffect effect = new ParticleEffect();
		effect.layers.add(new ParticleLayer());
		return effect;
	}
}
