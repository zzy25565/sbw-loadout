package cn.zzy.loadout.api;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.world.entity.Entity;

/** 可选雷达附属桥接；只在服务端主线程调用，挂载模组无需依赖雷达 JAR。 */
public final class RadarLink {
  public interface Provider {
    Entity lockedTarget(VehicleEntity launcher);
    default Entity launchTarget(VehicleEntity launcher, AmmoType ammo) { return lockedTarget(launcher); }
    default Entity trackedTarget(VehicleEntity launcher, java.util.UUID assigned) {
      Entity target = lockedTarget(launcher);
      return target != null && target.getUUID().equals(assigned) ? target : null;
    }
    default boolean hasInfraredControl() { return false; }
    default Entity infraredTarget(VehicleEntity launcher) { return null; }
    default void infraredLaunched(net.minecraft.world.entity.projectile.Projectile projectile,
        net.minecraft.server.level.ServerPlayer owner, VehicleEntity launcher, Entity target, AmmoType ammo) {}
    default int otherChannels(VehicleEntity launcher) { return 0; }
    default int maxChannels(VehicleEntity launcher, int ammoLimit) { return ammoLimit; }
  }
  private static Provider provider;
  public static void install(Provider value) { provider = java.util.Objects.requireNonNull(value); }
  public static void uninstall(Provider value) { if(provider==value) provider=null; }
  public static Provider provider() { return provider; }
  private RadarLink() {}
}
