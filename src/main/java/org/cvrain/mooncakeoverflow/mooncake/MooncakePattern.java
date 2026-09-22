package org.cvrain.mooncakeoverflow.mooncake;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * 月饼的压印图案。
 *
 * <p>图案存在 {@code mooncake_overflow:mooncake_pattern} 组件上，
 * 由**切石机**切换（见 {@code data/mooncake_overflow/recipe/*_mooncake_*.json}）。
 *
 * <p>选择切石机是刻意的：图案只需要加在**烤好的**月饼上，
 * 于是完全绕开了"熔炉配方不继承物品组件"这个限制，
 * 而且"切制"本来就是本模组致敬铜块梗的一部分。
 */
public enum MooncakePattern implements StringRepresentable {
    ROUND("round"),
    SQUARE("square"),
    FLOWER("flower");

    public static final MooncakePattern DEFAULT = ROUND;

    public static final Codec<MooncakePattern> CODEC =
            StringRepresentable.fromEnum(MooncakePattern::values);

    public static final StreamCodec<ByteBuf, MooncakePattern> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(
                    id -> MooncakePattern.values()[id],
                    MooncakePattern::ordinal);

    private final String name;

    MooncakePattern(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** 语言键：{@code mooncake_overflow.mooncake.pattern.<name>} */
    public String nameKey() {
        return "mooncake_overflow.mooncake.pattern." + this.name;
    }

    /** 用于模型 / 贴图文件名后缀。 */
    public String modelSuffix() {
        return this.name;
    }

    public static MooncakePattern byName(String name) {
        for (MooncakePattern pattern : values()) {
            if (pattern.name.equals(name.toLowerCase(Locale.ROOT))) {
                return pattern;
            }
        }
        return DEFAULT;
    }
}
