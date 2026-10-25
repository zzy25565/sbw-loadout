package cn.zzy.loadout.test;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.data.LoadoutState.Entry;
import cn.zzy.loadout.menu.LoadoutMenu;
import com.atsuishio.superbwarfare.entity.projectile.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class LoadoutGameTests {
  private static VehicleEntity plane(GameTestHelper h) {
    var v = TestFixtures.AIRCRAFT.create(h.getLevel());
    v.moveTo(h.absoluteVec(new Vec3(8, 8, 8)));
    v.setNoGravity(true);
    h.getLevel().addFreshEntity(v);
    return v;
  }

  private static ServerPlayer player(GameTestHelper h, VehicleEntity v) {
    var dummy =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "loadout_test"));
    var p =
        new ServerPlayer(
            h.getLevel().getServer(),
            h.getLevel(),
            new GameProfile(UUID.randomUUID(), "loadout_pilot"),
            net.minecraft.server.level.ClientInformation.createDefault());
    p.connection = dummy.connection;
    p.setGameMode(GameType.SURVIVAL);
    p.setPos(v.getX() + 2, v.getY(), v.getZ());
    p.getInventory().clearContent();
    return p;
  }

  private static void lockTv(GameTestHelper h, ServerPlayer p, VehicleEntity v, String id) {
    var a = LoadoutData.ammo().get(id);
    if (cn.zzy.loadout.guidance.SeekerMode.of(a.guidance())
        == cn.zzy.loadout.guidance.SeekerMode.TV) {
      var target = net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());
      target.setNoGravity(true);
      target.setNoAi(true);
      target.setPos(v.position().add(v.getViewVector(1).scale(12)).add(0, 1, 0));
      h.getLevel().addFreshEntity(target);
      boolean accepted =
          cn.zzy.loadout.guidance.TelevisionLocks.acquire(p, v, a, target.getUUID(), null);
      if (!accepted)
        com.mojang.logging.LogUtils.getLogger()
            .info(
                "TV_DEBUG id={} pilot={} from={} dir={} target={} cone={} visible={} loaded={}",
                id,
                LoadoutService.pilot(p, v),
                p.getEyePosition(),
                v.getViewVector(1),
                target.getBoundingBox().getCenter(),
                cn.zzy.loadout.guidance.AimSupport.inCone(
                    p.getEyePosition(),
                    v.getViewVector(1),
                    target.getBoundingBox().getCenter(),
                    a.seekRange(),
                    a.seekAngle()),
                cn.zzy.loadout.guidance.AimSupport.visible(
                    h.getLevel(), p, p.getEyePosition(), target.getBoundingBox().getCenter()),
                cn.zzy.loadout.guidance.AimSupport.loaded(
                    h.getLevel(), p.getEyePosition(), target.getBoundingBox().getCenter()));
      check(h, accepted, "TV lock acquired for regression");
    }
  }

  private static void check(GameTestHelper h, boolean b, String msg) {
    h.assertTrue(b, msg);
  }

  @GameTest(template = "empty")
  public static void dataAndThirdParty(GameTestHelper h) {
    var v = plane(h);
    check(h, LoadoutData.ammo().size() >= 4, "four JSON ammo loaded");
    check(h, LoadoutData.spec(v).slots() == 6, "generic entity JSON Loadout detected");
    var ground = TestFixtures.GROUND.create(h.getLevel());
    check(h, LoadoutData.spec(ground) == null, "ground vehicle rejected even with Loadout");
    for (var a : LoadoutData.ammo().values())
      check(h, LoadoutApi.stack(a.id(), 1).getMaxStackSize() == 16, "ammo item stack size");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void slotsAndSave(GameTestHelper h) {
    var v = plane(h);
    var s = LoadoutService.state(v);
    check(h, s.entries.size() == 1, "defaults initialized once");
    s.entries.clear();
    var spec = LoadoutData.spec(v);
    s.entries.add(new Entry(UUID.randomUUID(), "sbw_loadout:kh38mt", 0));
    s.entries.add(new Entry(UUID.randomUUID(), "sbw_loadout:kh38mt", 1));
    s.entries.add(new Entry(UUID.randomUUID(), "sbw_loadout:kab500kr", 2));
    s.entries.add(new Entry(UUID.randomUUID(), "sbw_loadout:kab500kr", 4));
    LoadoutState.validate(s.entries, spec, LoadoutData.ammo());
    check(
        h,
        LoadoutState.used(s.entries, LoadoutData.ammo()) == 6,
        "2 missiles + 2 bombs uses 6 slots");
    check(
        h,
        !LoadoutState.fits(s.entries, spec, LoadoutData.ammo(), "sbw_loadout:x555", 3),
        "contiguous occupancy blocks overlap");
    s.revision = 12;
    LoadoutService.save(v, s);
    CompoundTag saved = v.saveWithoutId(new CompoundTag());
    var copy = TestFixtures.AIRCRAFT.create(h.getLevel());
    copy.load(saved);
    check(
        h,
        LoadoutService.state(copy).entries.equals(s.entries),
        "entity NBT restores identities and order");
    check(h, LoadoutService.state(copy).revision == 12, "revision persistent");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void quickLoadAndReject(GameTestHelper h) {
    var v = plane(h);
    var p = player(h, v);
    var s = LoadoutService.state(v);
    s.entries.clear();
    LoadoutService.save(v, s);
    ItemStack held = LoadoutMod.stack("sbw_loadout:x555", 3);
    check(h, LoadoutService.loadOne(p, v, held), "first load");
    check(h, LoadoutService.loadOne(p, v, held), "second load");
    check(h, held.getCount() == 1, "survival consumes actual items");
    check(
        h,
        !LoadoutService.loadOne(p, v, held) && held.getCount() == 1,
        "full rejection consumes nothing");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void commitAndReplay(GameTestHelper h) {
    var v = plane(h);
    var p = player(h, v);
    var s = LoadoutService.state(v);
    s.entries.clear();
    LoadoutService.save(v, s);
    p.getInventory().add(LoadoutMod.stack("sbw_loadout:kab500kr", 2));
    var original = s.copy();
    var draft =
        List.of(
            new Entry(UUID.randomUUID(), "sbw_loadout:kab500kr", 0),
            new Entry(UUID.randomUUID(), "sbw_loadout:kab500kr", 2),
            new Entry(UUID.randomUUID(), "sbw_loadout:kab500kr", 4));
    check(
        h,
        LoadoutService.commit(p, v, s.revision, original, draft),
        "commit validates current inventory");
    check(
        h, LoadoutService.state(v).entries.size() == 2, "missing third item rolls back that slot");
    check(
        h,
        LoadoutService.counts(p).getOrDefault("sbw_loadout:kab500kr", 0) == 0,
        "only available items consumed");
    check(h, !LoadoutService.commit(p, v, s.revision, original, draft), "replayed commit rejected");
    s = LoadoutService.state(v);
    check(h, LoadoutService.commit(p, v, s.revision, s.copy(), List.of()), "unload commit");
    check(
        h,
        LoadoutService.counts(p).getOrDefault("sbw_loadout:kab500kr", 0) == 2,
        "unload returns exactly two");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void cancelAndConcurrent(GameTestHelper h) {
    var v = plane(h);
    var p = player(h, v);
    p.getInventory().add(LoadoutMod.stack("sbw_loadout:kh38mt", 2));
    var s = LoadoutService.state(v);
    List<Entry> before = s.copy();
    LoadoutMenu menu = new LoadoutMenu(7, p.getInventory(), v);
    menu.act("add", "sbw_loadout:kh38mt", 1);
    check(h, LoadoutService.state(v).entries.equals(before), "draft doesn't change vehicle");
    check(
        h, LoadoutService.counts(p).get("sbw_loadout:kh38mt") == 2, "draft doesn't take inventory");
    menu.removed(p);
    check(
        h,
        LoadoutService.state(v).entries.equals(before),
        "disconnect/close without commit discards draft");
    s.revision++;
    LoadoutService.save(v, s);
    check(
        h,
        !LoadoutService.commit(p, v, s.revision - 1, before, List.of()),
        "concurrent revision rejected");
    check(
        h, LoadoutService.counts(p).get("sbw_loadout:kh38mt") == 2, "conflict preserves inventory");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void firingAndInfinite(GameTestHelper h) {
    var v = plane(h);
    var p = player(h, v);
    p.startRiding(v, true);
    check(h, v.getSeatIndex(p) == 0, "fake pilot occupies seat zero");
    var s = LoadoutService.state(v);
    s.entries.clear();
    s.entries.add(new Entry(UUID.randomUUID(), "sbw_loadout:kh38mt", 0));
    s.entries.add(new Entry(UUID.randomUUID(), "sbw_loadout:kh29td", 1));
    LoadoutService.save(v, s);
    long t = h.getLevel().getGameTime();
    LoadoutService.fire(p, v, null, null);
    s = LoadoutService.state(v);
    check(
        h,
        s.entries.size() == 1 && s.entries.getFirst().ammo().endsWith("kh29td"),
        "FIFO consumes first missile");
    check(h, s.nextFire == t + 40, "cooldown authoritative");
    var spawned =
        h
            .getLevel()
            .getEntitiesOfClass(
                net.minecraft.world.entity.projectile.Projectile.class,
                v.getBoundingBox().inflate(15))
            .stream()
            .filter(e -> e.getOwner() == p)
            .toList();
    check(h, spawned.size() == 1, "exactly one native projectile created");
    var props = (IBulletProperties) spawned.getFirst();
    check(
        h,
        props.getDamage() == 620
            && props.getExplosionDamage() == 320
            && props.getExplosionRadius() == 8,
        "JSON damage parameters applied");
    LoadoutService.fire(p, v, null, null);
    check(h, LoadoutService.state(v).entries.size() == 1, "spam cannot bypass cooldown");
    s.nextFire = 0;
    LoadoutService.save(v, s);
    p.setGameMode(GameType.CREATIVE);
    lockTv(h, p, v, "sbw_loadout:kh29td");
    LoadoutService.fire(p, v, null, null);
    check(h, LoadoutService.state(v).entries.size() == 1, "creative auto refill keeps same ammo");
    p.setGameMode(GameType.SURVIVAL);
    s = LoadoutService.state(v);
    s.nextFire = 0;
    LoadoutService.save(v, s);
    LoadoutService.fire(p, v, null, null);
    check(h, LoadoutService.state(v).entries.isEmpty(), "survival exhausts loadout");
    long revision = LoadoutService.state(v).revision;
    LoadoutService.fire(p, v, null, null);
    check(h, LoadoutService.state(v).revision == revision, "empty fire does not mutate");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void movedAndInvalidIdentity(GameTestHelper h) {
    var v = plane(h);
    var p = player(h, v);
    var s = LoadoutService.state(v);
    var old = s.copy();
    var forged = List.of(new Entry(old.getFirst().token(), "sbw_loadout:kh29td", 1));
    check(
        h,
        !LoadoutService.commit(p, v, s.revision, old, forged),
        "token can't change ammo or slot");
    LoadoutMenu menu = new LoadoutMenu(9, p.getInventory(), v);
    v.setPos(v.position().add(15, 0, 0));
    check(h, !menu.stillValid(p), "moved aircraft invalidates menu");
    check(
        h,
        !LoadoutService.commit(p, v, s.revision, old, List.of()),
        "out of range commit rejected");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void dataOnlyAddonAndFill(GameTestHelper h) {
    var v = plane(h);
    var p = player(h, v);
    var s = LoadoutService.state(v);
    s.entries.clear();
    LoadoutService.save(v, s);
    p.getInventory().add(LoadoutApi.stack("fixture_addon:data_only", 2));
    check(
        h,
        LoadoutService.loadOne(p, v, p.getInventory().getItem(0)),
        "JSON-only addon uses generic ammo item");
    check(
        h,
        LoadoutService.state(v).entries.getFirst().ammo().equals("fixture_addon:data_only"),
        "third party id preserved");
    p.getInventory().add(LoadoutMod.stack("sbw_loadout:kab500kr", 2));
    var menu = new LoadoutMenu(8, p.getInventory(), v);
    menu.act("fill", "", -1);
    check(h, LoadoutService.state(v).entries.size() == 1, "fill is draft only");
    menu.act("commit", "", -1);
    s = LoadoutService.state(v);
    check(
        h,
        LoadoutState.used(s.entries, LoadoutData.ammo()) == 6,
        "fill uses Allow order and multislots");
    check(
        h,
        LoadoutService.counts(p).getOrDefault("sbw_loadout:kab500kr", 0) == 0,
        "fill deducts bombs exactly");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void fullInventoryRefund(GameTestHelper h) {
    var v = plane(h);
    var p = player(h, v);
    var s = LoadoutService.state(v);
    for (int i = 0; i < 36; i++)
      p.getInventory().setItem(i, new ItemStack(net.minecraft.world.item.Items.STONE, 64));
    check(
        h,
        LoadoutService.commit(p, v, s.revision, s.copy(), List.of()),
        "full backpack can unload");
    var drops =
        h.getLevel()
            .getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(3));
    check(
        h,
        drops.stream()
            .anyMatch(
                e ->
                    LoadoutMod.ammoId(e.getItem()).equals("sbw_loadout:kh38mt")
                        && e.getItem().getCount() == 1),
        "refund drops at player feet exactly once");
    check(h, LoadoutService.state(v).entries.isEmpty(), "unload persisted");
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void allAmmoNativeEntry(GameTestHelper h) {
    var v = plane(h);
    var p = player(h, v);
    p.startRiding(v, true);
    int projectiles = 0;
    for (String id : LoadoutMod.AMMO.keySet()) {
      var s = LoadoutService.state(v);
      s.entries.clear();
      s.nextFire = 0;
      s.entries.add(new Entry(UUID.randomUUID(), id, 0));
      LoadoutService.save(v, s);
      lockTv(h, p, v, id);
      if (cn.zzy.loadout.guidance.SeekerMode.of(LoadoutData.ammo().get(id).guidance())
          == cn.zzy.loadout.guidance.SeekerMode.LASER) {
        var e = cn.zzy.loadout.test.PodGameTests.target(h, v, 0, 0);
        cn.zzy.loadout.guidance.TargetingPod.open(p);
        cn.zzy.loadout.test.PodGameTests.aimAt(p, v, e.getBoundingBox().getCenter());
        cn.zzy.loadout.guidance.TargetingPod.lock(p, false);
        check(
            h,
            cn.zzy.loadout.guidance.TargetingPod.toggleLaser(p),
            "native-entry laser source established");
      }
      cn.zzy.loadout.api.RadarLink.Provider previous=cn.zzy.loadout.api.RadarLink.provider();
      cn.zzy.loadout.api.RadarLink.Provider scoped=null;
      if (cn.zzy.loadout.guidance.SeekerMode.of(LoadoutData.ammo().get(id).guidance())
          == cn.zzy.loadout.guidance.SeekerMode.RADAR) {
        var radarTarget=net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());
        radarTarget.setNoAi(true);radarTarget.setNoGravity(true);
        radarTarget.setPos(v.position().add(v.getViewVector(1).scale(12)).add(0,1,0));
        h.getLevel().addFreshEntity(radarTarget);
        scoped=new cn.zzy.loadout.api.RadarLink.Provider() {
          public net.minecraft.world.entity.Entity lockedTarget(VehicleEntity source) { return radarTarget; }
        };
        cn.zzy.loadout.api.RadarLink.install(scoped);
      }
      try { v.vehicleShoot(p, (UUID) null, null); }
      finally { if(scoped!=null) { if(previous!=null) cn.zzy.loadout.api.RadarLink.install(previous); else cn.zzy.loadout.api.RadarLink.uninstall(scoped); } }
      check(
          h,
          LoadoutService.state(v).entries.isEmpty(),
          "native VehicleEntity entry consumes " + id);
      projectiles++;
      var fired =
          h
              .getLevel()
              .getEntitiesOfClass(
                  net.minecraft.world.entity.projectile.Projectile.class,
                  v.getBoundingBox().inflate(15))
              .stream()
              .filter(e -> e.getOwner() == p)
              .toList();
      check(h, fired.size() == projectiles, "one entity for each ammo type");
    }
    h.succeed();
  }

  @GameTest(template = "empty")
  public static void infraredAndPermission(GameTestHelper h) {
    var v = plane(h);
    var p = player(h, v);
    p.startRiding(v, true);
    Vec3 dir = v.getViewVector(1);
    var target = net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());
    target.setNoGravity(true);
    target.setPos(v.position().add(dir.scale(12)).add(0, .4, 0));
    h.getLevel().addFreshEntity(target);
    var before = LoadoutMod.INFINITE.get();
    try {
      LoadoutMod.INFINITE.set(List.of(p.getUUID().toString()));
      LoadoutService.fire(p, v, null, null);
      check(
          h,
          LoadoutService.state(v).entries.size() == 1,
          "server UUID permission enables refill in survival");
      var missile =
          h
              .getLevel()
              .getEntitiesOfClass(
                  net.minecraft.world.entity.projectile.Projectile.class,
                  v.getBoundingBox().inflate(15))
              .stream()
              .filter(e -> e.getOwner() == p)
              .findFirst()
              .orElseThrow();
      check(
          h,
          ((ITrackableProjectile) missile).getTargetUUID().equals(target.getUUID().toString()),
          "IR acquires visible entity inside JSON cone");
    } finally {
      LoadoutMod.INFINITE.set(before);
    }
    h.succeed();
  }
}
