package com.jdj125.invasion.client;

import com.jdj125.invasion.Android;
import com.jdj125.invasion.Invasion;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The android looks like a chrome person with red eyes (zombie-shaped model, our own texture). */
@EventBusSubscriber(modid = Invasion.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InvasionClientSetup {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Invasion.MODID, "textures/entity/android.png");

    private InvasionClientSetup() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Invasion.ANDROID.get(), ctx -> new HumanoidMobRenderer<Android, HumanoidModel<Android>>(ctx,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f) {
            @Override
            public ResourceLocation getTextureLocation(Android android) {
                return TEXTURE;
            }
        });
    }
}
