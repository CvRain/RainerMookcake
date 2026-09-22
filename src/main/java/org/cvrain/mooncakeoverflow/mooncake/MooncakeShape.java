package org.cvrain.mooncakeoverflow.mooncake;

import net.minecraft.util.StringRepresentable;

/**
 * 月饼的**形状** —— 指的是方块本身的几何外形，不是表面花纹。
 *
 * <p>和 {@link MooncakePattern}（表面纹样）是两个独立的轴：
 * 可以存在"方纹的圆月饼"，也可以存在"圆纹的方月饼"。
 */
public enum MooncakeShape implements StringRepresentable {
    ROUND("round"),
    SQUARE("square");

    public static final MooncakeShape DEFAULT = ROUND;

    private final String name;

    MooncakeShape(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
