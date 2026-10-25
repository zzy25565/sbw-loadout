package cn.zzy.loadout.guidance;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class AimSupport {
  public record Hit(Vec3 point, UUID entity) {}

  public static boolean finite(Vec3 v) {
    return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
  }

  /** 只遍历已加载区块；远距离瞄准不会请求生成新地形。 */
  public static double loadedDistance(ServerLevel level, Vec3 from, Vec3 direction, double range) {
    Vec3 d = direction.normalize();
    int cx = (int) Math.floor(from.x / 16),
        cz = (int) Math.floor(from.z / 16),
        sx = d.x > 0 ? 1 : -1,
        sz = d.z > 0 ? 1 : -1;
    double
        tx =
            Math.abs(d.x) < 1e-12
                ? Double.POSITIVE_INFINITY
                : ((cx + (d.x > 0 ? 1 : 0)) * 16 - from.x) / d.x,
        tz =
            Math.abs(d.z) < 1e-12
                ? Double.POSITIVE_INFINITY
                : ((cz + (d.z > 0 ? 1 : 0)) * 16 - from.z) / d.z,
        dx = Math.abs(d.x) < 1e-12 ? Double.POSITIVE_INFINITY : 16 / Math.abs(d.x),
        dz = Math.abs(d.z) < 1e-12 ? Double.POSITIVE_INFINITY : 16 / Math.abs(d.z),
        at = 0;
    while (at <= range) {
      if (!level.hasChunk(cx, cz)) return Math.max(0, at - 1e-5);
      if (tx < tz) {
        at = tx;
        tx += dx;
        cx += sx;
      } else if (tz < tx) {
        at = tz;
        tz += dz;
        cz += sz;
      } else {
        at = tx;
        tx += dx;
        tz += dz;
        cx += sx;
        cz += sz;
      }
      if (!Double.isFinite(at)) break;
    }
    return range;
  }

  public static boolean loaded(ServerLevel level, Vec3 from, Vec3 to) {
    if (!finite(from) || !finite(to)) return false;
    double distance = from.distanceTo(to);
    return distance <= 65536
        && loadedDistance(level, from, to.subtract(from), distance) >= distance;
  }

  public static boolean visible(ServerLevel level, Entity context, Vec3 from, Vec3 to) {
    if (!loaded(level, from, to)) return false;
    var hit =
        level.clip(
            new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
    return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < .04;
  }

  public static boolean inCone(Vec3 from, Vec3 forward, Vec3 to, double range, double angle) {
    if (!finite(from)
        || !finite(forward)
        || !finite(to)
        || !Double.isFinite(range)
        || !Double.isFinite(angle)) return false;
    Vec3 d = to.subtract(from);
    return d.lengthSqr() > .0001
        && d.lengthSqr() <= range * range
        && forward.normalize().dot(d.normalize()) >= Math.cos(Math.toRadians(angle)) - 1e-8;
  }

  /** 实体必须在准星射线上，且优先于其后被方块遮挡的目标。 */
  public static Hit ray(ServerLevel level, Entity viewer, Vec3 from, Vec3 direction, double range) {
    if (!finite(from)
        || !finite(direction)
        || direction.lengthSqr() < 1e-10
        || !Double.isFinite(range)
        || range <= 0
        || range > 65536) return null;
    double distance = loadedDistance(level, from, direction, range);
    Vec3 end = from.add(direction.normalize().scale(distance));
    HitResult block =
        level.clip(
            new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
    double nearest =
        block.getType() == HitResult.Type.MISS
            ? distance * distance
            : from.distanceToSqr(block.getLocation());
    Hit result = block.getType() == HitResult.Type.MISS ? null : new Hit(block.getLocation(), null);
    AABB bounds = new AABB(from, end).inflate(1);
    for (Entity e :
        level.getEntities(
            viewer,
            bounds,
            e ->
                e.isAlive()
                    && e.isPickable()
                    && e.getRootVehicle() != viewer.getRootVehicle()
                    && !e.isSpectator())) {
      Optional<Vec3> hit = e.getBoundingBox().clip(from, end);
      if (hit.isPresent() && hit.get().distanceToSqr(from) < nearest) {
        nearest = hit.get().distanceToSqr(from);
        result = new Hit(hit.get(), e.getUUID());
      }
    }
    return result;
  }
}
