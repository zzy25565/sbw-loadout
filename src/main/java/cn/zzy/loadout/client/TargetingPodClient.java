package cn.zzy.loadout.client;

import cn.zzy.loadout.*;
import cn.zzy.loadout.guidance.TargetingPod;
import cn.zzy.loadout.network.LoadoutNetwork;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.google.gson.JsonObject;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = LoadoutMod.ID, value = Dist.CLIENT)
public final class TargetingPodClient {
  public static final KeyMapping VIEW =
      new KeyMapping("key.sbw_loadout.pod_view", GLFW.GLFW_KEY_F6, "key.categories.sbw_loadout");

  public static VehicleEntity vehicle() {
    var p = Minecraft.getInstance().player;
    return p != null
            && p.isAlive()
            && p.getVehicle() instanceof VehicleEntity v
            && v.getSeatIndex(p) == 0
            && ClientLoadouts.state(v) != null
        ? v
        : null;
  }

  public static boolean active() {
    return Minecraft.getInstance().screen instanceof TargetingPodScreen && vehicle() != null;
  }

  public static JsonObject snapshot() {
    var j = GuidanceClient.current();
    return j != null && j.has("pod") ? j.getAsJsonObject("pod") : null;
  }

  public static void send(String op) {
    JsonObject j = new JsonObject();
    j.addProperty("op", op);
    send(j);
  }

  public static void send(JsonObject j) {
    PacketDistributor.sendToServer(new LoadoutNetwork.Request(j.toString()));
  }

  public static void toggle() {
    var mc = Minecraft.getInstance();
    if (mc.screen instanceof TargetingPodScreen s) {
      s.onClose();
      return;
    }
    var v = vehicle();
    if (v == null) {
      if (mc.player != null)
        mc.player.displayClientMessage(Component.literal("请先进入支持自定义挂载的飞机驾驶席"), true);
      return;
    }
    if (mc.screen == null) mc.setScreen(new TargetingPodScreen(v));
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    while (VIEW.consumeClick()) toggle();
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void fov(ViewportEvent.ComputeFov e) {
    if (active() && e.usedConfiguredFov())
      e.setFOV(((TargetingPodScreen) Minecraft.getInstance().screen).fov());
  }

  @SubscribeEvent
  public static void hand(RenderHandEvent e) {
    if (active()) e.setCanceled(true);
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Pre e) {
    if (active()) e.setCanceled(true);
  }

  public static Vec3 origin(float partial) {
    var v = vehicle();
    return v == null ? Vec3.ZERO : TargetingPod.origin(v, partial);
  }
}
