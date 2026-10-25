package cn.zzy.loadout.guidance;

import com.atsuishio.superbwarfare.entity.projectile.*;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.init.ModSounds;
import com.atsuishio.superbwarfare.client.animation.entity.BasicProjectileAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 独立雷达版 9M336：保留 SBW 碰撞/爆炸及 9M336 推进，导引交给挂载状态机。 */
public final class RadarMissileEntity extends MissileProjectile implements BasicGeoProjectileEntity {
  public RadarMissileEntity(EntityType<? extends RadarMissileEntity> type,Level level) {
    super(type,level);this.noCulling=true;
  }
  @Override protected Item getDefaultItem() { return ModItems.MEDIUM_ANTI_AIR_MISSILE.get(); }
  @Override public SoundEvent getSound() { return ModSounds.ROCKET_FLY.get(); }
  @Override public float getVolume() { return .4f; }
  @Override public void tick() {
    super.tick(); mediumTrail();
    // 原 9M336 的推进系数；独立弹体避免原生红外转向和 200 刻失锁分支覆盖雷达阶段。
    if(!level().isClientSide && isAlive() && tickCount>1 && getDeltaMovement().lengthSqr()>1e-8)
      setDeltaMovement(getDeltaMovement().scale(.05).add(getDeltaMovement().normalize().scale(8)));
    if(!level().isClientSide && tickCount>=1200) discard(); // 60 秒上限，限制残弹存活。
  }
  @Override public ResourceLocation getModel() { return BasicGeoProjectileEntity.DefaultImpls.getModel(this); }
  @Override public ResourceLocation getAnimation() { return null; }
  @Override public BasicProjectileAnimationInstance<?> getAnimationInstance() { return null; }
  @Override public ResourceLocation getEmissiveTexture() { return null; }
  @Override public int getHiddenTicks() { return 0; }
  @Override public int getFlareHiddenTicks() { return 3; }
  @Override public BakedModelInstance getModelInstance() { return BasicGeoProjectileEntity.DefaultImpls.getModelInstance(this); }
}
