package org.cvrain.mooncakeoverflow.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.cvrain.mooncakeoverflow.MooncakeOverflow;
import org.cvrain.mooncakeoverflow.block.MooncakeDoughBlock;
import org.cvrain.mooncakeoverflow.block.MooncakePieceBlock;
import org.cvrain.mooncakeoverflow.block.MooncakePileBlock;
import org.cvrain.mooncakeoverflow.block.PattyBlock;

/**
 * 方块注册表。
 *
 * <ul>
 *   <li>{@code mooncake_dough_block} —— 可以放置、可以用模具压印的月饼面团</li>
 *   <li>{@code raw_mooncake} —— 压印好的生月饼，可以平放在地上（也是它的物品形态）</li>
 *   <li>{@code mooncake_block} —— 成品月饼堆，一个方块最多摆 4 块，四格可任意混装</li>
 * </ul>
 */
public final class ModBlocks {
    private ModBlocks() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MooncakeOverflow.MODID);

    public static final RegistryObject<Block> MOONCAKE_DOUGH_BLOCK = BLOCKS.register("mooncake_dough_block",
            () -> new MooncakeDoughBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("mooncake_dough_block"))
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(0.4F)
                    .sound(SoundType.SLIME_BLOCK)
            ));

    public static final RegistryObject<Block> RAW_MOONCAKE = BLOCKS.register("raw_mooncake",
            () -> new PattyBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("raw_mooncake"))
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(0.3F)
                    .sound(SoundType.SLIME_BLOCK)
                    .noOcclusion()
            ));

    /**
     * 月饼堆：一个方块，2×2 四格，每格可以放任意一块月饼。
     *
     * <p>内容（形态 / 是不是铜月饼 / 氧化度 / 涂蜡）全在方块实体里，
     * 所以普通月饼和铜月饼可以混放在同一堆 —— 这就是为什么只有一个方块。
     * 方块状态只留着给渲染器查模型用，见 {@link MooncakePileBlock}。
     */
    public static final RegistryObject<Block> MOONCAKE_BLOCK = BLOCKS.register("mooncake_block",
            () -> new MooncakePileBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("mooncake_block"))
                    .mapColor(MapColor.SAND)
                    .strength(0.3F)
                    .sound(SoundType.SLIME_BLOCK)
                    .randomTicks()
                    .noOcclusion()
            ));

    /**
     * 技术方块：月饼堆的模型表（见 {@link MooncakePieceBlock}）。
     *
     * <p>没有对应的物品，创造栏里也没有，正常玩法拿不到 ——
     * 它只是为了让渲染器能按「格子 × 形态 × 铜不铜 × 氧化度」查到一个模型。
     */
    public static final RegistryObject<Block> MOONCAKE_PIECE = BLOCKS.register("mooncake_piece",
            () -> new MooncakePieceBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("mooncake_piece"))
                    .noOcclusion()
                    .noLootTable()
            ));
}
