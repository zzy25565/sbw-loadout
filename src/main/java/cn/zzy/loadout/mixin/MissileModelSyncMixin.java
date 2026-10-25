package cn.zzy.loadout.mixin;

import cn.zzy.loadout.api.AmmoModelEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** SBW 超视距使用精简 NBT，不经过常规存档；仍需带上同一外观身份。 */
@Mixin(value = MissileProjectile.class, remap = false)
public abstract class MissileModelSyncMixin {
  @Inject(method = "buildBvrSyncNbt", at = @At("TAIL"))
  private void modelForDistantRender(CompoundTag tag, CallbackInfo ci) {
    tag.putString("SBWLoadoutAmmoModel", ((AmmoModelEntity) this).loadoutAmmoModel());
  }
}
