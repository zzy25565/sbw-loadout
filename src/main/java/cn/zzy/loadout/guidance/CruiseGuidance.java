package cn.zzy.loadout.guidance;

import cn.zzy.loadout.api.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** 服务器低空弹道；无坐标瞬移、无穿墙命中、无新增区块请求。 */
public final class CruiseGuidance {
  private static final String PROFILE="Cruise";
  private record Terrain(boolean loaded,double height){}

  public static boolean active(Projectile p){
    return p.getPersistentData().getCompound(SeekerController.KEY).contains(PROFILE,10);
  }

  public static void arm(Projectile p,AmmoType ammo,UUID source,UUID target,Vec3 point){
    if(!(p instanceof CruiseMissileEntity) || p.level().isClientSide || !ammo.cruise().enabled()
        || !AimSupport.finite(point)
        || !p.getType().is(com.atsuishio.superbwarfare.init.ModTags.EntityTypes.DESTROYABLE_PROJECTILE))
      throw new IllegalArgumentException("Invalid cruise projectile/target");
    CompoundTag n=new CompoundTag();n.put(PROFILE,ammo.cruise().save());
    n.putString("Mode",ammo.guidance());n.putString("Ammo",ammo.id());
    n.putString("Dimension",p.level().dimension().location().toString());
    n.putDouble("Range",ammo.seekRange());n.putDouble("Angle",ammo.seekAngle());
    n.putDouble("Turn",Math.min(6,ammo.turnRate()));
    if(source!=null)n.putUUID("Source",source);
    if(target!=null)n.putUUID("Target",target);
    point(n,point);n.putString("Phase","DESCENT");n.putString("Status","CRUISE_ACQUIRED");
    Vec3 v=p.getDeltaMovement();
    if(!AimSupport.finite(v) || v.lengthSqr()<1e-8)v=p.getLookAngle();
    // Carrier speed cannot silently turn a subsonic profile into a fast native missile.
    p.setDeltaMovement(v.normalize().scale(Math.min(ammo.cruise().speed(),Math.max(.2,v.length()))));
    p.getPersistentData().put(SeekerController.KEY,n);SeekerController.neutralize(p);
    ((com.atsuishio.superbwarfare.entity.projectile.IBulletProperties)p).setLife(ammo.cruise().maxTicks()+40);
  }

  private static void point(CompoundTag n,Vec3 p){n.putDouble("X",p.x);n.putDouble("Y",p.y);n.putDouble("Z",p.z);}

  private static Vec3 lead(Vec3 position,Vec3 target,Vec3 velocity,double speed){
    if(!AimSupport.finite(velocity) || velocity.lengthSqr()>speed*speed*.81)return target;
    Vec3 r=target.subtract(position);double a=velocity.lengthSqr()-speed*speed;
    double b=2*r.dot(velocity),c=r.lengthSqr(),discriminant=b*b-4*a*c,time=0;
    if(Math.abs(a)<1e-9){if(b< -1e-9)time=-c/b;}
    else if(discriminant>=0){
      double s=Math.sqrt(discriminant),t1=(-b-s)/(2*a),t2=(-b+s)/(2*a);
      time=t1>0 && t2>0?Math.min(t1,t2):Math.max(t1,t2);
    }
    return target.add(velocity.scale(Mth.clamp(time,0,48)));
  }

  /** Heightmap only from getChunkNow: this function never creates a chunk ticket. */
  private static Terrain terrain(ServerLevel level,Vec3 at,Vec3 forward,Vec3 horizontal,
      double distance,CruiseSettings cfg,double turn,double speed){
    Vec3 sample=at,dir=forward.horizontalDistanceSqr()>1e-8
        ?new Vec3(forward.x,0,forward.z).normalize():horizontal;
    double height=level.getMinBuildHeight();
    double ramp=Math.tan(Math.toRadians(cfg.climbAngle()*.65));
    double reactionDistance=cfg.speed()*(10+cfg.climbAngle()/Math.max(.1,turn));
    Map<Long,Double> cache=new HashMap<>();
    for(double d=0;d<=distance;d+=1){
      if(d>0){
        dir=SeekerController.rotateTowards(dir,horizontal,
            Math.toRadians(turn)/Math.max(.2,speed));sample=sample.add(dir);
      }
      Vec3 right=new Vec3(-dir.z,0,dir.x);
      for(double side:new double[]{-.75,0,.75}){
        Vec3 q=sample.add(right.scale(side));int x=Mth.floor(q.x),z=Mth.floor(q.z);
        long key=((long)x<<32)^(z&0xffffffffL);
        Double floor=cache.get(key);
        if(floor==null){
          var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
          if(chunk==null)return new Terrain(false,height);
          floor=(double)chunk.getHeight(Heightmap.Types.MOTION_BLOCKING,x&15,z&15)+1;
          cache.put(key,floor);
        }
        height=Math.max(height,floor+cfg.clearance()-Math.max(0,d-reactionDistance)*ramp);
      }
    }
    return new Terrain(true,height);
  }

  public static void tick(Projectile p){
    if(p.isRemoved() || !(p.level() instanceof ServerLevel level) || !active(p))return;
    CompoundTag n=p.getPersistentData().getCompound(SeekerController.KEY);
    SeekerController.neutralize(p);
    CruiseSettings cfg;
    try{cfg=CruiseSettings.load(n.getCompound(PROFILE));}
    catch(RuntimeException invalid){n.putString("Status","INVALID_PROFILE");p.discard();return;}
    int previousAge=n.getInt("Age");
    if(previousAge<0 || previousAge>=cfg.maxTicks()
        || !n.getString("Dimension").equals(level.dimension().location().toString())){
      n.putString("Status","EXPIRED");p.discard();return;
    }
    int age=previousAge+1;n.putInt("Age",age);
    Vec3 pos=p.position(),velocity=p.getDeltaMovement(),target=SeekerController.readPoint(n);
    if(!AimSupport.finite(pos) || !AimSupport.finite(velocity) || !AimSupport.finite(target)){
      n.putString("Status","INVALID_STATE");p.discard();return;
    }
    Vec3 forward=velocity.lengthSqr()>1e-8?velocity.normalize():p.getLookAngle().normalize();
    Entity tracked=null;
    if(n.hasUUID("Target")){
      Entity e=level.getEntity(n.getUUID("Target"));
      if(e!=null && e.isAlive() && e.getRootVehicle()!=p.getOwner()
          && AimSupport.inCone(pos,forward,e.getBoundingBox().getCenter(),n.getDouble("Range"),n.getDouble("Angle"))
          && AimSupport.visible(level,p,pos,e.getBoundingBox().getCenter())){
        tracked=e;target=e.getBoundingBox().getCenter();point(n,target);
        n.putString("Status","TRACKING_ENTITY");
      }else n.putString("Status","LAST_KNOWN");
    }else n.putString("Status","FIXED_POINT");
    Vec3 delta=target.subtract(pos);double horizontalDistance=delta.horizontalDistance();
    Vec3 horizontal=horizontalDistance>1e-6?new Vec3(delta.x,0,delta.z).normalize()
        :new Vec3(forward.x,0,forward.z).normalize();
    if(horizontal.lengthSqr()<1e-8)horizontal=new Vec3(0,0,1);
    double turn=n.getDouble("Turn");
    if(!Double.isFinite(turn) || turn<=0 || turn>6){p.discard();return;}
    boolean terminal=(n.getBoolean("Terminal") || horizontalDistance<=cfg.terminalRange())
        && AimSupport.visible(level,p,pos,target);
    Vec3 desired;double wantedSpeed=cfg.speed();
    if(terminal){
      n.putBoolean("Terminal",true);n.putString("Phase","TERMINAL");
      Vec3 aim=tracked!=null?lead(pos,target,tracked.getDeltaMovement(),cfg.speed()):target;
      // Never aim through an obstacle just because a linear motion estimate puts the target there.
      if(!AimSupport.visible(level,p,pos,aim))aim=target;
      desired=aim.subtract(pos).normalize();
      double offAngle=Math.acos(Mth.clamp(forward.dot(desired),-1,1));
      // Braking on an aligned approach invalidates the intercept ETA and can miss a crossing target.
      if(offAngle>Math.toRadians(turn*2))
        wantedSpeed=Math.min(wantedSpeed,Math.max(.25,delta.length()/(offAngle/Math.toRadians(turn)+6)));
      if(forward.dot(desired)<-.2){
        n.putString("Phase","MISSED");int passed=n.getInt("PassedTicks")+1;n.putInt("PassedTicks",passed);
        if(passed>40){p.discard();return;}desired=forward;
      }
    }else{
      double distance=Math.min(cfg.lookAhead(),Math.max(1,horizontalDistance));
      Terrain ground=terrain(level,pos,forward,horizontal,distance,cfg,turn,cfg.speed());
      if(!ground.loaded()){
        n.putString("Phase","TERRAIN_UNAVAILABLE");p.setDeltaMovement(Vec3.ZERO);p.hasImpulse=true;return;
      }
      double height=Math.min(level.getMaxBuildHeight()-2,ground.height());
      n.putDouble("PlannedAltitude",height);
      double error=height-pos.y;
      double pitch=Mth.clamp(Math.atan2(error,Math.max(10,cfg.speed()*10)),
          -Math.toRadians(cfg.descentAngle()),Math.toRadians(cfg.climbAngle()));
      desired=horizontal.scale(Math.cos(pitch)).add(0,Math.sin(pitch),0);
      n.putString("Phase",error>1?"TERRAIN_CLIMB":error< -1?"DESCENT":"CRUISE");
    }
    double speed=velocity.length();
    // Braking is responsive; acceleration is deliberately bounded after drops and holds.
    speed=speed>wantedSpeed?Math.max(wantedSpeed,speed-cfg.acceleration()*3)
        :Math.min(wantedSpeed,Math.max(.2,speed)+cfg.acceleration());
    Vec3 direction=SeekerController.rotateTowards(forward,desired,Math.toRadians(turn));
    Vec3 next=direction.scale(speed);
    if(!AimSupport.loaded(level,pos,pos.add(next))){
      n.putString("Phase","TERRAIN_UNAVAILABLE");p.setDeltaMovement(Vec3.ZERO);p.hasImpulse=true;return;
    }
    p.setDeltaMovement(next);p.setYRot((float)-Math.toDegrees(Math.atan2(direction.x,direction.z)));
    p.setXRot((float)-Math.toDegrees(Math.atan2(direction.y,direction.horizontalDistance())));p.hasImpulse=true;
  }
}
