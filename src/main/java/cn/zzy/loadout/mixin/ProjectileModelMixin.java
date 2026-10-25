package cn.zzy.loadout.mixin;

import cn.zzy.loadout.api.AmmoModelEntity;
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 沿用原生弹体、血量和近防炮标签，只添加可存档、随实体跟踪同步的外观 ID。 */
@Mixin(value = FastThrowableProjectile.class, remap = false)
public abstract class ProjectileModelMixin implements AmmoModelEntity {
  @Unique
  private static final EntityDataAccessor<String> LOADOUT_MODEL =
      SynchedEntityData.defineId(FastThrowableProjectile.class, EntityDataSerializers.STRING);

  @Inject(method = "defineSynchedData", at = @At("TAIL"))
  private void modelData(SynchedEntityData.Builder builder, CallbackInfo ci) {
    builder.define(LOADOUT_MODEL, "");
  }

  public String loadoutAmmoModel() {
    return ((Entity) (Object) this).getEntityData().get(LOADOUT_MODEL);
  }

  public void loadoutAmmoModel(String id) {
    ((Entity) (Object) this).getEntityData().set(LOADOUT_MODEL, id);
  }

  @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
  private void saveModel(CompoundTag tag, CallbackInfo ci) {
    tag.putString("SBWLoadoutAmmoModel", loadoutAmmoModel());
  }

  @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
  private void readModel(CompoundTag tag, CallbackInfo ci) {
    loadoutAmmoModel(tag.getString("SBWLoadoutAmmoModel"));
  }
}
