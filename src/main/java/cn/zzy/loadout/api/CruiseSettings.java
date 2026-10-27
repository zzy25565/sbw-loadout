package cn.zzy.loadout.api;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

/** 游戏单位：距离为格，速度为格/刻，角度为度。缺省关闭，必须显式接入独立弹体。 */
public record CruiseSettings(boolean enabled, double speed, double clearance, double lookAhead,
    double terminalRange, double climbAngle, double descentAngle, double acceleration, int maxTicks) {
  public static final CruiseSettings NONE =
      new CruiseSettings(false, 1.4, 8, 96, 96, 40, 35, .08, 3600);

  public CruiseSettings {
    for (double x : new double[] {speed,clearance,lookAhead,terminalRange,climbAngle,descentAngle,acceleration})
      if (!Double.isFinite(x)) throw new IllegalArgumentException("Nonfinite cruise setting");
    if (speed < .2 || speed > 4 || clearance < 2 || clearance > 64
        || lookAhead < 16 || lookAhead > 192 || terminalRange < 16 || terminalRange > 512
        || climbAngle < 10 || climbAngle > 60 || descentAngle < 5 || descentAngle > 60
        || acceleration <= 0 || acceleration > .5 || maxTicks < 40 || maxTicks > 12000)
      throw new IllegalArgumentException("Invalid low-altitude cruise settings");
  }

  public static CruiseSettings parse(JsonObject definition) {
    if (!definition.has("Cruise")) return NONE;
    JsonObject j = definition.getAsJsonObject("Cruise");
    if (!AmmoType.s(j,"Type","LOW_ALTITUDE").equals("LOW_ALTITUDE"))
      throw new IllegalArgumentException("Unknown cruise flight profile");
    return new CruiseSettings(true,AmmoType.d(j,"Speed",1.4),AmmoType.d(j,"Clearance",8),
        AmmoType.d(j,"LookAhead",96),AmmoType.d(j,"TerminalRange",96),
        AmmoType.d(j,"ClimbAngle",40),AmmoType.d(j,"DescentAngle",35),
        AmmoType.d(j,"Acceleration",.08),AmmoType.i(j,"MaxTicks",3600));
  }

  public JsonObject json() {
    JsonObject j=new JsonObject();j.addProperty("Type","LOW_ALTITUDE");
    j.addProperty("Speed",speed);j.addProperty("Clearance",clearance);
    j.addProperty("LookAhead",lookAhead);j.addProperty("TerminalRange",terminalRange);
    j.addProperty("ClimbAngle",climbAngle);j.addProperty("DescentAngle",descentAngle);
    j.addProperty("Acceleration",acceleration);j.addProperty("MaxTicks",maxTicks);return j;
  }

  /** 保存发射时的参数快照；数据包重载不会悄悄改变已经在飞的弹药。 */
  public CompoundTag save() {
    CompoundTag n=new CompoundTag();
    n.putDouble("Speed",speed);n.putDouble("Clearance",clearance);n.putDouble("LookAhead",lookAhead);
    n.putDouble("TerminalRange",terminalRange);n.putDouble("ClimbAngle",climbAngle);
    n.putDouble("DescentAngle",descentAngle);n.putDouble("Acceleration",acceleration);
    n.putInt("MaxTicks",maxTicks);return n;
  }

  public static CruiseSettings load(CompoundTag n) {
    return new CruiseSettings(true,n.getDouble("Speed"),n.getDouble("Clearance"),
        n.getDouble("LookAhead"),n.getDouble("TerminalRange"),n.getDouble("ClimbAngle"),
        n.getDouble("DescentAngle"),n.getDouble("Acceleration"),n.getInt("MaxTicks"));
  }
}
