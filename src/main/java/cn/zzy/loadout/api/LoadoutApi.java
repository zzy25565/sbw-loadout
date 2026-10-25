package cn.zzy.loadout.api;

import cn.zzy.loadout.LoadoutMod;
import cn.zzy.loadout.data.LoadoutData;
import java.util.*;
import net.minecraft.world.item.ItemStack;

/** 注册建议在 common setup 的 enqueueWork 中完成。数据包同名定义优先，可 /reload。 */
public final class LoadoutApi {
  private static final Map<String, AmmoType> REGISTERED = new LinkedHashMap<>();

  public static synchronized void register(AmmoType type) {
    if (REGISTERED.putIfAbsent(type.id(), type) != null)
      throw new IllegalArgumentException("Duplicate ammo " + type.id());
  }

  public static synchronized Map<String, AmmoType> registered() {
    return Map.copyOf(REGISTERED);
  }

  public static AmmoType get(String id) {
    return LoadoutData.ammo().get(id);
  }

  public static ItemStack stack(String id, int count) {
    if (get(id) == null) throw new IllegalArgumentException(id);
    return LoadoutMod.stack(id, count);
  }
}
