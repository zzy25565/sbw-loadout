package cn.zzy.loadout.mixin;

import cn.zzy.loadout.guidance.SeekerController;
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 直接接在原生运动之前：普通 tick 和 SBW 的远距离手动 tick 都经过同一导引头。 */
@Mixin(value = FastThrowableProjectile.class, remap = false)
public abstract class SeekerTickMixin {
  @Inject(method = "tick", at = @At("HEAD"))
  private void guide(CallbackInfo ci) {
    SeekerController.tick((Projectile) (Object) this);
  }
}
