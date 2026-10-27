package cn.zzy.loadout.client;

import com.atsuishio.superbwarfare.client.shader.ThermalShaderHandler;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import net.minecraft.client.Minecraft;

/** 吊舱只提供传感器资格，成像仍由 SBW 的原生热成像管线负责。 */
public final class PodThermalVision {
  private static boolean entered, previous;

  private PodThermalVision() {}

  public static void enter() {
    if (entered) return; // Screen.init 也会在窗口缩放时调用。
    previous = ClientEventHandler.activeThermalImaging;
    entered = true;
  }

  public static void apply(boolean enabled) {
    ClientEventHandler.activeThermalImaging = enabled;
    if (!enabled) ClientEventHandler.turnOffThermalImaging();
    else if (!ThermalShaderHandler.Companion.isActive()
        || Minecraft.getInstance().gameRenderer.currentEffect() == null)
      ClientEventHandler.turnOnThermalImaging();
  }

  public static void leave() {
    if (!entered) return;
    entered = false;
    // 断开世界时不重新创建着色器，也不把吊舱状态带入下一局。
    apply(Minecraft.getInstance().level != null && previous);
    previous = false;
  }

  public static void disconnect() {
    if (!entered) return;
    entered = false;
    previous = false;
    apply(false);
  }

  /** 返回 true 表示由吊舱负责本次检查，否则继续执行 SBW 的座位/护目镜检查。 */
  public static boolean handle() {
    if (TargetingPodClient.active()) {
      enter();
      apply(((TargetingPodScreen) Minecraft.getInstance().screen).thermalEnabled());
      return true;
    }
    leave();
    return false;
  }
}
