package cn.zzy.loadout.guidance;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.AmmoType;
import cn.zzy.loadout.data.LoadoutData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class TelevisionLocks {
  public record Lock(
      UUID vehicle, String ammo, String dimension, UUID target, Vec3 point, boolean pod) {
    public Lock(UUID vehicle, String ammo, String dimension, UUID target, Vec3 point) {
      this(vehicle, ammo, dimension, target, point, false);
    }
  }

  private static final Map<UUID, Lock> LOCKS = new HashMap<>();

  /** 客户端只提交锁定意图；实体身份、射界、遮挡与固定点的真实表面都由服务端验证。 */
  public static boolean acquire(
      ServerPlayer p, VehicleEntity v, AmmoType a, UUID requested, Vec3 point) {
    if (!LoadoutService.pilot(p, v) || !BombRelease.acceptsPointLock(a)) return false;
    Vec3 from = p.getEyePosition(), dir = v.getViewVector(1);
    Entity e = requested == null ? null : p.serverLevel().getEntity(requested);
    Vec3 target;
    if (e != null && e.isAlive() && e.getRootVehicle() != v) {
      target = e.getBoundingBox().getCenter();
    } else if (point != null) {
      if (!AimSupport.finite(point) || !AimSupport.loaded(p.serverLevel(), from, point))
        return false;
      Vec3 towards = point.subtract(from).normalize();
      var hit =
          AimSupport.ray(
              p.serverLevel(),
              p,
              from,
              towards,
              Math.min(a.seekRange(), from.distanceTo(point) + .5));
      if (hit == null || hit.point().distanceToSqr(point) > .25) return false;
      target = hit.point();
      requested = hit.entity();
    } else {
      var hit = AimSupport.ray(p.serverLevel(), p, from, p.getLookAngle(), a.seekRange());
      if (hit == null) return false;
      target = hit.point();
      requested = hit.entity();
    }
    if (!AimSupport.inCone(from, dir, target, a.seekRange(), a.seekAngle())
        || !AimSupport.visible(p.serverLevel(), p, from, target)) return false;
    LOCKS.put(
        p.getUUID(),
        new Lock(
            v.getUUID(), a.id(), p.level().dimension().location().toString(),
            BombRelease.pointGuided(a) ? null : requested, target));
    return true;
  }

  public static boolean acquireCurrent(ServerPlayer p, UUID target, Vec3 point) {
    if (!(p.getVehicle() instanceof VehicleEntity v)
        || !LoadoutService.pilot(p, v)
        || !LoadoutService.supported(v)) return false;
    var s = LoadoutService.state(v);
    if (s.entries.isEmpty()) return false;
    AmmoType a = LoadoutData.ammo().get(s.entries.getFirst().ammo());
    return a != null && acquire(p, v, a, target, point);
  }

  /** 仅供服务器已验证的吊舱射线调用；不能将客户端坐标作为吊舱锁定结果。 */
  static boolean acquireFromPod(
      ServerPlayer p, VehicleEntity v, AmmoType a, UUID target, Vec3 point) {
    var session = TargetingPod.current(p);
    if (session == null
        || session.point == null
        || !session.point.equals(point)
        || !Objects.equals(session.target, target)
        || !BombRelease.acceptsPointLock(a)) return false;
    LOCKS.put(
        p.getUUID(),
        new Lock(
            v.getUUID(), a.id(), p.level().dimension().location().toString(),
            BombRelease.pointGuided(a) ? null : target, point, true));
    return true;
  }

  public static Lock current(ServerPlayer p, VehicleEntity v, AmmoType a) {
    Lock lock = LOCKS.get(p.getUUID());
    if (lock == null
        || !lock.vehicle.equals(v.getUUID())
        || !lock.ammo.equals(a.id())
        || !lock.dimension.equals(p.level().dimension().location().toString())) return null;
    Vec3 point = lock.point;
    if (lock.target != null) {
      Entity e = p.serverLevel().getEntity(lock.target);
      if (e == null || !e.isAlive()) return null;
      point = e.getBoundingBox().getCenter();
    }
    Vec3 origin = lock.pod ? TargetingPod.origin(v, 1) : p.getEyePosition();
    if ((lock.pod && !TargetingPod.within(v, point))
        || !AimSupport.inCone(origin, v.getViewVector(1), point, a.seekRange(), a.seekAngle())
        || !AimSupport.visible(p.serverLevel(), p, origin, point)) return null;
    return new Lock(lock.vehicle, lock.ammo, lock.dimension, lock.target, point, lock.pod);
  }

  public static void remove(UUID player) {
    LOCKS.remove(player);
  }

  public static void clear() {
    LOCKS.clear();
  }
}
