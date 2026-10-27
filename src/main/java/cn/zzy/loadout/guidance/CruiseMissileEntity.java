package cn.zzy.loadout.guidance;

import com.atsuishio.superbwarfare.entity.projectile.*;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.init.ModSounds;
import com.atsuishio.superbwarfare.client.animation.entity.BasicProjectileAnimationInstance;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 独立巡航发动机：没有 AGM-65 的逐刻加速，仍沿用 SBW 扫掠碰撞、伤害及可拦截接口。 */
public final class CruiseMissileEntity extends MissileProjectile implements BasicGeoProjectileEntity {
  private static final String STATE="LoadoutCruiseState";

  public CruiseMissileEntity(EntityType<? extends CruiseMissileEntity> type,Level level) {
    super(type,level);noCulling=true;
  }
  @Override protected Item getDefaultItem(){return ModItems.MEDIUM_ANTI_AIR_MISSILE.get();}
  @Override public SoundEvent getSound(){return ModSounds.ROCKET_FLY.get();}
  @Override public float getVolume(){return .25f;}
  @Override public boolean forceLoadChunk(){return false;}
  @Override public com.atsuishio.superbwarfare.world.phys.EntityResult getHitResult(
      Entity target,Vec3 from,Vec3 to){
    // Keep SBW's rotated vehicle OBBs and armor-part selection intact.
    if(target instanceof com.atsuishio.superbwarfare.entity.OBBEntity obb && !obb.enableAABB())
      return super.getHitResult(target,from,to);
    // Native generic hit detection shifts an AABB by -5 movement ticks. A slow cruise
    // missile must intersect the current body, rather than a box left behind the target.
    var box=target.getBoundingBox();
    Vec3 hit=box.contains(from)?from:box.clip(from,to).orElse(null);
    return hit==null?null:new com.atsuishio.superbwarfare.world.phys.EntityResult(target,hit,false,false);
  }
  @Override public void tick(){
    if(!level().isClientSide && !CruiseGuidance.active(this)){discard();return;}
    super.tick();
    if(isAlive())smallTrail();
  }
  private void writeCruise(CompoundTag tag){
    if(CruiseGuidance.active(this))
      tag.put(STATE,getPersistentData().getCompound(SeekerController.KEY).copy());
  }
  @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);writeCruise(tag);}
  @Override public void readAdditionalSaveData(CompoundTag tag){
    super.readAdditionalSaveData(tag);
    if(tag.contains(STATE,10))getPersistentData().put(SeekerController.KEY,tag.getCompound(STATE).copy());
  }
  @Override public void buildBvrSyncNbt(CompoundTag tag){super.buildBvrSyncNbt(tag);writeCruise(tag);}
  // Spawn/first-frame fallback; full loadout mesh takes over once the appearance ID is available.
  @Override public ResourceLocation getModel(){return ResourceLocation.parse("superbwarfare:models/bedrock/projectile/agm_65.geo.json");}
  @Override public ResourceLocation getAnimation(){return null;}
  @Override public BasicProjectileAnimationInstance<?> getAnimationInstance(){return null;}
  @Override public ResourceLocation getEmissiveTexture(){return null;}
  @Override public int getHiddenTicks(){return 0;}
  @Override public int getFlareHiddenTicks(){return 3;}
  @Override public BakedModelInstance getModelInstance(){return BasicGeoProjectileEntity.DefaultImpls.getModelInstance(this);}
}
