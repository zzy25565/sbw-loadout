package cn.zzy.loadout.test;

import static cn.zzy.loadout.test.PodGameTests.*;
import static cn.zzy.loadout.test.SeekerGameTests.*;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.LoadoutData;
import cn.zzy.loadout.guidance.*;
import com.atsuishio.superbwarfare.entity.projectile.DestroyableProjectile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class AirborneLaserGameTests {
  static Projectile released(GameTestHelper h, net.minecraft.server.level.ServerPlayer p) {
    return h
        .getLevel()
        .getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(30))
        .stream()
        .filter(e -> e.getOwner() == p && e.getPersistentData().contains(SeekerController.KEY))
        .findFirst()
        .orElseThrow();
  }

  @GameTest(template = "empty", timeoutTicks = 90)
  public static void selfLaseContinuesAfterReturningToCockpit(GameTestHelper h) {
    var p = player(h);
    var v = plane(h, p, "sbw_loadout:kh29l");
    var e = target(h, v, 0, 0);
    ok(h, TargetingPod.open(p), "pilot opens common sensor");
    aimAt(p, v, e.getBoundingBox().getCenter());
    ok(
        h,
        TargetingPod.lock(p, false) && TargetingPod.toggleLaser(p),
        "aircraft illuminates tracked target without handheld item");
    var spot = TargetingPod.laser(p);
    ok(
        h,
        spot != null && spot.aircraft() && spot.source().equals(v.getUUID()),
        "aircraft UUID owns laser source, independently of handheld source");
    ok(
        h,
        spot.point().distanceTo(e.getBoundingBox().getCenter()) > .1,
        "tracked laser point remains on visible skin instead of inside entity");
    ok(
        h,
        LaserDesignations.resolve(h.getLevel(), v.getUUID(), 1689) == null,
        "mismatched code cannot guide a round");
    LoadoutService.fire(p, v, null, null);
    ok(h, LoadoutService.state(v).entries.isEmpty(), "Kh29L self-laser launch consumes round");
    var shot = released(h, p);
    SeekerController.tick(shot);
    ok(
        h,
        shot.getPersistentData()
            .getCompound(SeekerController.KEY)
            .getString("Status")
            .startsWith("TRACKING_LASER"),
        "released round uses aircraft beam");
    shot.discard();
    TargetingPod.closeView(p);
    h.runAfterDelay(
        45,
        () -> {
          ok(
              h,
              TargetingPod.laser(p) != null,
              "stable laser persists after view close beyond UI heartbeat timeout");
          p.stopRiding();
          ok(
              h,
              LaserDesignations.resolve(h.getLevel(), v.getUUID(), 1688) == null,
              "dismount immediately removes source permission");
          h.succeed();
        });
  }

  @GameTest(template = "empty")
  public static void aircraftLossAndReacquisitionRespectIdentityAndTurnLimit(GameTestHelper h) {
    var p = player(h);
    var v = plane(h, p, "sbw_loadout:kh29l");
    var e = target(h, v, 0, 0);
    TargetingPod.open(p);
    aimAt(p, v, e.getBoundingBox().getCenter());
    TargetingPod.lock(p, false);
    TargetingPod.toggleLaser(p);
    var spot = TargetingPod.laser(p);
    var shot = projectile(h, "agm_65", p);
    shot.setPos(TargetingPod.origin(v, 1).add(-2, 0, -2));
    GuidanceApi.laser(shot, v.getUUID(), 1688, spot.point(), 256, 90, 4);
    TargetingPod.toggleLaser(p);
    SeekerController.tick(shot);
    ok(
        h,
        shot.getPersistentData()
            .getCompound(SeekerController.KEY)
            .getString("Status")
            .equals("LASER_INERTIAL"),
        "stopped aircraft laser uses last observed point");
    e.setPos(e.position().add(1, 0, 0));
    TargetingPod.update(p);
    TargetingPod.toggleLaser(p);
    var before = shot.getDeltaMovement().normalize();
    SeekerController.tick(shot);
    var n = shot.getPersistentData().getCompound(SeekerController.KEY);
    ok(
        h,
        n.getString("Status").equals("TRACKING_LASER") && n.getUUID("Source").equals(v.getUUID()),
        "same aircraft source resumes guidance");
    double angle =
        Math.toDegrees(
            Math.acos(Math.clamp(before.dot(shot.getDeltaMovement().normalize()), -1, 1)));
    ok(h, angle <= 4.001, "reacquisition cannot snap round heading");
    var block =
        BlockPos.containing(TargetingPod.origin(v, 1).lerp(TargetingPod.current(p).point, .5));
    for (int x = -1; x <= 1; x++)
      for (int y = -1; y <= 1; y++)
        h.getLevel().setBlock(block.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
    ok(
        h,
        TargetingPod.laser(p) == null,
        "occlusion masks tracked laser instead of retargeting obstacle");
    SeekerController.tick(shot);
    ok(
        h,
        n.getString("Status").equals("LASER_INERTIAL"),
        "terrain masking returns shot to inertial state");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void reservedAmmoHiddenAndActiveModelsPersist(GameTestHelper h) {
    ok(
        h,
        !LoadoutData.ammo().containsKey("sbw_loadout:kh58ushk")
            && !LoadoutMod.AMMO.containsKey("sbw_loadout:kh58ushk"),
        "Kh58 has no data or playable item registration");
    ok(
        h,
        !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("sbw_loadout:ammo_kh58ushk")),
        "hidden ammo cannot be given as registered item");
    for (String id : List.of("kh29l", "kab1500kr", "kh59mk")) {
      var a = LoadoutData.ammo().get("sbw_loadout:" + id);
      ok(h, a != null && a.renderProjectileModel(), "active full-detail definition loaded: " + id);
      ok(
          h,
          a.turnRate() == (id.equals("kab1500kr") ? 4 : id.equals("kh59mk") ? 3 : 5),
          "missiles gain moderate turn rate while bomb remains gentler: " + id);
      var p = player(h);
      var v = plane(h, p, a.id());
      var e = target(h, v, 0, 0);
      TargetingPod.open(p);
      aimAt(p, v, e.getBoundingBox().getCenter());
      TargetingPod.lock(p, false);
      if (id.equals("kh29l")) TargetingPod.toggleLaser(p);
      LoadoutService.fire(p, v, null, null);
      var shot = released(h, p);
      ok(
          h,
          shot instanceof DestroyableProjectile
              && shot.getType()
                  .is(com.atsuishio.superbwarfare.init.ModTags.EntityTypes.DESTROYABLE_PROJECTILE),
          "native CIWS type/tag retained: " + id);
      ok(
          h,
          ((AmmoModelEntity) shot).loadoutAmmoModel().equals(a.id()),
          "released model id matches ammo: " + id);
      var tag = new CompoundTag();
      shot.saveWithoutId(tag);
      var copy = (Projectile) shot.getType().create(h.getLevel());
      copy.load(tag);
      ok(
          h,
          ((AmmoModelEntity) copy).loadoutAmmoModel().equals(a.id())
              && copy.getPersistentData().contains(SeekerController.KEY),
          "saved projectile keeps model and guidance: " + id);
      if (shot instanceof com.atsuishio.superbwarfare.entity.projectile.MissileProjectile missile) {
        var farTag = new CompoundTag();
        missile.buildBvrSyncNbt(farTag);
        var farCopy = (Projectile) shot.getType().create(h.getLevel());
        farCopy.load(farTag);
        ok(
            h,
            ((AmmoModelEntity) farCopy).loadoutAmmoModel().equals(a.id()),
            "beyond-visual-range snapshot keeps individual missile model: " + id);
      }
      shot.discard();
      e.discard();
      p.stopRiding();
      v.discard();
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void airborneBuddyLasingUsesTeamPermission(GameTestHelper h) {
    var source = player(h);
    var v = plane(h, source, "sbw_loadout:kh29l");
    var e = target(h, v, 0, 0);
    TargetingPod.open(source);
    aimAt(source, v, e.getBoundingBox().getCenter());
    TargetingPod.lock(source, false);
    TargetingPod.toggleLaser(source);
    var shooter = player(h);
    var from = TargetingPod.origin(v, 1).add(-2, 0, -2);
    var dir = e.getBoundingBox().getCenter().subtract(from).normalize();
    ok(
        h,
        LaserDesignations.select(shooter, 1688, from, dir, 256, 90) == null,
        "unrelated aircraft cannot supply launch solution");
    var board = h.getLevel().getScoreboard();
    var team = board.addPlayerTeam("air_" + UUID.randomUUID().toString().substring(0, 6));
    board.addPlayerToTeam(source.getScoreboardName(), team);
    board.addPlayerToTeam(shooter.getScoreboardName(), team);
    var spot = LaserDesignations.select(shooter, 1688, from, dir, 256, 90);
    ok(
        h,
        spot != null && spot.source().equals(v.getUUID()),
        "same-team aircraft can illuminate for another shooter");
    TargetingPod.closeView(source);
    ok(
        h,
        LaserDesignations.visibleTo(shooter).stream().anyMatch(x -> x.source().equals(v.getUUID())),
        "teammate receives aircraft spot while pilot flies in cockpit");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void unlockedOrUnprivilegedSensorCannotLase(GameTestHelper h) {
    var p = player(h);
    ok(h, !TargetingPod.toggleLaser(p), "on-foot laser request rejected");
    var v = plane(h, p, "sbw_loadout:kh29l");
    var e = target(h, v, 0, 0);
    TargetingPod.open(p);
    aimAt(p, v, e.getBoundingBox().getCenter());
    TargetingPod.lock(p, false);
    TargetingPod.toggleLaser(p);
    TargetingPod.unlock(p);
    ok(
        h,
        !TargetingPod.laserEnabled(p) && TargetingPod.laser(p) == null,
        "unlock stops laser rather than moving beam without approval");
    TargetingPod.closeView(p);
    ok(h, TargetingPod.current(p) == null, "inactive sensor closes normally");
    h.succeed();
  }
}
