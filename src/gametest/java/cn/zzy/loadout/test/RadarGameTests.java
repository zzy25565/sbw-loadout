package cn.zzy.loadout.test;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class RadarGameTests {
  static final String ID="sbw_loadout:9m336_radar";
  record Setup(ServerPlayer p,VehicleEntity v,Entity target,AmmoType ammo) {}
  static Setup setup(GameTestHelper h,int rounds,double distance) {
    var p=SeekerGameTests.player(h);var v=SeekerGameTests.plane(h,p,ID);
    // 长距离用例抬到其他并行测试墙体之上，避免测试场互相遮挡。
    if(distance>96) { v.setPos(v.position().add(0,80,0));p.setPos(p.position().add(0,80,0)); }
    var s=LoadoutService.state(v);s.entries.clear();
    for(int i=0;i<rounds;i++) s.entries.add(new LoadoutState.Entry(UUID.randomUUID(),ID,i));
    LoadoutService.save(v,s);
    var target=EntityType.PIG.create(h.getLevel());target.setNoGravity(true);target.setNoAi(true);
    target.setPos(v.position().add(v.getViewVector(1).scale(distance)).add(0,1,0));
    // 仅测试夹具预加载路径；生产制导的 AimSupport 不会请求新区块。
    int minX=(int)Math.floor(Math.min(p.getX(),target.getX())/16), maxX=(int)Math.floor(Math.max(p.getX(),target.getX())/16);
    int minZ=(int)Math.floor(Math.min(p.getZ(),target.getZ())/16), maxZ=(int)Math.floor(Math.max(p.getZ(),target.getZ())/16);
    for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) h.getLevel().getChunk(x,z);
    h.getLevel().addFreshEntity(target);
    return new Setup(p,v,target,LoadoutApi.get(ID));
  }
  static List<Projectile> shots(Setup s) {
    return s.p.serverLevel().getEntitiesOfClass(Projectile.class,s.v.getBoundingBox().inflate(180)).stream()
        .filter(e->e.getOwner()==s.p).toList();
  }
  static void scoped(RadarLink.Provider value,Runnable action) {
    var old=RadarLink.provider();if(old!=null) RadarLink.uninstall(old);
    if(value!=null) RadarLink.install(value);
    try { action.run(); } finally {
      if(value!=null) RadarLink.uninstall(value);
      if(old!=null) RadarLink.install(old);
    }
  }
  static RadarLink.Provider track(Setup s) { return v->v==s.v ? s.target : null; }
  static void check(GameTestHelper h,boolean b,String text) { h.assertTrue(b,text); }

  @GameTest(template="empty",batch="radar")
  public static void noLockAndForgedTargetDoNotSpend(GameTestHelper h) {
    var s=setup(h,1,16);
    scoped(null,()-> {
      LoadoutService.fire(s.p,s.v,s.target.getUUID(),s.target.position());
      check(h,shots(s).isEmpty() && LoadoutService.state(s.v).entries.size()==1,"fire payload cannot invent radar lock");
      check(h,RadarLocks.acquireCurrent(s.p,s.target.getUUID(),null),"standalone validated K lock");
      s.v.vehicleShoot(s.p,(UUID)null,null);
      check(h,shots(s).size()==1 && LoadoutService.state(s.v).entries.isEmpty(),"one native entry, one projectile, one spent round");
      check(h,BuiltInRegistries.ENTITY_TYPE.getKey(shots(s).getFirst().getType()).toString().equals("sbw_loadout:9m336_radar"),"independent radar projectile type");
    });h.succeed();
  }

  @GameTest(template="empty",batch="radar")
  public static void externalTrackIsAuthoritative(GameTestHelper h) {
    var s=setup(h,1,16);
    scoped(v->null,()-> {
      check(h,!RadarLocks.acquireCurrent(s.p,s.target.getUUID(),null),"no fallback around external SRC/no-track");
      LoadoutService.fire(s.p,s.v,s.target.getUUID(),null);
      check(h,shots(s).isEmpty(),"external radar without TRK blocks launch");
    });
    scoped(track(s),()-> {
      LoadoutService.fire(s.p,s.v,UUID.randomUUID(),new Vec3(0,999,0));
      var n=shots(s).getFirst().getPersistentData().getCompound(SeekerController.KEY);
      check(h,n.getUUID("Target").equals(s.target.getUUID()),"client target replaced by server TRK");
    });h.succeed();
  }

  @GameTest(template="empty",batch="radar")
  public static void boostMidcourseAndTerminalAreSeparate(GameTestHelper h) {
    var s=setup(h,1,130);
    final Projectile[] launched={null};
    scoped(track(s),()-> {
      check(h,RadarLocks.current(s.p,s.v,s.ammo)!=null,"far fixture track loaded="+AimSupport.loaded(s.p.serverLevel(),s.p.getEyePosition(),s.target.getBoundingBox().getCenter())+" visible="+AimSupport.visible(s.p.serverLevel(),s.p,s.p.getEyePosition(),s.target.getBoundingBox().getCenter()));
      LoadoutService.fire(s.p,s.v,null,null);launched[0]=shots(s).getFirst();
      var p=launched[0];var n=p.getPersistentData().getCompound(SeekerController.KEY);
      RadarGuidance.tick(p);check(h,n.getString("Phase").equals("BOOST"),"illumination-axis boost");
      // 其他并行测试的敌对夹具不应触发本用例的提前末段搜索。
      n.putDouble("TerminalRange",1);
      n.putInt("Age",s.ammo.radar().climbTicks());RadarGuidance.tick(p);
      check(h,n.getString("Phase").equals("MIDCOURSE"),"radar midcourse outside terminal range: phase="+n.getString("Phase")+" target="+n.getUUID("Target")+" expected="+s.target.getUUID());
      n.putDouble("TerminalRange",s.ammo.radar().terminalRange());
      s.target.setPos(p.position().add(0,0,30));p.setDeltaMovement(0,0,2);RadarGuidance.tick(p);
      check(h,n.getBoolean("Terminal"),"terminal handoff within seeker range: alive="+s.target.isAlive()+" position="+p.position()+" target="+s.target.position()+" visible="+AimSupport.visible(h.getLevel(),p,p.position(),s.target.getBoundingBox().getCenter())+" cone="+AimSupport.inCone(p.position(),p.getDeltaMovement(),s.target.getBoundingBox().getCenter(),96,35));
    });
    s.p.stopRiding();
    scoped(v->null,()-> {
      RadarGuidance.tick(launched[0]);
      var n=launched[0].getPersistentData().getCompound(SeekerController.KEY);
      check(h,n.getString("Phase").equals("TERMINAL") && n.getUUID("Target").equals(s.target.getUUID()),"terminal survives radar loss and pilot exit");
    });h.succeed();
  }

  @GameTest(template="empty",batch="radar")
  public static void channelsReleaseAtHandoff(GameTestHelper h) {
    var s=setup(h,3,130);
    scoped(track(s),()-> {
      for(int i=0;i<2;i++) { var st=LoadoutService.state(s.v);st.nextFire=0;LoadoutService.save(s.v,st);LoadoutService.fire(s.p,s.v,null,null); }
      var st=LoadoutService.state(s.v);st.nextFire=0;LoadoutService.save(s.v,st);
      LoadoutService.fire(s.p,s.v,null,null);
      check(h,shots(s).size()==2 && LoadoutService.state(s.v).entries.size()==1,"full channels reject without spending");
      var p=shots(s).getFirst();p.setPos(s.target.position().add(0,0,-30));p.setDeltaMovement(0,0,2);
      // 保持已加入世界的弹体留在原生实体可见区块内，用移动目标模拟进入末段距离。
      p.setPos(LoadoutService.launchOrigin(s.v));s.target.setPos(p.position().add(0,0,30));
      p.getPersistentData().getCompound(SeekerController.KEY).putInt("Age",12);RadarGuidance.tick(p);
      check(h,RadarGuidance.activeFor(s.v)==1,"terminal frees illumination channel");
      LoadoutService.fire(s.p,s.v,null,null);
      check(h,shots(s).size()==3 && LoadoutService.state(s.v).entries.isEmpty(),"released channel accepts next round");
    });h.succeed();
  }

  @GameTest(template="empty",batch="radar")
  public static void chaffSurvivesSaveAndDoesNotReacquire(GameTestHelper h) {
    var s=setup(h,1,16);
    scoped(track(s),()-> {
      LoadoutService.fire(s.p,s.v,null,null);var p=shots(s).getFirst();
      check(h,RadarGuidance.tryChaff(h.getLevel(),p.position(),.1,1,UUID.randomUUID().toString())==1,"chaff defeats radar missile");
      var n=p.getPersistentData().getCompound(SeekerController.KEY);
      var saved=new net.minecraft.nbt.CompoundTag();p.saveWithoutId(saved);
      var restored=(Projectile)p.getType().create(h.getLevel());restored.load(saved);restored.setOwner(s.p);
      RadarGuidance.tick(restored);
      check(h,restored.getPersistentData().getCompound(SeekerController.KEY).getString("Phase").equals("DECOYED"),"decoyed state persists without reacquisition");
      for(int i=0;i<=s.ammo.radar().lostTimeout();i++) RadarGuidance.tick(p);
      check(h,p.isRemoved(),"lost timeout bounds lifetime");restored.discard();
    });h.succeed();
  }

  @GameTest(template="empty",batch="radar")
  public static void friendlyAndOccludedLocksRejected(GameTestHelper h) {
    var s=setup(h,1,16);
    scoped(null,()-> {
      check(h,!RadarLocks.valid(s.p,s.v,s.ammo,s.p) && !RadarLocks.valid(s.p,s.v,s.ammo,s.v),"owner and launcher excluded");
      check(h,RadarLocks.acquireCurrent(s.p,s.target.getUUID(),null),"clear target lock");
      var center=net.minecraft.core.BlockPos.containing(s.p.getEyePosition().add(0,0,8));
      for(int x=-5;x<=5;x++) for(int y=-4;y<=4;y++) h.getLevel().setBlock(center.offset(x,y,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
      check(h,RadarLocks.current(s.p,s.v,s.ammo)==null,"occlusion invalidates stale lock");
      LoadoutService.fire(s.p,s.v,null,null);
      check(h,shots(s).isEmpty() && LoadoutService.state(s.v).entries.size()==1,"occlusion does not spend");
    });h.succeed();
  }

  @GameTest(template="empty",batch="radar")
  public static void dataFormatAndRadarSettingsRoundTrip(GameTestHelper h) {
    var a=LoadoutApi.get(ID);
    check(h,a!=null && a.equals(AmmoType.parse(a.id(),a.json())),"radar settings sync round trip");
    check(h,!LoadoutData.ammo().containsKey("sbw_radar:specs"),"radar aggregate is never a fake loadout ammo");
    check(h,LoadoutMod.AMMO.containsKey(ID),"independent radar ammo item registered");
    h.succeed();
  }
}
