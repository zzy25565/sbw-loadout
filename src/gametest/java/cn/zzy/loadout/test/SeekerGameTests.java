package cn.zzy.loadout.test;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import com.atsuishio.superbwarfare.entity.projectile.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class SeekerGameTests {
  static void ok(GameTestHelper h, boolean value, String text) {
    h.assertTrue(value, text);
  }

  static ServerPlayer player(GameTestHelper h) {
    var dummy =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "designator"));
    var p =
        new ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            new GameProfile(
                UUID.randomUUID(), "pilot_" + UUID.randomUUID().toString().substring(0, 6)),
            ClientInformation.createDefault());
    p.connection = dummy.connection;
    p.setGameMode(GameType.SURVIVAL);
    p.setPos(h.absoluteVec(new Vec3(10, 10, 4)));
    p.setYRot(0);
    p.setXRot(0);
    return p;
  }

  static void wall(ServerPlayer p) {
    ServerLevel l = p.serverLevel();
    BlockPos c = BlockPos.containing(p.getEyePosition().add(0, 0, 24));
    for (int x = -8; x <= 8; x++)
      for (int y = -2; y <= 2; y++)
        l.setBlock(c.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
  }

  static void illuminate(ServerPlayer p) {
    p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ARTILLERY_INDICATOR.get()));
    ModItems.ARTILLERY_INDICATOR.get().use(p.level(), p, InteractionHand.MAIN_HAND);
    LaserDesignations.tick(p);
  }

  static Projectile projectile(GameTestHelper h, String type, ServerPlayer p) {
    var entity =
        BuiltInRegistries.ENTITY_TYPE
            .get(ResourceLocation.parse("superbwarfare:" + type))
            .create(h.getLevel());
    Projectile shot = (Projectile) entity;
    shot.setOwner(p);
    shot.setPos(p.getEyePosition().add(5, 0, 1));
    shot.shoot(0, 0, 1, 1, 0);
    ((IBulletProperties) shot).setDamage(0);
    ((IBulletProperties) shot).setExplosionDamage(0);
    ((IBulletProperties) shot).setExplosionRadius(0);
    h.getLevel().addFreshEntity(shot);
    return shot;
  }

  static VehicleEntity plane(GameTestHelper h, ServerPlayer p, String ammo) {
    var v = TestFixtures.AIRCRAFT.create(h.getLevel());
    v.setPos(p.position().add(-2, 0, 0));
    v.setNoGravity(true);
    h.getLevel().addFreshEntity(v);
    p.startRiding(v, true);
    var s = LoadoutService.state(v);
    s.entries.clear();
    s.entries.add(new LoadoutState.Entry(UUID.randomUUID(), ammo, 0));
    LoadoutService.save(v, s);
    return v;
  }

  @GameTest(template = "empty")
  public static void indicatorRayAndRelease(GameTestHelper h) {
    var p = player(h);
    wall(p);
    illuminate(p);
    var s = LaserDesignations.sample(p);
    ok(h, s != null && s.code() == 1688, "original indicator use emits server ray");
    ok(h, s.point().z > p.getZ() + 20, "ray hits real forward surface");
    p.stopUsingItem();
    ok(
        h,
        LaserDesignations.resolve(h.getLevel(), p.getUUID(), 1688) == null,
        "release invalidates without waiting for player tick");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void laserFollowsMovedSpotAndCoasts(GameTestHelper h) {
    var p = player(h);
    wall(p);
    illuminate(p);
    var spot = LaserDesignations.sample(p);
    var shot = projectile(h, "agm_65", p);
    GuidanceApi.laser(shot, p.getUUID(), 1688, spot.point(), 128, 90, 8);
    SeekerController.tick(shot);
    Vec3 old =
        SeekerController.readPoint(shot.getPersistentData().getCompound(SeekerController.KEY));
    p.setYRot(10);
    SeekerController.tick(shot);
    var tag = shot.getPersistentData().getCompound(SeekerController.KEY);
    ok(h, SeekerController.readPoint(tag).distanceTo(old) > 1, "in-flight laser tracks new spot");
    p.stopUsingItem();
    Vec3 velocity = shot.getDeltaMovement();
    SeekerController.tick(shot);
    ok(
        h,
        tag.getString("Status").equals("LASER_INERTIAL")
            && SeekerController.readPoint(tag).distanceTo(old) > 1,
        "lost laser continues toward last received point");
    ((MissileProjectile) shot).setLostTargetTick(200);
    shot.tick();
    ok(
        h,
        shot.isAlive() && ((MissileProjectile) shot).getLostTargetTick() < 5,
        "native lost-lock self destruct is suppressed for own seeker");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void laserCodeTeamAndDimension(GameTestHelper h) {
    var source = player(h);
    wall(source);
    illuminate(source);
    var shooter = player(h);
    Vec3 from = shooter.getEyePosition();
    ok(
        h,
        LaserDesignations.select(shooter, 1688, from, new Vec3(0, 0, 1), 128, 90) == null,
        "unrelated player's laser not selected");
    var board = h.getLevel().getScoreboard();
    var team = board.addPlayerTeam("ls_" + UUID.randomUUID().toString().substring(0, 6));
    board.addPlayerToTeam(source.getScoreboardName(), team);
    board.addPlayerToTeam(shooter.getScoreboardName(), team);
    ok(
        h,
        LaserDesignations.select(shooter, 1688, from, new Vec3(0, 0, 1), 128, 90) != null,
        "teammate can guide launch");
    ok(
        h,
        LaserDesignations.select(shooter, 9999, from, new Vec3(0, 0, 1), 128, 90) == null,
        "code mismatch rejected");
    var other = h.getLevel().getServer().getLevel(Level.NETHER);
    ok(
        h,
        LaserDesignations.resolve(other, source.getUUID(), 1688) == null,
        "different dimension rejected");
    ok(
        h,
        LaserDesignations.resolve(h.getLevel(), source.getUUID(), 1688) != null,
        "wrong dimension query does not erase original source");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void laserNoSpotNoAmmoCost(GameTestHelper h) {
    var p = player(h);
    var v = plane(h, p, "fixture_addon:laser_missile");
    long r = LoadoutService.state(v).revision;
    LoadoutService.fire(p, v, null, null);
    ok(
        h,
        LoadoutService.state(v).entries.size() == 1 && LoadoutService.state(v).revision == r,
        "no designator rejects laser launch without ammo consumption");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void laserOcclusionAndBombNativeTick(GameTestHelper h) {
    var p = player(h);
    wall(p);
    illuminate(p);
    var spot = LaserDesignations.sample(p);
    var shot = projectile(h, "mk_82", p);
    GuidanceApi.laser(shot, p.getUUID(), 1688, spot.point(), 128, 90, 8);
    Vec3 before = shot.getDeltaMovement();
    shot.tick();
    ok(
        h,
        shot.getX() < p.getX() + 5 && shot.getDeltaMovement().x < 0,
        "native bomb tick follows attached laser seeker");
    BlockPos c = BlockPos.containing(p.getEyePosition().add(3, 0, 12));
    for (int x = -1; x <= 1; x++)
      for (int y = -2; y <= 2; y++)
        h.getLevel().setBlock(c.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
    SeekerController.tick(shot);
    ok(
        h,
        shot.getPersistentData()
            .getCompound(SeekerController.KEY)
            .getString("Status")
            .equals("LASER_INERTIAL"),
        "blocked signal uses inertial memory instead of live laser");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void televisionLaunchNeedsLockAndIsAutonomous(GameTestHelper h) {
    var p = player(h);
    var v = plane(h, p, "fixture_addon:tv_bomb");
    LoadoutService.fire(p, v, null, null);
    ok(h, LoadoutService.state(v).entries.size() == 1, "TV launch needs lock");
    var target = EntityType.PIG.create(h.getLevel());
    target.setNoAi(true);
    target.setNoGravity(true);
    target.setPos(v.position().add(0, 1, 20));
    h.getLevel().addFreshEntity(target);
    var a = LoadoutData.ammo().get("fixture_addon:tv_bomb");
    ok(
        h,
        TelevisionLocks.acquire(p, v, a, target.getUUID(), null),
        "server accepts visible target lock");
    LoadoutService.fire(p, v, null, null);
    ok(h, LoadoutService.state(v).entries.isEmpty(), "locked TV bomb launches");
    var shot =
        h.getLevel().getEntitiesOfClass(Projectile.class, v.getBoundingBox().inflate(12)).stream()
            .filter(e -> e.getOwner() == p)
            .findFirst()
            .orElseThrow();
    ok(h, shot instanceof AerialBombEntity, "bomb uses actual native bomb entity");
    p.stopRiding();
    p.setYRot(180);
    p.setHealth(0);
    TelevisionLocks.remove(p.getUUID());
    target.setPos(target.position().add(4, 0, 0));
    shot.tick();
    var tag = shot.getPersistentData().getCompound(SeekerController.KEY);
    ok(
        h,
        tag.getString("Status").equals("TRACKING_TV_ENTITY")
            && SeekerController.readPoint(tag).distanceTo(target.getBoundingBox().getCenter())
                < 1e-6,
        "TV updates moving target after operator turns, exits and dies");
    ok(h, shot.getDeltaMovement().x > 0, "TV bomb actually turns toward moved target");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void televisionFixedPointSaveRestore(GameTestHelper h) {
    var p = player(h);
    var shot = projectile(h, "mk_82", p);
    Vec3 target = shot.position().add(-8, -2, 20);
    GuidanceApi.television(shot, null, target, 128, 90, 8);
    CompoundTag saved = shot.saveWithoutId(new CompoundTag());
    var clone = (Projectile) shot.getType().create(h.getLevel());
    clone.load(saved);
    SeekerController.tick(clone);
    ok(
        h,
        SeekerController.readPoint(clone.getPersistentData().getCompound(SeekerController.KEY))
            .equals(target),
        "TV fixed-point lock restored with entity NBT");
    ok(h, clone.getDeltaMovement().x < 0, "restored seeker still steers");
    ok(
        h,
        Math.abs(clone.getDeltaMovement().length() - shot.getDeltaMovement().length()) < 1e-8,
        "controller preserves speed before native gravity");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void televisionNeverSwitchesIdentity(GameTestHelper h) {
    var p = player(h);
    var target = EntityType.PIG.create(h.getLevel());
    target.setPos(p.getEyePosition().add(0, 0, 20));
    h.getLevel().addFreshEntity(target);
    var shot = projectile(h, "agm_65", p);
    Vec3 last = target.getBoundingBox().getCenter();
    GuidanceApi.television(shot, target.getUUID(), last, 128, 90, 8);
    UUID id = target.getUUID();
    target.discard();
    var other = EntityType.PIG.create(h.getLevel());
    other.setPos(last.add(2, 0, 0));
    h.getLevel().addFreshEntity(other);
    shot.tick();
    var tag = shot.getPersistentData().getCompound(SeekerController.KEY);
    ok(
        h,
        tag.getUUID("Target").equals(id)
            && SeekerController.readPoint(tag).equals(last)
            && tag.getString("Status").equals("TV_LAST_KNOWN"),
        "TV retains original identity/last position and never auto-selects neighbour");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void televisionRejectsFakeAirCoordinate(GameTestHelper h) {
    var p = player(h);
    var v = plane(h, p, "fixture_addon:tv_bomb");
    var a = LoadoutData.ammo().get("fixture_addon:tv_bomb");
    ok(
        h,
        !TelevisionLocks.acquire(p, v, a, null, p.getEyePosition().add(0, 0, 10)),
        "arbitrary air coordinate is not a lockable surface");
    ok(h, !TelevisionLocks.acquire(p, v, a, null, new Vec3(Double.NaN, 0, 0)), "NaN lock rejected");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void laserAndSeekerPersistence(GameTestHelper h) {
    var p = player(h);
    wall(p);
    illuminate(p);
    var spot = LaserDesignations.sample(p);
    var shot = projectile(h, "agm_65", p);
    GuidanceApi.laser(shot, p.getUUID(), 1688, spot.point(), 128, 90, 8);
    var clone = (Projectile) shot.getType().create(h.getLevel());
    clone.load(shot.saveWithoutId(new CompoundTag()));
    p.setYRot(-8);
    clone.tick();
    var tag = clone.getPersistentData().getCompound(SeekerController.KEY);
    ok(
        h,
        tag.getUUID("Source").equals(p.getUUID())
            && tag.getString("Status").equals("TRACKING_LASER"),
        "saved laser keeps same source and resumes live tracking");
    p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    clone.tick();
    ok(
        h,
        tag.getString("Status").equals("LASER_INERTIAL"),
        "switching away stops live updates and keeps inertial guidance");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void turnLimitAndConfigRoundTrip(GameTestHelper h) {
    Vec3 a = new Vec3(0, 0, 1), b = new Vec3(0, 0, -1);
    Vec3 c = SeekerController.rotateTowards(a, b, Math.toRadians(4));
    ok(
        h,
        Math.abs(c.length() - 1) < 1e-8 && Math.abs(Math.toDegrees(Math.acos(a.dot(c))) - 4) < 1e-6,
        "180 degree turn is finite and limited");
    var original =
        AmmoType.builder("test:laser").guidance("Laser").laserCode(1234).turnRate(2.5).build();
    var restored = AmmoType.parse(original.id(), original.json());
    ok(h, original.equals(restored), "new seeker fields survive network JSON round trip");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void inertialReacquiresAndAvoidsUTurn(GameTestHelper h) {
    var p = player(h);
    wall(p);
    illuminate(p);
    var spot = LaserDesignations.sample(p);
    var shot = projectile(h, "agm_65", p);
    GuidanceApi.laser(shot, p.getUUID(), 1688, spot.point(), 128, 90, 45);
    SeekerController.tick(shot);
    p.stopUsingItem();
    Vec3 old = shot.getDeltaMovement();
    SeekerController.tick(shot);
    double turn =
        Math.toDegrees(
            Math.acos(
                Math.max(
                    -1, Math.min(1, old.normalize().dot(shot.getDeltaMovement().normalize())))));
    ok(h, turn <= 6.0001, "global six degree cap prevents abrupt bends even if ammo requests 45");
    p.setYRot(-10);
    illuminate(p);
    SeekerController.tick(shot);
    var tag = shot.getPersistentData().getCompound(SeekerController.KEY);
    ok(
        h,
        tag.getString("Status").equals("TRACKING_LASER")
            && SeekerController.readPoint(tag).distanceTo(spot.point()) > 1,
        "same source/code resumes live tracking");
    p.stopUsingItem();
    shot.setPos(SeekerController.readPoint(tag).add(0, 0, 5));
    shot.setDeltaMovement(0, 0, 1);
    SeekerController.tick(shot);
    ok(
        h,
        shot.getDeltaMovement().z == 1 && tag.getString("Status").endsWith("PASSED"),
        "passed target does not trigger reverse U turn");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void inertialMissileReachesLastSurface(GameTestHelper h) {
    var p = player(h);
    wall(p);
    illuminate(p);
    var spot = LaserDesignations.sample(p);
    var shot = projectile(h, "agm_65", p);
    shot.tickCount = 10;
    GuidanceApi.laser(shot, p.getUUID(), 1688, spot.point(), 128, 90, 4);
    p.stopUsingItem();
    for (int i = 0; i < 12 && shot.isAlive(); i++) {
      shot.tickCount++;
      shot.tick();
    }
    ok(
        h,
        !shot.isAlive(),
        "without illumination missile retains inertial target and collides with designated wall");
    Vec3 hit = SeekerHitProbe.HITS.remove(shot.getUUID());
    ok(
        h,
        hit != null && hit.distanceTo(spot.point()) < 5,
        "collision is near last designated surface: actual="
            + hit
            + " target="
            + spot.point()
            + " final="
            + shot.position());
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void ciwsCanSelectAndDestroyAllSeekerVariants(GameTestHelper h) {
    var attacker = player(h);
    var defender = player(h);
    var towerType =
        BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("superbwarfare:hpj_11"));
    var tower =
        new com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity(
            towerType, h.getLevel()) {
          @Override
          public LivingEntity getOwner() {
            return defender;
          }
        };
    tower.setPos(attacker.position().add(0, 0, 15));
    h.getLevel().addFreshEntity(tower);
    for (String type : List.of("agm_65", "mk_82"))
      for (SeekerMode mode : List.of(SeekerMode.LASER, SeekerMode.TV)) {
        var incoming = projectile(h, type, attacker);
        incoming.setPos(tower.getBoundingBox().getCenter().add(0, 0, -10));
        incoming.setDeltaMovement(
            tower.getBoundingBox().getCenter().subtract(incoming.position()).normalize());
        if (mode == SeekerMode.TV)
          GuidanceApi.television(
              incoming, tower.getUUID(), tower.getBoundingBox().getCenter(), 128, 90, 4);
        else
          GuidanceApi.laser(
              incoming, attacker.getUUID(), 1688, tower.getBoundingBox().getCenter(), 128, 90, 4);
        ok(
            h,
            com.atsuishio.superbwarfare.tools.SeekTool.BASIC_TYPE_FILTER.test(incoming),
            "native seeker target filter includes " + type + " " + mode);
        ok(
            h,
            com.atsuishio.superbwarfare.entity.vehicle.ai.TowerAI.TargetValidator
                .isValidProjectileTarget(tower, incoming, tower.getBoundingBox().getCenter()),
            "native CIWS validates incoming hostile collision trajectory");
        var shell =
            (SmallCannonShellEntity)
                BuiltInRegistries.ENTITY_TYPE
                    .get(ResourceLocation.parse("superbwarfare:small_cannon_shell"))
                    .create(h.getLevel());
        shell.setOwner(defender);
        shell.setPos(incoming.position().add(0, 0, -.1));
        shell.setDamage(10000);
        shell.setExplosionDamage(0);
        shell.setExplosionRadius(0);
        h.getLevel().addFreshEntity(shell);
        shell.crushProjectile(new Vec3(0, 0, 1));
        ok(
            h,
            ((DestroyableProjectile) incoming).getHealth() <= 0,
            "native AA shell reduces seeker projectile health");
        incoming.tick();
        ok(h, !incoming.isAlive(), "native CIWS hit destroys guided projectile permanently");
      }
    h.succeed();
  }
}
