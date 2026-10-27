package cn.zzy.loadout.client;

import cn.zzy.loadout.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import cn.zzy.loadout.test.TestFixtures;
import com.atsuishio.superbwarfare.client.shader.ThermalShaderHandler;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.init.ModKeyMappings;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
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

/** Opt-in real client acceptance; never included in production builds. */
@EventBusSubscriber(modid = LoadoutMod.ID, value = Dist.CLIENT)
public final class ThermalSmoke {
  private static boolean started;
  private static int ticks, checks;
  private static UUID vehicle, target;
  private static CompletableFuture<Void> reloading;
  private static InputConstants.Key originalKey;

  private static void check(boolean value, String text) {
    if (!value) throw new IllegalStateException("THERMAL_CLIENT_FAIL " + text);
    checks++;
    LogUtils.getLogger().info("THERMAL_CLIENT_PASS {}", text);
  }

  private static TargetingPodScreen pod() {
    return (TargetingPodScreen) Minecraft.getInstance().screen;
  }

  private static boolean nativeOn() {
    return ClientEventHandler.activeThermalImaging && ThermalShaderHandler.Companion.isActive()
        && Minecraft.getInstance().gameRenderer.currentEffect() != null;
  }

  private static boolean nativeOff() {
    return !ClientEventHandler.activeThermalImaging && !ThermalShaderHandler.Companion.isActive()
        && Minecraft.getInstance().gameRenderer.currentEffect() == null;
  }

  private static void capture(Minecraft mc, String name) {
    Screenshot.grab(mc.gameDirectory, "thermal_" + name + ".png", mc.getMainRenderTarget(),
        m -> LogUtils.getLogger().info("THERMAL_CLIENT_SCREENSHOT {}", name));
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!Boolean.getBoolean("sbw.thermalSmoke")) return;
    var mc = Minecraft.getInstance();
    mc.options.pauseOnLostFocus = false;
    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    if (!started && (mc.screen instanceof TitleScreen || (mc.screen != null
        && mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")))) {
      started = true;
      mc.createWorldOpenFlows().createFreshLevel("thermal_acceptance_" + System.currentTimeMillis(),
          new LevelSettings("Isolated thermal acceptance", GameType.CREATIVE, false,
              Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
          new WorldOptions(20261027L, false, false),
          r -> r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
              .value().createWorldDimensions(), new TitleScreen());
      return;
    }
    if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
    if (reloading != null && (!reloading.isDone() || mc.getOverlay() != null)) return;
    if (reloading != null) { reloading.join(); reloading = null; }
    ticks++;
    if (ticks == 60) mc.getSingleplayerServer().execute(() -> {
      var level = mc.getSingleplayerServer().overworld();
      var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
      level.setDayTime(6000);
      p.getInventory().clearContent();
      p.connection.teleport(0, -50, 0, 0, 0);
      p.setNoGravity(true);
      var v = TestFixtures.AIRCRAFT.create(level);
      v.setNoGravity(true); v.setPos(0, -50, 0); v.setYRot(0);
      level.addFreshEntity(v); p.startRiding(v, true); vehicle = v.getUUID();
      var state = LoadoutService.state(v); state.entries.clear();
      state.entries.add(new LoadoutState.Entry(UUID.randomUUID(), "fixture_addon:tv_bomb", 0));
      LoadoutService.save(v, state); LoadoutService.sync(v);
      var pig = EntityType.PIG.create(level);
      pig.setNoAi(true); pig.setNoGravity(true); pig.setPos(0, -60, 34);
      level.addFreshEntity(pig); target = pig.getUUID();
      for (int x = -8; x <= 8; x++) for (int y = -60; y <= -55; y++)
        level.setBlock(new BlockPos(x, y, 42),
            (x < -2 ? Blocks.RED_CONCRETE : x > 2 ? Blocks.BLUE_CONCRETE : Blocks.OAK_PLANKS)
                .defaultBlockState(), 2);
    });
    if (ticks == 110) {
      check(TargetingPodClient.vehicle() != null, "pilot and loadout synchronize");
      check(!TargetingPodClient.vehicle().computed().seats().getFirst().hasThermalImaging,
          "fixture seat has no native thermal equipment");
      mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
      TargetingPodClient.toggle();
      check(mc.screen instanceof TargetingPodScreen && !pod().thermalEnabled() && nativeOff(),
          "F6 entry starts EO with native thermal disabled");
      var e = mc.level.entitiesForRendering();
      for (var entity : e) if (entity.getUUID().equals(target)) {
        Vec3 d = entity.getBoundingBox().getCenter().subtract(TargetingPodClient.origin(1));
        pod().aim(TargetingPod.yaw(d), TargetingPod.pitch(d));
      }
    }
    if (ticks == 125) pod().keyPressed(GLFW.GLFW_KEY_K, 0, 0);
    if (ticks == 140) {
      var j = TargetingPodClient.snapshot();
      check(j != null && j.get("mode").getAsString().equals("TRACK")
          && j.get("target").getAsString().equals(target.toString()), "EO locks intended live entity");
      capture(mc, "01_eo");
      originalKey = ModKeyMappings.ACTIVE_THERMAL_IMAGING.getKey();
      LogUtils.getLogger().info("THERMAL_NATIVE_KEY {}", originalKey.getName());
      pod().keyPressed(GLFW.GLFW_KEY_K, 0, GLFW.GLFW_MOD_SHIFT);
      check(pod().thermalEnabled() && nativeOn(), "Shift+K resolves default native K and lock K conflict");
      pod().keyPressed(GLFW.GLFW_KEY_K, 0, GLFW.GLFW_MOD_SHIFT);
      check(!pod().thermalEnabled() && nativeOff(), "Shift+K returns EO without changing lock");
      ModKeyMappings.ACTIVE_THERMAL_IMAGING.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8));
      pod().keyPressed(GLFW.GLFW_KEY_F8, 0, 0);
      check(pod().thermalEnabled() && nativeOn(), "remapped native thermal key enables original pipeline");
    }
    if (ticks == 150) ModKeyMappings.ACTIVE_THERMAL_IMAGING.setKey(originalKey);
    if (ticks == 160) {
      check(nativeOn(), "thermal remains enabled across native eligibility checks on unequipped seat");
      check(TargetingPodClient.snapshot().get("target").getAsString().equals(target.toString())
          && GuidanceClient.current().get("tv_locked").getAsBoolean(), "switching sensor retains TV lock");
      capture(mc, "02_ir");
      pod().mouseScrolled(200, 150, 0, 1);
      check(pod().fov() == 25 && pod().thermalEnabled(), "IR zoom retains sensor mode");
    }
    if (ticks == 170) pod().resize(mc, pod().width, pod().height);
    if (ticks == 180) {
      check(nativeOn() && pod().thermalEnabled() && pod().fov() == 25, "screen resize preserves IR and zoom");
      mc.gameRenderer.shutdownEffect();
    }
    if (ticks == 190) {
      check(nativeOn(), "native post effect recovers when renderer releases it");
      reloading = mc.reloadResourcePacks();
    }
    if (ticks == 210) {
      check(nativeOn(), "resource reload recovers original thermal resources");
      capture(mc, "03_ir_zoom");
      pod().mouseClicked(25, 25, 0);
      check(!pod().thermalEnabled() && nativeOff(), "mode button switches IR back to EO");
      check(TargetingPodClient.snapshot().get("mode").getAsString().equals("TRACK"),
          "mode button does not trigger lock or unlock");
    }
    if (ticks == 220) pod().mouseClicked(25, 25, 0);
    if (ticks == 230) {
      check(nativeOn(), "mode button enables IR again");
      pod().keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
      check(!(mc.screen instanceof TargetingPodScreen) && nativeOff()
          && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK,
          "Esc restores native thermal and previous third person camera after resize");
    }
    if (ticks == 240) {
      TargetingPodClient.toggle();
      check(!pod().thermalEnabled() && nativeOff(), "reopening starts a fresh EO session");
      pod().toggleThermal();
      mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList()
          .getPlayers().getFirst().stopRiding());
    }
    if (ticks == 270) {
      check(!(mc.screen instanceof TargetingPodScreen) && nativeOff(), "dismount closes pod and releases thermal");
      mc.getSingleplayerServer().execute(() -> {
        var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        p.startRiding(mc.getSingleplayerServer().overworld().getEntity(vehicle), true);
      });
    }
    if (ticks == 300) {
      // 原生热成像座位：验证退出吊舱后保留驾驶员原来的开关状态。
      TargetingPodClient.vehicle().computed().seats().getFirst().hasThermalImaging = true;
      ClientEventHandler.activeThermalImaging = true;
      ClientEventHandler.INSTANCE.handleThermalImaging(mc.player);
      check(nativeOn(), "native equipped seat activates thermal outside pod");
      TargetingPodClient.toggle();
      check(!pod().thermalEnabled() && nativeOff(), "pod EO temporarily suspends previous native thermal");
      pod().resize(mc, pod().width, pod().height);
    }
    if (ticks == 310) {
      pod().keyPressed(GLFW.GLFW_KEY_F6, 0, 0);
      check(nativeOn(), "F6 exit restores preexisting native thermal after repeated init");
    }
    if (ticks == 320) {
      check(nativeOn(), "restored thermal survives native seat validation");
      TargetingPodClient.toggle(); pod().toggleThermal();
      mc.setScreen(new PauseScreen(false));
      check(nativeOn(), "screen replacement restores preexisting thermal");
      mc.setScreen(null);
      TargetingPodClient.vehicle().computed().seats().getFirst().hasThermalImaging = false;
      ClientEventHandler.activeThermalImaging = false;
      ClientEventHandler.INSTANCE.handleThermalImaging(mc.player);
      check(nativeOff(), "native seat behavior resumes after pod closes");
      ModKeyMappings.ACTIVE_THERMAL_IMAGING.setKey(originalKey);
    }
    if (ticks == 330) {
      TargetingPodClient.vehicle().computed().seats().getFirst().hasThermalImaging = true;
      ClientEventHandler.activeThermalImaging = true;
      ClientEventHandler.INSTANCE.handleThermalImaging(mc.player);
      TargetingPodClient.toggle(); pod().toggleThermal();
      check(nativeOn(), "IR enabled before world disconnect");
      mc.level.disconnect();
      mc.disconnect(new TitleScreen());
      check(nativeOff() && mc.level == null && !(mc.screen instanceof TargetingPodScreen),
          "world disconnect releases pod thermal without leaking into next world");
      LogUtils.getLogger().info("THERMAL_CLIENT_COMPLETE {} checks", checks);
      mc.stop();
    }
    if (ticks > 500) check(false, "acceptance timed out");
  }
}
