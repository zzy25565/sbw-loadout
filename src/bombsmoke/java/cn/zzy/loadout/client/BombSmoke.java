package cn.zzy.loadout.client;

import cn.zzy.loadout.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import cn.zzy.loadout.test.TestFixtures;
import com.atsuishio.superbwarfare.entity.projectile.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Live pod UI, actual network release and full production bomb model. */
@EventBusSubscriber(modid=LoadoutMod.ID,value=Dist.CLIENT)
public final class BombSmoke {
  private static boolean started; private static int ticks, checks;
  private static UUID target;
  private static volatile boolean ordinary, guided;
  private static final Set<UUID> seen = new HashSet<>();
  private static void check(boolean value,String text) {
    if(!value)throw new IllegalStateException("BOMB_CLIENT_FAIL "+text);
    checks++;LogUtils.getLogger().info("BOMB_CLIENT_PASS {}",text);
  }
  private static TargetingPodScreen pod(){return (TargetingPodScreen)Minecraft.getInstance().screen;}
  private static void capture(Minecraft mc,String name){
    Screenshot.grab(mc.gameDirectory,"bomb_"+name+".png",mc.getMainRenderTarget(),
        m->LogUtils.getLogger().info("BOMB_CLIENT_SCREENSHOT {}",name));
  }
  @SubscribeEvent public static void tick(ClientTickEvent.Post event){
    if(!Boolean.getBoolean("sbw.bombSmoke"))return;
    var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    if(!started&&(mc.screen instanceof TitleScreen||(mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")))){
      started=true;mc.createWorldOpenFlows().createFreshLevel("bomb_release_"+System.currentTimeMillis(),
          new LevelSettings("Isolated bomb release",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
          new WorldOptions(20261027L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());return;
    }
    if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null)return;
    ticks++;
    if(ticks==60)mc.getSingleplayerServer().execute(()->{
      var l=mc.getSingleplayerServer().overworld();var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
      l.setDayTime(6000);p.connection.teleport(0,-46,0,0,0);
      var v=TestFixtures.AIRCRAFT.create(l);v.setNoGravity(true);v.setPos(0,-46,0);v.setYRot(0);l.addFreshEntity(v);p.startRiding(v,true);
      var s=LoadoutService.state(v);s.entries.clear();s.entries.add(new LoadoutState.Entry(UUID.randomUUID(),"sbw_loadout:kab1500kr",0));LoadoutService.save(v,s);LoadoutService.sync(v);
      var pig=EntityType.PIG.create(l);pig.setNoAi(true);pig.setNoGravity(true);pig.setPos(0,-60,44);l.addFreshEntity(pig);target=pig.getUUID();
    });
    // 关闭验收场景的杀伤，只观察本次投放动力学和模型。
    if(ticks>100&&ticks%2==0)mc.getSingleplayerServer().execute(()->{
      var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
      for(var shot:p.serverLevel().getEntitiesOfClass(Projectile.class,p.getBoundingBox().inflate(120))){
        if(shot.getOwner()!=p)continue;
        if(shot instanceof IBulletProperties props){props.setDamage(0);props.setExplosionDamage(0);props.setExplosionRadius(0);}
        if(seen.add(shot.getUUID())){
          if(shot.getPersistentData().contains(SeekerController.KEY)){
            check(target.equals(shot.getPersistentData().getCompound(SeekerController.KEY).getUUID("Target")),"actual locked release arms intended entity seeker");guided=true;
          }else {check(shot instanceof AerialBombEntity,"actual unlocked release is a falling bomb");ordinary=true;}
        }
      }
    });
    if(ticks==110){TargetingPodClient.toggle();check(mc.screen instanceof TargetingPodScreen,"F6 opens pod for production bomb");
      for(var e:mc.level.entitiesForRendering())if(e.getUUID().equals(target)){var d=e.getBoundingBox().getCenter().subtract(TargetingPodClient.origin(1));pod().aim(TargetingPod.yaw(d),TargetingPod.pitch(d));}}
    if(ticks==125){var j=GuidanceClient.current();check(j.get("is_bomb").getAsBoolean()&&!j.get("bomb_guided").getAsBoolean()&&j.get("bomb_spread").getAsDouble()>=2,"HUD receives ballistic release with server scatter");capture(mc,"01_unlocked");pod().fire();}
    if(ticks==140){check(ordinary,"Enter releases unlocked production bomb");pod().keyPressed(GLFW.GLFW_KEY_K,0,0);}
    if(ticks==155){var j=GuidanceClient.current();check(j.get("bomb_guided").getAsBoolean()&&j.get("bomb_spread").getAsDouble()==0,"server snapshot changes to guided zero-scatter release");pod().toggleThermal();}
    if(ticks==165)capture(mc,"02_locked_ir");
    if(ticks==175)pod().fire();
    if(ticks==188){check(guided,"Enter releases locked production bomb");check(AmmoRenderer.renderedProjectileModels().contains("sbw_loadout:kab1500kr"),"full production model renders on ordinary and guided releases");}
    if(ticks==195)pod().keyPressed(GLFW.GLFW_KEY_K,0,0);
    if(ticks==205){check(!GuidanceClient.current().get("bomb_guided").getAsBoolean(),"unlock returns pod HUD to ordinary release");pod().onClose();check(!(mc.screen instanceof TargetingPodScreen),"exit restores normal view");LogUtils.getLogger().info("BOMB_CLIENT_COMPLETE {} checks",checks);}
    if(ticks==220)mc.stop();
  }
}
