package cn.zzy.loadout.guidance;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.LoadoutData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** 有外部雷达时只接受其服务端 TRK；独立安装时 K 键建立经过射界/遮挡校验的实体锁定。 */
public final class RadarLocks {
  private record Lock(UUID vehicle, String ammo, String dimension, UUID target) {}
  private static final Map<UUID, Lock> LOCKS = new HashMap<>();

  public static boolean valid(ServerPlayer p, VehicleEntity v, AmmoType a, Entity e) {
    return e != null && e.level() == v.level() && RadarGuidance.hostile(v, p, e)
        && AimSupport.inCone(p.getEyePosition(), v.getViewVector(1),
            e.getBoundingBox().getCenter(), a.seekRange(), a.seekAngle())
        && AimSupport.visible(p.serverLevel(), p, p.getEyePosition(), e.getBoundingBox().getCenter());
  }

  public static Entity current(ServerPlayer p, VehicleEntity v, AmmoType a) {
    if (!LoadoutService.pilot(p,v) || SeekerMode.of(a.guidance()) != SeekerMode.RADAR) return null;
    Entity target;
    if (RadarLink.provider() != null) target = RadarLink.provider().launchTarget(v,a);
    else {
      Lock lock=LOCKS.get(p.getUUID());
      if(lock==null || !lock.vehicle.equals(v.getUUID()) || !lock.ammo.equals(a.id())
          || !lock.dimension.equals(p.level().dimension().location().toString())) return null;
      target=p.serverLevel().getEntity(lock.target);
    }
    return valid(p,v,a,target) ? target : null;
  }

  public static Entity tracked(ServerPlayer p, VehicleEntity v, AmmoType a, UUID assigned) {
    if (!LoadoutService.pilot(p,v)) return null;
    Entity target = RadarLink.provider() == null ? current(p,v,a)
        : RadarLink.provider().trackedTarget(v,assigned);
    return target != null && target.getUUID().equals(assigned) && valid(p,v,a,target) ? target : null;
  }

  public static boolean acquireCurrent(ServerPlayer p, UUID target, Vec3 point) {
    if (!(p.getVehicle() instanceof VehicleEntity v) || !LoadoutService.pilot(p,v)
        || !LoadoutService.supported(v)) return false;
    var state=LoadoutService.state(v);
    if(state.entries.isEmpty()) return false;
    AmmoType a=LoadoutData.ammo().get(state.entries.getFirst().ammo());
    if(a==null || SeekerMode.of(a.guidance())!=SeekerMode.RADAR) return false;
    if(RadarLink.provider()!=null) return current(p,v,a)!=null;
    Entity e=target==null ? null : p.serverLevel().getEntity(target);
    if(e==null) {
      var hit=AimSupport.ray(p.serverLevel(),p,p.getEyePosition(),p.getLookAngle(),a.seekRange());
      e=hit==null || hit.entity()==null ? null : p.serverLevel().getEntity(hit.entity());
    }
    if(!valid(p,v,a,e)) return false;
    LOCKS.put(p.getUUID(),new Lock(v.getUUID(),a.id(),p.level().dimension().location().toString(),e.getUUID()));
    return true;
  }
  public static void remove(UUID player) { LOCKS.remove(player); }
  public static void clear() { LOCKS.clear(); }
  private RadarLocks() {}
}
