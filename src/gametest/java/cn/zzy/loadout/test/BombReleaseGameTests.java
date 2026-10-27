package cn.zzy.loadout.test;

import static cn.zzy.loadout.test.SeekerGameTests.*;
import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import com.atsuishio.superbwarfare.entity.projectile.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class BombReleaseGameTests {
  private static void load(VehicleEntity v, String id) {
    var s=LoadoutService.state(v); s.entries.clear(); s.nextFire=0;
    s.entries.add(new LoadoutState.Entry(UUID.randomUUID(),id,0)); LoadoutService.save(v,s);
  }
  private static Projectile fire(GameTestHelper h, ServerPlayer p, VehicleEntity v) {
    var s=LoadoutService.state(v);s.nextFire=0;LoadoutService.save(v,s);
    LoadoutService.fire(p,v,null,null);
    return h.getLevel().getEntitiesOfClass(Projectile.class,v.getBoundingBox().inflate(10)).stream()
        .filter(e->e.getOwner()==p).findFirst().orElseThrow();
  }
  private static void exact(GameTestHelper h, Projectile shot, VehicleEntity v, double speed) {
    Vec3 expected=v.getViewVector(1).normalize().scale(speed).add(v.getDeltaMovement());
    ok(h,shot.getDeltaMovement().distanceTo(expected)<1e-7,"locked release has zero added angular error; actual="+shot.getDeltaMovement());
  }
  private static void ballistic(GameTestHelper h, Projectile shot) {
    ok(h,shot instanceof AerialBombEntity,"unlocked bomb uses native falling bomb physics");
    ok(h,!shot.getPersistentData().contains(SeekerController.KEY),"unlocked release does not attach a seeker");
    ok(h,!shot.isNoGravity(),"unlocked release retains gravity");
  }

  @GameTest(template="empty",batch="bombRelease")
  public static void randomDropsAndMovingAircraft(GameTestHelper h) {
    var p=player(h); var v=plane(h,p,"fixture_bombs:plain");
    v.setDeltaMovement(.4,.1,-.2);
    var first=fire(h,p,v); ballistic(h,first);
    ok(h,LoadoutService.state(v).entries.isEmpty(),"unlocked survival drop consumes one bomb"); first.discard();
    p.setGameMode(GameType.CREATIVE); load(v,"fixture_bombs:plain");
    List<Vec3> velocities=new ArrayList<>();
    for(int i=0;i<32;i++) {
      var shot=fire(h,p,v); ballistic(h,shot);
      velocities.add(shot.getDeltaMovement().subtract(v.getDeltaMovement()));
      double y=shot.getDeltaMovement().y; shot.tick();
      ok(h,shot.getDeltaMovement().y<y-.02,"ordinary drop accelerates downwards"); shot.discard();
    }
    double spanX=velocities.stream().mapToDouble(e->e.x).max().orElseThrow()-velocities.stream().mapToDouble(e->e.x).min().orElseThrow();
    double spanY=velocities.stream().mapToDouble(e->e.y).max().orElseThrow()-velocities.stream().mapToDouble(e->e.y).min().orElseThrow();
    ok(h,spanX>.01&&spanY>.01,"actual repeated releases scatter horizontally and vertically");
    com.mojang.logging.LogUtils.getLogger().info("BOMB_RELEASE_SAMPLES n=32 spanX={} spanY={}",spanX,spanY);
    h.succeed();
  }

  @GameTest(template="empty",batch="bombRelease")
  public static void televisionLockAndLoss(GameTestHelper h) {
    var p=player(h); var v=plane(h,p,"fixture_bombs:tv");
    p.setGameMode(GameType.CREATIVE);v.setDeltaMovement(.4,.1,-.2);
    var a=LoadoutData.ammo().get("fixture_bombs:tv");
    var target=EntityType.PIG.create(h.getLevel()); target.setNoAi(true);target.setNoGravity(true);
    target.setPos(v.position().add(0,1,24));h.getLevel().addFreshEntity(target);
    ok(h,TelevisionLocks.acquire(p,v,a,target.getUUID(),null),"validated TV entity lock");
    ok(h,BombRelease.ready(p,v,a),"server release status agrees with TV lock");
    for(int i=0;i<16;i++) {
      var shot=fire(h,p,v); exact(h,shot,v,1);
      ok(h,shot.getPersistentData().getCompound(SeekerController.KEY).getUUID("Target").equals(target.getUUID()),"zero-scatter drop still arms intended seeker");shot.discard();
    }
    target.discard();
    ok(h,!BombRelease.ready(p,v,a),"dead target invalidates zero-scatter eligibility");
    var shot=fire(h,p,v);ballistic(h,shot);
    ok(h,BombRelease.spread(a,false)==4,"unlocked custom Spread above global minimum is preserved");
    shot.discard();h.succeed();
  }

  @GameTest(template="empty",batch="bombRelease")
  public static void laserValidityControlsScatter(GameTestHelper h) {
    var p=player(h);var v=plane(h,p,"fixture_bombs:laser");p.setGameMode(GameType.CREATIVE);
    var a=LoadoutData.ammo().get("fixture_bombs:laser");
    var shot=fire(h,p,v);ballistic(h,shot);shot.discard();
    wall(p);illuminate(p);
    ok(h,BombRelease.ready(p,v,a),"matching visible laser qualifies for zero scatter");
    shot=fire(h,p,v);exact(h,shot,v,1);
    ok(h,shot.getPersistentData().getCompound(SeekerController.KEY).getString("Mode").equals("LASER"),"locked laser bomb keeps laser guidance");shot.discard();
    CompoundTag code=new CompoundTag();code.putInt("SBWLoadoutLaserCode",1234);
    p.getUseItem().set(DataComponents.CUSTOM_DATA,CustomData.of(code));
    ok(h,!BombRelease.ready(p,v,a),"wrong code does not cancel scatter");
    shot=fire(h,p,v);ballistic(h,shot);shot.discard();
    p.stopUsingItem();shot=fire(h,p,v);ballistic(h,shot);shot.discard();
    h.succeed();
  }

  @GameTest(template="empty",batch="bombRelease")
  public static void coordinatesNeedExplicitSurfaceLock(GameTestHelper h) {
    var p=player(h);var v=plane(h,p,"fixture_bombs:ins");p.setGameMode(GameType.CREATIVE);wall(p);
    var a=LoadoutData.ammo().get("fixture_bombs:ins");
    var shot=fire(h,p,v);ballistic(h,shot);shot.discard();
    ok(h,!TelevisionLocks.acquire(p,v,a,null,new Vec3(Double.NaN,0,0)),"invalid client coordinate cannot remove spread");
    ok(h,TelevisionLocks.acquireCurrent(p,null,null),"K path supports explicit coordinate-bomb surface lock");
    Vec3 point=TelevisionLocks.current(p,v,a).point();
    shot=fire(h,p,v);exact(h,shot,v,1);
    var n=shot.getPersistentData().getCompound(SeekerController.KEY);
    ok(h,!n.hasUUID("Target")&&SeekerController.readPoint(n).equals(point),"coordinate bomb guides to frozen validated point");
    shot.discard();TelevisionLocks.remove(p.getUUID());
    shot=fire(h,p,v);ballistic(h,shot);shot.discard();h.succeed();
  }

  @GameTest(template="empty",batch="bombRelease")
  public static void plainBombCannotGainGuidanceFromPod(GameTestHelper h) {
    var p=player(h);var v=plane(h,p,"fixture_bombs:plain");wall(p);
    ok(h,TargetingPod.open(p),"pod opens for ordinary bomb");
    var point=p.getEyePosition().add(0,0,24);
    var d=point.subtract(TargetingPod.origin(v,1));TargetingPod.aim(p,TargetingPod.yaw(d),TargetingPod.pitch(d));
    ok(h,TargetingPod.lock(p,true),"plain bomb can observe stabilized surface");
    var a=LoadoutData.ammo().get("fixture_bombs:plain");
    ok(h,!BombRelease.ready(p,v,a)&&BombRelease.spread(a,false)>0,"ordinary targeting pod does not grant a seeker");
    var shot=fire(h,p,v);ballistic(h,shot);shot.discard();TargetingPod.close(p.getUUID());h.succeed();
  }

  @GameTest(template="empty",batch="bombRelease")
  public static void coordinatePodLockFreezesWorldPoint(GameTestHelper h) {
    var p=player(h);var v=plane(h,p,"fixture_bombs:ins");
    var target=EntityType.PIG.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);
    target.setPos(v.position().add(0,1,20));h.getLevel().addFreshEntity(target);
    ok(h,TargetingPod.open(p),"coordinate bomb opens pod");
    var d=target.getBoundingBox().getCenter().subtract(TargetingPod.origin(v,1));
    TargetingPod.aim(p,TargetingPod.yaw(d),TargetingPod.pitch(d));
    ok(h,TargetingPod.lock(p,false),"pod K acquires coordinate bomb point");
    var a=LoadoutData.ammo().get("fixture_bombs:ins");var lock=TelevisionLocks.current(p,v,a);
    ok(h,lock!=null&&lock.target()==null&&TargetingPod.current(p).mode.equals("AREA"),"coordinate lock is fixed AREA, not autonomous entity tracking");
    var point=lock.point();target.setPos(target.position().add(4,0,0));TargetingPod.update(p);
    ok(h,TargetingPod.current(p).point.equals(point)&&TelevisionLocks.current(p,v,a).point().equals(point),"moving original entity does not drag coordinate guidance");
    var shot=fire(h,p,v);exact(h,shot,v,1);shot.discard();TargetingPod.close(p.getUUID());h.succeed();
  }

  @GameTest(template="empty",batch="bombRelease")
  public static void allElevenBombTypesCanDropUnlocked(GameTestHelper h) {
    var p=player(h);var v=plane(h,p,"fixture_bombs:plain");p.setGameMode(GameType.CREATIVE);
    int count=0;
    for(var a:LoadoutData.ammo().values()) {
      if(!a.id().startsWith("sbw_loadout:")||!BombRelease.isBomb(a))continue;
      load(v,a.id());var shot=fire(h,p,v);ballistic(h,shot);
      var props=(IBulletProperties)shot;
      ok(h,props.getDamage()==(float)a.damage()&&props.getExplosionRadius()==(float)a.explosionRadius(),"unlocked drop retains damage balance: "+a.id());
      if(a.renderProjectileModel())ok(h,shot instanceof AmmoModelEntity model&&model.loadoutAmmoModel().equals(a.id()),"ordinary drop retains full ammo model: "+a.id());
      shot.discard();count++;
    }
    ok(h,count==11,"all eleven production bombs covered");h.succeed();
  }

  @GameTest(template="empty",batch="bombRelease")
  public static void missilesKeepTheirLockRequirements(GameTestHelper h) {
    var p=player(h);var v=plane(h,p,"sbw_loadout:kh29l");
    for(String id:List.of("kh29l","kh29td","kh59mk","9m336_radar")) {
      load(v,"sbw_loadout:"+id);LoadoutService.fire(p,v,null,null);
      ok(h,LoadoutService.state(v).entries.size()==1,"unlocked missile remains rejected: "+id);
      var a=LoadoutData.ammo().get("sbw_loadout:"+id);
      ok(h,BombRelease.spread(a,true)==a.spread()&&BombRelease.spread(a,false)==a.spread(),"missile spread unaffected: "+id);
    }
    h.succeed();
  }
}
