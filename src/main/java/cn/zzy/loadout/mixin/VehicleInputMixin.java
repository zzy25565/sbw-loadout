package cn.zzy.loadout.mixin;

import cn.zzy.loadout.client.ClientLoadouts;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class VehicleInputMixin {
  @Inject(method = "handleVehicleGunShoot", at = @At("HEAD"), cancellable = true)
  private void loadoutInput(CallbackInfo ci) {
    if (ClientLoadouts.handleFire()) ci.cancel();
  }
}
