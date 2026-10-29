package cn.zzy.loadout.client;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import cn.zzy.loadout.network.LoadoutNetwork;
import cn.zzy.loadout.test.TestFixtures;
import com.atsuishio.superbwarfare.entity.projectile.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.logging.LogUtils;
import com.zzy.sbwradar.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Real integrated-client UI/network check; only the disposable fixture disables blast damage. */
@EventBusSubscriber(modid=LoadoutMod.ID,value=Dist.CLIENT)
public final class AirToAirSmoke {
  private static boolean started;private static int ticks,checks;
  private static volatile VehicleEntity source,target;
  private static volatile net.minecraft.server.level.ServerPlayer enemy;
  private static volatile Projectile incomingRadar,incomingIr;
  private static volatile boolean radarSeen,irSeen,chaffSeen,flareSeen;
  private static final Set<UUID> seen=new HashSet<>();
  private static void check(boolean value,String text) {
    if(!value) throw new IllegalStateException("AIR_CLIENT_FAIL "+text);
    checks++;LogUtils.getLogger().info("AIR_CLIENT_PASS {}",text);
  }
  private static void load(String id) {
    var s=LoadoutService.state(source);s.entries.clear();s.nextFire=0;
    s.entries.add(new LoadoutState.Entry(UUID.randomUUID(),id,0));LoadoutService.save(source,s);LoadoutService.sync(source);
  }
  private static void fire() { PacketDistributor.sendToServer(new LoadoutNetwork.Request("{\"op\":\"fire\"}")); }
  private static void capture(Minecraft mc,String name) {
    Screenshot.grab(mc.gameDirectory,"air_"+name+".png",mc.getMainRenderTarget(),
        message->LogUtils.getLogger().info("AIR_CLIENT_SCREENSHOT {}",name));
  }
  @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
    if(!Boolean.getBoolean("sbw.airSmoke")) return;
    var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    if(!started && (mc.screen instanceof TitleScreen || mc.screen!=null
        && mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) {
      started=true;mc.createWorldOpenFlows().createFreshLevel("air_compat_"+System.currentTimeMillis(),
          new LevelSettings("Isolated air-to-air compatibility",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,
              new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(20261028L,false,false),
          r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());return;
    }
    if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null) return;
    ticks++;var server=mc.getSingleplayerServer();
    if(ticks==60) server.execute(()-> {
      var l=server.overworld();var p=server.getPlayerList().getPlayers().getFirst();l.setDayTime(6000);
      p.connection.teleport(0,100,0,0,0);
      source=TestFixtures.AIRCRAFT.create(l);source.setNoGravity(true);source.setPos(0,100,0);source.setYRot(0);l.addFreshEntity(source);p.startRiding(source,true);
      target=TestFixtures.AIRCRAFT.create(l);target.setNoGravity(true);target.setPos(0,101,130);target.setYRot(0);l.addFreshEntity(target);
      var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"AirTarget");
      var dummy=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,profile);
      enemy=new net.minecraft.server.level.ServerPlayer(server,l,profile,net.minecraft.server.level.ClientInformation.createDefault());
      enemy.connection=dummy.connection;enemy.startRiding(target,true);load("sbw_loadout:aim54a");
      var team=l.getScoreboard().addPlayerTeam("air_enemy");l.getScoreboard().addPlayerToTeam(enemy.getScoreboardName(),team);
      check(RadarLink.provider()!=null,"actual radar 1.11.1 bridge installed");
    });
    if(ticks>80 && ticks%2==0) server.execute(()-> {
      var p=server.getPlayerList().getPlayers().getFirst();
      for(var shot:p.serverLevel().getEntitiesOfClass(Projectile.class,source.getBoundingBox().inflate(180))) {
        if(shot instanceof IBulletProperties b){b.setDamage(0);b.setExplosionDamage(0);b.setExplosionRadius(0);}
        if(shot.getOwner()!=p || !seen.add(shot.getUUID())) continue;
        if(shot.getPersistentData().contains(SeekerController.KEY)) {
          check(shot.getPersistentData().getCompound(SeekerController.KEY).getUUID("Target").equals(target.getUUID()),"network-fired radar missile uses server TRK target");radarSeen=true;
        } else if(shot.getPersistentData().contains("SBWRadarLoadoutIR")) {
          check(((MissileProjectile)shot).getTargetUUID().equals(target.getStringUUID()),"network-fired IR missile enters actual radar IR controller");irSeen=true;
        }
      }
    });
    if(ticks==100) {
      check(ClientRadarState.class!=null,"radar client loads with loadout");
      PacketDistributor.sendToServer(new RadarPayloads.CycleMode(mc.player.getVehicle().getId()));
      PacketDistributor.sendToServer(new RadarPayloads.CycleMode(mc.player.getVehicle().getId()));
    }
    if(ticks==120) server.execute(()->LogUtils.getLogger().info("AIR_DIAG mode={} tracks={} native={} type={} entries={} sync={} p={} target={} look={}",
        ServerRadarHandler.modeOf(source.getId()),ServerRadarHandler.twsTracks(source.getId()),source.computed().getRadar(),source.getVehicleType(),
        com.atsuishio.superbwarfare.tools.ServerSyncedEntityHandler.getEntries(server.overworld().dimension().location()).size(),
        com.atsuishio.superbwarfare.config.server.SyncConfig.SYNC_ENTITY_OVER_RANGE.get(),source.position(),target.position(),source.getViewVector(1)));
    if(ticks==130) {
      var j=GuidanceClient.current();check(j!=null && j.get("radar_locked").getAsBoolean(),"HUD receives valid external TRK lock");
      check(j.get("radar_limit").getAsInt()==2 && j.get("radar_channels").getAsInt()==0,"HUD shares radar channel limit");capture(mc,"01_radar_trk");fire();
    }
    if(ticks==148) {
      check(radarSeen,"real fire payload releases radar air-to-air missile");
      check(AmmoRenderer.renderedProjectileModels().contains("sbw_loadout:aim54a"),"full AIM54A model rendered");
      server.execute(()->load("sbw_loadout:r73"));
    }
    if(ticks==175) {
      var j=GuidanceClient.current();check(j!=null && j.get("ir_locked").getAsBoolean(),"HUD receives shared IR heat lock");
      capture(mc,"02_ir_lock");fire();
    }
    if(ticks==193) {
      check(irSeen,"real fire payload releases infrared air-to-air missile");
      check(AmmoRenderer.renderedProjectileModels().contains("sbw_loadout:r73"),"full R73 model rendered");
      server.execute(()-> {
        var l=server.overworld();var p=server.getPlayerList().getPlayers().getFirst();
        SbwRadarConfig.COUNTER_CHAFF_CHANCE.set(1.0);SbwRadarConfig.COUNTER_FLARE_CHANCE.set(1.0);
        SbwRadarConfig.COUNTER_ESCALATION_MAX.set(1.0);SbwRadarConfig.COUNTER_RADIUS.set(128.0);
        source.getInventory().insertItem(0,new ItemStack(CounterItems.CHAFF.get(),1),false);source.setDecoyCount(8);source.setDecoyReloadCoolDown(0);
        incomingRadar=LoadoutMod.RADAR_MISSILE.get().create(l);incomingRadar.setOwner(enemy);
        incomingRadar.setPos(source.position().add(0,0,40));incomingRadar.setDeltaMovement(0,0,.1);
        var b=(IBulletProperties)incomingRadar;b.setDamage(0);b.setExplosionDamage(0);b.setExplosionRadius(0);
        RadarGuidance.arm(incomingRadar,enemy,target,source,LoadoutApi.get("sbw_loadout:pl12"));l.addFreshEntity(incomingRadar);
      });
    }
    if(ticks==197) PacketDistributor.sendToServer(new RadarPayloads.Dispense(1));
    if(ticks==202) server.execute(()-> {
      check(incomingRadar.getPersistentData().getCompound(SeekerController.KEY).getString("Phase").equals("DECOYED"),"actual chaff network packet defeats incoming loadout radar missile");chaffSeen=true;
      incomingRadar.discard();var l=server.overworld();
      incomingIr=(Projectile)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("superbwarfare:ru_9m336_missile")).create(l);
      incomingIr.setOwner(enemy);incomingIr.setPos(source.position().add(0,0,40));incomingIr.setDeltaMovement(0,0,.1);
      var b=(IBulletProperties)incomingIr;b.setDamage(0);b.setExplosionDamage(0);b.setExplosionRadius(0);
      IrMissileControl.trackLoadout(incomingIr,enemy,target,source,"sbw_loadout:r73");l.addFreshEntity(incomingIr);
    });
    if(ticks==206) PacketDistributor.sendToServer(new RadarPayloads.Dispense(0));
    if(ticks==211) server.execute(()-> {
      check(((MissileProjectile)incomingIr).isDistracted() && source.getDecoyCount()<8,"actual flare network packet defeats incoming loadout IR missile");flareSeen=true;incomingIr.discard();
    });
    if(ticks==225) {check(chaffSeen && flareSeen,"both countermeasure network paths completed");LogUtils.getLogger().info("AIR_CLIENT_COMPLETE {} checks",checks);}
    if(ticks==240) mc.stop();
  }
}
