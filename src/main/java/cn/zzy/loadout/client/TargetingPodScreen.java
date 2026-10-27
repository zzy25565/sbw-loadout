package cn.zzy.loadout.client;

import cn.zzy.loadout.guidance.TargetingPod;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModKeyMappings;
import com.google.gson.JsonObject;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** 屏幕仅接管观测输入，世界继续运行。相机挂在机腹，机头/玩家朝向不随镜头转动。 */
public final class TargetingPodScreen extends Screen {
  private final java.util.UUID vehicle;
  private CameraType previous;
  private int ticks, zoom;
  private boolean thermal;
  private double mx = Double.NaN, my;
  private float yaw, pitch = 25;
  private static final float[] FOV = {50, 25, 12, 6};

  public TargetingPodScreen(VehicleEntity v) {
    super(Component.literal("光电瞄准吊舱"));
    vehicle = v.getUUID();
    yaw = v.getYRot();
  }

  @Override
  protected void init() {
    PodThermalVision.enter();
    PodThermalVision.apply(thermal);
    if (previous == null) {
      previous = minecraft.options.getCameraType();
      minecraft.options.setCameraType(CameraType.FIRST_PERSON);
      TargetingPodClient.send("pod_open");
    }
    mx = Double.NaN;
  }

  public float yaw() {
    return yaw;
  }

  public float pitch() {
    return pitch;
  }

  public float fov() {
    return FOV[zoom];
  }

  public int zoomIndex() {
    return zoom;
  }

  public boolean thermalEnabled() {
    return thermal;
  }

  public void toggleThermal() {
    thermal = !thermal;
    PodThermalVision.apply(thermal);
  }

  private boolean thermalKeyConflicts() {
    var key = ModKeyMappings.ACTIVE_THERMAL_IMAGING;
    if (key.getKey().equals(GuidanceClient.LOCK.getKey())
        || key.getKey().equals(TargetingPodClient.VIEW.getKey())) return true;
    for (int reserved : new int[] {GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_L, GLFW.GLFW_KEY_G,
        GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_EQUAL,
        GLFW.GLFW_KEY_KP_ADD, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT,
        GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN})
      if (key.matches(reserved, 0)) return true;
    return false;
  }

  private String thermalKeyHint() {
    return (thermalKeyConflicts() ? "Shift+" : "")
        + ModKeyMappings.ACTIVE_THERMAL_IMAGING.getTranslatedKeyMessage().getString();
  }

  public void zoom(int delta) {
    zoom = Mth.clamp(zoom + delta, 0, FOV.length - 1);
  }

  private boolean locked() {
    var j = TargetingPodClient.snapshot();
    return j != null
        && j.get("active").getAsBoolean()
        && !j.get("mode").getAsString().equals("FREE");
  }

  public void slew(double dx, double dy) {
    var v = TargetingPodClient.vehicle();
    if (v == null || locked()) return;
    float sensitivity = fov() / Math.max(180, width);
    yaw = TargetingPod.clampYaw(yaw + (float) (dx * sensitivity), v.getYRot());
    pitch =
        Mth.clamp(
            pitch + (float) (dy * sensitivity),
            (float) TargetingPod.MIN_PITCH,
            (float) TargetingPod.MAX_PITCH);
  }

  public void aim(float yaw, float pitch) {
    var v = TargetingPodClient.vehicle();
    if (v == null) return;
    this.yaw = TargetingPod.clampYaw(yaw, v.getYRot());
    this.pitch = Mth.clamp(pitch, -25, 89);
    heartbeat();
  }

  public void heartbeat() {
    JsonObject j = new JsonObject();
    j.addProperty("op", "pod_aim");
    j.addProperty("yaw", yaw);
    j.addProperty("pitch", pitch);
    TargetingPodClient.send(j);
  }

  public void lockTarget() {
    heartbeat();
    TargetingPodClient.send(locked() ? "pod_unlock" : "pod_lock");
  }

  public void stabilize() {
    heartbeat();
    TargetingPodClient.send(locked() ? "pod_unlock" : "pod_stabilize");
  }

  public void fire() {
    TargetingPodClient.send("fire");
  }

  @Override
  public void tick() {
    var v = TargetingPodClient.vehicle();
    if (v == null || !vehicle.equals(v.getUUID())) {
      onClose();
      return;
    }
    ticks++;
    yaw = TargetingPod.clampYaw(yaw, v.getYRot());
    var j = TargetingPodClient.snapshot();
    if (j != null && j.get("active").getAsBoolean()) {
      if (locked()) {
        yaw = j.get("yaw").getAsFloat();
        pitch = j.get("pitch").getAsFloat();
      }
    } else if (ticks > 30) {
      onClose();
      return;
    }
    if (ticks % 4 == 0) heartbeat();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

  @Override
  public void removed() {
    PodThermalVision.leave();
    if (previous != null) minecraft.options.setCameraType(previous);
    if (minecraft.getConnection() != null) TargetingPodClient.send("pod_close");
    // 不保留飞行按键的按下状态；退出后由真实输入重新接管。
    KeyMapping.releaseAll();
  }

  @Override
  public void mouseMoved(double x, double y) {
    mx = x;
    my = y;
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    if (button == 2) slew(dx, dy);
    return true;
  }

  @Override
  public boolean mouseScrolled(double x, double y, double sx, double sy) {
    zoom(sy > 0 ? 1 : -1);
    return true;
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (button == 0 && x >= 18 && x < 91 && y >= 20 && y < 36) toggleThermal();
    else if (button == 0) lockTarget();
    else if (button == 1) stabilize();
    return true;
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    // SBW 原生热成像与本附属锁定都默认为 K；冲突时用 Shift+原生键。
    if (ModKeyMappings.ACTIVE_THERMAL_IMAGING.matches(key, scan)
        && (!thermalKeyConflicts() || (modifiers & GLFW.GLFW_MOD_SHIFT) != 0)) {
      toggleThermal();
      return true;
    }
    if (key == GLFW.GLFW_KEY_ESCAPE || TargetingPodClient.VIEW.matches(key, scan)) {
      onClose();
      return true;
    }
    if (GuidanceClient.LOCK.matches(key, scan)) {
      lockTarget();
      return true;
    }
    if (key == GLFW.GLFW_KEY_L) {
      heartbeat();
      TargetingPodClient.send("pod_laser");
      return true;
    }
    if (key == GLFW.GLFW_KEY_G) {
      stabilize();
      return true;
    }
    if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
      fire();
      return true;
    }
    if (key == GLFW.GLFW_KEY_R) {
      TargetingPodClient.send("pod_unlock");
      var v = TargetingPodClient.vehicle();
      if (v != null) {
        yaw = v.getYRot();
        pitch = 25;
      }
      return true;
    }
    if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
      zoom(1);
      return true;
    }
    if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
      zoom(-1);
      return true;
    }
    if (key == GLFW.GLFW_KEY_LEFT) {
      slew(-12, 0);
      return true;
    }
    if (key == GLFW.GLFW_KEY_RIGHT) {
      slew(12, 0);
      return true;
    }
    if (key == GLFW.GLFW_KEY_UP) {
      slew(0, -12);
      return true;
    }
    if (key == GLFW.GLFW_KEY_DOWN) {
      slew(0, 12);
      return true;
    }
    return true;
  }

  private void line(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
    g.fill(x1, y1, x2, y2, c);
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    int w = width, h = height, c = 0xffdcffe6, muted = 0xff7fbb9a, cx = w / 2, cy = h / 2;
    int top = 38, bottom = h - 59;
    g.fill(0, 0, w, top, 0xe4101c1b);
    g.fill(0, bottom, w, h, 0xe4101c1b);
    g.fill(0, top, 12, bottom, 0x99101c1b);
    g.fill(w - 12, top, w, bottom, 0x99101c1b);
    g.drawString(font, "TGP / 光电吊舱", 20, 10, c);
    boolean modeHover = mouseX >= 18 && mouseX < 91 && mouseY >= 20 && mouseY < 36;
    g.fill(18, 20, 91, 36, modeHover ? 0xff35574a : 0xff223c32);
    g.drawString(font, thermal ? "IR 热成像 ▾" : "EO 可见光 ▾", 22, 24, thermal ? c : muted);
    g.drawString(font, "FOV " + (int) fov() + "°", 99, 24, muted);
    var state = GuidanceClient.current();
    var pod = TargetingPodClient.snapshot();
    String mode =
        pod == null || !pod.get("active").getAsBoolean()
            ? "CONNECTING"
            : pod.get("mode").getAsString();
    String label =
        switch (mode) {
          case "TRACK" -> "实体跟踪";
          case "AREA" -> "地面稳定";
          case "LOST" -> "目标遮挡 / 丢失";
          case "LIMIT" -> "云台限位";
          case "FREE" -> "自由搜索";
          default -> "正在连接";
        };
    boolean laserAmmo =
        state != null
            && state.has("seeker")
            && state.get("seeker").getAsString().equalsIgnoreCase("LASER");
    boolean lasing = pod != null && pod.has("laser_on") && pod.get("laser_on").getAsBoolean();
    boolean laserHit = pod != null && pod.has("laser_hit") && pod.get("laser_hit").getAsBoolean();
    boolean ready = state != null && (laserAmmo ? laserHit : state.get("tv_locked").getAsBoolean());
    String laserText =
        "L LASER "
            + (lasing ? (laserHit ? "ON" : "MASKED") : "OFF")
            + " / "
            + (pod != null && pod.has("laser_code") ? pod.get("laser_code").getAsInt() : 1688);
    g.drawString(font, laserText, w - font.width(laserText) - 20, 23, lasing ? 0xffff7970 : muted);
    int statusColor = mode.equals("LOST") || mode.equals("LIMIT") ? 0xffffba79 : c;
    g.drawCenteredString(font, label, cx, 11, statusColor);
    String seeker = laserAmmo ? "LASER" : "TV";
    String readyText = seeker + (ready ? " READY / 检查发射射界" : " HOLD / 检查照射或锁定");
    if (state != null && state.has("is_bomb") && state.get("is_bomb").getAsBoolean()) {
      ready = state.get("bomb_guided").getAsBoolean();
      readyText = ready ? "GUIDED / 无投放散布"
          : "BOMB / 普通投放" + (state.get("bomb_spread").getAsDouble() > 0 ? " · 有散布" : "");
    }
    g.drawString(font, readyText, w - font.width(readyText) - 20, 11, ready ? c : 0xffffba79);
    line(g, cx - 28, cy, cx - 8, cy + 1, c);
    line(g, cx + 8, cy, cx + 28, cy + 1, c);
    line(g, cx, cy - 28, cx + 1, cy - 8, c);
    line(g, cx, cy + 8, cx + 1, cy + 28, c);
    int box = locked() ? 18 : 10;
    for (int sx : new int[] {-1, 1})
      for (int sy : new int[] {-1, 1}) {
        int x = cx + sx * box, y = cy + sy * box;
        line(g, Math.min(x, x - sx * 5), y, Math.max(x, x - sx * 5) + 1, y + 1, statusColor);
        line(g, x, Math.min(y, y - sy * 5), x + 1, Math.max(y, y - sy * 5) + 1, statusColor);
      }
    for (int i = -3; i <= 3; i++) {
      int y = cy + i * 17;
      line(g, 28, y, 28 + (i == 0 ? 10 : 5), y + 1, muted);
    }
    String az =
        String.format(
            java.util.Locale.ROOT,
            "AZ %+04.0f°  EL %+03.0f°",
            (double) Mth.wrapDegrees(yaw),
            (double) -pitch);
    g.drawString(font, az, 22, bottom - 17, c);
    if (pod != null && pod.has("x")) {
      Vec3 point =
          new Vec3(
              pod.get("x").getAsDouble(), pod.get("y").getAsDouble(), pod.get("z").getAsDouble());
      double distance = TargetingPodClient.origin(partial).distanceTo(point);
      String text =
          String.format(
              java.util.Locale.ROOT,
              "RNG %.0f m  |  %.0f / %.0f / %.0f",
              distance,
              point.x,
              point.y,
              point.z);
      g.drawString(font, text, w - font.width(text) - 22, bottom - 17, c);
    } else g.drawString(font, "RNG ---", w - 85, bottom - 17, muted);
    g.drawCenteredString(
        font,
        thermalKeyHint() + " 切换热成像 · 点击左上角 EO / IR 切换",
        cx,
        h - 47,
        muted);
    g.drawCenteredString(
        font,
        "中键拖动/方向键转镜头 · 滚轮变倍 · "
            + GuidanceClient.LOCK.getTranslatedKeyMessage().getString()
            + " 锁定 · G 稳定",
        cx,
        h - 32,
        c);
    g.drawCenteredString(
        font,
        "L 激光开关 · Enter 投放 · R 回正 · "
            + TargetingPodClient.VIEW.getTranslatedKeyMessage().getString()
            + "/Esc 返回驾驶视角",
        cx,
        h - 17,
        muted);
  }
}
