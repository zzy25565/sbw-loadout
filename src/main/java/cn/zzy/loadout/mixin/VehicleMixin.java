package cn.zzy.loadout.mixin;

import cn.zzy.loadout.LoadoutService;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在原生发射入口阻断旁路；服务端仍校验驾驶席、冷却和持久库存。 */
@Mixin(value = VehicleEntity.class, remap = false)
public abstract class VehicleMixin {
  @Inject(
      method =
          "vehicleShoot(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void fire(LivingEntity living, UUID target, Vec3 pos, CallbackInfo ci) {
    VehicleEntity v = (VehicleEntity) (Object) this;
    if (LoadoutService.supported(v) && (v.getSeatIndex(living) <= 0)) {
      ci.cancel();
      if (living instanceof ServerPlayer p) LoadoutService.fire(p, v, target, pos);
    }
  }

  @Inject(
      method =
          "vehicleShoot(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;Lnet/minecraft/world/phys/Vec3;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void named(LivingEntity living, String name, Vec3 pos, CallbackInfo ci) {
    VehicleEntity v = (VehicleEntity) (Object) this;
    if (LoadoutService.supported(v) && (v.getSeatIndex(living) <= 0)) {
      ci.cancel();
      if (living instanceof ServerPlayer p) LoadoutService.fire(p, v, null, pos);
    }
  }

  @Inject(
      method =
          "vehicleShoot(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;Ljava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V",
      at = @At("HEAD"),
      cancellable = true,
      require = 0)
  private void namedTarget(
      LivingEntity living, String name, UUID target, Vec3 pos, CallbackInfo ci) {
    VehicleEntity v = (VehicleEntity) (Object) this;
    if (LoadoutService.supported(v) && (v.getSeatIndex(living) <= 0)) {
      ci.cancel();
      if (living instanceof ServerPlayer p) LoadoutService.fire(p, v, target, pos);
    }
  }
}
