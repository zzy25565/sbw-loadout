package cn.zzy.loadout.test;

import cn.zzy.loadout.LoadoutMod;
import cn.zzy.loadout.guidance.CruiseMissileEntity;
import com.atsuishio.superbwarfare.api.event.ProjectileHitEvent;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid=LoadoutMod.ID)
public final class CruiseHitProbe {
  public record Hit(Vec3 point,UUID target,Vec3 targetCenter){}
  public static final Map<UUID,Hit> HITS=new HashMap<>();
  @SubscribeEvent public static void block(ProjectileHitEvent.HitBlock e){
    if(e.getProjectile() instanceof CruiseMissileEntity)
      HITS.putIfAbsent(e.getProjectile().getUUID(),new Hit(e.getHitVec(),null,null));
  }
  @SubscribeEvent public static void entity(ProjectileHitEvent.HitEntity e){
    if(e.getProjectile() instanceof CruiseMissileEntity)
      HITS.putIfAbsent(e.getProjectile().getUUID(),new Hit(e.getHitVec(),e.getTarget().getUUID(),e.getTarget().getBoundingBox().getCenter()));
  }
}
