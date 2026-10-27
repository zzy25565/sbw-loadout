package cn.zzy.loadout.network;

import cn.zzy.loadout.*;
import cn.zzy.loadout.data.LoadoutData;
import cn.zzy.loadout.menu.LoadoutMenu;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.google.gson.*;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class LoadoutNetwork {
  public record Request(String json) implements CustomPacketPayload {
    public static final Type<Request> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LoadoutMod.ID, "request"));
    public static final StreamCodec<FriendlyByteBuf, Request> CODEC =
        StreamCodec.of((b, p) -> b.writeUtf(p.json, 4096), b -> new Request(b.readUtf(4096)));

    public Type<? extends CustomPacketPayload> type() {
      return TYPE;
    }
  }

  public record Update(String json) implements CustomPacketPayload {
    public static final Type<Update> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(LoadoutMod.ID, "update"));
    public static final StreamCodec<FriendlyByteBuf, Update> CODEC =
        StreamCodec.of((b, p) -> b.writeUtf(p.json, 1048576), b -> new Update(b.readUtf(1048576)));

    public Type<? extends CustomPacketPayload> type() {
      return TYPE;
    }
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    var r = event.registrar("5");
    r.playToServer(
        Request.TYPE,
        Request.CODEC,
        (payload, context) ->
            context.enqueueWork(
                () -> {
                  if (!(context.player() instanceof ServerPlayer p)) return;
                  try {
                    JsonObject j = JsonParser.parseString(payload.json).getAsJsonObject();
                    String op = j.get("op").getAsString();
                    if (op.startsWith("pod_")) {
                      switch (op) {
                        case "pod_open" -> {
                          if (!cn.zzy.loadout.guidance.TargetingPod.open(p))
                            LoadoutService.message(p, "只有支持通用挂载的飞机驾驶席可以使用吊舱");
                        }
                        case "pod_aim" ->
                            cn.zzy.loadout.guidance.TargetingPod.aim(
                                p, j.get("yaw").getAsDouble(), j.get("pitch").getAsDouble());
                        case "pod_lock", "pod_stabilize" -> {
                          boolean ok =
                              cn.zzy.loadout.guidance.TargetingPod.lock(
                                  p, op.equals("pod_stabilize"));
                          LoadoutService.message(p, ok ? "吊舱已稳定目标；制导投放仍需有效锁定或激光照射" : "吊舱未发现可见目标");
                        }
                        case "pod_unlock" -> cn.zzy.loadout.guidance.TargetingPod.unlock(p);
                        case "pod_laser" -> {
                          if (!cn.zzy.loadout.guidance.TargetingPod.toggleLaser(p))
                            LoadoutService.message(p, "激光未开启：请先对准可见目标并检查云台范围");
                        }
                        case "pod_close" -> cn.zzy.loadout.guidance.TargetingPod.closeView(p);
                        default -> {
                          return;
                        }
                      }
                      cn.zzy.loadout.guidance.GuidanceEvents.sync(p);
                      return;
                    }
                    if (op.equals("radar_lock") || op.equals("radar_unlock")) {
                      if (op.equals("radar_unlock")) cn.zzy.loadout.guidance.RadarLocks.remove(p.getUUID());
                      else {
                        boolean ok=cn.zzy.loadout.guidance.RadarLocks.acquireCurrent(p,
                            j.has("target") ? UUID.fromString(j.get("target").getAsString()) : null,null);
                        LoadoutService.message(p,ok ? "雷达实体目标已锁定" : "雷达未锁定：检查 TRK、射界和可见性");
                      }
                      cn.zzy.loadout.guidance.GuidanceEvents.sync(p);
                      return;
                    }
                    if (op.equals("tv_lock")) {
                      boolean ok =
                          cn.zzy.loadout.guidance.TelevisionLocks.acquireCurrent(
                              p,
                              j.has("target")
                                  ? UUID.fromString(j.get("target").getAsString())
                                  : null,
                              j.has("x")
                                  ? new Vec3(
                                      j.get("x").getAsDouble(),
                                      j.get("y").getAsDouble(),
                                      j.get("z").getAsDouble())
                                  : null);
                      LoadoutService.message(
                          p, ok ? "目标已锁定，可进行制导发射/投放" : "锁定失败：请对准可见目标并选择 TV 或坐标制导弹药");
                      cn.zzy.loadout.guidance.GuidanceEvents.sync(p);
                      return;
                    }
                    if (op.equals("tv_unlock")) {
                      cn.zzy.loadout.guidance.TelevisionLocks.remove(p.getUUID());
                      cn.zzy.loadout.guidance.GuidanceEvents.sync(p);
                      return;
                    }
                    if (op.equals("fire")) {
                      if (p.getVehicle() instanceof VehicleEntity v) {
                        UUID target =
                            j.has("target") ? UUID.fromString(j.get("target").getAsString()) : null;
                        Vec3 pos =
                            j.has("x")
                                ? new Vec3(
                                    j.get("x").getAsDouble(),
                                    j.get("y").getAsDouble(),
                                    j.get("z").getAsDouble())
                                : null;
                        LoadoutService.fire(p, v, target, pos);
                      }
                      return;
                    }
                    if (p.containerMenu instanceof LoadoutMenu menu
                        && j.get("menu").getAsInt() == menu.containerId)
                      menu.act(
                          op,
                          j.has("ammo") ? j.get("ammo").getAsString() : "",
                          j.has("slot") ? j.get("slot").getAsInt() : -1);
                  } catch (RuntimeException e) {
                    com.mojang.logging.LogUtils.getLogger()
                        .warn(
                            "Rejected loadout request from {}: {}",
                            p.getGameProfile().getName(),
                            e.toString());
                  }
                }));
    r.playToClient(
        Update.TYPE,
        Update.CODEC,
        (payload, context) ->
            context.enqueueWork(() -> cn.zzy.loadout.client.ClientLoadouts.accept(payload.json)));
  }

  public static void send(ServerPlayer p, String type, JsonObject body) {
    body = body.deepCopy();
    body.addProperty("kind", type);
    PacketDistributor.sendToPlayer(p, new Update(body.toString()));
  }

  public static void definitions(ServerPlayer p) {
    JsonObject j = new JsonObject();
    j.add("ammo", LoadoutData.definitions());
    send(p, "definitions", j);
  }

  public static void tracking(VehicleEntity v, JsonObject body) {
    body = body.deepCopy();
    body.addProperty("kind", "state");
    PacketDistributor.sendToPlayersTrackingEntity(v, new Update(body.toString()));
    for (var e : v.getPassengers()) if (e instanceof ServerPlayer p) send(p, "state", body);
  }
}
