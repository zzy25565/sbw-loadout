package cn.zzy.loadout.test;

import cn.zzy.loadout.LoadoutMod;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = LoadoutMod.ID, bus = EventBusSubscriber.Bus.MOD)
public final class TestFixtures {
  public static EntityType<VehicleEntity> AIRCRAFT, GROUND;

  @SubscribeEvent
  public static void register(RegisterEvent event) {
    event.register(
        Registries.ENTITY_TYPE,
        helper -> {
          AIRCRAFT =
              EntityType.Builder.<VehicleEntity>of(VehicleEntity::new, MobCategory.MISC)
                  .sized(2, 1)
                  .build("sbw_loadout:fixture_aircraft");
          helper.register(
              net.minecraft.resources.ResourceLocation.parse("sbw_loadout:fixture_aircraft"),
              AIRCRAFT);
          GROUND =
              EntityType.Builder.<VehicleEntity>of(VehicleEntity::new, MobCategory.MISC)
                  .sized(2, 1)
                  .build("sbw_loadout:fixture_ground");
          helper.register(
              net.minecraft.resources.ResourceLocation.parse("sbw_loadout:fixture_ground"), GROUND);
        });
  }
}
