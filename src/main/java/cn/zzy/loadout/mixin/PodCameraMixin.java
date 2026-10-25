package cn.zzy.loadout.mixin;

import cn.zzy.loadout.client.*;
import net.minecraft.client.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在原生载具相机接管前应用独立云台姿态，避免 SBW 再将镜头绑回机头。 */
@Mixin(value = Camera.class, priority = 2000)
public abstract class PodCameraMixin {
  @Shadow
  protected abstract void setPosition(double x, double y, double z);

  @Shadow
  protected abstract void setRotation(float yaw, float pitch, float roll);

  @Inject(
      method = "setup",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/Camera;setRotation(FFF)V",
              ordinal = 0),
      cancellable = true)
  private void podCamera(
      BlockGetter level,
      Entity entity,
      boolean detached,
      boolean reverse,
      float partial,
      CallbackInfo ci) {
    if (!TargetingPodClient.active()) return;
    var screen = (TargetingPodScreen) Minecraft.getInstance().screen;
    var p = TargetingPodClient.origin(partial);
    setPosition(p.x, p.y, p.z);
    setRotation(screen.yaw(), screen.pitch(), 0);
    ci.cancel();
  }
}
