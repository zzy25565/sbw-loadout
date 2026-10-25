package cn.zzy.loadout.client;

import cn.zzy.loadout.*;
import cn.zzy.loadout.data.LoadoutData;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/** 保留全部三角面与逐角法线/UV；不转方块、不合并 atlas、不缩图。 */
public final class AmmoRenderer extends BlockEntityWithoutLevelRenderer {
  private record Part(ResourceLocation texture, boolean translucent, float[] vertices) {}

  private record Mesh(List<Part> parts, float length) {}

  private static final Map<String, Mesh> CACHE = new HashMap<>();
  private static AmmoRenderer instance;
  private static final Set<String> WORLD_RENDERED = new HashSet<>();

  /** 已成功绘制的世界弹药模型，供诊断资源加载；重载资源时清空。 */
  public static Set<String> renderedProjectileModels() {
    return Set.copyOf(WORLD_RENDERED);
  }

  public static AmmoRenderer instance() {
    if (instance == null) {
      var mc = Minecraft.getInstance();
      instance = new AmmoRenderer();
    }
    return instance;
  }

  private AmmoRenderer() {
    super(
        Minecraft.getInstance().getBlockEntityRenderDispatcher(),
        Minecraft.getInstance().getEntityModels());
  }

  public static void clear() {
    CACHE.clear();
    WORLD_RENDERED.clear();
  }

  private static Mesh read(String path, String fallback) {
    try (var reader =
        Minecraft.getInstance().getResourceManager().openAsReader(ResourceLocation.parse(path))) {
      JsonObject geo =
          JsonParser.parseReader(reader)
              .getAsJsonObject()
              .getAsJsonArray("minecraft:geometry")
              .get(0)
              .getAsJsonObject();
      List<Part> parts = new ArrayList<>();
      float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
      for (JsonElement el : geo.getAsJsonArray("bones")) {
        JsonObject b = el.getAsJsonObject();
        if (!b.has("poly_mesh")) continue;
        JsonObject mesh = b.getAsJsonObject("poly_mesh");
        JsonArray ps = mesh.getAsJsonArray("positions"),
            ns = mesh.getAsJsonArray("normals"),
            uv = mesh.getAsJsonArray("uvs"),
            polys = mesh.getAsJsonArray("polys");
        float[] vertices = new float[polys.size() * 24];
        int at = 0;
        for (JsonElement poly : polys) {
          if (poly.getAsJsonArray().size() != 3)
            throw new IllegalArgumentException("Expected triangles");
          for (JsonElement corner : poly.getAsJsonArray()) {
            JsonArray indices = corner.getAsJsonArray(),
                pos = ps.get(indices.get(0).getAsInt()).getAsJsonArray(),
                norm = ns.get(indices.get(1).getAsInt()).getAsJsonArray(),
                tex = uv.get(indices.get(2).getAsInt()).getAsJsonArray();
            for (int k = 0; k < 3; k++) vertices[at++] = pos.get(k).getAsFloat() / 16;
            for (int k = 0; k < 3; k++) vertices[at++] = norm.get(k).getAsFloat();
            vertices[at++] = tex.get(0).getAsFloat();
            vertices[at++] = tex.get(1).getAsFloat();
            lo = Math.min(lo, pos.get(2).getAsFloat() / 16);
            hi = Math.max(hi, pos.get(2).getAsFloat() / 16);
          }
        }
        parts.add(
            new Part(
                ResourceLocation.parse(
                    b.has("loadout_material")
                        ? b.getAsJsonObject("loadout_material").get("texture").getAsString()
                        : fallback),
                b.has("loadout_material")
                    && b.getAsJsonObject("loadout_material").get("translucent").getAsBoolean(),
                vertices));
      }
      return new Mesh(List.copyOf(parts), hi - lo);
    } catch (Exception e) {
      com.mojang.logging.LogUtils.getLogger().error("Loadout model failed {}", path, e);
      return new Mesh(List.of(), 1);
    }
  }

  @Override
  public void renderByItem(
      ItemStack item,
      ItemDisplayContext context,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay) {
    String id = LoadoutMod.ammoId(item);
    var a = LoadoutData.clientAmmo().getOrDefault(id, LoadoutData.ammo().get(id));
    if (a == null) return;
    Mesh mesh =
        CACHE.computeIfAbsent(a.model() + "|" + a.texture(), key -> read(a.model(), a.texture()));
    pose.pushPose();
    pose.translate(.5, .5, .5);
    float scale = .9f / Math.max(.1f, mesh.length);
    pose.scale(scale, scale, scale);
    pose.mulPose(Axis.YP.rotationDegrees(55));
    pose.mulPose(Axis.XP.rotationDegrees(18));
    draw(mesh, pose, buffers, light, overlay);
    pose.popPose();
  }

  private static void draw(
      Mesh mesh, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
    for (Part part : mesh.parts) {
      VertexConsumer out =
          buffers.getBuffer(
              part.translucent
                  ? RenderType.entityTranslucent(part.texture)
                  : RenderType.entityCutoutNoCull(part.texture));
      float[] v = part.vertices;
      for (int tri = 0; tri < v.length; tri += 24)
        for (int c = 0; c < 4; c++) {
          int i = tri + Math.min(c, 2) * 8;
          out.addVertex(pose.last(), v[i], v[i + 1], v[i + 2])
              .setColor(255, 255, 255, 255)
              .setUv(v[i + 6], v[i + 7])
              .setOverlay(overlay)
              .setLight(light)
              .setNormal(pose.last(), v[i + 3], v[i + 4], v[i + 5]);
        }
    }
  }

  /** 模型保持源文件米制尺寸，鼻尖朝局部 +Z；只跟随弹体插值姿态。 */
  public static boolean renderProjectile(
      net.minecraft.world.entity.Entity entity,
      String id,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    var ammo = LoadoutData.clientAmmo().get(id);
    if (ammo == null || !ammo.renderProjectileModel()) return false;
    Mesh mesh =
        CACHE.computeIfAbsent(
            ammo.model() + "|" + ammo.texture(), key -> read(ammo.model(), ammo.texture()));
    if (mesh.parts.isEmpty()) return false;
    if (WORLD_RENDERED.add(id))
      com.mojang.logging.LogUtils.getLogger().info("LOADOUT_PROJECTILE_MODEL {}", id);
    pose.pushPose();
    pose.mulPose(
        Axis.YP.rotationDegrees(
            -net.minecraft.util.Mth.rotLerp(partial, entity.yRotO, entity.getYRot())));
    pose.mulPose(
        Axis.XP.rotationDegrees(
            net.minecraft.util.Mth.lerp(partial, entity.xRotO, entity.getXRot())));
    draw(
        mesh,
        pose,
        buffers,
        light,
        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
    pose.popPose();
    return true;
  }
}
