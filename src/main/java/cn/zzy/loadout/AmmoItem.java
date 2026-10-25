package cn.zzy.loadout;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class AmmoItem extends Item {
  private final String ammo;

  public AmmoItem(String ammo) {
    super(new Properties().stacksTo(16));
    this.ammo = ammo;
  }

  public String ammo(ItemStack stack) {
    if (ammo != null) return ammo;
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null ? "" : data.copyTag().getString("Ammo");
  }

  @Override
  public Component getName(ItemStack stack) {
    var a =
        cn.zzy.loadout.data.LoadoutData.clientAmmo()
            .getOrDefault(ammo(stack), cn.zzy.loadout.api.LoadoutApi.get(ammo(stack)));
    return a == null ? super.getName(stack) : Component.literal(a.displayName());
  }

  @Override
  public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    consumer.accept(
        new IClientItemExtensions() {
          @Override
          public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return cn.zzy.loadout.client.AmmoRenderer.instance();
          }
        });
  }
}
