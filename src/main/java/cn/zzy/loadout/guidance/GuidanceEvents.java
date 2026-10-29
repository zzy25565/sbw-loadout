package cn.zzy.loadout.guidance;

import cn.zzy.loadout.*;
import cn.zzy.loadout.data.LoadoutData;
import cn.zzy.loadout.network.LoadoutNetwork;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = LoadoutMod.ID)
public final class GuidanceEvents {
  @SubscribeEvent
  public static void tick(PlayerTickEvent.Post e) {
    if (!(e.getEntity() instanceof ServerPlayer p)) return;
    TargetingPod.update(p);
    LaserDesignations.tick(p);
    if (p.tickCount % 4 == 0) sync(p);
  }

  public static void sync(ServerPlayer p) {
    JsonObject j = new JsonObject();
    j.addProperty("dimension", p.level().dimension().location().toString());
    j.addProperty(
        "laser_active", LaserDesignations.illuminating(p) || TargetingPod.laserEnabled(p));
    j.addProperty("laser_hit", LaserDesignations.sample(p) != null);
    var ownSpot = LaserDesignations.sample(p);
    j.addProperty(
        "laser_code",
        ownSpot != null
            ? ownSpot.code()
            : TargetingPod.laserEnabled(p)
                ? TargetingPod.current(p).laserCode
                : LaserDesignations.code(p.getUseItem()));
    JsonArray spots = new JsonArray();
    for (var spot : LaserDesignations.visibleTo(p)) {
      JsonObject s = new JsonObject();
      s.addProperty("source", spot.source().toString());
      s.addProperty("aircraft", spot.aircraft());
      s.addProperty("x", spot.from().x);
      s.addProperty("y", spot.from().y);
      s.addProperty("z", spot.from().z);
      s.addProperty("tx", spot.point().x);
      s.addProperty("ty", spot.point().y);
      s.addProperty("tz", spot.point().z);
      spots.add(s);
    }
    j.add("spots", spots);
    j.addProperty("tv_locked", false);
    j.addProperty("radar_locked", false);
    j.addProperty("radar_external", cn.zzy.loadout.api.RadarLink.provider()!=null);
    j.add("pod", TargetingPod.snapshot(p));
    if (p.getVehicle() instanceof VehicleEntity v && LoadoutService.supported(v)) {
      var state = LoadoutService.state(v);
      if (!state.entries.isEmpty()) {
        var a = LoadoutData.ammo().get(state.entries.getFirst().ammo());
        if (a != null) {
          j.addProperty("seeker", a.guidance());
          if (BombRelease.isBomb(a)) {
            boolean guided = BombRelease.ready(p, v, a);
            j.addProperty("is_bomb", true);
            j.addProperty("bomb_guided", guided);
            j.addProperty("bomb_spread", BombRelease.spread(a, guided));
          }
          if (SeekerMode.of(a.guidance())==SeekerMode.RADAR) {
            EntityRadarSnapshot.fill(j,p,v,a);
          }
          if (a.category().equalsIgnoreCase("AirToAirMissile") && a.guidance().equalsIgnoreCase("IR")
              && cn.zzy.loadout.api.RadarLink.provider()!=null
              && cn.zzy.loadout.api.RadarLink.provider().hasInfraredControl()) {
            var ir=cn.zzy.loadout.api.RadarLink.provider().infraredTarget(v);
            boolean locked=RadarLocks.valid(p,v,a,ir);
            j.addProperty("ir_air_to_air",true);j.addProperty("ir_locked",locked);
            if(locked) j.addProperty("ir_target",ir.getName().getString());
          }
          var lock = TelevisionLocks.current(p, v, a);
          j.addProperty("tv_locked", lock != null);
          if (lock != null) {
            j.addProperty("tx", lock.point().x);
            j.addProperty("ty", lock.point().y);
            j.addProperty("tz", lock.point().z);
          }
        }
      }
    }
    LoadoutNetwork.send(p, "guidance", j);
  }

  private static final class EntityRadarSnapshot {
    static void fill(JsonObject j,ServerPlayer p,VehicleEntity v,cn.zzy.loadout.api.AmmoType a) {
      var radar=RadarLocks.current(p,v,a);
      j.addProperty("radar_locked",radar!=null);
      j.addProperty("radar_channels",RadarGuidance.usedChannels(v));
      j.addProperty("radar_limit",RadarGuidance.channelLimit(v,a));
      if(radar!=null) j.addProperty("radar_target",radar.getName().getString());
    }
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
    LaserDesignations.remove(e.getEntity().getUUID());
    TelevisionLocks.remove(e.getEntity().getUUID());
    RadarLocks.remove(e.getEntity().getUUID());
    TargetingPod.close(e.getEntity().getUUID());
  }

  @SubscribeEvent
  public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
    LaserDesignations.remove(e.getEntity().getUUID());
    TelevisionLocks.remove(e.getEntity().getUUID());
    RadarLocks.remove(e.getEntity().getUUID());
    TargetingPod.close(e.getEntity().getUUID());
  }

  @SubscribeEvent
  public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
    LaserDesignations.remove(e.getEntity().getUUID());
    TelevisionLocks.remove(e.getEntity().getUUID());
    RadarLocks.remove(e.getEntity().getUUID());
    TargetingPod.close(e.getEntity().getUUID());
  }

  @SubscribeEvent
  public static void stop(ServerStoppedEvent e) {
    LaserDesignations.clear();
    TelevisionLocks.clear();
    RadarLocks.clear();
    RadarGuidance.clear();
    TargetingPod.clear();
  }
}
