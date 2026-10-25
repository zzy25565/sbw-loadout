package cn.zzy.loadout.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** 自行实现的加色细光束/径向光斑：受深度遮挡，满亮度，不写深度，不以粒子代替有效落点。 */
public final class LaserRenderer extends RenderType {
  private LaserRenderer() {
    super(
        "sbw_laser",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        512,
        false,
        true,
        () -> {},
        () -> {});
  }

  private static final RenderType GLOW =
      create(
          "sbw_loadout_laser",
          DefaultVertexFormat.POSITION_COLOR,
          VertexFormat.Mode.QUADS,
          512,
          false,
          true,
          CompositeState.builder()
              .setShaderState(POSITION_COLOR_SHADER)
              .setTransparencyState(LIGHTNING_TRANSPARENCY)
              .setCullState(NO_CULL)
              .setDepthTestState(LEQUAL_DEPTH_TEST)
              .setWriteMaskState(COLOR_WRITE)
              .createCompositeState(false));

  private static void vertex(VertexConsumer b, PoseStack.Pose pose, Vec3 p, int color, int alpha) {
    b.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
        .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, alpha);
  }

  private static void quad(
      VertexConsumer b,
      PoseStack.Pose pose,
      Vec3 from,
      Vec3 to,
      Vec3 side,
      double width,
      int color,
      int alpha) {
    Vec3 s = side.scale(width);
    vertex(b, pose, from.add(s), color, alpha);
    vertex(b, pose, to.add(s), color, alpha);
    vertex(b, pose, to.subtract(s), color, alpha);
    vertex(b, pose, from.subtract(s), color, alpha);
  }

  public static void render(RenderLevelStageEvent e, JsonObject snapshot) {
    var mc = Minecraft.getInstance();
    Vec3 camera = e.getCamera().getPosition();
    var buffers = mc.renderBuffers().bufferSource();
    var out = buffers.getBuffer(GLOW);
    var pose = e.getPoseStack().last();
    int color = LaserVisualConfig.COLOR.get();
    double width = LaserVisualConfig.WIDTH.get();
    for (var el : snapshot.getAsJsonArray("spots")) {
      var s = el.getAsJsonObject();
      Vec3 from =
          new Vec3(s.get("x").getAsDouble(), s.get("y").getAsDouble(), s.get("z").getAsDouble());
      Vec3 to =
          new Vec3(s.get("tx").getAsDouble(), s.get("ty").getAsDouble(), s.get("tz").getAsDouble());
      Vec3 dir = to.subtract(from).normalize();
      if (dir.lengthSqr() < .1) continue;
      Vec3 right = dir.cross(new Vec3(0, 1, 0)).normalize();
      if (right.lengthSqr() < .1) right = new Vec3(1, 0, 0);
      // 指示器位于眼下侧前方；射线判定仍使用服务器原始观测位置。
      boolean airborne = s.has("aircraft") && s.get("aircraft").getAsBoolean();
      if (!airborne) from = from.add(dir.scale(.32)).add(right.scale(.09)).add(0, -.10, 0);
      Vec3 a = from.subtract(camera), b = to.subtract(camera);
      Vec3 side = dir.cross(from.add(to).scale(.5).subtract(camera)).normalize();
      if (side.lengthSqr() < .1) side = right;
      double angular = Math.max(1, Math.min(3, from.distanceTo(camera) / 60));
      boolean ownScope =
          mc.player != null
              && ((airborne
                      && TargetingPodClient.active()
                      && mc.player.getVehicle() != null
                      && s.get("source")
                          .getAsString()
                          .equals(mc.player.getVehicle().getUUID().toString()))
                  || (!airborne
                      && s.get("source").getAsString().equals(mc.player.getUUID().toString())
                      && mc.player.isUsingItem()
                      && mc.player
                          .getUseItem()
                          .is(com.atsuishio.superbwarfare.init.ModItems.ARTILLERY_INDICATOR.get())
                      && e.getCamera().getEntity() == mc.player
                      && mc.options.getCameraType().isFirstPerson()));
      if (LaserVisualConfig.BEAM.get() && !ownScope) {
        quad(out, pose, a, b, side, width * 4 * angular, color, 22);
        quad(out, pose, a, b, side, width * 2 * angular, color, 65);
        quad(out, pose, a, b, side, width * angular, color, 220);
      }
      Vec3 center = to.subtract(dir.scale(.015)).subtract(camera);
      Vec3 normal = camera.subtract(to).normalize();
      Vec3 u = normal.cross(new Vec3(0, 1, 0)).normalize();
      if (u.lengthSqr() < .1) u = new Vec3(1, 0, 0);
      Vec3 v = normal.cross(u).normalize();
      // 按视距维持远端光点可辨认，半径最大 0.24 格；边缘透明形成柔和光晕。
      double radius = Math.max(.018, Math.min(.24, to.distanceTo(camera) * .0009));
      for (int i = 0; i < 20; i++) {
        double t = i * Math.PI / 10, n = (i + 1) * Math.PI / 10;
        Vec3 p = center.add(u.scale(Math.cos(t) * radius)).add(v.scale(Math.sin(t) * radius));
        Vec3 q = center.add(u.scale(Math.cos(n) * radius)).add(v.scale(Math.sin(n) * radius));
        vertex(out, pose, center, color, 255);
        vertex(out, pose, p, color, 0);
        vertex(out, pose, q, color, 0);
        vertex(out, pose, center, color, 255);
      }
    }
    buffers.endBatch(GLOW);
  }
}
