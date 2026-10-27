package cn.zzy.loadout.client;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import cn.zzy.loadout.test.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Actual launched missile and native world renderer; opt-in and excluded from release. */
@EventBusSubscriber(modid=LoadoutMod.ID,value=Dist.CLIENT)
public final class CruiseSmoke {
  private static boolean started;private static int ticks,doneTick;
  private static volatile int missileId=-1;
  private static volatile UUID missile,target;
  private static volatile String phase="STARTING";
  private static volatile double speed,distance,maxSpeed;
  private static volatile boolean hit;
  private static LivingEntity camera;
  private static final Set<String> captures=new HashSet<>();

  private static void check(boolean value,String text){
    if(!value)throw new IllegalStateException("CRUISE_CLIENT_FAIL "+text);
    LogUtils.getLogger().info("CRUISE_CLIENT_PASS {}",text);
  }
  private static void capture(Minecraft mc,String name){
    if(!captures.add(name))return;
    Screenshot.grab(mc.gameDirectory,"cruise_"+name+".png",mc.getMainRenderTarget(),
        message->LogUtils.getLogger().info("CRUISE_CLIENT_SCREENSHOT {}",name));
  }
  @SubscribeEvent public static void tick(ClientTickEvent.Post event){
    if(!Boolean.getBoolean("sbw.cruiseSmoke"))return;
    var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    if(!started && (mc.screen instanceof TitleScreen || (mc.screen!=null
        && mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")))){
      started=true;
      mc.createWorldOpenFlows().createFreshLevel("cruise_acceptance_"+System.currentTimeMillis(),
          new LevelSettings("Isolated low altitude flight",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,
              new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261026L,false,false),
          r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
          new TitleScreen());return;
    }
    if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null)return;
    ticks++;
    if(ticks==60)mc.getSingleplayerServer().execute(()->{
      var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayers().getFirst();
      for(int x=-3;x<=3;x++)for(int z=-2;z<=34;z++)level.getChunk(x,z);
      for(int x=-15;x<=15;x++)for(int y=-60;y<=-40;y++)for(int z=220;z<=228;z++)
        level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),2);
      p.connection.teleport(0,0,0,0,0);p.setNoGravity(true);p.getAbilities().flying=true;p.onUpdateAbilities();
      VehicleEntity plane=TestFixtures.AIRCRAFT.create(level);plane.setPos(0,0,0);plane.setYRot(0);
      plane.setNoGravity(true);level.addFreshEntity(plane);p.startRiding(plane,true);
      var pig=EntityType.PIG.create(level);pig.setNoAi(true);pig.setNoGravity(true);pig.setPos(0,-60,480);
      level.addFreshEntity(pig);target=pig.getUUID();
      var a=LoadoutData.ammo().get("sbw_loadout:kh59mk");
      var state=LoadoutService.state(plane);state.entries.clear();state.entries.add(new LoadoutState.Entry(UUID.randomUUID(),a.id(),0));
      LoadoutService.save(plane,state);
      check(TelevisionLocks.acquire(p,plane,a,target,null),"validated TV lock before launch");
      LoadoutService.fire(p,plane,null,null);
      var shot=level.getEntitiesOfClass(CruiseMissileEntity.class,plane.getBoundingBox().inflate(30)).stream()
          .filter(e->e.getOwner()==p).findFirst().orElseThrow();missile=shot.getUUID();missileId=shot.getId();
      check(LoadoutService.state(plane).entries.size()==1,"creative mode retains loaded ammunition");
      p.stopRiding();plane.discard();
      check(CruiseGuidance.active(shot),"real launch arms independent cruise profile");
    });
    if(missile!=null && ticks%4==0)mc.getSingleplayerServer().execute(()->{
      var level=mc.getSingleplayerServer().overworld();var e=level.getEntity(missile);
      if(e instanceof CruiseMissileEntity p && p.isAlive()){
        phase=p.getPersistentData().getCompound(SeekerController.KEY).getString("Phase");
        speed=p.getDeltaMovement().length();maxSpeed=Math.max(maxSpeed,speed);distance=p.getZ();
        var observer=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        observer.connection.teleport(p.getX()+12,p.getY()+8,p.getZ()-18,0,0);
      }
      var result=CruiseHitProbe.HITS.get(missile);
      if(result!=null){check(target.equals(result.target()),"actual missile collision hits the locked entity");hit=true;}
    });
    Entity entity=missileId<0?null:mc.level.getEntity(missileId);
    if(entity!=null && entity.isAlive()){
      if(camera==null)camera=EntityType.ARMOR_STAND.create(mc.level);
      Vec3 pos=entity.position().add(13,6,-15),d=entity.position().subtract(pos);
      float yaw=(float)-Math.toDegrees(Math.atan2(d.x,d.z));
      float pitch=(float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance()));
      camera.moveTo(pos.x,pos.y,pos.z,yaw,pitch);camera.xo=pos.x;camera.yo=pos.y;camera.zo=pos.z;
      camera.yRotO=yaw;camera.xRotO=pitch;camera.setYHeadRot(yaw);camera.yHeadRotO=yaw;
      mc.setCameraEntity(camera);mc.options.setCameraType(CameraType.FIRST_PERSON);
      if(distance>30 && phase.equals("DESCENT"))capture(mc,"01_descent");
      if(distance>80 && distance<220 && phase.equals("CRUISE"))capture(mc,"02_low_cruise");
      if(distance>120 && phase.equals("TERRAIN_CLIMB"))capture(mc,"03_terrain_clearance");
      if(distance>410 && phase.equals("TERMINAL"))capture(mc,"04_terminal");
    }
    if(hit && doneTick==0){
      doneTick=ticks;check(maxSpeed<=1.4001,"cruise speed remains bounded in live server");
      check(AmmoRenderer.renderedProjectileModels().contains("sbw_loadout:kh59mk"),"full Kh59 model renders on new entity");
      check(captures.size()==4,"all four world flight stages captured");
      LogUtils.getLogger().info("CRUISE_CLIENT_COMPLETE target hit, four stages, full model, bounded speed");
    }
    if(doneTick>0 && ticks>doneTick+20)mc.stop();
    if(ticks>1800)check(false,"live flight timed out, phase="+phase);
  }

  @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
    if(!Boolean.getBoolean("sbw.cruiseSmoke") || missile==null)return;
    var mc=Minecraft.getInstance();var g=event.getGuiGraphics();
    g.fill(8,8,390,54,0xcc13202b);
    g.drawString(mc.font,"Kh-59MK / independent low-altitude flight",14,14,0xffe0edf5);
    g.drawString(mc.font,String.format(java.util.Locale.ROOT,"%s   %.2f blocks/tick   z=%.1f",phase,speed,distance),14,31,0xff9cd1be);
  }
}
