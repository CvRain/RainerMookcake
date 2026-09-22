package org.cvrain.mooncakeoverflow.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.cvrain.mooncakeoverflow.MooncakeOverflow;
import org.cvrain.mooncakeoverflow.block.MooncakeBlock;
import org.cvrain.mooncakeoverflow.block.MooncakeDoughBlock;
import org.cvrain.mooncakeoverflow.block.PattyBlock;

/**
 * 方块注册表。
 *
 * <ul>
 *   <li>{@code mooncake_dough_block} —— 可以放置、可以用模具压印的月饼面团</li>
 *   <li>{@code raw_mooncake} —— 压印好的生月饼，可以平放在地上（也是它的物品形态）</li>
 *   <li>{@code mooncake_block} —— 成品月饼，一个方块最多摆 4 块</li>
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

    public static final RegistryObject<Block> MOONCAKE_BLOCK = BLOCKS.register("mooncake_block",
            () -> new MooncakeBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("mooncake_block"))
                    .mapColor(MapColor.SAND)
                    .strength(0.3F)
                    .sound(SoundType.SLIME_BLOCK)
                    .noOcclusion()
            ));
}
