package cn.zzy.loadout;

import cn.zzy.loadout.data.*;
import cn.zzy.loadout.menu.LoadoutMenu;
import cn.zzy.loadout.network.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

@Mod(LoadoutMod.ID)
public final class LoadoutMod {
  public static final String ID = "sbw_loadout";
  public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.ENTITY_TYPE,ID);
  public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<cn.zzy.loadout.guidance.RadarMissileEntity>> RADAR_MISSILE =
      ENTITIES.register("9m336_radar",()->net.minecraft.world.entity.EntityType.Builder
          .<cn.zzy.loadout.guidance.RadarMissileEntity>of(cn.zzy.loadout.guidance.RadarMissileEntity::new,net.minecraft.world.entity.MobCategory.MISC)
          .sized(.5f,.5f).clientTrackingRange(256).updateInterval(1).build("sbw_loadout:9m336_radar"));
  public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
  public static final Map<String, DeferredItem<AmmoItem>> AMMO = new LinkedHashMap<>();

  static {
    for (String id :
        List.of("kh38mt", "kh29td", "kab500kr", "x555", "kh29l", "kab1500kr", "kh59mk", "9m336_radar"))
      AMMO.put(ID + ":" + id, ITEMS.register("ammo_" + id, () -> new AmmoItem(ID + ":" + id)));
  }

  public static final DeferredItem<AmmoItem> GENERIC =
      ITEMS.register("ammo", () -> new AmmoItem(null));
  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(Registries.MENU, ID);
  public static final DeferredHolder<MenuType<?>, MenuType<LoadoutMenu>> MENU =
      MENUS.register("loadout", () -> IMenuTypeExtension.create(LoadoutMenu::new));
  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
  public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
      TABS.register(
          "ammo",
          () ->
              CreativeModeTab.builder()
                  .title(Component.literal("SBW 自定义挂载"))
                  .icon(() -> AMMO.values().iterator().next().get().getDefaultInstance())
                  .displayItems(
                      (params, out) -> {
                        for (String id :
                            (LoadoutData.clientAmmo().isEmpty()
                                    ? LoadoutData.ammo()
                                    : LoadoutData.clientAmmo())
                                .keySet()) out.accept(stack(id, 1));
                        if (LoadoutData.ammo().isEmpty())
                          for (var item : AMMO.values()) out.accept(item.get());
                      })
                  .build());
  public static final ModConfigSpec CONFIG;
  public static final ModConfigSpec.IntValue LASER_RANGE;
  public static final ModConfigSpec.IntValue AIRCRAFT_LASER_CODE;
  public static final ModConfigSpec.IntValue POD_RANGE;
  public static final ModConfigSpec.BooleanValue POD_ENABLED;
  public static final ModConfigSpec.ConfigValue<List<? extends String>> INFINITE;

  static {
    var b = new ModConfigSpec.Builder();
    INFINITE =
        b.comment("生存模式无限弹药许可：玩家 UUID 列表；默认空列表。")
            .defineListAllowEmpty(
                "infiniteAmmoPlayers", List.of(), o -> o instanceof String s && validUuid(s));
    LASER_RANGE =
        b.comment("火炮指示器的最大照射距离，单位格；不加载沿途新区块。").defineInRange("laserRange", 2048, 16, 4096);
    AIRCRAFT_LASER_CODE =
        b.comment("机载激光编码；与激光弹药 LaserCode 一致时可自照射/友军协同。")
            .defineInRange("aircraftLaserCode", 1688, 1, 9999);
    POD_ENABLED = b.comment("为已接入通用挂载的飞机/直升机启用独立光电吊舱视角。").define("podEnabled", true);
    POD_RANGE =
        b.comment("吊舱观测距离，单位格；不加载新区块，弹药仍受自身射程和射界限制。").defineInRange("podRange", 2048, 16, 4096);
    CONFIG = b.build();
  }

  private static boolean validUuid(String s) {
    try {
      UUID.fromString(s);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public LoadoutMod(IEventBus bus, ModContainer container) {
    ENTITIES.register(bus);
    ITEMS.register(bus);
    MENUS.register(bus);
    TABS.register(bus);
    bus.addListener(LoadoutNetwork::register);
    container.registerConfig(ModConfig.Type.SERVER, CONFIG);
    container.registerConfig(ModConfig.Type.CLIENT, cn.zzy.loadout.client.LaserVisualConfig.CONFIG);
  }

  public static ItemStack stack(String id, int count) {
    var item = AMMO.get(id);
    ItemStack stack = new ItemStack(item == null ? GENERIC.get() : item.get(), count);
    if (item == null) {
      CompoundTag t = new CompoundTag();
      t.putString("Ammo", id);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }
    return stack;
  }

  public static String ammoId(ItemStack stack) {
    return stack.getItem() instanceof AmmoItem a ? a.ammo(stack) : "";
  }
}
