package cn.zzy.loadout.test;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import com.atsuishio.superbwarfare.entity.projectile.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class AirToAirGameTests {
  public static final List<String> IDS=List.of("aim54a","aim54c","pl12","pl12a","r40rd","r40td","r73","r73l","r77","r77_1");
  public static void load(VehicleEntity v,String id) {
    var s=LoadoutService.state(v);s.entries.clear();s.nextFire=0;
    s.entries.add(new LoadoutState.Entry(UUID.randomUUID(),id,0));LoadoutService.save(v,s);
  }
  private static void ok(GameTestHelper h,boolean value,String text) { h.assertTrue(value,text); }

  @GameTest(template="empty",batch="airToAir")
  public static void tenProductionRoundsRetainIdentityAndHalfBlast(GameTestHelper h) {
    var s=RadarGameTests.setup(h,1,130);
    double[] blast={167.31,151.005,32.555,32.555,152,152,16.47,16.47,43,43};
    RadarGameTests.scoped(RadarGameTests.track(s),()-> {
      for(int i=0;i<IDS.size();i++) {
        String id="sbw_loadout:"+IDS.get(i);var a=LoadoutApi.get(id);load(s.v(),id);
        LoadoutService.fire(s.p(),s.v(),null,null);
        var p=RadarGameTests.shots(s).getFirst();var b=(IBulletProperties)p;
        ok(h,a.category().equals("AirToAirMissile") && a.explosionDamage()==blast[i],"approved half blast: "+id);
        ok(h,b.getExplosionDamage()==(float)blast[i] && b.getDamage()==(float)a.damage()
            && b.getExplosionRadius()==(float)a.explosionRadius(),"actual projectile damage/radius: "+id);
        ok(h,((AmmoModelEntity)p).loadoutAmmoModel().equals(id),"original full model identity: "+id);
        ok(h,LoadoutService.state(s.v()).entries.isEmpty(),"exactly one paid round: "+id);p.discard();
      }
    });h.succeed();
  }

  @GameTest(template="empty",batch="airToAir")
  public static void infraredProviderCannotBeBypassedWithClientTarget(GameTestHelper h) {
    var s=RadarGameTests.setup(h,1,130);load(s.v(),"sbw_loadout:r73");
    RadarGameTests.scoped(new RadarLink.Provider() {
      public Entity lockedTarget(VehicleEntity v) { return null; }
      public boolean hasInfraredControl() { return true; }
    },()-> {
      LoadoutService.fire(s.p(),s.v(),s.target().getUUID(),s.target().position());
      ok(h,RadarGameTests.shots(s).isEmpty() && LoadoutService.state(s.v()).entries.size()==1,
          "external IR without valid heat lock cannot be bypassed by forged fire payload");
    });h.succeed();
  }

  @GameTest(template="empty",batch="airToAir")
  public static void infraredRegistersOnlyTheOnePaidProjectile(GameTestHelper h) {
    var s=RadarGameTests.setup(h,1,130);load(s.v(),"sbw_loadout:r40td");int[] calls={0};
    RadarGameTests.scoped(new RadarLink.Provider() {
      public Entity lockedTarget(VehicleEntity v) { return null; }
      public boolean hasInfraredControl() { return true; }
      public Entity infraredTarget(VehicleEntity v) { return s.target(); }
      public void infraredLaunched(Projectile p,net.minecraft.server.level.ServerPlayer owner,
          VehicleEntity v,Entity target,AmmoType a) {
        calls[0]++;ok(h,p.isAddedToLevel() && owner==s.p() && v==s.v() && target==s.target(),"post-spawn callback uses server identity");
      }
    },()-> {
      LoadoutService.fire(s.p(),s.v(),UUID.randomUUID(),null);
      ok(h,calls[0]==1 && RadarGameTests.shots(s).size()==1 && LoadoutService.state(s.v()).entries.isEmpty(),
          "one shot, one callback, one round, no replacement missile");
    });h.succeed();
  }

  @GameTest(template="empty",batch="airToAir")
  public static void airGroundInfraredDoesNotUseAirToAirProvider(GameTestHelper h) {
    var s=RadarGameTests.setup(h,1,130);load(s.v(),"sbw_loadout:kh38mt");
    RadarGameTests.scoped(new RadarLink.Provider() {
      public Entity lockedTarget(VehicleEntity v) { return null; }
      public boolean hasInfraredControl() { return true; }
      public Entity infraredTarget(VehicleEntity v) { throw new AssertionError("air-ground queried AA provider"); }
      public void infraredLaunched(Projectile p,net.minecraft.server.level.ServerPlayer owner,
          VehicleEntity v,Entity target,AmmoType a) { throw new AssertionError("air-ground joined AA controller"); }
    },()-> {
      LoadoutService.fire(s.p(),s.v(),null,null);
      ok(h,RadarGameTests.shots(s).size()==1,"Kh38 retains its native launch flow");
    });h.succeed();
  }

  @GameTest(template="empty",batch="airToAir")
  public static void assignedRadarTrackSurvivesChangingLaunchSelection(GameTestHelper h) {
    var s=RadarGameTests.setup(h,1,130);
    RadarGameTests.scoped(new RadarLink.Provider() {
      public Entity lockedTarget(VehicleEntity v) { return null; }
      public Entity launchTarget(VehicleEntity v,AmmoType a) { return s.target(); }
      public Entity trackedTarget(VehicleEntity v,UUID assigned) { return assigned.equals(s.target().getUUID()) ? s.target() : null; }
    },()-> {
      LoadoutService.fire(s.p(),s.v(),null,null);var p=RadarGameTests.shots(s).getFirst();
      var n=p.getPersistentData().getCompound(SeekerController.KEY);n.putDouble("TerminalRange",1);n.putInt("Age",12);
      RadarGuidance.tick(p);ok(h,n.getString("Phase").equals("MIDCOURSE"),"TWS assigned track remains independent of next launch selection");
      ok(h,RadarGuidance.assignedTargets(s.v()).contains(s.target().getUUID()),"midcourse target is reserved");
      RadarGuidance.tryChaff(h.getLevel(),p.position(),1,1,UUID.randomUUID().toString());
      ok(h,RadarGuidance.assignedTargets(s.v()).isEmpty(),"chaff frees the assigned track and channel");
    });h.succeed();
  }
}
