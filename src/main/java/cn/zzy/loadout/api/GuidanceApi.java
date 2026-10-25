package cn.zzy.loadout.api;

import cn.zzy.loadout.guidance.*;
import java.util.UUID;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** 给第三方发射代码接入导引头；在服务端弹丸加入世界前调用，模型与弹丸注册无需改变。 */
public final class GuidanceApi {
  public static void laser(
      Projectile p,
      UUID designator,
      int code,
      Vec3 initialSpot,
      double range,
      double angle,
      double turnDegrees) {
    SeekerController.arm(
        p, SeekerMode.LASER, designator, code, null, initialSpot, range, angle, turnDegrees);
  }

  public static void television(
      Projectile p,
      UUID lockedEntity,
      Vec3 lockedPoint,
      double range,
      double angle,
      double turnDegrees) {
    SeekerController.arm(
        p, SeekerMode.TV, null, 0, lockedEntity, lockedPoint, range, angle, turnDegrees);
  }
}
