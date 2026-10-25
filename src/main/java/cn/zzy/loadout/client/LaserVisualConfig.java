package cn.zzy.loadout.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** 仅影响本机光束外观，不修改服务器判定的照射坐标、编码或有效距离。 */
public final class LaserVisualConfig {
  public static final ModConfigSpec CONFIG;
  public static final ModConfigSpec.IntValue COLOR;
  public static final ModConfigSpec.DoubleValue WIDTH;
  public static final ModConfigSpec.BooleanValue BEAM;

  static {
    var b = new ModConfigSpec.Builder();
    COLOR =
        b.comment("激光 RGB 十进制颜色，默认 16736352（FF6060），范围 0–16777215。")
            .defineInRange("laserColor", 0xff6060, 0, 0xffffff);
    WIDTH = b.comment("光束核心半宽，单位格；外层柔光约为 4 倍。").defineInRange("laserWidth", .004, .001, .03);
    BEAM = b.comment("是否显示照射光束；关闭后仍显示有效落点，不影响制导。").define("showLaserBeam", true);
    CONFIG = b.build();
  }
}
