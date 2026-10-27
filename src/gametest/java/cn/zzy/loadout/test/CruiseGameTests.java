package cn.zzy.loadout.test;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class CruiseGameTests {
  private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
  private static final List<JsonObject> METRICS=new ArrayList<>();

  private static Vec3 yard(GameTestHelper h,int lane,int distance){
    Vec3 start=h.absoluteVec(new Vec3(1024+lane*96,0,1024));
    for(int x=BlockPos.containing(start).getX()/16-3;x<=BlockPos.containing(start).getX()/16+5;x++)
      for(int z=BlockPos.containing(start).getZ()/16-3;z<=BlockPos.containing(start).getZ()/16+distance/16+4;z++)
        h.getLevel().getChunk(x,z); // Only the test fixture preloads its known test course.
    return new Vec3(start.x,h.getLevel().getMinBuildHeight()+4,start.z);
  }
  private static void fill(ServerLevel level,Vec3 base,int xmin,int xmax,int ymin,int ymax,int zmin,int zmax,boolean water){
    BlockPos origin=BlockPos.containing(base);
    for(int x=xmin;x<=xmax;x++)for(int y=ymin;y<=ymax;y++)for(int z=zmin;z<=zmax;z++)
      level.setBlock(origin.offset(x,y,z),(water?Blocks.WATER:Blocks.STONE).defaultBlockState(),2);
  }
  private static Vec3 wall(ServerLevel level,Vec3 start,int distance,double offset){
    Vec3 center=start.add(offset,0,distance);fill(level,center,-2,2,0,5,0,0,false);
    return new Vec3(center.x,center.y+2.5,Math.floor(center.z));
  }
  private static CruiseMissileEntity shot(GameTestHelper h,String id,Vec3 from,Vec3 target,Entity entity,double yaw){
    var a=LoadoutData.ammo().get("sbw_loadout:"+id);var p=LoadoutMod.CRUISE_MISSILE.get().create(h.getLevel());
    p.setPos(from);p.setDamage(0);p.setExplosionDamage(0);p.setExplosionRadius(0);p.setExplosionDestroy(false);
    p.shoot(Math.sin(Math.toRadians(yaw)),0,Math.cos(Math.toRadians(yaw)),(float)a.velocity(),0);
    ((AmmoModelEntity)(Object)p).loadoutAmmoModel(a.id());
    CruiseGuidance.arm(p,a,null,entity==null?null:entity.getUUID(),target);
    h.getLevel().addFreshEntity(p);return p;
  }
  private static JsonObject trace(GameTestHelper h,String name,String id,Vec3 start,Vec3 target,
      int distance,Entity entity,Vec3 targetMotion,int reloadAt,double yaw){
    CruiseMissileEntity p=shot(h,id,start,target,entity,yaw);
    JsonArray samples=new JsonArray();double maxSpeed=0,maxTurn=0,minCruise=Double.POSITIVE_INFINITY;
    double plannedMax=0,minClearance=Double.POSITIVE_INFINITY;
    int lowTicks=0;boolean terminal=false,climbed=false;Vec3 before=p.getDeltaMovement();
    for(int tick=0;tick<1800 && p.isAlive();tick++){
      if(entity!=null){
        // Match the server's per-tick movement history; SBW collision rewinds the target using xOld/yOld/zOld.
        entity.xOld=entity.getX();entity.yOld=entity.getY();entity.zOld=entity.getZ();
        if(entity instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity v)
          v.setPositionO(entity.position());
        entity.setPos(entity.position().add(targetMotion));entity.setDeltaMovement(targetMotion);
        if(entity instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity v){
          v.tickCount++;v.updateOBB();v.refreshBoundingBoxFromOBBs();v.invalidateAABBCache();
        }
      }
      if(tick==reloadAt){
        CompoundTag saved=p.saveWithoutId(new CompoundTag());p.discard();
        var restored=LoadoutMod.CRUISE_MISSILE.get().create(h.getLevel());restored.load(saved);p=restored;
        h.assertTrue(CruiseGuidance.active(p),name+": cruise snapshot survives save/reload");
      }
      p.tickCount++;p.tick();
      CompoundTag n=p.getPersistentData().getCompound(SeekerController.KEY);
      String phase=n.getString("Phase");terminal|=phase.equals("TERMINAL");climbed|=phase.equals("TERRAIN_CLIMB");
      plannedMax=Math.max(plannedMax,n.getDouble("PlannedAltitude")-(h.getLevel().getMinBuildHeight()+4));
      double floor=h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
          BlockPos.containing(p.position()).getX(),BlockPos.containing(p.position()).getZ());
      if(!terminal)minClearance=Math.min(minClearance,p.getY()-floor);
      Vec3 v=p.getDeltaMovement();maxSpeed=Math.max(maxSpeed,v.length());
      if(before.lengthSqr()>1e-10 && v.lengthSqr()>1e-10)
        maxTurn=Math.max(maxTurn,Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,before.normalize().dot(v.normalize()))))));
      if(phase.equals("CRUISE") && !terminal){
        lowTicks++;minCruise=Math.min(minCruise,p.getY()-(h.getLevel().getMinBuildHeight()+4));
      }
      JsonObject row=new JsonObject();row.addProperty("tick",tick);row.addProperty("x",p.getX()-start.x);
      row.addProperty("y",p.getY()-(h.getLevel().getMinBuildHeight()+4));row.addProperty("z",p.getZ()-start.z);
      row.addProperty("speed",v.length());row.addProperty("phase",phase);samples.add(row);before=v;
      row.addProperty("floor",floor-(h.getLevel().getMinBuildHeight()+4));
      row.addProperty("status",n.getString("Status"));
      row.addProperty("targetX",n.getDouble("X")-start.x);row.addProperty("targetY",n.getDouble("Y")-(h.getLevel().getMinBuildHeight()+4));
      row.addProperty("targetZ",n.getDouble("Z")-start.z);
      if(CruiseHitProbe.HITS.containsKey(p.getUUID()))break;
    }
    var hit=CruiseHitProbe.HITS.remove(p.getUUID());
    Vec3 expected=hit!=null && hit.targetCenter()!=null?hit.targetCenter():entity==null?target:entity.getBoundingBox().getCenter();
    JsonObject result=new JsonObject();result.addProperty("scenario",name);result.addProperty("ammo",id);
    result.addProperty("range",distance);result.addProperty("hit",hit!=null);
    result.addProperty("hitEntity",hit!=null && entity!=null && entity.getUUID().equals(hit.target()));
    result.addProperty("error",hit==null?-1:hit.point().distanceTo(expected));
    if(entity!=null){
      var box=entity.getBoundingBox();double dx=box.maxX-box.minX,dy=box.maxY-box.minY,dz=box.maxZ-box.minZ;
      result.addProperty("targetBoundingRadius",Math.sqrt(dx*dx+dy*dy+dz*dz)*.5);
    }
    result.addProperty("maxSpeed",maxSpeed);result.addProperty("maxTurn",maxTurn);
    result.addProperty("lowCruiseTicks",lowTicks);result.addProperty("terminal",terminal);result.addProperty("terrainClimb",climbed);
    result.addProperty("minCruiseHeight",Double.isFinite(minCruise)?minCruise:-1);
    result.addProperty("maxPlannedHeight",plannedMax);
    result.addProperty("minPreTerminalClearance",Double.isFinite(minClearance)?minClearance:-1);
    result.add("samples",samples);METRICS.add(result);saveMetrics();
    LogUtils.getLogger().info("CRUISE_TRAJECTORY {} hit={} error={} maxSpeed={} turn={} ticks={} terminal={}",
        name,hit!=null,hit==null?-1:hit.point().distanceTo(expected),maxSpeed,maxTurn,samples.size(),terminal);
    p.discard();if(entity!=null)entity.discard();return result;
  }
  private static void saveMetrics(){
    try{Path out=Path.of("../validation/low_altitude_1.3.0/trajectory_metrics.json");
      Files.createDirectories(out.getParent());Files.writeString(out,GSON.toJson(METRICS));
    }catch(Exception e){throw new IllegalStateException(e);}
  }
  private static void hit(GameTestHelper h,JsonObject r,double tolerance){
    h.assertTrue(r.get("hit").getAsBoolean() && r.get("error").getAsDouble()<=tolerance,
        r.get("scenario")+": actual collision accuracy "+r.get("error"));
    h.assertTrue(r.get("maxSpeed").getAsDouble()<=1.40001,"independent engine never accelerates to AGM-65 speed");
    h.assertTrue(r.get("maxTurn").getAsDouble()<=3.001,"bounded steering under native physics");
  }

  @GameTest(template="empty",timeoutTicks=200)
  public static void flightAccuracyMatrix(GameTestHelper h){
    record Case(String name,String ammo,int range,int height,double offset,double yaw,int reload){}
    List<Case> cases=List.of(new Case("flat_480_high","kh59mk",480,80,0,0,-1),
        new Case("flat_320_low","kh59mk",320,12,0,0,-1),
        new Case("left_offset","kh59mk",384,45,-24,8,-1),
        new Case("right_offset","kh59mk",384,45,24,-8,-1),
        new Case("near_40","kh59mk",40,14,0,0,-1),
        new Case("midflight_reload","kh59mk",384,60,0,0,100),
        new Case("x555_low_cruise","x555",480,80,0,0,-1));
    int lane=0;
    for(Case c:cases){
      Vec3 ground=yard(h,lane++,c.range());Vec3 target=wall(h.getLevel(),ground,c.range(),c.offset());
      var r=trace(h,c.name(),c.ammo(),ground.add(0,c.height(),0),target,c.range(),null,Vec3.ZERO,c.reload(),c.yaw());
      hit(h,r,1.5);
      if(c.range()>=320)h.assertTrue(r.get("lowCruiseTicks").getAsInt()>30,"long flight really enters sustained low cruise");
      if(c.range()>=320)h.assertTrue(r.get("minPreTerminalClearance").getAsDouble()>=6,"cruise maintains terrain clearance");
    }
    h.succeed();
  }

  @GameTest(template="empty",timeoutTicks=200)
  public static void terrainAndMovingTargetMatrix(GameTestHelper h){
    for(int i=0;i<6;i++){
      Vec3 ground=yard(h,10+i,420);Vec3 target=wall(h.getLevel(),ground,420,0);
      String name;
      if(i==0){fill(h.getLevel(),ground,-16,16,0,20,140,148,false);name="ridge_21_blocks";}
      else if(i==1){fill(h.getLevel(),ground,-16,16,0,15,181,181,false);name="thin_wall_1_block";}
      else if(i==2){fill(h.getLevel(),ground,-16,16,0,5,80,220,true);name="water_surface";}
      else{name=i==3?"moving_crossing_target":i==4?"moving_crossing_reverse":"moving_vehicle_obb";}
      Entity moving=null;
      if(i>=3){
        // No target wall in this case; the scored hit must be on the moving entity itself.
        fill(h.getLevel(),ground,-2,2,0,5,420,420,false);
        if(i==5){
          moving=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
              net.minecraft.resources.ResourceLocation.parse("superbwarfare:lav_25")).create(h.getLevel());
        }else{var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);moving=pig;}
        moving.setNoGravity(true);moving.setPos(ground.add(0,0,380));h.getLevel().addFreshEntity(moving);
        target=moving.getBoundingBox().getCenter();
      }
      var r=trace(h,name,"kh59mk",ground.add(0,70,0),target,420,moving,
          i>=3?new Vec3(i==4?-.12:.12,0,0):Vec3.ZERO,-1,0);
      hit(h,r,i==5?r.get("targetBoundingRadius").getAsDouble()+.001:1.5);
      if(i<3){
        h.assertTrue(r.get("maxPlannedHeight").getAsDouble()>(i==2?12:20),name+": obstacle/water surface is included in height planning");
        h.assertTrue(r.get("minPreTerminalClearance").getAsDouble()>=6,name+": terrain clearance is measured under the real flight path");
      }
      else h.assertTrue(r.get("hitEntity").getAsBoolean(),"moving target hit is scored by real entity collision");
    }
    h.succeed();
  }

  @GameTest(template="empty")
  public static void dataValidationAndFlightSnapshot(GameTestHelper h){
    var a=LoadoutData.ammo().get("sbw_loadout:kh59mk");
    h.assertTrue(a.cruise().enabled() && a.equals(AmmoType.parse(a.id(),a.json())),"full profile survives network JSON");
    h.assertTrue(!LoadoutData.ammo().get("sbw_loadout:kh29l").cruise().enabled(),"ordinary missile remains on old trajectory");
    for(String field:List.of("Speed","Clearance","LookAhead","TerminalRange","Acceleration","MaxTicks")){
      JsonObject invalid=a.json();invalid.getAsJsonObject("Cruise").addProperty(field,-1);
      boolean rejected=false;try{AmmoType.parse(a.id(),invalid);}catch(IllegalArgumentException e){rejected=true;}
      h.assertTrue(rejected,"invalid cruise setting rejected: "+field);
    }
    JsonObject mismatch=a.json();mismatch.addProperty("Projectile","superbwarfare:agm_65");
    boolean rejected=false;try{AmmoType.parse(a.id(),mismatch);}catch(IllegalArgumentException e){rejected=true;}
    h.assertTrue(rejected,"profile cannot silently use native accelerating engine");
    Vec3 ground=yard(h,20,200);Vec3 target=wall(h.getLevel(),ground,200,0);
    var p=shot(h,"kh59mk",ground.add(0,20,0),target,null,0);p.tickCount++;p.tick();
    CompoundTag state=p.getPersistentData().getCompound(SeekerController.KEY).copy();
    CompoundTag bvr=new CompoundTag();p.buildBvrSyncNbt(bvr);
    var restored=LoadoutMod.CRUISE_MISSILE.get().create(h.getLevel());restored.load(bvr);
    h.assertTrue(state.equals(restored.getPersistentData().getCompound(SeekerController.KEY)),"BVR snapshot retains phase, target, age and profile");
    h.assertTrue(((AmmoModelEntity)(Object)restored).loadoutAmmoModel().equals(a.id()),"BVR retains full model identity");
    h.assertTrue(!p.forceLoadChunk() && p.getType().is(com.atsuishio.superbwarfare.init.ModTags.EntityTypes.DESTROYABLE_PROJECTILE),"CIWS tag retained without forced chunks");
    p.discard();restored.discard();h.succeed();
  }

  @GameTest(template="empty")
  public static void unloadedTerrainAndExpiredFlightsFailSafely(GameTestHelper h){
    Vec3 anchor=h.absoluteVec(new Vec3(-4096,40,-4096));
    int cx=BlockPos.containing(anchor).getX()>>4,cz=BlockPos.containing(anchor).getZ()>>4;
    h.getLevel().getChunk(cx,cz);
    Vec3 start=new Vec3(cx*16+8,anchor.y,cz*16+15);
    var p=shot(h,"kh59mk",start,start.add(0,-10,200),null,0);
    int before=h.getLevel().getChunkSource().getLoadedChunksCount();
    p.tickCount++;p.tick();
    h.assertTrue(p.getDeltaMovement().lengthSqr()==0 && p.position().distanceTo(start)<1e-8,"unloaded lookahead never guesses ground or moves blindly");
    h.assertTrue(h.getLevel().getChunkSource().getLoadedChunksCount()==before,"terrain query never generates additional chunks");
    p.getPersistentData().getCompound(SeekerController.KEY).putInt("Age",3600);
    p.tickCount++;p.tick();h.assertTrue(p.isRemoved(),"profile lifetime is bounded across chunk holds");
    var wrongDimension=shot(h,"kh59mk",start,start.add(0,-10,200),null,0);
    wrongDimension.getPersistentData().getCompound(SeekerController.KEY).putString("Dimension","minecraft:the_nether");
    SeekerController.tick(wrongDimension);
    h.assertTrue(wrongDimension.isRemoved(),"dimension mismatch cannot bypass cruise lifetime/guidance");
    h.succeed();
  }

  @GameTest(template="empty")
  public static void nativeCiwsCanInterceptCruiseMissile(GameTestHelper h){
    var attacker=SeekerGameTests.player(h);var defender=SeekerGameTests.player(h);
    var towerType=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
        net.minecraft.resources.ResourceLocation.parse("superbwarfare:hpj_11"));
    var tower=new com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity(towerType,h.getLevel()){
      @Override public LivingEntity getOwner(){return defender;}
    };
    tower.setPos(attacker.position().add(0,0,15));h.getLevel().addFreshEntity(tower);
    Vec3 goal=tower.getBoundingBox().getCenter();
    var incoming=shot(h,"kh59mk",goal.add(0,0,-10),goal,null,0);incoming.setOwner(attacker);
    h.assertTrue(com.atsuishio.superbwarfare.tools.SeekTool.BASIC_TYPE_FILTER.test(incoming),"native filter accepts cruise missile");
    h.assertTrue(com.atsuishio.superbwarfare.entity.vehicle.ai.TowerAI.TargetValidator
        .isValidProjectileTarget(tower,incoming,goal),"native CIWS classifies hostile incoming cruise trajectory");
    var shell=(com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity)
        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(
            net.minecraft.resources.ResourceLocation.parse("superbwarfare:small_cannon_shell")).create(h.getLevel());
    shell.setOwner(defender);shell.setPos(incoming.position().add(0,0,-.1));shell.setDamage(10000);
    shell.setExplosionDamage(0);shell.setExplosionRadius(0);h.getLevel().addFreshEntity(shell);
    shell.crushProjectile(new Vec3(0,0,1));
    h.assertTrue(incoming.getHealth()<=0,"native CIWS damage reaches cruise projectile health");
    incoming.tickCount++;incoming.tick();
    h.assertTrue(incoming.isRemoved(),"intercepted cruise missile stays destroyed");h.succeed();
  }

  @GameTest(template="empty")
  public static void slowProjectileUsesCurrentBodyWithoutGhostHits(GameTestHelper h){
    Vec3 center=h.absoluteVec(new Vec3(4,4,4));
    var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setNoGravity(true);
    pig.setPos(center);pig.xOld=center.x-.25;pig.yOld=center.y;pig.zOld=center.z;
    h.getLevel().addFreshEntity(pig);
    var p=LoadoutMod.CRUISE_MISSILE.get().create(h.getLevel());
    Vec3 body=pig.getBoundingBox().getCenter();
    h.assertTrue(p.getHitResult(pig,body.add(0,0,-2),body.add(0,0,2))!=null,
        "slow projectile intersects moving target's real current body");
    h.assertTrue(p.getHitResult(pig,body.add(-1.25,0,-2),body.add(-1.25,0,2))==null,
        "five-tick-old space cannot score a phantom hit");
    p.discard();pig.discard();h.succeed();
  }
}
