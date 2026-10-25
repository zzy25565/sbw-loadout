package cn.zzy.loadout.client;

import cn.zzy.loadout.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.network.LoadoutNetwork;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = LoadoutMod.ID, value = Dist.CLIENT)
public final class ClientLoadouts {
  private static final Map<UUID, JsonObject> states = new HashMap<>();
  private static Object level;
  private static long lastShot = Long.MIN_VALUE;
  private static boolean creativeDirty;
  public static JsonObject menu;

  public static void accept(String data) {
    JsonObject j = JsonParser.parseString(data).getAsJsonObject();
    switch (j.get("kind").getAsString()) {
      case "guidance" -> GuidanceClient.accept(j);
      case "definitions" -> {
        LoadoutData.installClient(j.getAsJsonObject("ammo"));
        creativeDirty = true;
      }
      case "state" -> {
        fresh();
        UUID uuid = UUID.fromString(j.get("uuid").getAsString());
        if (j.has("spec")) states.put(uuid, j);
        else states.remove(uuid);
      }
      case "menu" -> {
        menu = j;
        fresh();
        states.put(UUID.fromString(j.get("uuid").getAsString()), j);
      }
    }
  }

  private static void fresh() {
    Object now = Minecraft.getInstance().level;
    if (now != level) {
      states.clear();
      level = now;
      lastShot = Long.MIN_VALUE;
    }
  }

  public static JsonObject state(VehicleEntity v) {
    fresh();
    return states.get(v.getUUID());
  }

  public static boolean handleFire() {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null
        || !(mc.player.getVehicle() instanceof VehicleEntity v)
        || state(v) == null
        || v.getSeatIndex(mc.player) != 0) return false;
    if (mc.screen != null || !mc.isWindowActive()) return true;
    long now = mc.level.getGameTime();
    if (ClientEventHandler.holdFireVehicle && now != lastShot) {
      lastShot = now;
      JsonObject j = new JsonObject();
      j.addProperty("op", "fire");
      // 吊舱/手动 K 键确认的 TV 锁定优先，不能被原生雷达残留目标覆盖。
      var guidance = GuidanceClient.current();
      boolean storedTv = guidance != null && guidance.get("tv_locked").getAsBoolean();
      if (!storedTv && ClientEventHandler.lockingEntityVehicle != null)
        j.addProperty("target", ClientEventHandler.lockingEntityVehicle.getUUID().toString());
      var p = ClientEventHandler.lockingPosVehicle;
      if (!storedTv && p != null) {
        j.addProperty("x", p.x);
        j.addProperty("y", p.y);
        j.addProperty("z", p.z);
      }
      PacketDistributor.sendToServer(new LoadoutNetwork.Request(j.toString()));
    }
    return true;
  }

  public static void action(int menu, String op, String ammo, int slot) {
    JsonObject j = new JsonObject();
    j.addProperty("menu", menu);
    j.addProperty("op", op);
    j.addProperty("ammo", ammo);
    j.addProperty("slot", slot);
    PacketDistributor.sendToServer(new LoadoutNetwork.Request(j.toString()));
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    var mc = Minecraft.getInstance();
    if (creativeDirty && mc.level != null && mc.player != null) {
      LoadoutMod.TAB
          .get()
          .buildContents(
              new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                  mc.level.enabledFeatures(), false, mc.level.registryAccess()));
      creativeDirty = false;
    }
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
    states.clear();
    menu = null;
    level = null;
    LoadoutData.installClient(new JsonObject());
    AmmoRenderer.clear();
  }

  @SubscribeEvent
  public static void hud(RenderGuiEvent.Post e) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null
        || mc.options.hideGui
        || !(mc.player.getVehicle() instanceof VehicleEntity v)) return;
    JsonObject j = state(v);
    if (j == null || !j.has("entries")) return;
    var g = e.getGuiGraphics();
    Map<String, Integer> counts = new LinkedHashMap<>();
    var entries = LoadoutState.fromJson(j.getAsJsonArray("entries"));
    for (var entry : entries) counts.merge(entry.ammo(), 1, Integer::sum);
    int x = g.guiWidth() - 180, y = g.guiHeight() - 100 - counts.size() * 20;
    g.fill(x - 5, y - 5, g.guiWidth() - 5, g.guiHeight() - 80, 0xb0121922);
    g.drawString(
        mc.font,
        "已用 "
            + LoadoutState.used(entries, LoadoutData.clientAmmo())
            + "/"
            + j.getAsJsonObject("spec").get("Slots").getAsInt(),
        x,
        y,
        0xffffff);
    y += 18;
    for (var count : counts.entrySet()) {
      var a = LoadoutData.clientAmmo().get(count.getKey());
      if (a == null) continue;
      g.blit(ResourceLocation.parse(a.icon()), x, y, 0, 0, 16, 16, 16, 16);
      g.drawString(mc.font, a.displayName() + " ×" + count.getValue(), x + 20, y + 4, 0xe0e9f0);
      y += 20;
    }
  }
}
