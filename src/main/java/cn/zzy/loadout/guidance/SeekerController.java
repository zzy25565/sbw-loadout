package cn.zzy.loadout.guidance;

import com.atsuishio.superbwarfare.entity.projectile.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** 导引头状态存入实体 ForgeData；限角改变速度方向，保留原生推进、重力、碰撞和爆炸。 */
public final class SeekerController {
  public static final String KEY = "SBWLoadoutSeeker";

  public static void arm(
      Projectile projectile,
      SeekerMode mode,
      UUID source,
      int code,
      UUID target,
      Vec3 point,
      double range,
      double angle,
      double turn) {
    if (!(projectile instanceof DestroyableProjectile)
        || projectile.level().isClientSide
        || mode == SeekerMode.OTHER
        || !AimSupport.finite(point)
        || range <= 0
        || range > 65536
        || angle < 0
        || angle > 180
        || turn <= 0
        || turn > 45
        || !Double.isFinite(range + angle + turn))
      throw new IllegalArgumentException("Invalid seeker/projectile");
    if (!projectile
        .getType()
        .is(com.atsuishio.superbwarfare.init.ModTags.EntityTypes.DESTROYABLE_PROJECTILE))
      throw new IllegalArgumentException(
          "Projectile type must be in superbwarfare:destroyable_projectile for CIWS interception");
    if (mode == SeekerMode.LASER && (source == null || code < 1 || code > 9999))
      throw new IllegalArgumentException("Laser source required");
    CompoundTag n = new CompoundTag();
    n.putString("Mode", mode.name());
    n.putString("Dimension", projectile.level().dimension().location().toString());
    n.putInt("LaserCode", code);
    n.putDouble("Range", range);
    n.putDouble("Angle", angle);
    n.putDouble("Turn", turn);
    if (source != null) n.putUUID("Source", source);
    if (target != null) n.putUUID("Target", target);
    writePoint(n, point);
    n.putString("Status", "ACQUIRED");
    projectile.getPersistentData().put(KEY, n);
    neutralize(projectile);
  }

  static void neutralize(Projectile p) {
    if (p instanceof ITrackableProjectile nativeSeeker) {
      nativeSeeker.setTargetUUID("none");
      nativeSeeker.setTargetPos(null);
      nativeSeeker.setGuideType(1);
    }
    if (p instanceof MissileProjectile missile) {
      missile.setLostTargetTick(0);
      missile.setDistracted(true);
    }
  }

  public static void tick(Projectile projectile) {
    if (projectile.isRemoved()
        || projectile.level().isClientSide
        || !projectile.getPersistentData().contains(KEY)) return;
    CompoundTag n = projectile.getPersistentData().getCompound(KEY);
    ServerLevel level = (ServerLevel) projectile.level();
    if (CruiseGuidance.active(projectile)) {
      CruiseGuidance.tick(projectile);
      return;
    }
    if (!n.getString("Dimension").equals(level.dimension().location().toString())) {
      n.putString("Status", "WRONG_DIMENSION");
      neutralize(projectile);
      return;
    }
    if (SeekerMode.of(n.getString("Mode")) == SeekerMode.RADAR) {
      RadarGuidance.tick(projectile);
      return;
    }
    neutralize(projectile);
    SeekerMode mode = SeekerMode.of(n.getString("Mode"));
    Vec3 point = null;
    Vec3 forward =
        projectile.getDeltaMovement().lengthSqr() > .00001
            ? projectile.getDeltaMovement().normalize()
            : projectile.getLookAngle();
    double range = n.getDouble("Range"), angle = n.getDouble("Angle");
    if (mode == SeekerMode.LASER) {
      var spot =
          n.hasUUID("Source")
              ? LaserDesignations.resolve(level, n.getUUID("Source"), n.getInt("LaserCode"))
              : null;
      if (spot != null
          && AimSupport.inCone(projectile.position(), forward, spot.point(), range, angle)
          && AimSupport.visible(level, projectile, projectile.position(), spot.point())) {
        point = spot.point();
        writePoint(n, point);
        n.putString("Status", "TRACKING_LASER");
      } else {
        point = readPoint(n);
        n.putString("Status", "LASER_INERTIAL");
      }
    } else if (mode == SeekerMode.TV) {
      point = readPoint(n);
      if (n.hasUUID("Target")) {
        Entity target = level.getEntity(n.getUUID("Target"));
        if (target != null
            && target.isAlive()
            && AimSupport.inCone(
                projectile.position(), forward, target.getBoundingBox().getCenter(), range, angle)
            && AimSupport.visible(
                level, projectile, projectile.position(), target.getBoundingBox().getCenter())) {
          point = target.getBoundingBox().getCenter();
          writePoint(n, point);
          n.putString("Status", "TRACKING_TV_ENTITY");
        } else n.putString("Status", "TV_LAST_KNOWN");
      } else n.putString("Status", "TRACKING_TV_POINT");
    }
    if (point != null
        && AimSupport.finite(point)
        && projectile.position().distanceToSqr(point) > .04) {
      Vec3 before = projectile.getDeltaMovement();
      double speed = before.length();
      if (speed > .00001) {
        Vec3 desired = point.subtract(projectile.position()).normalize();
        if (before.normalize().dot(desired) < 0) {
          n.putString("Status", n.getString("Status") + "_PASSED");
          return;
        }
        Vec3 direction =
            rotateTowards(
                before.normalize(), desired, Math.toRadians(Math.min(6, n.getDouble("Turn"))));
        projectile.setDeltaMovement(direction.scale(speed));
        projectile.setYRot((float) -Math.toDegrees(Math.atan2(direction.x, direction.z)));
        projectile.setXRot(
            (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
        projectile.hasImpulse = true;
      }
    }
  }

  /** 球面插值只改变方向，角速度单位为弧度/刻；180 度反向也选确定的旋转平面。 */
  public static Vec3 rotateTowards(Vec3 from, Vec3 to, double radians) {
    double dot = Math.max(-1, Math.min(1, from.dot(to))), angle = Math.acos(dot);
    if (angle <= radians) return to;
    if (angle < 1e-8) return from;
    Vec3 tangent = to.subtract(from.scale(dot));
    if (tangent.lengthSqr() < 1e-12)
      tangent = from.cross(Math.abs(from.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
    return from.scale(Math.cos(radians))
        .add(tangent.normalize().scale(Math.sin(radians)))
        .normalize();
  }

  public static Vec3 readPoint(CompoundTag n) {
    return new Vec3(n.getDouble("X"), n.getDouble("Y"), n.getDouble("Z"));
  }

  private static void writePoint(CompoundTag n, Vec3 p) {
    n.putDouble("X", p.x);
    n.putDouble("Y", p.y);
    n.putDouble("Z", p.z);
  }
}
