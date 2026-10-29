package cn.zzy.loadout.test;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.guidance.*;
import com.atsuishio.superbwarfare.entity.projectile.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.zzy.sbwradar.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Runs only with the actual radar 1.11.1 JAR on the runtime classpath. */
@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class RadarCompatGameTests {
  private static void ok(GameTestHelper h,boolean value,String message) { h.assertTrue(value,message); }
  private static Object state(VehicleEntity v) {
    try {var f=ServerRadarHandler.class.getDeclaredField("STATES");f.setAccessible(true);return ((Map<?,?>)f.get(null)).get(v.getId());}
    catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
  }
  private static void set(Object o,String key,Object value) {
    try {var f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}
    catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
  }
  private static void scan(ServerPlayer p) {
    try {var f=ServerRadarHandler.class.getDeclaredField("PROCESSED_VEHICLES_THIS_TICK");f.setAccessible(true);((Set<?>)f.get(null)).clear();}
    catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
    for(Entity e:p.serverLevel().getAllEntities())
      if(e instanceof VehicleEntity) com.atsuishio.superbwarfare.tools.ServerSyncedEntityHandler.register(e);
    try {var m=ServerRadarHandler.class.getDeclaredMethod("handlePlayer",ServerLevel.class,net.minecraft.world.entity.player.Player.class);
      m.setAccessible(true);m.invoke(null,p.serverLevel(),p);}
    catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
  }
  private static RadarGameTests.Setup setup(GameTestHelper h,String id) {
    var s=RadarGameTests.setup(h,1,130);s.target().discard();
    var target=TestFixtures.AIRCRAFT.create(h.getLevel());target.setNoGravity(true);
    target.setPos(s.v().position().add(0,1,130));h.getLevel().addFreshEntity(target);
    var driver=SeekerGameTests.player(h);driver.startRiding(target,true);
    var scoreboard=h.getLevel().getScoreboard();
    var team=scoreboard.addPlayerTeam("aa_"+UUID.randomUUID().toString().substring(0,8));
    scoreboard.addPlayerToTeam(driver.getScoreboardName(),team);
    AirToAirGameTests.load(s.v(),"sbw_loadout:"+id);
    ServerRadarHandler.cycleMode(s.p(),s.v().getId()); // initialize state (TWS)
    set(state(s.v()),"mode",RadarMode.TRK);set(state(s.v()),"targetId",target.getId());scan(s.p());
    ok(h,RadarLink.provider()!=null,"real radar bridge installed from common setup");
    ok(h,ServerRadarHandler.hasFreshTrack(s.v(),target.getId()),"actual SBW radar scan produces server hostile track; source="+s.v().position()+" target="+target.position()+" tracked="+ServerRadarHandler.twsTracks(s.v().getId())+" nativeRadar="+s.v().computed().getRadar()+" type="+s.v().getVehicleType()+" entries="+com.atsuishio.superbwarfare.tools.ServerSyncedEntityHandler.getEntries(h.getLevel().dimension().location()).size()+" syncEnabled="+com.atsuishio.superbwarfare.config.server.SyncConfig.SYNC_ENTITY_OVER_RANGE.get());
    return new RadarGameTests.Setup(s.p(),s.v(),target,LoadoutApi.get("sbw_loadout:"+id));
  }
  private static Projectile launch(GameTestHelper h,RadarGameTests.Setup s) {
    LoadoutService.fire(s.p(),s.v(),UUID.randomUUID(),new Vec3(999,999,999));
    var shots=RadarGameTests.shots(s);
    if(shots.isEmpty()) com.mojang.logging.LogUtils.getLogger().info("IR_DIAG source={} look={} target={} bbox={} loaded={} enabled={} front={} rear={}",
        s.v().position(),s.v().getLookAngle(),s.target().position(),s.target().getBoundingBox(),
        AimSupport.loaded(h.getLevel(),s.v().position().add(0,1.5,0),s.target().getBoundingBox().getCenter()),
        SbwRadarConfig.IR_ENABLE.get(),SbwRadarConfig.IR_RANGE_FRONT.get(),SbwRadarConfig.IR_RANGE_REAR.get());
    var ir=LoadoutService.state(s.v()).entries.isEmpty() ? null : RadarLink.provider().infraredTarget(s.v());
    ok(h,shots.size()==1,"one real projectile from loadout fire; shots="+shots.size()+" entries="+LoadoutService.state(s.v()).entries.size()
        +" ir="+(ir==null ? "null" : ir.getUUID()+"@"+ir.position())+" ammo="+s.ammo().id()+" pilot="+LoadoutService.pilot(s.p(),s.v())
        +" validIR="+(ir!=null && RadarLocks.valid(s.p(),s.v(),s.ammo(),ir)));return shots.getFirst();
  }

  @GameTest(template="empty",batch="radarCompat")
  public static void actualTrkFiresEveryRadarAirToAirModel(GameTestHelper h) {
    var s=setup(h,"aim54a");
    for(String id:AirToAirGameTests.IDS) {
      var a=LoadoutApi.get("sbw_loadout:"+id);if(!a.guidance().equals("RADAR")) continue;
      AirToAirGameTests.load(s.v(),a.id());var shot=launch(h,s);
      ok(h,shot.getPersistentData().getCompound(SeekerController.KEY).getUUID("Target").equals(s.target().getUUID()),"actual TRK overrides client target: "+id);
      ok(h,LoadoutService.state(s.v()).entries.isEmpty(),"one survival round spent: "+id);shot.discard();
    }h.succeed();
  }

  @GameTest(template="empty",batch="radarCompat")
  public static void srcAndExpiredTrackCannotAuthorizeLaunch(GameTestHelper h) {
    var s=setup(h,"pl12");set(state(s.v()),"mode",RadarMode.SRC);
    LoadoutService.fire(s.p(),s.v(),s.target().getUUID(),null);
    ok(h,RadarGameTests.shots(s).isEmpty() && LoadoutService.state(s.v()).entries.size()==1,"SRC has no launch authorization");
    set(state(s.v()),"mode",RadarMode.TRK);set(state(s.v()),"lastScanTick",Integer.MIN_VALUE);
    LoadoutService.fire(s.p(),s.v(),s.target().getUUID(),null);
    ok(h,RadarGameTests.shots(s).isEmpty(),"stale TRK cannot authorize launch");h.succeed();
  }

  @GameTest(template="empty",batch="radarCompat")
  public static void nativeRadarAircraftAlsoGetsLoadoutFireControl(GameTestHelper h) {
    var s=setup(h,"pl12");var data=s.v().computed();var previous=data.getRadar();
    try {
      data.setRadar(new com.atsuishio.superbwarfare.data.ObjectToList<>(new com.atsuishio.superbwarfare.data.vehicle.subdata.RadarInfo()));
      set(state(s.v()),"lastHostiles",new ArrayList<Integer>());set(state(s.v()),"lastScanTick",Integer.MIN_VALUE);
      scan(s.p());ok(h,ServerRadarHandler.hasFreshTrack(s.v(),s.target().getId()),"native-radar loadout aircraft gets a fresh addon fire-control scan");
      launch(h,s);
    }finally {data.setRadar(previous);}h.succeed();
  }

  @GameTest(template="empty",batch="radarCompat")
  public static void twsDistributesAndKeepsAssignedTrack(GameTestHelper h) {
    var s=setup(h,"r77");
    var second=TestFixtures.AIRCRAFT.create(h.getLevel());second.setNoGravity(true);
    second.setPos(s.target().position().add(10,0,0));h.getLevel().getChunk(second.getBlockX()>>4,second.getBlockZ()>>4);
    for(int x=Math.min(s.v().getBlockX(),second.getBlockX())>>4;x<=(Math.max(s.v().getBlockX(),second.getBlockX())>>4);x++)
      for(int z=Math.min(s.v().getBlockZ(),second.getBlockZ())>>4;z<=(Math.max(s.v().getBlockZ(),second.getBlockZ())>>4);z++) h.getLevel().getChunk(x,z);
    h.getLevel().addFreshEntity(second);
    var driver=SeekerGameTests.player(h);driver.startRiding(second,true);
    var team=h.getLevel().getScoreboard().addPlayerTeam("aa_"+UUID.randomUUID().toString().substring(0,8));
    h.getLevel().getScoreboard().addPlayerToTeam(driver.getScoreboardName(),team);
    set(state(s.v()),"targetId",second.getId());scan(s.p());
    ok(h,ServerRadarHandler.hasFreshTrack(s.v(),second.getId()),"second target also actually scanned");
    set(state(s.v()),"mode",RadarMode.TWS);set(state(s.v()),"lastHostiles",new ArrayList<>(List.of(s.target().getId(),second.getId())));
    var first=launch(h,s);AirToAirGameTests.load(s.v(),s.ammo().id());LoadoutService.fire(s.p(),s.v(),null,null);
    var targets=RadarGuidance.assignedTargets(s.v());ok(h,targets.size()==2,"TWS allocates two different server tracks");
    var n=first.getPersistentData().getCompound(SeekerController.KEY);n.putDouble("TerminalRange",1);n.putInt("Age",12);RadarGuidance.tick(first);
    ok(h,n.getString("Phase").equals("MIDCOURSE"),"first missile follows its own TWS assignment");
    AirToAirGameTests.load(s.v(),s.ammo().id());LoadoutService.fire(s.p(),s.v(),null,null);
    ok(h,RadarGameTests.shots(s).size()==2 && LoadoutService.state(s.v()).entries.size()==1,"shared full channel limit blocks without spending");h.succeed();
  }

  @GameTest(template="empty",batch="radarCompat")
  public static void actualIrControllerReceivesAllThreeIrModels(GameTestHelper h) {
    var s=setup(h,"r73");
    // 使用单独的高度，避免同批其他用例的飞机成为更近的热目标。
    Vec3 isolated=new Vec3(0,210,0);
    s.v().setPos(s.v().position().add(isolated));s.p().setPos(s.p().position().add(isolated));
    s.target().setPos(s.v().position().add(0,1,80));s.target().setYRot(0);
    for(String id:List.of("r73","r73l","r40td")) {
      AirToAirGameTests.load(s.v(),"sbw_loadout:"+id);var shot=launch(h,s);
      ok(h,shot.getPersistentData().contains("SBWRadarLoadoutIR") && shot instanceof MissileProjectile,
          "actual radar IR controller tracks existing projectile: "+id);
      ok(h,((MissileProjectile)shot).getTargetUUID().equals(s.target().getStringUUID()),"native IR target identity: "+id);
      ok(h,RadarGuidance.activeFor(s.v())==0,"IR does not occupy radar channels");shot.discard();
    }h.succeed();
  }

  @GameTest(template="empty",batch="radarCompat")
  public static void infraredDisabledCannotFallBackToIndependentSearch(GameTestHelper h) {
    var s=setup(h,"r73");boolean previous=SbwRadarConfig.IR_ENABLE.get();SbwRadarConfig.IR_ENABLE.set(false);
    try {LoadoutService.fire(s.p(),s.v(),s.target().getUUID(),null);
      ok(h,RadarGameTests.shots(s).isEmpty() && LoadoutService.state(s.v()).entries.size()==1,"IR disabled blocks AA launch, no fallback or ammo loss");
    }finally {SbwRadarConfig.IR_ENABLE.set(previous);}h.succeed();
  }

  @GameTest(template="empty",batch="radarCompat")
  public static void chaffAndFlareStayInTheirOwnChannels(GameTestHelper h) {
    var s=setup(h,"pl12");var radar=launch(h,s);
    AirToAirGameTests.load(s.v(),"sbw_loadout:r73");LoadoutService.fire(s.p(),s.v(),null,null);
    var ir=RadarGameTests.shots(s).stream().filter(e->e!=radar).findFirst().orElseThrow();
    int nativeIr=IrMissileControl.tryDecoy(h.getLevel(),ir.position(),1,1,UUID.randomUUID().toString());
    ok(h,nativeIr>=1 && ((MissileProjectile)ir).isDistracted(),"radar flare API defeats loadout IR");
    ok(h,!radar.getPersistentData().getCompound(SeekerController.KEY).getString("Phase").equals("DECOYED"),"flare does not defeat radar");
    ok(h,LoadoutIntegration.tryDecoy(h.getLevel(),radar.position(),1,1,UUID.randomUUID().toString())==1,"radar chaff bridge defeats loadout radar");
    ok(h,RadarGuidance.activeFor(s.v())==0,"chaff frees shared radar channel");
    var saved=new net.minecraft.nbt.CompoundTag();ir.saveWithoutId(saved);
    var restored=(MissileProjectile)ir.getType().create(h.getLevel());restored.load(saved);
    ir.discard();h.getLevel().addFreshEntity(restored);
    ok(h,restored.isDistracted() && restored.getPersistentData().getCompound("SBWRadarLoadoutIR").getBoolean("Decoyed"),"IR distraction is restored from persistent marker on entity join");restored.discard();h.succeed();
  }

  @GameTest(template="empty",batch="radarCompat")
  public static void closeFuzeIgnoresLauncherAndUsesAmmoBlast(GameTestHelper h) {
    var s=setup(h,"aim54a");var shot=(FastThrowableProjectile)launch(h,s);
    var n=shot.getPersistentData().getCompound(SeekerController.KEY);n.putInt("Age",12);
    try {
      var check=AaProximityFuze.class.getDeclaredMethod("check",ServerLevel.class,Projectile.class,double.class);check.setAccessible(true);
      check.invoke(null,h.getLevel(),shot,8.0);ok(h,!shot.isRemoved(),"fuze ignores launcher and its pilot");
      s.target().setPos(shot.position().add(0,0,2));shot.setDamage(0);
      shot.setExplosionDamage(0);shot.setExplosionRadius(0);
      check.invoke(null,h.getLevel(),shot,8.0);
      ok(h,shot.isRemoved() && shot.getExploded(),"fuze invokes SBW projectile explosion rather than a separate vanilla blast; removed="+shot.isRemoved()+" exploded="+shot.getExploded()+" target="+s.target().position()+" shot="+shot.position()+" onGround="+s.target().onGround());
    }catch(ReflectiveOperationException e) {throw new RuntimeException(e);}h.succeed();
  }

  @GameTest(template="empty",batch="radarCompat")
  public static void splitDefenseKeepsAircraftManualAndGroundLaunchWorking(GameTestHelper h) {
    var s=setup(h,"pl12");
    var info=new com.atsuishio.superbwarfare.data.gun.ProjectileInfo();info.setId("superbwarfare:aa_missile");info.setItemId("superbwarfare:aa_missile");
    var consumer=new com.atsuishio.superbwarfare.data.gun.AmmoConsumer();
    consumer.setProjectile(new com.atsuishio.superbwarfare.data.StringToObject<>(info));
    var defaults=new com.atsuishio.superbwarfare.data.gun.DefaultGunData();
    defaults.setAmmoConsumers(new com.atsuishio.superbwarfare.data.ObjectToList<>(new com.atsuishio.superbwarfare.data.StringToObject<>(consumer)));
    var gun=com.atsuishio.superbwarfare.data.gun.GunData.from(new net.minecraft.world.item.ItemStack(com.atsuishio.superbwarfare.init.ModItems.EMPTY_GUN.get()),()->defaults);
    s.v().setGunDataMap(new HashMap<>(Map.of("native_missile",gun)));
    set(s.v(),"gunDataMapCache",new HashMap<>(Map.of("native_missile",gun)));
    set(s.v(),"gunDataMapWeaponKeys",new HashSet<>(Set.of("native_missile")));
    ok(h,RadarMissileControl.missileGun(s.v())!=null,"fixture has an actual native missile weapon as well as a paid custom loadout; ammo="+RadarMissileControl.ammoId(gun)+" consumers="+gun.get(com.atsuishio.superbwarfare.data.gun.GunProp.AMMO_CONSUMER));
    ok(h,!RadarMissileControl.launchFromLauncher(s.v(),s.v(),s.target()),"remote native launch API cannot bypass custom aircraft loadout");
    try {
      var tick=SplitAirDefense.class.getDeclaredMethod("tickLevel",ServerLevel.class,int.class);tick.setAccessible(true);
      tick.invoke(null,h.getLevel(),h.getLevel().getServer().getTickCount());
    }catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
    ok(h,h.getLevel().getEntitiesOfClass(Projectile.class,s.v().getBoundingBox().inflate(180)).stream()
        .noneMatch(p->p.getOwner()==s.v() || p.getOwner()==s.p()) && LoadoutService.state(s.v()).entries.size()==1,
        "TRK does not auto-fire or spend custom aircraft rounds");
    var ground=TestFixtures.GROUND.create(h.getLevel());ground.setPos(s.v().position().add(20,0,0));h.getLevel().addFreshEntity(ground);
    ground.setGunDataMap(new HashMap<>(Map.of("native_missile",gun)));
    set(ground,"gunDataMapCache",new HashMap<>(Map.of("native_missile",gun)));
    set(ground,"gunDataMapWeaponKeys",new HashSet<>(Set.of("native_missile")));
    String previous=SbwRadarConfig.MISSILE_ENTITY.get();SbwRadarConfig.MISSILE_ENTITY.set("superbwarfare:ru_9m336_missile");
    try {ok(h,RadarMissileControl.launchFromLauncher(ground,ground,s.target()),"upstream ground-launch API remains available");
      ok(h,h.getLevel().getEntitiesOfClass(Projectile.class,ground.getBoundingBox().inflate(8)).stream()
          .anyMatch(p->p.getOwner()==ground),"ground API still creates its own real missile");
      var groundShot=h.getLevel().getEntitiesOfClass(Projectile.class,ground.getBoundingBox().inflate(8)).stream()
          .filter(p->p.getOwner()==ground).findFirst().orElseThrow();
      try {
        var f=RadarMissileControl.class.getDeclaredField("GUIDED");f.setAccessible(true);
        Object state=((Map<?,?>)f.get(null)).get(groundShot);
        var friendly=RadarMissileControl.class.getDeclaredMethod("isFriendly",Entity.class,state.getClass(),Entity.class);friendly.setAccessible(true);
        friendly.invoke(null,groundShot,state,s.target());
      }catch(ReflectiveOperationException e) {throw new RuntimeException("autonomous ground shot with no player owner must not crash terminal seeker",e);}
    }finally {SbwRadarConfig.MISSILE_ENTITY.set(previous);}h.succeed();
  }
}
