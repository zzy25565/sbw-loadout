package cn.zzy.loadout.client;

import cn.zzy.loadout.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.menu.*;
import cn.zzy.loadout.network.*;
import cn.zzy.loadout.test.TestFixtures;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/**
 * Only included with -Pgametest -Psmoke. Creates a disposable world and captures real client
 * evidence.
 */
@EventBusSubscriber(modid = LoadoutMod.ID, value = Dist.CLIENT)
public final class LoadoutSmoke {
  private static boolean started;
  private static int ticks;
  private static UUID aircraft;

  private static void check(boolean value, String reason) {
    if (!value) throw new IllegalStateException("LOADOUT_SMOKE " + reason);
    LogUtils.getLogger().info("LOADOUT_SMOKE_PASS {}", reason);
  }

  private static void shot(Minecraft mc, String name) {
    Screenshot.grab(
        mc.gameDirectory,
        "loadout_" + name + ".png",
        mc.getMainRenderTarget(),
        m -> LogUtils.getLogger().info("LOADOUT_SCREENSHOT {}", name));
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    mc.options.pauseOnLostFocus = false;
    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    if (!started
        && mc.screen != null
        && (mc.screen instanceof TitleScreen
            || mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) {
      started = true;
      mc.createWorldOpenFlows()
          .createFreshLevel(
              "loadout_acceptance_" + System.currentTimeMillis(),
              new LevelSettings(
                  "Loadout isolated acceptance",
                  GameType.SURVIVAL,
                  false,
                  Difficulty.PEACEFUL,
                  true,
                  new GameRules(),
                  WorldDataConfiguration.DEFAULT),
              new WorldOptions(20261020L, false, false),
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
    if (ticks == 40) {
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var server = mc.getSingleplayerServer();
                var level = server.overworld();
                var p = server.getPlayerList().getPlayers().getFirst();
                p.connection.teleport(0, -59, 0, 0, 0);
                p.getInventory().clearContent();
                for (String id : LoadoutMod.AMMO.keySet())
                  p.getInventory().add(LoadoutMod.stack(id, 4));
                var v = TestFixtures.AIRCRAFT.create(level);
                v.moveTo(2, -59, 0);
                v.setNoGravity(true);
                level.addFreshEntity(v);
                aircraft = v.getUUID();
                p.setShiftKeyDown(true);
                var eventClick =
                    new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
                        .EntityInteract(p, net.minecraft.world.InteractionHand.MAIN_HAND, v);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(eventClick);
                p.setShiftKeyDown(false);
                check(
                    eventClick.isCanceled() && p.containerMenu instanceof LoadoutMenu,
                    "shift interaction opens menu");
              });
    }
    if (ticks == 100) {
      check(mc.screen instanceof LoadoutScreen, "menu opened from server");
      check(ClientLoadouts.menu != null, "menu state synchronized");
      shot(mc, "initial");
    }
    if (ticks == 110) {
      LoadoutScreen screen = (LoadoutScreen) mc.screen;
      int w = Math.min(400, mc.getWindow().getGuiScaledWidth() - 12),
          h = Math.min(310, mc.getWindow().getGuiScaledHeight() - 12),
          left = (mc.getWindow().getGuiScaledWidth() - w) / 2,
          top = (mc.getWindow().getGuiScaledHeight() - h) / 2;
      screen.mouseClicked(left + 15, top + 60, 0);
      screen.mouseReleased(left + 8 + (w - 16) / 8 + 8, top + 110, 0);
    }
    if (ticks == 130) {
      check(ClientLoadouts.menu.getAsJsonArray("draft").size() == 2, "drag intention synchronized");
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                check(
                    LoadoutService.counts(p).get("sbw_loadout:kh38mt") == 4,
                    "draft does not consume items");
              });
      shot(mc, "draft");
    }
    if (ticks == 140) {
      ClientLoadouts.action(mc.player.containerMenu.containerId, "commit", "", -1);
    }
    if (ticks == 170) {
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var v = (VehicleEntity) mc.getSingleplayerServer().overworld().getEntity(aircraft);
                check(
                    LoadoutService.counts(p).get("sbw_loadout:kh38mt") == 3,
                    "save consumes one item");
                check(LoadoutService.state(v).entries.size() == 2, "server vehicle committed");
                p.setShiftKeyDown(true);
                var eventClick =
                    new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
                        .EntityInteract(p, net.minecraft.world.InteractionHand.MAIN_HAND, v);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(eventClick);
                p.setShiftKeyDown(false);
                check(
                    eventClick.isCanceled() && p.containerMenu instanceof LoadoutMenu,
                    "shift interaction opens menu");
              });
    }
    if (ticks == 200) {
      check(ClientLoadouts.menu.getAsJsonArray("draft").size() == 2, "reopen preserves loadout");
      ClientLoadouts.action(mc.player.containerMenu.containerId, "remove", "", 0);
    }
    if (ticks == 220) {
      check(ClientLoadouts.menu.getAsJsonArray("draft").size() == 1, "unload is draft only");
      mc.screen.onClose();
    }
    if (ticks == 250) {
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var v = (VehicleEntity) mc.getSingleplayerServer().overworld().getEntity(aircraft);
                check(
                    LoadoutService.state(v).entries.size() == 2
                        && LoadoutService.counts(p).get("sbw_loadout:kh38mt") == 3,
                    "cancel leaves vehicle and inventory unchanged");
              });
      mc.setScreen(new Gallery());
    }
    if (ticks == 280) {
      shot(mc, "models");
    }
    if (ticks == 300) {
      mc.setScreen(null);
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                var v = (VehicleEntity) mc.getSingleplayerServer().overworld().getEntity(aircraft);
                p.startRiding(v, true);
                LoadoutService.sync(v);
              });
    }
    if (ticks == 330) {
      check(mc.player.getVehicle() instanceof VehicleEntity, "client riding generic fixture");
      shot(mc, "hud");
      check(mc.isWindowActive(), "client window active for native input");
      com.atsuishio.superbwarfare.event.ClientEventHandler.holdFireVehicle = true;
      com.atsuishio.superbwarfare.event.ClientEventHandler.INSTANCE.handleVehicleGunShoot();
      com.atsuishio.superbwarfare.event.ClientEventHandler.holdFireVehicle = false;
    }
    if (ticks == 350) {
      mc.getSingleplayerServer()
          .execute(
              () -> {
                var v = (VehicleEntity) mc.getSingleplayerServer().overworld().getEntity(aircraft);
                check(
                    LoadoutService.state(v).entries.size() == 1,
                    "native client fire hook consumes one");
              });
    }
    if (ticks == 380) {
      LogUtils.getLogger().info("LOADOUT_SMOKE_COMPLETE");
      mc.stop();
    }
  }

  private static final class Gallery extends Screen {
    Gallery() {
      super(Component.literal("Full detail ammo models"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
      g.fill(0, 0, width, height, 0xff19212c);
      g.drawCenteredString(font, "SBW Loadout · full detail models", width / 2, 12, 0xffffff);
      int i = 0;
      for (String id : LoadoutMod.AMMO.keySet()) {
        int x = width * (i % 2 * 2 + 1) / 4, y = height * (i / 2 * 2 + 1) / 4;
        g.pose().pushPose();
        g.pose().translate(x - 32, y - 24, 0);
        g.pose().scale(4, 4, 4);
        g.renderItem(LoadoutMod.stack(id, 1), 0, 0);
        g.pose().popPose();
        g.drawCenteredString(font, id, x, y + 40, 0xffffff);
        i++;
      }
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }
  }
}
