package cn.zzy.loadout.data;

import cn.zzy.loadout.api.AmmoType;
import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

public record LoadoutSpec(int slots, List<String> allowed, List<String> defaults) {
  public LoadoutSpec {
    allowed = List.copyOf(allowed);
    defaults = List.copyOf(defaults);
    if (slots < 1
        || slots > 64
        || allowed.size() > 256
        || new HashSet<>(allowed).size() != allowed.size())
      throw new IllegalArgumentException("Invalid Loadout");
    allowed.forEach(ResourceLocation::parse);
    defaults.forEach(ResourceLocation::parse);
  }

  public static LoadoutSpec parse(JsonObject j) {
    return new LoadoutSpec(j.get("Slots").getAsInt(), list(j, "Allowed"), list(j, "Default"));
  }

  private static List<String> list(JsonObject j, String key) {
    List<String> out = new ArrayList<>();
    if (j.has(key)) for (JsonElement e : j.getAsJsonArray(key)) out.add(e.getAsString());
    return out;
  }

  public JsonObject json() {
    JsonObject j = new JsonObject();
    j.addProperty("Slots", slots);
    j.add("Allowed", new Gson().toJsonTree(allowed));
    j.add("Default", new Gson().toJsonTree(defaults));
    return j;
  }

  public void validate(Map<String, AmmoType> ammo) {
    for (String id : allowed)
      if (!ammo.containsKey(id)) throw new IllegalArgumentException("Unknown ammo " + id);
    int used = 0;
    for (String id : defaults) {
      if (!allowed.contains(id)) throw new IllegalArgumentException("Default not allowed " + id);
      used += ammo.get(id).slots();
    }
    if (used > slots) throw new IllegalArgumentException("Defaults exceed slots");
  }
}
