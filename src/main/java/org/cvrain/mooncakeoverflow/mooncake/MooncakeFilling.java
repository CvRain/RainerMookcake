package org.cvrain.mooncakeoverflow.mooncake;

import net.minecraft.network.chat.Component;
import org.cvrain.mooncakeoverflow.block.MooncakePileBlockEntity;

import java.util.List;

/**
 * 五仁月饼的名字 —— 把四个角拼成「五仁月饼（左上·… + 右上·… + …）」。
 *
 * <p>名字是**可翻译组件树**，所以照样跟着玩家语言走；
 * 它最后被塞进原版的 {@code custom_name} 组件（铁砧改名用的那个）。
 */
public final class MooncakeFilling {
    private MooncakeFilling() {
    }

    /**
     * 拼出「五仁月饼（左上·圆月饼 + 右上·氧化的方铜月饼·花形纹 + …）」。
     *
     * <p>名字里的"左上/右上"说的是这一片是**从哪个角切下来的**，
     * 不代表它摆下去会占四格 —— 五仁月饼和普通月饼一样只占一格。
     */
    public static Component describe(List<MooncakePileBlockEntity.Piece> pieces) {
        Component parts = Component.empty();
        for (int i = 0; i < pieces.size(); i++) {
            if (i > 0) {
                parts = parts.copy().append(Component.literal(" + "));
            }
            parts = parts.copy().append(Component.translatable("mooncake_overflow.quadrant",
                    Component.translatable(PileCell.byIndex(i).translationKey()),
                    nameOf(pieces.get(i))));
        }
        return Component.translatable("mooncake_overflow.five_kernel", parts);
    }

    /** 一片的名字（铜的带氧化度 / 涂蜡前缀）。 */
    private static Component nameOf(MooncakePileBlockEntity.Piece piece) {
        Component name = Component.translatable(
                piece.copper() ? piece.kind().copperNameKey() : piece.kind().nameKey());
        String prefix = piece.copper() ? piece.oxidation().prefixKey() : null;
        if (prefix != null) {
            name = Component.translatable(prefix, name);
        }
        if (piece.waxed()) {
            name = Component.translatable("mooncake_overflow.waxed", name);
        }
        return name;
    }
}
