package org.cvrain.mooncakeoverflow.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.cvrain.mooncakeoverflow.MooncakeOverflow;
import org.cvrain.mooncakeoverflow.block.MooncakePileBlockEntity;

import java.util.Set;

/**
 * 方块实体注册表。
 *
 * <p>26.1 的 {@code BlockEntityType} 把构造器和 {@code register} 都设成了 private，
 * Forge 也没补公开工厂，所以只能靠 {@code META-INF/accesstransformer.cfg}
 * 把构造器开成 public（见那个文件里的说明）。
 */
public final class ModBlockEntities {
    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MooncakeOverflow.MODID);

    public static final RegistryObject<BlockEntityType<MooncakePileBlockEntity>> MOONCAKE_PILE =
            BLOCK_ENTITIES.register("mooncake_pile",
                    () -> new BlockEntityType<>(
                            MooncakePileBlockEntity::new,
                            Set.of(ModBlocks.MOONCAKE_BLOCK.get())));
}
