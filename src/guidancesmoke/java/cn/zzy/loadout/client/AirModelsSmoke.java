package cn.zzy.loadout.client;

import static cn.zzy.loadout.client.GuidanceSmoke.*;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.AmmoModelEntity;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.guidance.*;
import cn.zzy.loadout.test.TestFixtures;
import com.atsuishio.superbwarfare.entity.projectile.IBulletProperties;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** 同一真实客户端的追加验收：机载照射及新增物品/弹体模型，不进入发布包。 */
final class AirModelsSmoke {
  static UUID vehicle, target;
  static volatile UUID shot;
  static List<UUID> previews = new ArrayList<>();

  static net.minecraft.server.level.ServerPlayer pilot(Minecraft mc) {
    return mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
  }

  static void server(Minecraft mc, Runnable r) {
    mc.getSingleplayerServer().execute(r);
  }

  static Projectile latest(Minecraft mc, String id) {
    var p = pilot(mc);
    return p
        .serverLevel()
        .getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(100))
        .stream()
        .filter(
            x ->
                x.getOwner() == p
                    && x instanceof AmmoModelEntity m
                    && m.loadoutAmmoModel().equals("sbw_loadout:" + id))
        .findFirst()
        .orElseThrow();
  }

  static void aim(Minecraft mc) {
    var entity =
        mc.level.getEntitiesOfClass(Entity.class, mc.player.getBoundingBox().inflate(150)).stream()
            .filter(x -> x.getUUID().equals(target))
            .findFirst()
            .orElseThrow();
    Vec3 d = entity.getBoundingBox().getCenter().subtract(TargetingPodClient.origin(1));
    ((TargetingPodScreen) mc.screen).aim(TargetingPod.yaw(d), TargetingPod.pitch(d));
  }

  static void tick(Minecraft mc, int t) {
    // 仅外观验收夹具暂停原生发动机计时，保持弹体近距离可见；实际发射已单独断言。
    if (t >= 509 && t < 595 && shot != null) {
      server(mc, () -> hold(pilot(mc).serverLevel().getEntity(shot), new Vec3(2, -43, 12)));
      for (var e :
          mc.level.getEntitiesOfClass(Projectile.class, mc.player.getBoundingBox().inflate(150)))
        if (e.getUUID().equals(shot)) hold(e, new Vec3(2, -43, 12));
    }
    if (t >= 596 && t < 650 && previews.size() == 3) {
      server(
          mc,
          () -> {
            for (int i = 0; i < 3; i++)
              hold(
                  pilot(mc).serverLevel().getEntity(previews.get(i)),
                  new Vec3(2, -42 - i * 2.5, 15));
          });
      for (var e :
          mc.level.getEntitiesOfClass(Projectile.class, mc.player.getBoundingBox().inflate(150))) {
        int i = previews.indexOf(e.getUUID());
        if (i >= 0) hold(e, new Vec3(2, -42 - i * 2.5, 15));
      }
    }
    if (t == 440)
      server(
          mc,
          () -> {
            var p = pilot(mc);
            var level = p.serverLevel();
            for (var e :
                level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(200)))
              e.discard();
            if (p.getVehicle() != null) p.stopRiding();
            TargetingPod.close(p.getUUID());
            TelevisionLocks.remove(p.getUUID());
            var v = TestFixtures.AIRCRAFT.create(level);
            v.setPos(2, -43, 0);
            v.setNoGravity(true);
            level.addFreshEntity(v);
            vehicle = v.getUUID();
            p.startRiding(v, true);
            v.setYRot(0);
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), 0, 0);
            var state = LoadoutService.state(v);
            state.entries.clear();
            state.entries.add(new LoadoutState.Entry(UUID.randomUUID(), "sbw_loadout:kh29l", 0));
            state.nextFire = 0;
            LoadoutService.save(v, state);
            LoadoutService.sync(v);
            var pig = EntityType.PIG.create(level);
            pig.setNoAi(true);
            pig.setNoGravity(true);
            pig.setPos(2, -47, 80);
            level.addFreshEntity(pig);
            target = pig.getUUID();
          });
    if (t == 460) KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F6));
    if (t == 470) aim(mc);
    if (t == 480) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_K, 0, 0);
    if (t == 490) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_L, 0, 0);
    if (t == 500) {
      var j = TargetingPodClient.snapshot();
      ok(
          j != null && j.get("laser_on").getAsBoolean() && j.get("laser_hit").getAsBoolean(),
          "L enables aircraft laser through server packet");
      ok(
          GuidanceClient.current().getAsJsonArray("spots").asList().stream()
              .anyMatch(
                  x ->
                      x.getAsJsonObject().get("source").getAsString().equals(vehicle.toString())
                          && x.getAsJsonObject().get("aircraft").getAsBoolean()),
          "client receives aircraft-origin laser spot");
      shot(mc, "airborne_laser_pod");
    }
    if (t == 505) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
    if (t == 508)
      server(
          mc,
          () -> {
            var projectile = latest(mc, "kh29l");
            shot = projectile.getUUID();
            ok(
                projectile
                    .getPersistentData()
                    .getCompound(SeekerController.KEY)
                    .getUUID("Source")
                    .equals(vehicle),
                "Kh29L launch binds aircraft source");
            // 外观验收将已真实发射的弹体停在近处；不会修改生产弹道。
            projectile.setPos(2, -43, 12);
            projectile.setDeltaMovement(Vec3.ZERO);
            ((IBulletProperties) projectile).setCustomGravity(0);
          });
    if (t == 512) ((TargetingPodScreen) mc.screen).onClose();
    if (t == 520) {
      var projectile =
          mc
              .level
              .getEntitiesOfClass(Projectile.class, mc.player.getBoundingBox().inflate(100))
              .stream()
              .filter(x -> x.getUUID().equals(shot))
              .findFirst()
              .orElseThrow();
      ok(
          projectile instanceof AmmoModelEntity m
              && m.loadoutAmmoModel().equals("sbw_loadout:kh29l"),
          "released full-detail model id synchronizes to client");
      var observer = EntityType.ARMOR_STAND.create(mc.level);
      observer.moveTo(13, -39, 4, 54, 18);
      observer.setYHeadRot(54);
      observer.yHeadRotO = 54;
      mc.setCameraEntity(observer);
      mc.options.setCameraType(CameraType.FIRST_PERSON);
    }
    if (t == 530) {
      shot(mc, "airborne_laser_flight");
    }
    if (t == 560) {
      ok(
          TargetingPodClient.snapshot().get("laser_on").getAsBoolean()
              && GuidanceClient.current().get("laser_hit").getAsBoolean(),
          "returning to cockpit keeps laser after UI heartbeat timeout");
      mc.setCameraEntity(mc.player);
      TargetingPodClient.toggle();
    }
    if (t == 565) ((TargetingPodScreen) mc.screen).keyPressed(GLFW.GLFW_KEY_L, 0, 0);
    if (t == 575) {
      ok(
          !TargetingPodClient.snapshot().get("laser_on").getAsBoolean(),
          "reopen pod and L stops aircraft illumination");
      server(
          mc,
          () -> {
            var p = pilot(mc);
            var projectile = (Projectile) p.serverLevel().getEntity(shot);
            SeekerController.tick(projectile);
            ok(
                projectile
                    .getPersistentData()
                    .getCompound(SeekerController.KEY)
                    .getString("Status")
                    .startsWith("LASER_INERTIAL"),
                "stopping aircraft laser enters inertial guidance in live world");
          });
      ((TargetingPodScreen) mc.screen).onClose();
      mc.setScreen(new Gallery());
    }
    if (t == 585) {
      ok(
          LoadoutData.clientAmmo()
                  .keySet()
                  .containsAll(
                      List.of("sbw_loadout:kh29l", "sbw_loadout:kab1500kr", "sbw_loadout:kh59mk"))
              && !LoadoutData.clientAmmo().containsKey("sbw_loadout:kh58ushk"),
          "client catalogue exposes three active munitions and hides Kh58");
      shot(mc, "new_ammo_items");
    }
    if (t == 595) {
      mc.setScreen(null);
      server(
          mc,
          () -> {
            var p = pilot(mc);
            var level = p.serverLevel();
            var v = (VehicleEntity) level.getEntity(vehicle);
            p.stopRiding();
            TargetingPod.close(p.getUUID());
            var old = level.getEntity(shot);
            if (old != null) old.discard();
            String[] ids = {"kh29l", "kab1500kr", "kh59mk"};
            for (int i = 0; i < 3; i++) {
              var a = LoadoutData.ammo().get("sbw_loadout:" + ids[i]);
              var entity =
                  (Projectile)
                      net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                          .get(net.minecraft.resources.ResourceLocation.parse(a.projectile()))
                          .create(level);
              entity.setOwner(p);
              entity.setPos(2, -42 - i * 2.5, 15);
              entity.setDeltaMovement(Vec3.ZERO);
              entity.setYRot(0);
              ((IBulletProperties) entity).setCustomGravity(0);
              ((AmmoModelEntity) entity).loadoutAmmoModel(a.id());
              level.addFreshEntity(entity);
              previews.add(entity.getUUID());
            }
          });
    }
    if (t == 620) {
      var present =
          mc
              .level
              .getEntitiesOfClass(Projectile.class, mc.player.getBoundingBox().inflate(100))
              .stream()
              .filter(
                  x ->
                      previews.contains(x.getUUID())
                          && x instanceof AmmoModelEntity m
                          && !m.loadoutAmmoModel().isEmpty())
              .count();
      ok(present == 3, "all three native projectile types carry synchronized individual models");
      mc.options.hideGui = true;
      var observer = EntityType.ARMOR_STAND.create(mc.level);
      observer.moveTo(13, -44.5, 6, 50.7f, 0);
      observer.setYHeadRot(50.7f);
      observer.yHeadRotO = 50.7f;
      mc.setCameraEntity(observer);
    }
    if (t == 635) {
      ok(
          AmmoRenderer.renderedProjectileModels()
              .containsAll(
                  List.of("sbw_loadout:kh29l", "sbw_loadout:kab1500kr", "sbw_loadout:kh59mk")),
          "world renderer actually draws all three full-detail models: "
              + AmmoRenderer.renderedProjectileModels());
      shot(mc, "three_projectile_models");
    }
    if (t == 650) {
      mc.options.hideGui = false;
      mc.setCameraEntity(mc.player);
      mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }
  }

  static void hold(Entity e, Vec3 point) {
    if (e == null) return;
    e.tickCount = 0;
    e.setXRot(0);
    e.setYRot(0);
    e.xRotO = 0;
    e.yRotO = 0;
    e.setPos(point);
    e.setDeltaMovement(Vec3.ZERO);
    e.hasImpulse = true;
  }

  static class Gallery extends Screen {
    Gallery() {
      super(Component.literal("New full-detail ammunition"));
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
      g.fill(0, 0, width, height, 0xff202d3c);
      g.drawCenteredString(font, "v1.1beta / 完整网格弹药", width / 2, 16, 0xffffffff);
      String[] ids = {"kh29l", "kab1500kr", "kh59mk"};
      String[] labels = {"Kh-29L / LASER", "KAB-1500Kr / TV", "Kh-59MK / TV"};
      for (int i = 0; i < 3; i++) {
        int cx = width * (2 * i + 1) / 6;
        g.drawCenteredString(font, labels[i], cx, 46, 0xffdddddd);
        g.pose().pushPose();
        g.pose().translate(cx - 48, height / 2 - 38, 0);
        g.pose().scale(6, 6, 6);
        g.renderItem(LoadoutMod.stack("sbw_loadout:" + ids[i], 1), 0, 0);
        g.pose().popPose();
      }
      g.drawCenteredString(
          font, "原始模型无贴图，保留灰色材质；Kh-58UShK 仅保留资源", width / 2, height - 28, 0xffcccccc);
    }
  }
}
