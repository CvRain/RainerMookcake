package org.cvrain.mooncakeoverflow.mooncake;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 月饼堆某一格的**几何形状** —— 只有三个值：空 / 圆 / 方。
 *
 * <p>为什么不复用 {@link MooncakeKind}：这里刻意把"几何"和"外观"拆开。
 * 26.1 的 {@code BlockStateBase} 会给**每个方块状态**缓存一份
 * {@code collisionShape} / {@code largeCollisionShape} / {@code isCollisionShapeFullBlock}
 * （在 {@code Cache} 里，用 {@code EmptyBlockGetter} + {@code BlockPos.ZERO} 算一次），
 * 所以**碰撞箱必须能只从方块状态推出来**，不能依赖方块实体或坐标。
 *
 * <p>而每格真正是什么月饼（纹样 / 是不是铜的 / 氧化到哪一步 / 涂没涂蜡）住在方块实体里，
 * 那些只影响外观和掉落。几何只关心圆还是方，四个格子 3⁴ = 81 种状态就够了。
 *
 * <p>（踩过的坑：一开始把 {@code getShape} 写成读方块实体，方块实体的内容拿不到时
 * 退化成整块 —— 结果那个"整块"被缓存进了碰撞箱和遮挡箱，
 * 于是地上多出一圈阴影、空格子也走不过去。）
 */
public enum CellShape implements StringRepresentable {
    NONE("none"),
    ROUND("round"),
    SQUARE("square");

    public static final Codec<CellShape> CODEC = StringRepresentable.fromEnum(CellShape::values);

    private final String name;

    CellShape(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean isEmpty() {
        return this == NONE;
    }

    public static CellShape of(MooncakeKind kind) {
        MooncakeShape shape = kind.shape();
        if (shape == null) {
            return NONE;
        }
        return shape == MooncakeShape.ROUND ? ROUND : SQUARE;
    }

    // ------------------------------------------------------------------ 几何

    private static final double H = 3 / 16.0;

    // 方形：整块 7×7
    private static final VoxelShape BOX_NW = box(1, 1, 8, 8);
    private static final VoxelShape BOX_NE = box(8, 1, 15, 8);
    private static final VoxelShape BOX_SW = box(1, 8, 8, 15);
    private static final VoxelShape BOX_SE = box(8, 8, 15, 15);

    // 圆形：切掉四角（横条 + 竖条）
    private static final VoxelShape ROUND_NW = Shapes.or(box(1, 2, 8, 7), box(2, 1, 7, 8));
    private static final VoxelShape ROUND_NE = Shapes.or(box(8, 2, 15, 7), box(9, 1, 14, 8));
    private static final VoxelShape ROUND_SW = Shapes.or(box(1, 9, 8, 14), box(2, 8, 7, 15));
    private static final VoxelShape ROUND_SE = Shapes.or(box(8, 9, 15, 14), box(9, 8, 14, 15));

    private static VoxelShape box(double x0, double z0, double x1, double z1) {
        return Shapes.box(x0 / 16.0, 0.0, z0 / 16.0, x1 / 16.0, H, z1 / 16.0);
    }

    /** 这一格在四格中的形状，{@code slot} 是 0～3（左上 → 右上 → 左下 → 右下）。 */
    public VoxelShape shapeAt(int slot) {
        if (this == NONE) {
            return Shapes.empty();
        }
        boolean square = this == SQUARE;
        return switch (slot) {
            case 0 -> square ? BOX_NW : ROUND_NW;
            case 1 -> square ? BOX_NE : ROUND_NE;
            case 2 -> square ? BOX_SW : ROUND_SW;
            default -> square ? BOX_SE : ROUND_SE;
        };
    }
}
