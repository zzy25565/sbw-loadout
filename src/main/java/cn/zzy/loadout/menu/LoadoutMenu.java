package cn.zzy.loadout.menu;

import cn.zzy.loadout.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.data.LoadoutState.Entry;
import cn.zzy.loadout.network.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.google.gson.*;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** UI 是事务草稿，不设置可搬动物品的虚拟容器槽；断线/关闭只销毁草稿。 */
public final class LoadoutMenu extends AbstractContainerMenu {
  public final int entityId;
  private final VehicleEntity vehicle;
  private final ServerPlayer owner;
  private final long revision, generation;
  private final Vec3 origin;
  private final List<Entry> original, draft;
  private boolean finished;

  public LoadoutMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
    super(LoadoutMod.MENU.get(), id);
    entityId = buf.readVarInt();
    vehicle = null;
    owner = null;
    revision = 0;
    generation = 0;
    origin = Vec3.ZERO;
    original = List.of();
    draft = new ArrayList<>();
  }

  public LoadoutMenu(int id, Inventory inventory, VehicleEntity vehicle) {
    super(LoadoutMod.MENU.get(), id);
    this.entityId = vehicle.getId();
    this.vehicle = vehicle;
    owner = (ServerPlayer) inventory.player;
    LoadoutState s = LoadoutService.state(vehicle);
    revision = s.revision;
    generation = LoadoutData.generation;
    origin = vehicle.position();
    original = s.copy();
    draft = s.copy();
  }

  @Override
  public boolean stillValid(Player player) {
    return vehicle == null
        || (!finished
            && LoadoutService.editable((ServerPlayer) player, vehicle)
            && vehicle.position().distanceToSqr(origin) <= 64
            && generation == LoadoutData.generation);
  }

  @Override
  public ItemStack quickMoveStack(Player p, int index) {
    return ItemStack.EMPTY;
  }

  public void act(String action, String ammo, int slot) {
    if (owner == null || finished) return;
    if (!stillValid(owner)) {
      LoadoutService.message(owner, "目标或配置已变化，已取消");
      owner.closeContainer();
      return;
    }
    LoadoutSpec spec = LoadoutData.spec(vehicle);
    switch (action) {
      case "add" -> {
        if (LoadoutState.fits(draft, spec, LoadoutData.ammo(), ammo, slot))
          draft.add(new Entry(UUID.randomUUID(), ammo, slot));
        else LoadoutService.message(owner, "弹种不允许或连续挂点不足");
      }
      case "remove" -> {
        draft.removeIf(
            e -> {
              var a = LoadoutData.ammo().get(e.ammo());
              return a != null && slot >= e.slot() && slot < e.slot() + a.slots();
            });
      }
      case "fill" -> {
        Map<String, Integer> counts = LoadoutService.counts(owner);
        Set<UUID> remaining = new HashSet<>();
        draft.forEach(e -> remaining.add(e.token()));
        for (Entry e : original)
          if (!remaining.contains(e.token())) counts.merge(e.ammo(), 1, Integer::sum);
        Set<UUID> old = new HashSet<>();
        original.forEach(e -> old.add(e.token()));
        for (Entry e : draft)
          if (!old.contains(e.token())) counts.merge(e.ammo(), -1, Integer::sum);
        for (String id : spec.allowed()) {
          int n =
              LoadoutService.infinite(owner)
                  ? spec.slots()
                  : Math.max(0, counts.getOrDefault(id, 0));
          for (int i = 0; i < n; i++) {
            int at = LoadoutState.firstFree(draft, spec, LoadoutData.ammo(), id);
            if (at < 0) break;
            draft.add(new Entry(UUID.randomUUID(), id, at));
          }
        }
      }
      case "commit" -> {
        LoadoutService.commit(owner, vehicle, revision, original, draft);
        finished = true;
        owner.closeContainer();
        return;
      }
      case "cancel" -> {
        finished = true;
        owner.closeContainer();
        return;
      }
      default -> {
        return;
      }
    }
    send();
  }

  public void send() {
    if (owner == null) return;
    JsonObject j = LoadoutService.snapshot(vehicle);
    j.addProperty("menu", containerId);
    j.addProperty("name", vehicle.getName().getString());
    j.add("draft", LoadoutState.json(draft));
    j.add("inventory", new Gson().toJsonTree(LoadoutService.counts(owner)));
    j.addProperty("infinite", LoadoutService.infinite(owner));
    LoadoutNetwork.send(owner, "menu", j);
  }
}
