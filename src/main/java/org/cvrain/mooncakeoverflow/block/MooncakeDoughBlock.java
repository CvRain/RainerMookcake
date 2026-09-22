package org.cvrain.mooncakeoverflow.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.cvrain.mooncakeoverflow.mooncake.MooncakeShape;
import org.cvrain.mooncakeoverflow.registry.ModItems;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 放在地上的月饼面团 —— 四分之一个方块大小，用**模具**压印。
 *
 * <p>有两个属性：
 * <ul>
 *   <li>{@code shape} —— 形状，由**用哪个模具**决定（圆形模具 / 方形模具）</li>
 *   <li>{@code stamped} —— 是否已压印；压印后被压扁一点</li>
 * </ul>
 *
 * <p>形状从这里开始，会一路带到：生月饼物品 → 烤好的月饼 → 摆下去的方块。
 * 为了让它过得了熔炉，生月饼按形状拆成了两个物品
 * （熔炉配方读不到输入物品的组件，只能靠原料本身不同来区分）。
 */
public class MooncakeDoughBlock extends Block {
    public static final BooleanProperty STAMPED = BooleanProperty.create("stamped");
    public static final EnumProperty<MooncakeShape> SHAPE =
            EnumProperty.create("shape", MooncakeShape.class);

    /** 用哪个模具压出什么形状，不是模具就返回 null。 */
    @Nullable
    public static MooncakeShape moldShape(ItemStack stack) {
        if (stack.getItem() == ModItems.MOONCAKE_MOLD.get()) {
            return MooncakeShape.ROUND;
        }
        if (stack.getItem() == ModItems.SQUARE_MOONCAKE_MOLD.get()) {
            return MooncakeShape.SQUARE;
        }
        return null;
    }

    // 占地 8×8，居中。圆形切掉四角，方形是完整方块
    private static VoxelShape patty(double height, boolean square) {
        double y = height / 16.0;
        double x0 = 4 / 16.0, x1 = 12 / 16.0;
        if (square) {
            return Shapes.box(x0, 0.0, x0, x1, y, x1);
        }
        return Shapes.or(
                Shapes.box(x0, 0.0, 5 / 16.0, x1, y, 11 / 16.0),
                Shapes.box(5 / 16.0, 0.0, x0, 11 / 16.0, y, x1));
    }

    private static final VoxelShape ROUND_RAW = patty(4, false);
    private static final VoxelShape ROUND_STAMPED = patty(3, false);
    private static final VoxelShape SQUARE_RAW = patty(4, true);
    private static final VoxelShape SQUARE_STAMPED = patty(3, true);

    public MooncakeDoughBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(STAMPED, false)
                .setValue(SHAPE, MooncakeShape.DEFAULT));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(MooncakeDoughBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAMPED, SHAPE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean square = state.getValue(SHAPE) == MooncakeShape.SQUARE;
        boolean stamped = state.getValue(STAMPED);
        if (square) {
            return stamped ? SQUARE_STAMPED : SQUARE_RAW;
        }
        return stamped ? ROUND_STAMPED : ROUND_RAW;
    }

    /** 手持模具右键 → 压印，并把形状定下来。 */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        MooncakeShape shape = state.getValue(STAMPED) ? null : moldShape(stack);
        if (shape == null) {
            // 关键：原版只有在 useItemOn 返回 TRY_WITH_EMPTY_HAND 时，才会继续调用 useWithoutItem。
            // 返回 PASS 的话，下面那个"空手取出"永远不会被触发。
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.setBlockAndUpdate(pos, state.setValue(STAMPED, true).setValue(SHAPE, shape));
            serverLevel.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);

            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND
                    : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, player, slot);
        }

        return InteractionResult.SUCCESS;
    }

    /** 空手右键已压印的面团 → 取出对应形状的生月饼。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!state.getValue(STAMPED)) {
            return InteractionResult.PASS;
        }

        // 必须真的空手。useWithoutItem 在手持物品时也会被调用
        // （只要 useItemOn 返回了 TRY_WITH_EMPTY_HAND），所以这里得自己把关。
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.removeBlock(pos, false);
            popResource(serverLevel, pos, new ItemStack(rawItem(state)));
            serverLevel.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 0.7F);
        }

        return InteractionResult.SUCCESS;
    }

    private static net.minecraft.world.item.Item rawItem(BlockState state) {
        return state.getValue(SHAPE) == MooncakeShape.SQUARE
                ? ModItems.SQUARE_RAW_MOONCAKE.get()
                : ModItems.RAW_MOONCAKE.get();
    }

    /** 形状不同掉落也不同，所以不走战利品表。 */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (!state.getValue(STAMPED)) {
            return List.of(new ItemStack(ModItems.FILLED_MOONCAKE_DOUGH.get()));
        }
        return List.of(new ItemStack(rawItem(state)));
    }
}
