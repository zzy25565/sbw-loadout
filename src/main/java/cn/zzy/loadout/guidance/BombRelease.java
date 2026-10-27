package cn.zzy.loadout.guidance;

import cn.zzy.loadout.LoadoutMod;
import cn.zzy.loadout.LoadoutService;
import cn.zzy.loadout.api.AmmoType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.Locale;
import net.minecraft.server.level.ServerPlayer;

/** 航弹的投放误差只在释放瞬间采样；有效锁定由服务端现有导引校验决定。 */
public final class BombRelease {
  public static boolean isBomb(AmmoType a) {
    return switch (a.category().toUpperCase(Locale.ROOT)) {
      case "BOMB", "GUIDEDBOMB", "UNGUIDEDBOMB", "AERIALBOMB" -> true;
      default -> false;
    };
  }

  public static boolean pointGuided(AmmoType a) {
    String mode = a.guidance().toUpperCase(Locale.ROOT);
    return isBomb(a) && (mode.startsWith("INS") || mode.startsWith("GNSS") || mode.startsWith("GPS"));
  }

  public static boolean acceptsPointLock(AmmoType a) {
    return SeekerMode.of(a.guidance()) == SeekerMode.TV || pointGuided(a);
  }

  public static double spread(AmmoType a, boolean guided) {
    return isBomb(a) ? (guided ? 0 : Math.max(a.spread(), LoadoutMod.BOMB_RELEASE_SPREAD.get())) : a.spread();
  }

  public static boolean ready(ServerPlayer p, VehicleEntity v, AmmoType a) {
    if (!isBomb(a)) return false;
    if (acceptsPointLock(a)) return TelevisionLocks.current(p, v, a) != null;
    if (SeekerMode.of(a.guidance()) == SeekerMode.LASER)
      return LaserDesignations.select(p, a.laserCode(), LoadoutService.launchOrigin(v),
          v.getViewVector(1).normalize(), a.seekRange(), a.seekAngle()) != null;
    if (SeekerMode.of(a.guidance()) == SeekerMode.RADAR)
      return RadarLocks.current(p, v, a) != null && RadarGuidance.canLaunch(v, a);
    return false;
  }
}
