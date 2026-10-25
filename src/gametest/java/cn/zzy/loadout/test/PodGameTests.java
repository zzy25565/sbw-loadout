package cn.zzy.loadout.test;

import static cn.zzy.loadout.test.SeekerGameTests.*;

import cn.zzy.loadout.*;
import cn.zzy.loadout.guidance.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class PodGameTests {
  static Entity target(GameTestHelper h, VehicleEntity v, float yaw, float pitch) {
    var e = EntityType.PIG.create(h.getLevel());
    e.setNoGravity(true);
    e.setNoAi(true);
    e.setPos(
        TargetingPod.origin(v, 1)
            .add(TargetingPod.direction(yaw, pitch).scale(8))
            .add(0, -.45, 0));
    h.getLevel().addFreshEntity(e);
    return e;
  }

  static void aimAt(net.minecraft.server.level.ServerPlayer p, VehicleEntity v, Vec3 point) {
    Vec3 d = point.subtract(TargetingPod.origin(v, 1));
    okDummy(TargetingPod.aim(p, TargetingPod.yaw(d), TargetingPod.pitch(d)));
  }

  static void okDummy(boolean value) {
    if (!value) throw new IllegalStateException("pod aim failed");
  }

  @GameTest(template = "empty")
  public static void onlyPilotAndFiniteAngles(GameTestHelper h) {
    var p = player(h);
    ok(h, !TargetingPod.open(p), "on-foot player cannot open aircraft sensor");
    var v = plane(h, p, "fixture_addon:tv_bomb");
    ok(h, TargetingPod.open(p), "generic configured aircraft pilot can open sensor");
    ok(h, !TargetingPod.aim(p, Double.NaN, 0), "non-finite sensor input rejected");
    ok(h, TargetingPod.aim(p, 179, 100), "finite input is safely clamped");
    var s = TargetingPod.current(p);
    ok(h, s.yaw == 150 && s.pitch == 89, "mechanical gimbal bounds apply");
    p.stopRiding();
    ok(h, TargetingPod.current(p) == null, "dismount invalidates sensor authority");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void podRayIsIndependentOfPilotLook(GameTestHelper h) {
    var p = player(h);
    var v = plane(h, p, "fixture_addon:tv_bomb");
    var e = target(h, v, 30, 5);
    p.setYRot(180);
    p.setXRot(-20);
    TargetingPod.open(p);
    aimAt(p, v, e.getBoundingBox().getCenter());
    ok(h, TargetingPod.lock(p, false), "server pod ray locks actual off-axis target");
    var s = TargetingPod.current(p);
    ok(h, e.getUUID().equals(s.target), "nearest visible entity is server-selected");
    var a = TargetingPod.firstAmmo(v);
    var lock = TelevisionLocks.current(p, v, a);
    ok(
        h,
        lock != null && lock.pod() && e.getUUID().equals(lock.target()),
        "pod target reaches TV seeker");
    ok(
        h,
        p.getYRot() == 180 && p.getXRot() == -20 && v.getYRot() == 0,
        "sensor input never rotates pilot or aircraft");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void areaStabilizesWorldPointAndObeysOcclusion(GameTestHelper h) {
    var p = player(h);
    var v = plane(h, p, "fixture_addon:tv_bomb");
    BlockPos wall = BlockPos.containing(TargetingPod.origin(v, 1).add(0, 0, 12));
    for (int x = -5; x <= 5; x++)
      for (int y = -3; y <= 3; y++)
        h.getLevel().setBlock(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
    TargetingPod.open(p);
    TargetingPod.aim(p, 0, 0);
    ok(h, TargetingPod.lock(p, true), "surface point stabilizes from real pod camera");
    var s = TargetingPod.current(p);
    Vec3 fixed = s.point;
    float yaw = s.yaw;
    v.setPos(v.position().add(2, 0, 0));
    TargetingPod.update(p);
    ok(
        h,
        s.point.equals(fixed) && Math.abs(s.yaw - yaw) > 1,
        "camera keeps world point as aircraft translates");
    Vec3 block = TargetingPod.origin(v, 1).lerp(fixed, .5);
    BlockPos c = BlockPos.containing(block);
    for (int x = -1; x <= 1; x++)
      for (int y = -1; y <= 1; y++)
        h.getLevel().setBlock(c.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
    TargetingPod.update(p);
    ok(
        h,
        s.mode.equals("LOST") && s.point.equals(fixed),
        "terrain occlusion loses observation without inventing a new point");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void trackingDoesNotGrantOutOfEnvelopeLaunch(GameTestHelper h) {
    var p = player(h);
    var v = plane(h, p, "sbw_loadout:kh29td");
    var e = target(h, v, 65, 0);
    TargetingPod.open(p);
    aimAt(p, v, e.getBoundingBox().getCenter());
    ok(h, TargetingPod.lock(p, false), "wide pod gimbal can identify side target");
    ok(
        h,
        TelevisionLocks.current(p, v, TargetingPod.firstAmmo(v)) == null,
        "weapon release cone remains stricter than sensor cone");
    LoadoutService.fire(p, v, null, null);
    ok(h, LoadoutService.state(v).entries.size() == 1, "out-of-envelope launch consumes no ammo");
    v.setYRot(-100);
    TargetingPod.update(p);
    // 转到镜头背后后，原目标不能被任意别的实体替换。
    ok(
        h,
        e.getUUID().equals(TargetingPod.current(p).target),
        "sensor retains target identity through maneuver");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void podLaunchTracksAfterLeavingCameraAndAircraft(GameTestHelper h) {
    var p = player(h);
    var v = plane(h, p, "fixture_addon:tv_bomb");
    var e = target(h, v, 5, 0);
    TargetingPod.open(p);
    aimAt(p, v, e.getBoundingBox().getCenter());
    ok(h, TargetingPod.lock(p, false), "pod confirms TV target");
    TargetingPod.close(p.getUUID());
    LoadoutService.fire(p, v, null, null);
    ok(
        h,
        LoadoutService.state(v).entries.isEmpty(),
        "returning to cockpit preserves valid prelaunch target");
    Projectile shot =
        h.getLevel().getEntitiesOfClass(Projectile.class, v.getBoundingBox().inflate(20)).stream()
            .filter(x -> x.getOwner() == p && x.getPersistentData().contains(SeekerController.KEY))
            .findFirst()
            .orElseThrow();
    p.stopRiding();
    e.setPos(e.position().add(1, 0, 0));
    SeekerController.tick(shot);
    var tag = shot.getPersistentData().getCompound(SeekerController.KEY);
    ok(
        h,
        tag.getString("Status").equals("TRACKING_TV_ENTITY")
            && tag.getUUID("Target").equals(e.getUUID()),
        "released TV shot remains autonomous after pod closes and pilot leaves");
    h.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 70)
  public static void missingClientHeartbeatExpiresSession(GameTestHelper h) {
    var p = player(h);
    plane(h, p, "fixture_addon:tv_bomb");
    TargetingPod.open(p);
    h.runAfterDelay(
        42,
        () -> {
          ok(
              h,
              TargetingPod.current(p) == null,
              "lost/disconnected UI heartbeat cannot leave a live sensor session");
          h.succeed();
        });
  }
}
