package com.sergej.particlestudio.client.studio;

/**
 * Один слой эффекта — одна команда /particle (или серия команд, если задана форма).
 * Поля публичные: слой сериализуется в пресеты через Gson.
 */
public class ParticleLayer {
	public boolean enabled = true;

	public String particle = "minecraft:flame";
	/** Параметры частицы в SNBT, например {color:[1f,0f,0f],scale:1f}. Пусто — без параметров. */
	public String options = "";

	/** По умолчанию — перед глазами (^ ^1.5 ^4), чтобы превью было видно из первого лица. */
	public PositionMode positionMode = PositionMode.LOCAL;
	public double x = 0.0;
	public double y = 1.5;
	public double z = 4.0;

	public double dx = 0.3;
	public double dy = 0.3;
	public double dz = 0.3;
	public double speed = 0.02;
	public int count = 20;
	public boolean force = false;

	public Shape shape = Shape.POINT;
	public Plane plane = Plane.HORIZONTAL;
	public Motion motion = Motion.COMMAND;
	public double radius = 1.5;
	public int points = 24;
	public double height = 2.0;
	public double turns = 2.0;
	public double lineX = 0.0;
	public double lineY = 0.0;
	public double lineZ = 4.0;

	public enum PositionMode {
		/** ~x ~y ~z — относительно игрока (или командного блока). */
		RELATIVE,
		/** ^x ^y ^z — относительно взгляда: влево, вверх, вперёд. */
		LOCAL,
		/** Точка под прицелом + смещение, в команду пишутся абсолютные координаты. */
		CROSSHAIR,
		/** Фиксированные абсолютные координаты. */
		ABSOLUTE
	}

	public enum Shape {
		POINT, CIRCLE, SPHERE, LINE, HELIX
	}

	public enum Plane {
		HORIZONTAL, VERTICAL_X, VERTICAL_Z
	}

	/** Как двигаются частицы формы. Всё, кроме COMMAND, использует count=0: delta становится направлением полёта. */
	public enum Motion {
		COMMAND, OUTWARD, INWARD, UP
	}
}
