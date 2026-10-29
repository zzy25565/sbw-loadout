package cn.zzy.loadout.guidance;

import cn.zzy.loadout.LoadoutService;
import cn.zzy.loadout.api.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 9M336 雷达流程：照射轴爬升、中段雷达更新、末段独立搜索；速度仍由 SBW 原生物理决定。 */
public final class RadarGuidance {
  private static final Map<Projectile, Boolean> TRACKED = new WeakHashMap<>();

  public static boolean hostile(Entity launcher, Entity owner, Entity target) {
    if(target==null || !target.isAlive() || target.isSpectator()
        || (!(target instanceof LivingEntity) && !(target instanceof VehicleEntity))
        || target==owner || target==launcher || target.getRootVehicle()==launcher
        || (owner!=null && target.getUUID().equals(owner.getUUID()))) return false;
    if(owner!=null) {
      if(owner.isAlliedTo(target)) return false;
      for(Entity rider:target.getPassengers()) if(owner.isAlliedTo(rider) || rider==owner) return false;
    }
    return true;
  }

  public static int activeFor(VehicleEntity launcher) {
    return (int)TRACKED.keySet().stream().filter(p -> !p.isRemoved() && p.level()==launcher.level())
        .map(p -> p.getPersistentData().getCompound(SeekerController.KEY))
        .filter(n -> n.hasUUID("Source") && n.getUUID("Source").equals(launcher.getUUID())
            && !n.getBoolean("Terminal") && !n.getString("Phase").equals("DECOYED")).count();
  }
  public static Set<UUID> assignedTargets(VehicleEntity launcher) {
    Set<UUID> result = new HashSet<>();
    for (Projectile p : TRACKED.keySet()) {
      var n = p.getPersistentData().getCompound(SeekerController.KEY);
      if (!p.isRemoved() && p.level()==launcher.level() && n.hasUUID("Source")
          && n.getUUID("Source").equals(launcher.getUUID()) && n.hasUUID("Target")
          && !n.getBoolean("Terminal") && !n.getString("Phase").equals("DECOYED"))
        result.add(n.getUUID("Target"));
    }
    return result;
  }
  public static int channelLimit(VehicleEntity v, AmmoType a) {
    int limit=a.radar().maxChannels();
    return RadarLink.provider()==null ? limit : Math.max(0,RadarLink.provider().maxChannels(v,limit));
  }
  public static int usedChannels(VehicleEntity v) {
    return activeFor(v)+(RadarLink.provider()==null ? 0 : Math.max(0,RadarLink.provider().otherChannels(v)));
  }
  public static boolean canLaunch(VehicleEntity v, AmmoType a) {
    return usedChannels(v)<channelLimit(v,a);
  }

  public static void arm(Projectile p, ServerPlayer owner, VehicleEntity launcher, Entity target, AmmoType a) {
    var r=a.radar();
    SeekerController.arm(p,SeekerMode.RADAR,launcher.getUUID(),0,target.getUUID(),
        target.getBoundingBox().getCenter(),a.seekRange(),a.seekAngle(),a.turnRate());
    CompoundTag n=p.getPersistentData().getCompound(SeekerController.KEY);
    n.putUUID("Owner",owner.getUUID()); n.putString("Ammo",a.id());
    if(owner.getTeam()!=null) n.putString("OwnerTeam",owner.getTeam().getName());
    n.putInt("ClimbTicks",r.climbTicks()); n.putDouble("TerminalRange",r.terminalRange());
    n.putInt("LostTimeout",r.lostTimeout()); n.putString("Phase","BOOST");
    Vec3 axis=target.getBoundingBox().getCenter().subtract(p.position()).normalize()
        .add(0,Math.tan(Math.toRadians(r.climbUpDeg())),0).normalize();
    n.putDouble("AxisX",axis.x); n.putDouble("AxisY",axis.y); n.putDouble("AxisZ",axis.z);
    TRACKED.put(p,true);
  }

  private static boolean candidate(ServerLevel level, Projectile p, Entity launcher, Entity owner,
                                   Entity e, CompoundTag n) {
    if(!hostile(launcher,owner,e)) return false;
    String team=n.getString("OwnerTeam");
    if(!team.isEmpty()) {
      if(e.getTeam()!=null && e.getTeam().getName().equals(team)) return false;
      for(Entity rider:e.getPassengers())
        if(rider.getTeam()!=null && rider.getTeam().getName().equals(team)) return false;
    }
    return AimSupport.inCone(p.position(),p.getDeltaMovement(),e.getBoundingBox().getCenter(),
            n.getDouble("TerminalRange"),n.getDouble("Angle"))
        && AimSupport.visible(level,p,p.position(),e.getBoundingBox().getCenter());
  }

  /** 返回 true 表示本刻取得有效目标，便于诊断/验收。 */
  public static boolean tick(Projectile p) {
    if(!(p.level() instanceof ServerLevel level) || p.isRemoved()) return false;
    CompoundTag n=p.getPersistentData().getCompound(SeekerController.KEY);
    if(!n.getString("Mode").equals("RADAR")) return false;
    TRACKED.put(p,true);
    SeekerController.neutralize(p); // 阻止原生红外/诱饵逻辑覆盖雷达导引。
    int age=n.getInt("Age")+1; n.putInt("Age",age);
    if(n.getString("Phase").equals("DECOYED")) { lose(p,n); return false; }
    Entity launcher=n.hasUUID("Source") ? level.getEntity(n.getUUID("Source")) : null;
    Entity owner=p.getOwner();
    if(owner==null && n.hasUUID("Owner")) owner=level.getServer().getPlayerList().getPlayer(n.getUUID("Owner"));
    Vec3 point=SeekerController.readPoint(n);
    boolean found=false;
    if(age<=n.getInt("ClimbTicks")) {
      n.putString("Phase","BOOST"); n.putString("Status","RADAR_BOOST");
      Vec3 axis=new Vec3(n.getDouble("AxisX"),n.getDouble("AxisY"),n.getDouble("AxisZ"));
      turn(p,p.position().add(axis.scale(64)),n.getDouble("Turn"));
      return true;
    }

    Entity desired=n.hasUUID("Target") ? level.getEntity(n.getUUID("Target")) : null;
    // 末段进入后不再读取发射雷达，也不会被驾驶员切换 TRK 目标强行改向。
    if(!candidate(level,p,launcher,owner,desired,n)) {
      desired=null;
      double best=Math.cos(Math.toRadians(n.getDouble("Angle")));
      double range=n.getDouble("TerminalRange");
      for(Entity e:level.getEntities(p,p.getBoundingBox().inflate(range),e->e.isAlive())) {
        if(!candidate(level,p,launcher,owner,e,n)) continue;
        double dot=p.getDeltaMovement().normalize().dot(e.getBoundingBox().getCenter().subtract(p.position()).normalize());
        if(dot>best) { best=dot; desired=e; }
      }
    }
    if(desired!=null) {
      n.putBoolean("Terminal",true); n.putUUID("Target",desired.getUUID());
      point=desired.getBoundingBox().getCenter(); found=true;
      n.putString("Phase","TERMINAL"); n.putString("Status","RADAR_TERMINAL");
    } else if(!n.getBoolean("Terminal") && launcher instanceof VehicleEntity v
        && owner instanceof ServerPlayer pilot && LoadoutService.pilot(pilot,v)) {
      AmmoType a=cn.zzy.loadout.api.LoadoutApi.get(n.getString("Ammo"));
      Entity track=a==null || !n.hasUUID("Target") ? null
          : RadarLocks.tracked(pilot,v,a,n.getUUID("Target"));
      if(track!=null && n.hasUUID("Target") && track.getUUID().equals(n.getUUID("Target"))) {
        point=track.getBoundingBox().getCenter(); found=true;
        n.putString("Phase","MIDCOURSE"); n.putString("Status","RADAR_MIDCOURSE");
      }
    }
    if(found) {
      n.putInt("LostTicks",0);
      n.putDouble("X",point.x);n.putDouble("Y",point.y);n.putDouble("Z",point.z);
    } else {
      n.putString("Status","RADAR_LAST_KNOWN");
      if(lose(p,n)) return false;
    }
    turn(p,point,n.getDouble("Turn"));
    return found;
  }

  private static boolean lose(Projectile p,CompoundTag n) {
    int lost=n.getInt("LostTicks")+1;n.putInt("LostTicks",lost);
    if(lost>n.getInt("LostTimeout")) { p.discard();TRACKED.remove(p);return true; }
    return false;
  }
  private static void turn(Projectile p,Vec3 point,double degrees) {
    Vec3 velocity=p.getDeltaMovement(),to=point.subtract(p.position());
    if(velocity.lengthSqr()<1e-8 || to.lengthSqr()<1e-8 || velocity.dot(to)<0) return;
    Vec3 dir=SeekerController.rotateTowards(velocity.normalize(),to.normalize(),Math.toRadians(degrees));
    p.setDeltaMovement(dir.scale(velocity.length()));p.setYRot((float)-Math.toDegrees(Math.atan2(dir.x,dir.z)));
    p.setXRot((float)-Math.toDegrees(Math.atan2(dir.y,dir.horizontalDistance())));p.hasImpulse=true;
  }

  /** 箔条命中后保持脱锁，不会下一刻重新抓回同一目标；热诱弹不调用此接口。 */
  public static int tryChaff(Level level,Vec3 pos,double radius,double chance,String defender) {
    int count=0;
    for(Projectile p:new ArrayList<>(TRACKED.keySet())) {
      if(p.isRemoved() || p.level()!=level || p.position().distanceToSqr(pos)>radius*radius) continue;
      CompoundTag n=p.getPersistentData().getCompound(SeekerController.KEY);
      if(n.getString("Phase").equals("DECOYED") || (n.hasUUID("Owner") && n.getUUID("Owner").toString().equals(defender))) continue;
      if(level.random.nextDouble()>=chance) continue;
      n.putString("Phase","DECOYED");n.putString("Status","RADAR_CHAFF_DECOYED");n.putInt("LostTicks",0);
      SeekerController.neutralize(p);count++;
    }
    return count;
  }
  public static void clear() { TRACKED.clear(); }
  private RadarGuidance() {}
}
