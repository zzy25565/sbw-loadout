package cn.zzy.loadout.guidance;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.AmmoType;
import cn.zzy.loadout.data.LoadoutData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** 通用机腹光电吊舱。客户端只传镜头角度，目标由服务器射线确认；不转动玩家或机体。 */
public final class TargetingPod {
  public static final double YAW_LIMIT = 150, MIN_PITCH = -25, MAX_PITCH = 89;
  private static final Map<UUID, Session> SESSIONS = new HashMap<>();

  public static final class Session {
    final UUID vehicle;
    final String dimension;
    long heartbeat, aimed = Long.MIN_VALUE;
    public float yaw, pitch;
    public UUID target;
    public Vec3 point;
    public String mode = "FREE";
    public boolean laser, view = true;
    public int laserCode = 1688;

    Session(ServerPlayer p, VehicleEntity v) {
      vehicle = v.getUUID();
      dimension = p.level().dimension().location().toString();
      heartbeat = p.level().getGameTime();
      yaw = v.getYRot();
      pitch = 25;
    }
  }

  /** 机腹中央下方 0.35 格；客户端插值和服务端锁定使用同一几何定义。 */
  public static Vec3 origin(VehicleEntity v, float partial) {
    return v.getPosition(partial).add(0, -.35, 0);
  }

  public static Vec3 direction(float yaw, float pitch) {
    return Vec3.directionFromRotation(pitch, yaw);
  }

  public static float yaw(Vec3 d) {
    return (float) -Math.toDegrees(Math.atan2(d.x, d.z));
  }

  public static float pitch(Vec3 d) {
    return (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
  }

  public static float clampYaw(float yaw, float bodyYaw) {
    return bodyYaw
        + Mth.clamp(Mth.wrapDegrees(yaw - bodyYaw), (float) -YAW_LIMIT, (float) YAW_LIMIT);
  }

  public static boolean within(VehicleEntity v, Vec3 point) {
    Vec3 d = point.subtract(origin(v, 1));
    return AimSupport.finite(point)
        && d.lengthSqr() > .0001
        && Math.abs(Mth.wrapDegrees(yaw(d) - v.getYRot())) <= YAW_LIMIT
        && pitch(d) >= MIN_PITCH
        && pitch(d) <= MAX_PITCH;
  }

  public static VehicleEntity vehicle(ServerPlayer p) {
    return p.isAlive()
            && !p.isSpectator()
            && LoadoutMod.POD_ENABLED.get()
            && p.getVehicle() instanceof VehicleEntity v
            && LoadoutService.pilot(p, v)
            && LoadoutService.supported(v)
        ? v
        : null;
  }

  public static Session current(ServerPlayer p) {
    Session s = SESSIONS.get(p.getUUID());
    VehicleEntity v = vehicle(p);
    if (s == null) return null;
    if (v == null
        || !s.vehicle.equals(v.getUUID())
        || !s.dimension.equals(p.level().dimension().location().toString())
        || p.hasDisconnected()
        || (!s.view && !s.laser)
        || (s.view && p.level().getGameTime() - s.heartbeat > 40)) {
      SESSIONS.remove(p.getUUID());
      return null;
    }
    return s;
  }

  public static boolean open(ServerPlayer p) {
    VehicleEntity v = vehicle(p);
    if (v == null) return false;
    Session s = current(p);
    if (s == null) SESSIONS.put(p.getUUID(), new Session(p, v));
    else {
      s.view = true;
      s.heartbeat = p.level().getGameTime();
    }
    return true;
  }

  public static boolean aim(ServerPlayer p, double yaw, double pitch) {
    Session s = current(p);
    VehicleEntity v = vehicle(p);
    if (s == null
        || v == null
        || !Double.isFinite(yaw)
        || !Double.isFinite(pitch)
        || Math.abs(yaw) > 1e7
        || Math.abs(pitch) > 360) return false;
    long now = p.level().getGameTime();
    s.heartbeat = now;
    if (s.aimed == now) return true;
    s.aimed = now;
    if (s.point == null) {
      s.yaw = clampYaw((float) yaw, v.getYRot());
      s.pitch = Mth.clamp((float) pitch, (float) MIN_PITCH, (float) MAX_PITCH);
    }
    return true;
  }

  public static double range(ServerPlayer p) {
    return LoadoutMod.POD_RANGE.get();
  }

  public static AimSupport.Hit hit(ServerPlayer p) {
    Session s = current(p);
    VehicleEntity v = vehicle(p);
    return s == null || v == null
        ? null
        : AimSupport.ray(p.serverLevel(), p, origin(v, 1), direction(s.yaw, s.pitch), range(p));
  }

  /** stabilize=true 为固定地面点；false 为实体/位置自动识别。 */
  public static boolean lock(ServerPlayer p, boolean stabilize) {
    Session s = current(p);
    VehicleEntity v = vehicle(p);
    if (s == null || v == null) return false;
    var hit = hit(p);
    if (hit == null) return false;
    s.target = stabilize ? null : hit.entity();
    s.point = hit.point();
    if (s.target != null) {
      Entity e = p.serverLevel().getEntity(s.target);
      if (e == null || !e.isAlive()) return false;
      s.point = e.getBoundingBox().getCenter();
    }
    s.mode = s.target != null ? "TRACK" : "AREA";
    update(p);
    // 传感器可以观察侧后方；弹药仍必须通过自己的发射射界与距离校验。
    AmmoType a = firstAmmo(v);
    if (a != null && SeekerMode.of(a.guidance()) == SeekerMode.TV)
      TelevisionLocks.acquireFromPod(p, v, a, s.target, s.point);
    return true;
  }

  public static AmmoType firstAmmo(VehicleEntity v) {
    var entries = LoadoutService.state(v).entries;
    return entries.isEmpty() ? null : LoadoutData.ammo().get(entries.getFirst().ammo());
  }

  public static void unlock(ServerPlayer p) {
    Session s = current(p);
    if (s == null) return;
    s.laser = false;
    s.point = null;
    s.target = null;
    s.mode = "FREE";
    TelevisionLocks.remove(p.getUUID());
  }

  public static void update(ServerPlayer p) {
    Session s = current(p);
    VehicleEntity v = vehicle(p);
    if (s == null || v == null) return;
    s.yaw = clampYaw(s.yaw, v.getYRot());
    if (s.point == null) return;
    Vec3 point = s.point;
    if (s.target != null) {
      Entity e = p.serverLevel().getEntity(s.target);
      if (e == null || !e.isAlive()) {
        s.mode = "LOST";
        return;
      }
      point = e.getBoundingBox().getCenter();
    }
    if (!within(v, point)) {
      s.mode = "LIMIT";
      return;
    }
    if (origin(v, 1).distanceToSqr(point) > range(p) * range(p)
        || !AimSupport.visible(p.serverLevel(), p, origin(v, 1), point)) {
      s.mode = "LOST";
      return;
    }
    s.point = point;
    Vec3 d = point.subtract(origin(v, 1));
    s.yaw = yaw(d);
    s.pitch = pitch(d);
    s.mode = s.target == null ? "AREA" : "TRACK";
  }

  public static JsonObject snapshot(ServerPlayer p) {
    JsonObject j = new JsonObject();
    Session s = current(p);
    j.addProperty("active", s != null);
    if (s == null) return j;
    j.addProperty("vehicle", s.vehicle.toString());
    j.addProperty("yaw", s.yaw);
    j.addProperty("pitch", s.pitch);
    j.addProperty("mode", s.mode);
    j.addProperty("laser_on", s.laser);
    j.addProperty("laser_code", s.laserCode);
    j.addProperty("laser_hit", laser(p) != null);
    j.addProperty("range", range(p));
    Vec3 point = s.point;
    if (point == null) {
      var hit = hit(p);
      if (hit != null) point = hit.point();
    }
    if (point != null) {
      j.addProperty("x", point.x);
      j.addProperty("y", point.y);
      j.addProperty("z", point.z);
    }
    if (s.target != null) j.addProperty("target", s.target.toString());
    return j;
  }

  /** 返回驾驶视角后，已稳定的激光继续照射；退出载具/断线会失去发射源权限。 */
  public static void closeView(ServerPlayer p) {
    Session s = current(p);
    if (s != null && s.laser && s.point != null) s.view = false;
    else close(p.getUUID());
  }

  /** 开启时先稳定实际可见落点。编码使用服务端配置，不能由任意客户端伪造落点。 */
  public static boolean toggleLaser(ServerPlayer p) {
    Session s = current(p);
    if (s == null) return false;
    if (s.laser) {
      s.laser = false;
      return true;
    }
    if (s.point == null && !lock(p, true)) return false;
    update(p);
    if (!s.mode.equals("AREA") && !s.mode.equals("TRACK")) return false;
    s.laserCode = LoadoutMod.AIRCRAFT_LASER_CODE.get();
    s.laser = true;
    LaserDesignations.tick(p);
    return true;
  }

  public static boolean laserEnabled(ServerPlayer p) {
    Session s = current(p);
    return s != null && s.laser;
  }

  public static LaserDesignations.Spot laser(ServerPlayer p) {
    Session s = current(p);
    VehicleEntity v = vehicle(p);
    if (s == null || v == null || !s.laser || s.point == null) return null;
    update(p);
    if ((!s.mode.equals("AREA") && !s.mode.equals("TRACK"))
        || origin(v, 1).distanceToSqr(s.point) > Math.pow(LoadoutMod.LASER_RANGE.get(), 2))
      return null;
    Vec3 point = s.point;
    if (s.target != null) {
      // 光斑落在被跟踪实体的可见表面，不能埋进实体中心或穿过前景实体。
      Vec3 d = point.subtract(origin(v, 1));
      var hit = AimSupport.ray(p.serverLevel(), p, origin(v, 1), d, d.length() + .1);
      if (hit == null || !s.target.equals(hit.entity())) return null;
      point = hit.point();
    }
    return new LaserDesignations.Spot(
        v.getUUID(), s.laserCode, origin(v, 1), point, p.serverLevel(), true);
  }

  public static void close(UUID player) {
    SESSIONS.remove(player);
  }

  public static void clear() {
    SESSIONS.clear();
  }
}
