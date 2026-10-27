package cn.zzy.loadout.client;

import cn.zzy.loadout.LoadoutMod;
import cn.zzy.loadout.test.TestFixtures;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = LoadoutMod.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FixtureRenderer {
  @SubscribeEvent
  public static void register(EntityRenderersEvent.RegisterRenderers e) {
    e.registerEntityRenderer(
        TestFixtures.AIRCRAFT,
        c ->
            new EntityRenderer<>(c) {
              public ResourceLocation getTextureLocation(
                  com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity v) {
                return ResourceLocation.parse("minecraft:textures/block/stone.png");
              }
            });
    e.registerEntityRenderer(
        TestFixtures.GROUND,
        c ->
            new EntityRenderer<>(c) {
              public ResourceLocation getTextureLocation(
                  com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity v) {
                return ResourceLocation.parse("minecraft:textures/block/stone.png");
              }
            });
  }
}
