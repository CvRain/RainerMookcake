package org.cvrain.mooncakeoverflow.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.cvrain.mooncakeoverflow.MooncakeOverflow;
import org.cvrain.mooncakeoverflow.registry.ModBlockEntities;

/**
 * 客户端专属的注册（渲染器之类）。
 *
 * <p>单独一个 {@code Dist.CLIENT} 的类，免得专用服务端也去加载渲染器相关的类。
 */
@Mod.EventBusSubscriber(modid = MooncakeOverflow.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MOONCAKE_PILE.get(), MooncakePileRenderer::new);
    }
}
