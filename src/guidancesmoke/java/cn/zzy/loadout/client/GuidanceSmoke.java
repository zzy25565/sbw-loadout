package cn.zzy.loadout.client;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.GuidanceApi;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import cn.zzy.loadout.test.TestFixtures;
import com.atsuishio.superbwarfare.entity.projectile.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.init.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** 仅测试源：真实双向包、原生指示器使用状态、激光绘制与键位进入 TV 发射链。 */
@EventBusSubscriber(modid = LoadoutMod.ID, value = Dist.CLIENT)
public final class GuidanceSmoke {
  private static boolean started;
  private static volatile boolean failed;
  private static int ticks;
  private static int focusWait;
  private static UUID laserShot, vehicle, target, tvShot;
  private static float pilotYaw, bodyYaw, podYaw;
  private static CameraType savedCamera;

  static void ok(boolean b, String s) {
    if (!b) {
      failed = true;
      throw new IllegalStateException("GUIDANCE_SMOKE " + s);
    }
    LogUtils.getLogger().info("GUIDANCE_SMOKE_PASS {}", s);
  }

  static void shot(Minecraft mc, String name) {
    Screenshot.grab(
        mc.gameDirectory,
        "seeker_" + name + ".png",
        mc.getMainRenderTarget(),
        m -> LogUtils.getLogger().info("GUIDANCE_SCREENSHOT {}", name));
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("sbw.guidanceSmoke")) return;
    var mc = Minecraft.getInstance();
    mc.options.pauseOnLostFocus = false;
    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    if (!started
        && mc.screen != null
        && (mc.screen instanceof TitleScreen
            || mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) {
      started = true;
      mc.createWorldOpenFlows()
          .createFreshLevel(
              "seeker_validation_" + System.currentTimeMillis(),
              new LevelSettings(
                  "Guidance isolated validation",
                  GameType.CREATIVE,
                  false,
                  Difficulty.PEACEFUL,
                  true,
                  new GameRules(),
                  WorldDataConfiguration.DEFAULT),
              new WorldOptions(20261021L, false, false),
              r ->
                  r.registryOrThrow(Registries.WORLD_PRESET)
                      .getHolderOrThrow(WorldPresets.FLAT)
                      .value()
                      .createWorldDimensions(),
              new TitleScreen());
      return;
    }
    if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
    ticks++;
    if (ticks == 260 && !mc.isWindowActive()) {
      if (focusWait++ == 0)
        LogUtils.getLogger()
            .info("GUIDANCE_WAIT_FOCUS waiting for Minecraft window before native fire validation");
      if (focusWait >= 1200) ok(false, "native fire focus wait timed out");
      ticks--;
      return;
    }
    if (ticks == 40)
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                p.getInventory().clearContent();
                p.setItemInHand(
                    InteractionHand.MAIN_HAND, new ItemStack(ModItems.ARTILLERY_INDICATOR.get()));
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
                p.connection.teleport(0, -57, 0, 0, 0);
                BlockPos c = BlockPos.containing(new Vec3(0, -55.38, 24));
                for (int x = -10; x <= 10; x++)
                  for (int y = -3; y <= 3; y++)
                    p.serverLevel()
                        .setBlock(c.offset(x, y, 0), Blocks.IRON_BLOCK.defaultBlockState(), 3);
              });
    if (ticks == 60) {
      mc.options.keyUse.setDown(true);
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                ModItems.ARTILLERY_INDICATOR.get().use(p.level(), p, InteractionHand.MAIN_HAND);
              });
    }
    if (ticks == 100) {
      var j = GuidanceClient.current();
      ok(
          j != null
              && j.get("laser_active").getAsBoolean()
              && j.get("laser_hit").getAsBoolean()
              && j.getAsJsonArray("spots").size() > 0,
          "original indicator live laser status received");
      shot(mc, "laser_scope");
      // 侧向观察照射者，避免光束与镜头完全共线而被玩家模型遮住。
      var observer = EntityType.ARMOR_STAND.create(mc.level);
      observer.moveTo(8, -55, -4, 26.565f, 5);
      mc.setCameraEntity(observer);
      mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }
    if (ticks == 110) shot(mc, "laser_beam");
    if (ticks == 115) mc.setCameraEntity(mc.player);
    if (ticks == 120)
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var spot = LaserDesignations.sample(p);
                ok(spot != null, "laser source valid on server");
                var projectile =
                    (Projectile)
                        BuiltInRegistries.ENTITY_TYPE
                            .get(ResourceLocation.parse("superbwarfare:mk_82"))
                            .create(p.serverLevel());
                projectile.setOwner(p);
                projectile.setPos(p.getEyePosition().add(-3, 0, 2));
                projectile.shoot(0, 0, 1, .25f, 0);
                var props = (IBulletProperties) projectile;
                props.setDamage(0);
                props.setExplosionDamage(0);
                props.setExplosionRadius(0);
                props.setCustomGravity(0);
                GuidanceApi.laser(projectile, p.getUUID(), 1688, spot.point(), 256, 90, 4);
                p.serverLevel().addFreshEntity(projectile);
                laserShot = projectile.getUUID();
              });
    if (ticks == 140) {
      mc.options.keyUse.setDown(false);
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                p.stopUsingItem();
              });
    }
    if (ticks == 155)
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var projectile = mc.getSingleplayerServer().overworld().getEntity(laserShot);
                ok(
                    projectile != null
                        && projectile
                            .getPersistentData()
                            .getCompound(SeekerController.KEY)
                            .getString("Status")
                            .equals("LASER_INERTIAL"),
                    "release uses inertial guidance in live world");
              });
    if (ticks == 160) {
      mc.options.keyUse.setDown(true);
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                ModItems.ARTILLERY_INDICATOR.get().use(p.level(), p, InteractionHand.MAIN_HAND);
              });
    }
    if (ticks == 180)
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var projectile = mc.getSingleplayerServer().overworld().getEntity(laserShot);
                ok(
                    projectile != null
                        && projectile
                            .getPersistentData()
                            .getCompound(SeekerController.KEY)
                            .getString("Status")
                            .equals("TRACKING_LASER"),
                    "laser reacquisition succeeds in live world");
              });
    if (ticks == 195) {
      mc.options.keyUse.setDown(false);
      mc.options.setCameraType(CameraType.FIRST_PERSON);
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                p.stopUsingItem();
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                var oldShot = p.serverLevel().getEntity(laserShot);
                if (oldShot != null) oldShot.discard();
                var v = TestFixtures.AIRCRAFT.create(p.serverLevel());
                v.setNoGravity(true);
                v.setPos(2, -57, 0);
                p.serverLevel().addFreshEntity(v);
                vehicle = v.getUUID();
                p.startRiding(v, true);
                var s = LoadoutService.state(v);
                s.entries.clear();
                s.entries.add(
                    new LoadoutState.Entry(UUID.randomUUID(), "fixture_addon:tv_bomb", 0));
                LoadoutService.save(v, s);
                LoadoutService.sync(v);
                var pig = EntityType.PIG.create(p.serverLevel());
                pig.setNoAi(true);
                pig.setNoGravity(true);
                pig.setPos(v.position().add(0, 2, 20));
                p.serverLevel().addFreshEntity(pig);
                target = pig.getUUID();
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0, 0);
              });
    }
    if (ticks == 220)
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var pig = p.serverLevel().getEntity(target);
                // 乘坐位置已同步后再将目标中心放到实际准星射线上。
                pig.setPos(p.getEyePosition().add(0, -.45, 20));
              });
    if (ticks == 230) KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
    if (ticks == 250) {
      var j = GuidanceClient.current();
      ok(
          j != null && j.get("tv_locked").getAsBoolean(),
          "K mapping locks TV target over server packet");
      shot(mc, "tv_locked");
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var v = (VehicleEntity) p.serverLevel().getEntity(vehicle);
                var a = LoadoutData.ammo().get("fixture_addon:tv_bomb");
                var lock = TelevisionLocks.current(p, v, a);
                ok(
                    lock != null && target.equals(lock.target()),
                    "TV lock identifies intended entity");
              });
    }
    if (ticks == 260) {
      ok(mc.isWindowActive(), "native fire input is active");
      ClientEventHandler.holdFireVehicle = true;
      ClientEventHandler.INSTANCE.handleVehicleGunShoot();
      ClientEventHandler.holdFireVehicle = false;
    }
    if (ticks == 270)
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var v = (VehicleEntity) p.serverLevel().getEntity(vehicle);
                var entries = LoadoutService.state(v).entries;
                ok(entries.size() == 1, "creative launch keeps loadout");
                var projectiles =
                    p.serverLevel()
                        .getEntitiesOfClass(Projectile.class, v.getBoundingBox().inflate(35));
                var found =
                    projectiles.stream()
                        .filter(
                            e ->
                                e.getOwner() == p
                                    && e.getPersistentData()
                                        .getCompound(SeekerController.KEY)
                                        .getString("Mode")
                                        .equals("TV"))
                        .findFirst()
                        .orElseThrow();
                tvShot = found.getUUID();
                p.stopRiding();
                p.setYRot(180);
                var pig = p.serverLevel().getEntity(target);
                pig.setPos(pig.position().add(3, 0, 0));
              });
    if (ticks == 275)
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var level = mc.getSingleplayerServer().overworld();
                var projectile = level.getEntity(tvShot);
                ok(
                    projectile != null
                        && projectile
                            .getPersistentData()
                            .getCompound(SeekerController.KEY)
                            .getString("Status")
                            .equals("TRACKING_TV_ENTITY"),
                    "TV tracks after shooter stops controlling vehicle");
              });
    if (ticks == 285)
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var level = p.serverLevel();
                var v = (VehicleEntity) level.getEntity(vehicle);
                // 测试夹具声明 Gravity=0；此处升空以验证机腹视线，正式载具物理不变。
                v.setPos(2, -53, 0);
                v.setDeltaMovement(Vec3.ZERO);
                var old = level.getEntity(tvShot);
                if (old != null) old.discard();
                p.startRiding(v, true);
                p.setYRot(0);
                p.setXRot(0);
                v.setYRot(0);
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0, 0);
                var state = LoadoutService.state(v);
                state.entries.clear();
                state.entries.add(
                    new LoadoutState.Entry(UUID.randomUUID(), "fixture_addon:tv_bomb", 0));
                state.nextFire = 0;
                LoadoutService.save(v, state);
                LoadoutService.sync(v);
                TelevisionLocks.remove(p.getUUID());
                var pig = EntityType.PIG.create(level);
                pig.setNoAi(true);
                pig.setNoGravity(true);
                pig.setPos(TargetingPod.origin(v, 1).add(7, -.45, 22));
                level.addFreshEntity(pig);
                target = pig.getUUID();
              });
    if (ticks == 310) {
      savedCamera = CameraType.THIRD_PERSON_BACK;
      mc.options.setCameraType(savedCamera);
      KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F6));
    }
    if (ticks == 320) {
      ok(mc.screen instanceof TargetingPodScreen, "F6 mapping opens dedicated targeting pod view");
      var pod = (TargetingPodScreen) mc.screen;
      pilotYaw = mc.player.getYRot();
      bodyYaw = mc.player.getVehicle().getYRot();
      podYaw = pod.yaw();
      pod.mouseDragged(200, 150, 2, 60, 8);
      ok(Math.abs(pod.yaw() - podYaw) > 1, "middle drag slews independent pod camera");
      pod.mouseScrolled(200, 150, 0, 1);
      pod.mouseScrolled(200, 150, 0, 1);
      ok(pod.zoomIndex() == 2 && pod.fov() == 12, "pod wheel zoom changes dedicated field of view");
    }
    if (ticks == 325) {
      var pod = (TargetingPodScreen) mc.screen;
      var camera = mc.gameRenderer.getMainCamera();
      ok(
          camera.getPosition().distanceTo(TargetingPodClient.origin(1)) < .1
              && Math.abs(camera.getYRot() - pod.yaw()) < .1,
          "rendered camera uses pod belly position and gimbal rotation: actual="
              + camera.getPosition()
              + " yaw="
              + camera.getYRot()
              + " expected="
              + TargetingPodClient.origin(1)
              + " yaw="
              + pod.yaw());
      ok(
          Math.abs(mc.player.getYRot() - pilotYaw) < .1
              && Math.abs(mc.player.getVehicle().getYRot() - bodyYaw) < .1,
          "pod slew does not steer pilot or airframe");
    }
    if (ticks == 330) {
      var pod = (TargetingPodScreen) mc.screen;
      var e =
          mc
              .level
              .getEntitiesOfClass(Entity.class, mc.player.getBoundingBox().inflate(100))
              .stream()
              .filter(x -> x.getUUID().equals(target))
              .findFirst()
              .orElseThrow();
      Vec3 d = e.getBoundingBox().getCenter().subtract(TargetingPodClient.origin(1));
      pod.aim(TargetingPod.yaw(d), TargetingPod.pitch(d));
    }
    if (ticks == 340) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_K, 0, 0);
    if (ticks == 350) {
      var j = TargetingPodClient.snapshot();
      ok(
          j != null
              && j.get("mode").getAsString().equals("TRACK")
              && j.get("target").getAsString().equals(target.toString()),
          "pod K lock acquires intended entity through server ray: " + j + " expected=" + target);
      ok(
          GuidanceClient.current().get("tv_locked").getAsBoolean(),
          "pod lock arms mounted TV seeker");
      shot(mc, "pod_locked");
      podYaw = ((TargetingPodScreen) mc.screen).yaw();
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var v = mc.getSingleplayerServer().overworld().getEntity(vehicle);
                v.setPos(v.position().add(2, 0, 0));
              });
    }
    if (ticks == 365) {
      var j = TargetingPodClient.snapshot();
      ok(
          j.get("mode").getAsString().equals("TRACK")
              && Math.abs(((TargetingPodScreen) mc.screen).yaw() - podYaw) > 1,
          "pod stabilizes target while aircraft translates");
    }
    if (ticks == 370) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_G, 0, 0);
    if (ticks == 375) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_G, 0, 0);
    if (ticks == 385) {
      ok(
          TargetingPodClient.snapshot().get("mode").getAsString().equals("AREA"),
          "G stabilizes a fixed world point");
      shot(mc, "pod_area");
    }
    if (ticks == 390) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_K, 0, 0);
    if (ticks == 395) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_K, 0, 0);
    if (ticks == 405) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    if (ticks == 410)
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var v = (VehicleEntity) p.serverLevel().getEntity(vehicle);
                var shot =
                    p
                        .serverLevel()
                        .getEntitiesOfClass(Projectile.class, v.getBoundingBox().inflate(35))
                        .stream()
                        .filter(
                            x ->
                                x.getOwner() == p
                                    && x.getPersistentData()
                                        .getCompound(SeekerController.KEY)
                                        .getString("Mode")
                                        .equals("TV"))
                        .findFirst()
                        .orElseThrow();
                tvShot = shot.getUUID();
                ok(
                    shot.getPersistentData()
                        .getCompound(SeekerController.KEY)
                        .getUUID("Target")
                        .equals(target),
                    "Enter releases TV projectile with pod target identity");
              });
    if (ticks == 413) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_F6, 0, 0);
    if (ticks == 415) {
      ok(
          mc.screen == null && mc.options.getCameraType() == savedCamera,
          "leaving pod restores previous aircraft camera");
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var shot = mc.getSingleplayerServer().overworld().getEntity(tvShot);
                ok(
                    shot != null
                        && shot.getPersistentData()
                            .getCompound(SeekerController.KEY)
                            .getString("Status")
                            .equals("TRACKING_TV_ENTITY"),
                    "TV continues autonomous guidance after pod view closes");
              });
    }
    if (ticks >= 440) AirModelsSmoke.tick(mc, ticks);
    if (ticks == 680) {
      LogUtils.getLogger().info(failed ? "GUIDANCE_SMOKE_FAILED" : "GUIDANCE_SMOKE_COMPLETE");
      mc.options.keyUse.setDown(false);
      mc.stop();
    }
  }
}
