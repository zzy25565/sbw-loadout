package cn.zzy.loadout.mixin;

import cn.zzy.loadout.client.PodThermalVision;
import com.atsuishio.superbwarfare.event.ClientEventHandler;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 仅在吊舱打开期间补充热成像资格，原生飞行视角仍按 SBW 规则检查。 */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class PodThermalMixin {
  @Inject(
      method = "handleThermalImaging(Lnet/minecraft/world/entity/player/Player;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void podThermal(Player player, CallbackInfo ci) {
    if (PodThermalVision.handle()) ci.cancel();
  }
}
