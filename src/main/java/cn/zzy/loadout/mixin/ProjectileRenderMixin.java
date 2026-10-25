package cn.zzy.loadout.mixin;

import cn.zzy.loadout.api.AmmoModelEntity;
import cn.zzy.loadout.client.AmmoRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class ProjectileRenderMixin {
  @Inject(method = "render", at = @At("HEAD"), cancellable = true)
  private void renderAmmo(
      Entity entity,
      double x,
      double y,
      double z,
      float yaw,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      CallbackInfo ci) {
    if (!(entity instanceof AmmoModelEntity model) || model.loadoutAmmoModel().isEmpty()) return;
    pose.pushPose();
    pose.translate(x, y, z);
    boolean rendered =
        AmmoRenderer.renderProjectile(
            entity, model.loadoutAmmoModel(), partial, pose, buffers, light);
    pose.popPose();
    if (rendered) ci.cancel();
  }
}
