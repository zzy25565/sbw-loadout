package cn.zzy.loadout.api;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;

/** 单位：速度为格/刻，冷却为游戏刻，角度为度；Slots 是连续占用的挂点数。 */
public record AmmoType(
    String id,
    String displayName,
    String category,
    String icon,
    String model,
    String texture,
    int slots,
    String projectile,
    double damage,
    double explosionDamage,
    double explosionRadius,
    double velocity,
    int burst,
    double spread,
    String guidance,
    double seekRange,
    double seekAngle,
    int cooldown,
    int laserCode,
    double turnRate,
    boolean renderProjectileModel,
    RadarSettings radar,
    CruiseSettings cruise) {
  public record RadarSettings(int climbTicks, double climbUpDeg, double terminalRange,
                              int maxChannels, int lostTimeout) {
    public static final RadarSettings DEFAULT = new RadarSettings(12, 10, 96, 2, 120);
    public RadarSettings {
      if (climbTicks < 0 || climbTicks > 2000 || !Double.isFinite(climbUpDeg)
          || climbUpDeg < 0 || climbUpDeg > 45 || !Double.isFinite(terminalRange)
          || terminalRange <= 0 || terminalRange > 4096 || maxChannels < 1
          || maxChannels > 16 || lostTimeout < 1 || lostTimeout > 72000)
        throw new IllegalArgumentException("Invalid radar seeker parameters");
    }
    static RadarSettings parse(JsonObject j) {
      if (!j.has("Radar")) return DEFAULT;
      var r=j.getAsJsonObject("Radar");
      return new RadarSettings(i(r,"ClimbTicks",12),d(r,"ClimbUpDeg",10),
          d(r,"TerminalRange",96),i(r,"MaxChannels",2),i(r,"LostTimeout",120));
    }
    JsonObject json() {
      var r=new JsonObject(); r.addProperty("ClimbTicks",climbTicks);
      r.addProperty("ClimbUpDeg",climbUpDeg); r.addProperty("TerminalRange",terminalRange);
      r.addProperty("MaxChannels",maxChannels); r.addProperty("LostTimeout",lostTimeout); return r;
    }
  }

  /** 保留旧构造接口，已有第三方弹种无需修改。 */
  public AmmoType(String id, String displayName, String category, String icon, String model,
      String texture, int slots, String projectile, double damage, double explosionDamage,
      double explosionRadius, double velocity, int burst, double spread, String guidance,
      double seekRange, double seekAngle, int cooldown, int laserCode, double turnRate,
      boolean renderProjectileModel, RadarSettings radar) {
    this(id,displayName,category,icon,model,texture,slots,projectile,damage,explosionDamage,
        explosionRadius,velocity,burst,spread,guidance,seekRange,seekAngle,cooldown,laserCode,
        turnRate,renderProjectileModel,radar,CruiseSettings.NONE);
  }

  /** 保留最初版本的构造接口。 */
  public AmmoType(
    String id,
    String displayName,
    String category,
    String icon,
    String model,
    String texture,
    int slots,
    String projectile,
    double damage,
    double explosionDamage,
    double explosionRadius,
    double velocity,
    int burst,
    double spread,
    String guidance,
    double seekRange,
    double seekAngle,
    int cooldown,
    int laserCode,
    double turnRate,
    boolean renderProjectileModel) {
    this(id, displayName, category, icon, model, texture, slots, projectile, damage, explosionDamage, explosionRadius, velocity, burst, spread, guidance, seekRange, seekAngle, cooldown, laserCode, turnRate, renderProjectileModel, RadarSettings.DEFAULT);
  }

  public AmmoType {
    java.util.Objects.requireNonNull(radar);
    java.util.Objects.requireNonNull(cruise);
    if (cruise.enabled() && (!projectile.equals("sbw_loadout:cruise_missile")
        || !(guidance.equalsIgnoreCase("TV")
             || guidance.toUpperCase(java.util.Locale.ROOT).startsWith("INS"))))
      throw new IllegalArgumentException("Low-altitude flight requires the cruise projectile and TV/INS guidance");
    if (projectile.equals("sbw_loadout:cruise_missile") && !cruise.enabled())
      throw new IllegalArgumentException("Cruise projectile requires a Cruise profile");
    ResourceLocation.parse(id);
    ResourceLocation.parse(projectile);
    ResourceLocation.parse(icon);
    ResourceLocation.parse(model);
    ResourceLocation.parse(texture);
    if (displayName.isBlank()
        || slots < 1
        || slots > 64
        || burst < 1
        || burst > 16
        || cooldown < 1
        || cooldown > 72000
        || !finite(
            damage,
            explosionDamage,
            explosionRadius,
            velocity,
            spread,
            seekRange,
            seekAngle,
            turnRate)
        || damage < 0
        || explosionDamage < 0
        || explosionRadius < 0
        || explosionRadius > 128
        || velocity <= 0
        || velocity > 100
        || spread < 0
        || seekRange < 0
        || seekRange > 65536
        || seekAngle < 0
        || seekAngle > 180
        || laserCode < 1
        || laserCode > 9999
        || turnRate <= 0
        || turnRate > 45) throw new IllegalArgumentException("Invalid ammo: " + id);
  }

  private static boolean finite(double... values) {
    for (double v : values) if (!Double.isFinite(v)) return false;
    return true;
  }

  public static AmmoType parse(String id, JsonObject j) {
    return new AmmoType(
        id,
        s(j, "DisplayName", id),
        s(j, "Category", "Missile"),
        s(j, "Icon", "sbw_loadout:textures/overlay/weapon/icons/kh38mt.png"),
        s(j, "Model", "sbw_loadout:models/bedrock/ammo/kh38mt.geo.json"),
        s(j, "Texture", "sbw_loadout:textures/bedrock/ammo/kh38mt/material_00.png"),
        i(j, "Slots", 1),
        s(j, "Projectile", "superbwarfare:agm_65"),
        d(j, "Damage", 0),
        d(j, "ExplosionDamage", 0),
        d(j, "ExplosionRadius", 0),
        d(j, "Velocity", 1),
        i(j, "Burst", 1),
        d(j, "Spread", 0),
        s(j, "Guidance", "TV"),
        d(j, "SeekRange", 512),
        d(j, "SeekAngle", 20),
        i(j, "Cooldown", 40),
        i(j, "LaserCode", 1688),
        d(j, "TurnRate", s(j, "Category", "Missile").equalsIgnoreCase("GuidedBomb") ? 4 : 5),
        j.has("RenderProjectileModel") && j.get("RenderProjectileModel").getAsBoolean(),
        RadarSettings.parse(j),
        CruiseSettings.parse(j));
  }

  public JsonObject json() {
    JsonObject j = new JsonObject();
    j.addProperty("DisplayName", displayName);
    j.addProperty("Category", category);
    j.addProperty("Icon", icon);
    j.addProperty("Model", model);
    j.addProperty("Texture", texture);
    j.addProperty("Slots", slots);
    j.addProperty("Projectile", projectile);
    j.addProperty("Damage", damage);
    j.addProperty("ExplosionDamage", explosionDamage);
    j.addProperty("ExplosionRadius", explosionRadius);
    j.addProperty("Velocity", velocity);
    j.addProperty("Burst", burst);
    j.addProperty("Spread", spread);
    j.addProperty("Guidance", guidance);
    j.addProperty("SeekRange", seekRange);
    j.addProperty("SeekAngle", seekAngle);
    j.addProperty("Cooldown", cooldown);
    j.addProperty("LaserCode", laserCode);
    j.addProperty("TurnRate", turnRate);
    j.addProperty("RenderProjectileModel", renderProjectileModel);
    if (guidance.equalsIgnoreCase("RADAR")) j.add("Radar", radar.json());
    if (cruise.enabled()) j.add("Cruise", cruise.json());
    return j;
  }

  static String s(JsonObject j, String k, String v) {
    return j.has(k) ? j.get(k).getAsString() : v;
  }

  static int i(JsonObject j, String k, int v) {
    return j.has(k) ? j.get(k).getAsInt() : v;
  }

  static double d(JsonObject j, String k, double v) {
    return j.has(k) ? j.get(k).getAsDouble() : v;
  }

  public static Builder builder(String id) {
    return new Builder(id);
  }

  public static final class Builder {
    private final String id;
    private final JsonObject j = new JsonObject();

    public Builder(String id) {
      this.id = id;
    }

    public Builder displayName(String v) {
      j.addProperty("DisplayName", v);
      return this;
    }

    public Builder projectile(String v) {
      j.addProperty("Projectile", v);
      return this;
    }

    public Builder damage(double v) {
      j.addProperty("Damage", v);
      return this;
    }

    public Builder velocity(double v) {
      j.addProperty("Velocity", v);
      return this;
    }

    public Builder slots(int v) {
      j.addProperty("Slots", v);
      return this;
    }

    public Builder model(String v) {
      j.addProperty("Model", v);
      return this;
    }

    public Builder texture(String v) {
      j.addProperty("Texture", v);
      return this;
    }

    public Builder guidance(String value) {
      j.addProperty("Guidance", value);
      return this;
    }

    public Builder laserCode(int value) {
      j.addProperty("LaserCode", value);
      return this;
    }

    public Builder turnRate(double value) {
      j.addProperty("TurnRate", value);
      return this;
    }

    public Builder property(String k, JsonElement v) {
      j.add(k, v);
      return this;
    }

    public AmmoType build() {
      return parse(id, j);
    }
  }
}
