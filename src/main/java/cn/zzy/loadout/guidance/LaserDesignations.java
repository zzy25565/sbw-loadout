package cn.zzy.loadout.guidance;

import cn.zzy.loadout.LoadoutMod;
import com.atsuishio.superbwarfare.init.ModItems;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** 照射点是短期服务端观测，不存档；源停止使用或离开维度后立即失效。 */
public final class LaserDesignations {
  public record Spot(
      UUID source, int code, Vec3 from, Vec3 point, ServerLevel level, boolean aircraft) {}

  private static final Map<UUID, WeakReference<ServerPlayer>> SOURCES = new HashMap<>();

  public static boolean illuminating(ServerPlayer p) {
    return p.isAlive()
        && !p.isSpectator()
        && !p.hasDisconnected()
        && p.isUsingItem()
        && p.getUseItem().is(ModItems.ARTILLERY_INDICATOR.get())
        && p.getItemInHand(p.getUsedItemHand()).is(ModItems.ARTILLERY_INDICATOR.get());
  }

  public static int code(ItemStack stack) {
    var data = stack.get(DataComponents.CUSTOM_DATA);
    int n = data == null ? 0 : data.copyTag().getInt("SBWLoadoutLaserCode");
    return n >= 1 && n <= 9999 ? n : 1688;
  }

  private static Spot handSample(ServerPlayer p) {
    if (!illuminating(p)) return null;
    var level = p.serverLevel();
    var hit =
        AimSupport.ray(
            level, p, p.getEyePosition(), p.getLookAngle(), LoadoutMod.LASER_RANGE.get());
    return hit == null || hit.point().distanceToSqr(p.getEyePosition()) < 1e-6
        ? null
        : new Spot(
            p.getUUID(), code(p.getUseItem()), p.getEyePosition(), hit.point(), level, false);
  }

  public static Spot sample(ServerPlayer p) {
    var airborne = TargetingPod.laser(p);
    return airborne != null ? airborne : handSample(p);
  }

  private static List<Spot> samples(ServerPlayer p) {
    List<Spot> list = new ArrayList<>(2);
    var hand = handSample(p);
    var airborne = TargetingPod.laser(p);
    if (hand != null) list.add(hand);
    if (airborne != null) list.add(airborne);
    return list;
  }

  public static void tick(ServerPlayer p) {
    if (illuminating(p) || TargetingPod.laserEnabled(p))
      SOURCES.put(p.getUUID(), new WeakReference<>(p));
    else SOURCES.remove(p.getUUID());
  }

  public static Spot resolve(ServerLevel level, UUID source, int code) {
    for (var ref : SOURCES.values()) {
      ServerPlayer p = ref.get();
      if (p == null || p.level() != level) continue;
      for (Spot spot : samples(p)) if (spot.source.equals(source) && spot.code == code) return spot;
    }
    return null;
  }

  public static Spot select(
      ServerPlayer shooter, int code, Vec3 origin, Vec3 direction, double range, double angle) {
    tick(shooter);
    List<Spot> spots = new ArrayList<>();
    for (var it = SOURCES.entrySet().iterator(); it.hasNext(); ) {
      var entry = it.next();
      var p = entry.getValue().get();
      if (p == null) {
        it.remove();
        continue;
      }
      if (p.level() != shooter.level() || !(p == shooter || shooter.isAlliedTo(p))) continue;
      for (Spot s : samples(p)) {
        if (s != null
            && s.code == code
            && AimSupport.inCone(origin, direction, s.point, range, angle)
            && AimSupport.visible(s.level, shooter, origin, s.point)) spots.add(s);
      }
    }
    spots.sort(
        Comparator.comparingInt(
                (Spot s) ->
                    s.source.equals(shooter.getUUID())
                            || (shooter.getVehicle() != null
                                && s.source.equals(shooter.getVehicle().getUUID()))
                        ? 0
                        : 1)
            .thenComparingDouble(s -> s.point.distanceToSqr(origin))
            .thenComparing(s -> s.source.toString()));
    return spots.isEmpty() ? null : spots.getFirst();
  }

  public static List<Spot> visibleTo(ServerPlayer viewer) {
    List<Spot> spots = new ArrayList<>();
    for (var ref : SOURCES.values()) {
      var p = ref.get();
      if (p == null || p.level() != viewer.level() || !(p == viewer || viewer.isAlliedTo(p)))
        continue;
      spots.addAll(samples(p));
    }
    return spots;
  }

  public static void remove(UUID player) {
    SOURCES.remove(player);
  }

  public static void clear() {
    SOURCES.clear();
  }
}
