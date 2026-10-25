package cn.zzy.loadout;

import cn.zzy.loadout.data.*;
import cn.zzy.loadout.menu.LoadoutMenu;
import cn.zzy.loadout.network.LoadoutNetwork;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = LoadoutMod.ID)
public final class LoadoutEvents {
  @SubscribeEvent
  public static void reload(AddReloadListenerEvent e) {
    e.addListener(new LoadoutData());
  }

  @SubscribeEvent
  public static void datapack(OnDatapackSyncEvent e) {
    if (e.getPlayer() != null) LoadoutNetwork.definitions(e.getPlayer());
    else for (ServerPlayer p : e.getPlayerList().getPlayers()) LoadoutNetwork.definitions(p);
  }

  @SubscribeEvent
  public static void tracking(PlayerEvent.StartTracking e) {
    if (e.getEntity() instanceof ServerPlayer p
        && e.getTarget() instanceof VehicleEntity v
        && LoadoutService.supported(v)) LoadoutNetwork.send(p, "state", LoadoutService.snapshot(v));
  }

  @SubscribeEvent
  public static void tick(EntityTickEvent.Post e) {
    if (e.getEntity() instanceof VehicleEntity v
        && !v.level().isClientSide
        && v.tickCount % 20 == 0
        && LoadoutService.supported(v)
        && !v.getPassengers().isEmpty()) LoadoutService.sync(v);
  }

  /** 同时拦截精确点击与普通点击，成功后取消 SBW 上车动作；副手不重复执行。 */
  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void interact(PlayerInteractEvent.EntityInteract e) {
    handle(
        e,
        e.getTarget(),
        () -> {
          e.setCancellationResult(InteractionResult.SUCCESS);
          e.setCanceled(true);
        });
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void interactAt(PlayerInteractEvent.EntityInteractSpecific e) {
    handle(
        e,
        e.getTarget(),
        () -> {
          e.setCancellationResult(InteractionResult.SUCCESS);
          e.setCanceled(true);
        });
  }

  private static void handle(
      PlayerInteractEvent e, net.minecraft.world.entity.Entity target, Runnable cancel) {
    if (!(target instanceof VehicleEntity v)) return;
    boolean shift = e.getEntity().isShiftKeyDown();
    boolean ammo = e.getItemStack().getItem() instanceof AmmoItem;
    if (!shift && !ammo) return;
    if (e.getHand() != InteractionHand.MAIN_HAND) {
      if (shift) {
        cancel.run();
      }
      return;
    }
    cancel.run();
    if (!(e.getEntity() instanceof ServerPlayer p)) return;
    if (!LoadoutService.supported(v)) {
      LoadoutService.message(p, "该载具不支持自定义挂载");
      return;
    }
    if (!LoadoutService.editable(p, v)) {
      LoadoutService.message(p, "载具距离过远、被占用或无法编辑");
      return;
    }
    if (shift) {
      p.openMenu(
          new SimpleMenuProvider(
              (id, inv, player) -> new LoadoutMenu(id, inv, v), Component.literal("挂载配置")),
          b -> b.writeVarInt(v.getId()));
      if (p.containerMenu instanceof LoadoutMenu menu) menu.send();
    } else LoadoutService.loadOne(p, v, e.getItemStack());
  }
}
