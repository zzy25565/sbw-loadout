package cn.zzy.loadout.data;

import cn.zzy.loadout.api.*;
import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;

/** 单独读取原始载具 JSON，保留 SBW 反序列化器会忽略的 Loadout 扩展段。 */
public final class LoadoutData extends SimplePreparableReloadListener<LoadoutData.Snapshot> {
  public record Snapshot(Map<String, AmmoType> ammo, Map<String, LoadoutSpec> specs) {}

  private static volatile Snapshot current = new Snapshot(Map.of(), Map.of());
  public static long generation;
  private static volatile Map<String, AmmoType> clientAmmo = Map.of();

  public static Map<String, AmmoType> clientAmmo() {
    return clientAmmo;
  }

  public static Map<String, AmmoType> ammo() {
    return current.ammo;
  }

  public static LoadoutSpec spec(VehicleEntity v) {
    if (v.getVehicleType() != VehicleType.AIRPLANE && v.getVehicleType() != VehicleType.HELICOPTER)
      return null;
    // 实体覆盖配置优先，第三方继承链无需列举具体机型。
    try {
      String override = v.getOverride();
      if (override != null && !override.isBlank()) {
        JsonElement e = JsonParser.parseString(override);
        if (e.isJsonObject() && e.getAsJsonObject().has("Loadout")) {
          LoadoutSpec s = LoadoutSpec.parse(e.getAsJsonObject().getAsJsonObject("Loadout"));
          s.validate(ammo());
          return s;
        }
      }
    } catch (RuntimeException ignored) {
    }
    return current.specs.get(BuiltInRegistries.ENTITY_TYPE.getKey(v.getType()).toString());
  }

  protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
    Map<String, AmmoType> ammo = new TreeMap<>(LoadoutApi.registered());
    Map<String, LoadoutSpec> specs = new TreeMap<>();
    manager
        .listResources("ammo", id -> id.getPath().endsWith(".json"))
        .forEach(
            (id, r) -> {
              try (var reader = r.openAsReader()) {
                String key =
                    id.getNamespace() + ":" + id.getPath().substring(5, id.getPath().length() - 5);
                JsonObject definition = JsonParser.parseReader(reader).getAsJsonObject();
                // 雷达附属在同一 ammo 目录保存的是总表；不能把总表伪造成一枚挂载弹药。
                if (definition.has("ammo") && definition.get("ammo").isJsonObject()
                    && !definition.has("Projectile")) return;
                ammo.put(key, AmmoType.parse(key, definition));
              } catch (Exception e) {
                throw new IllegalArgumentException("Invalid ammo " + id, e);
              }
            });
    if (ammo.size() > 1024) throw new IllegalArgumentException("Too many ammo types");
    for (AmmoType a : ammo.values()) {
      ResourceLocation id = ResourceLocation.parse(a.projectile());
      if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id))
        throw new IllegalArgumentException("Unknown Projectile " + id);
    }
    manager
        .listResources("sbw/vehicles", id -> id.getPath().endsWith(".json"))
        .forEach(
            (id, r) -> {
              try (var reader = r.openAsReader()) {
                JsonObject j = JsonParser.parseReader(reader).getAsJsonObject();
                if (j.has("Loadout")) {
                  LoadoutSpec s = LoadoutSpec.parse(j.getAsJsonObject("Loadout"));
                  s.validate(ammo);
                  String key =
                      id.getNamespace()
                          + ":"
                          + id.getPath().substring(13, id.getPath().length() - 5);
                  specs.put(key, s);
                }
              } catch (Exception e) {
                throw new IllegalArgumentException("Invalid vehicle " + id, e);
              }
            });
    return new Snapshot(Collections.unmodifiableMap(new LinkedHashMap<>(ammo)), Map.copyOf(specs));
  }

  protected void apply(Snapshot next, ResourceManager manager, ProfilerFiller profiler) {
    current = next;
    generation++;
    com.mojang.logging.LogUtils.getLogger()
        .info(
            "LOADOUT_DATA ammo={} vehicles={} generation={}",
            next.ammo.size(),
            next.specs.size(),
            generation);
  }

  public static void installClient(JsonObject definitions) {
    Map<String, AmmoType> a = new LinkedHashMap<>();
    definitions
        .entrySet()
        .forEach(
            e -> a.put(e.getKey(), AmmoType.parse(e.getKey(), e.getValue().getAsJsonObject())));
    clientAmmo = Map.copyOf(a);
  }

  public static JsonObject definitions() {
    JsonObject j = new JsonObject();
    ammo().forEach((id, a) -> j.add(id, a.json()));
    return j;
  }
}
