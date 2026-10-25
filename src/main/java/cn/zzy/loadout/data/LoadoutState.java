package cn.zzy.loadout.data;

import cn.zzy.loadout.api.AmmoType;
import com.google.gson.*;
import java.util.*;
import net.minecraft.nbt.*;

/** 每枚弹药用唯一 token 标识；卸装差额由 token 计算，防止重复提交退弹。 */
public final class LoadoutState {
  public record Entry(UUID token, String ammo, int slot) {}

  public final List<Entry> entries = new ArrayList<>();
  public long revision;
  public long nextFire;

  public static LoadoutState read(CompoundTag root) {
    LoadoutState s = new LoadoutState();
    s.revision = root.getLong("Revision");
    s.nextFire = root.getLong("NextFire");
    ListTag list = root.getList("Entries", Tag.TAG_COMPOUND);
    if (list.size() > 64) throw new IllegalArgumentException("Too many entries");
    Set<UUID> ids = new HashSet<>();
    for (Tag t : list) {
      CompoundTag n = (CompoundTag) t;
      if (n.hasUUID("Token")) {
        Entry e = new Entry(n.getUUID("Token"), n.getString("Ammo"), n.getInt("Slot"));
        if (!ids.add(e.token)) throw new IllegalArgumentException("Duplicate token");
        s.entries.add(e);
      }
    }
    return s;
  }

  public CompoundTag tag() {
    CompoundTag n = new CompoundTag();
    ListTag l = new ListTag();
    for (Entry e : entries) {
      CompoundTag t = new CompoundTag();
      t.putUUID("Token", e.token);
      t.putString("Ammo", e.ammo);
      t.putInt("Slot", e.slot);
      l.add(t);
    }
    n.put("Entries", l);
    n.putLong("Revision", revision);
    n.putLong("NextFire", nextFire);
    return n;
  }

  public List<Entry> copy() {
    return new ArrayList<>(entries);
  }

  public static int used(List<Entry> entries, Map<String, AmmoType> ammo) {
    int n = 0;
    for (Entry e : entries) {
      AmmoType a = ammo.get(e.ammo);
      if (a != null) n += a.slots();
    }
    return n;
  }

  public static boolean fits(
      List<Entry> entries, LoadoutSpec spec, Map<String, AmmoType> ammo, String id, int slot) {
    AmmoType a = ammo.get(id);
    if (a == null || !spec.allowed().contains(id) || slot < 0 || slot > spec.slots() - a.slots())
      return false;
    for (Entry e : entries) {
      AmmoType b = ammo.get(e.ammo);
      if (b == null) return false;
      if (slot < e.slot + b.slots() && slot + a.slots() > e.slot) return false;
    }
    return true;
  }

  public static int firstFree(
      List<Entry> entries, LoadoutSpec spec, Map<String, AmmoType> ammo, String id) {
    for (int i = 0; i < spec.slots(); i++) if (fits(entries, spec, ammo, id, i)) return i;
    return -1;
  }

  public static void validate(List<Entry> entries, LoadoutSpec spec, Map<String, AmmoType> ammo) {
    List<Entry> seen = new ArrayList<>();
    Set<UUID> tokens = new HashSet<>();
    for (Entry e : entries) {
      if (!tokens.add(e.token) || !fits(seen, spec, ammo, e.ammo, e.slot))
        throw new IllegalArgumentException("Invalid entry " + e);
      seen.add(e);
    }
  }

  public static JsonArray json(List<Entry> entries) {
    JsonArray a = new JsonArray();
    for (Entry e : entries) {
      JsonObject j = new JsonObject();
      j.addProperty("token", e.token.toString());
      j.addProperty("ammo", e.ammo);
      j.addProperty("slot", e.slot);
      a.add(j);
    }
    return a;
  }

  public static List<Entry> fromJson(JsonArray a) {
    List<Entry> l = new ArrayList<>();
    for (JsonElement el : a) {
      JsonObject j = el.getAsJsonObject();
      l.add(
          new Entry(
              UUID.fromString(j.get("token").getAsString()),
              j.get("ammo").getAsString(),
              j.get("slot").getAsInt()));
    }
    return l;
  }
}
