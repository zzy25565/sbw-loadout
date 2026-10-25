package cn.zzy.loadout.guidance;

/** 激光必须持续收到指定照射源；电视保存锁定身份，发射后不再读取操作者瞄准。 */
public enum SeekerMode {
  LASER,
  TV,
  RADAR,
  OTHER;

  public static SeekerMode of(String name) {
    return switch (name.toUpperCase(java.util.Locale.ROOT)) {
      case "LASER", "SALH", "激光" -> LASER;
      case "TV", "TELEVISION", "电视" -> TV;
      case "RADAR", "ARH", "雷达" -> RADAR;
      default -> OTHER;
    };
  }
}
