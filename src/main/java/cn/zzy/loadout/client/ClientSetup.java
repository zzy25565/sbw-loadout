package cn.zzy.loadout.client;

import cn.zzy.loadout.LoadoutMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid = LoadoutMod.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
  @SubscribeEvent
  public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e) {
    e.register(GuidanceClient.LOCK);
    e.register(TargetingPodClient.VIEW);
  }

  @SubscribeEvent
  public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
    e.registerEntityRenderer(LoadoutMod.RADAR_MISSILE.get(),com.atsuishio.superbwarfare.client.renderer.projectile.BasicProjectileRenderer::new);
    e.registerEntityRenderer(LoadoutMod.CRUISE_MISSILE.get(),com.atsuishio.superbwarfare.client.renderer.projectile.BasicProjectileRenderer::new);
  }

  @SubscribeEvent
  public static void menus(RegisterMenuScreensEvent e) {
    e.register(LoadoutMod.MENU.get(), LoadoutScreen::new);
  }

  @SubscribeEvent
  public static void reload(RegisterClientReloadListenersEvent e) {
    e.registerReloadListener(
        (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
            manager -> AmmoRenderer.clear());
  }
}
