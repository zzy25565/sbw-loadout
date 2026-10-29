package cn.zzy.loadout.client;

import cn.zzy.loadout.LoadoutMod;
import cn.zzy.loadout.guidance.SeekerMode;
import cn.zzy.loadout.network.LoadoutNetwork;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.init.ModItems;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = LoadoutMod.ID, value = Dist.CLIENT)
public final class GuidanceClient {
  public static final KeyMapping LOCK =
      new KeyMapping(
          "key.sbw_loadout.television_lock", GLFW.GLFW_KEY_K, "key.categories.sbw_loadout");
  private static JsonObject snapshot;
  private static long received;

  public static void accept(JsonObject j) {
    snapshot = j;
    var level = Minecraft.getInstance().level;
    received = level == null ? 0 : level.getGameTime();
  }

  public static JsonObject current() {
    var mc = Minecraft.getInstance();
    return snapshot != null
            && mc.level != null
            && mc.level
                .dimension()
                .location()
                .toString()
                .equals(snapshot.get("dimension").getAsString())
            && mc.level.getGameTime() - received <= 12
        ? snapshot
        : null;
  }

  public static void requestLock(boolean cancel) {
    JsonObject j = new JsonObject();
    var state=current();
    boolean radar=state!=null && state.has("seeker") && SeekerMode.of(state.get("seeker").getAsString())==SeekerMode.RADAR;
    j.addProperty("op", radar ? (cancel ? "radar_unlock" : "radar_lock") : (cancel ? "tv_unlock" : "tv_lock"));
    if (!cancel) {
      if (ClientEventHandler.lockingEntityVehicle != null)
        j.addProperty("target", ClientEventHandler.lockingEntityVehicle.getUUID().toString());
      var point = ClientEventHandler.lockingPosVehicle;
      if (point != null) {
        j.addProperty("x", point.x);
        j.addProperty("y", point.y);
        j.addProperty("z", point.z);
      }
    }
    PacketDistributor.sendToServer(new LoadoutNetwork.Request(j.toString()));
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    var mc = Minecraft.getInstance();
    while (LOCK.consumeClick())
      if (mc.player != null && mc.screen == null) {
        var j = current();
        boolean radar=j!=null && j.has("seeker") && SeekerMode.of(j.get("seeker").getAsString())==SeekerMode.RADAR;
        if(radar && j.get("radar_external").getAsBoolean()) continue;
        requestLock(j != null && j.get(radar ? "radar_locked" : "tv_locked").getAsBoolean());
      }
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
    snapshot = null;
  }

  @SubscribeEvent
  public static void tooltip(ItemTooltipEvent e) {
    if (e.getItemStack().is(ModItems.ARTILLERY_INDICATOR.get()))
      e.getToolTip().add(Component.literal("按住右键持续激光照射 · 默认编码 1688"));
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Post e) {
    if (TargetingPodClient.active()) return;
    var mc = Minecraft.getInstance();
    var j = current();
    if (j == null || mc.options.hideGui || mc.player == null) return;
    String text = null;
    int color = 0xeeeeee;
    if (j.get("laser_active").getAsBoolean()) {
      boolean hit = j.get("laser_hit").getAsBoolean();
      boolean airborne =
          j.has("pod")
              && j.getAsJsonObject("pod").has("laser_on")
              && j.getAsJsonObject("pod").get("laser_on").getAsBoolean();
      text =
          (airborne ? "机载 " : "")
              + "LASER "
              + j.get("laser_code").getAsInt()
              + (hit ? " · 照射中" : " · 无有效照射点")
              + (airborne ? " · F6 后 L 关闭" : "");
      color = hit ? 0x77ff99 : 0xffcc77;
    } else if (j.has("seeker") && SeekerMode.of(j.get("seeker").getAsString()) == SeekerMode.TV) {
      boolean locked = j.get("tv_locked").getAsBoolean();
      text = locked ? "TV 已锁定 · 发射后自主追踪 · K 取消" : "TV 未锁定 · K 锁定目标";
      color = locked ? 0x77ff99 : 0xffcc77;
    }
    if(j.has("seeker") && SeekerMode.of(j.get("seeker").getAsString())==SeekerMode.RADAR) {
      boolean locked=j.get("radar_locked").getAsBoolean();
      text=locked ? "RADAR 已锁定 · "+j.get("radar_target").getAsString()
          : (j.get("radar_external").getAsBoolean() ? "RADAR 未锁定 · 使用雷达 TRK / TWS" : "RADAR 未锁定 · K 锁定实体目标");
      text+=" · 通道 "+j.get("radar_channels").getAsInt()+"/"+j.get("radar_limit").getAsInt();
      color=locked ? 0x77ff99 : 0xffcc77;
    }
    if (j.has("is_bomb") && j.get("is_bomb").getAsBoolean()) {
      boolean guided = j.get("bomb_guided").getAsBoolean();
      boolean scattered = j.get("bomb_spread").getAsDouble() > 0;
      boolean laser = SeekerMode.of(j.get("seeker").getAsString()) == SeekerMode.LASER;
      text = guided ? "制导投放 · 无随机投放散布"
          : "普通投放 · " + (scattered ? "有随机散布" : "无额外散布") + " · "
              + (laser ? "F6 后 L 激光照射" : "K / F6 锁定");
      color = guided ? 0x77ff99 : 0xffcc77;
    }
    if(j.has("ir_air_to_air") && j.get("ir_air_to_air").getAsBoolean()) {
      boolean locked=j.get("ir_locked").getAsBoolean();
      text=locked ? "IR 已锁定 · "+j.get("ir_target").getAsString() : "IR 未锁定 · 对准有效热目标";
      color=locked ? 0x77ff99 : 0xffcc77;
    }
    if (text != null)
      e.getGuiGraphics()
          .drawCenteredString(
              mc.font,
              text,
              e.getGuiGraphics().guiWidth() / 2,
              j.get("laser_active").getAsBoolean() ? e.getGuiGraphics().guiHeight() / 2 + 28 : 18,
              color);
  }

  @SubscribeEvent
  public static void beam(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
    var j = current();
    if (j == null) return;
    LaserRenderer.render(e, j);
  }
}
