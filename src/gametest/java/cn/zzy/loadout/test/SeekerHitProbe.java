package cn.zzy.loadout.test;

import cn.zzy.loadout.LoadoutMod;
import com.atsuishio.superbwarfare.api.event.ProjectileHitEvent;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** 测试用：记录真实碰撞点，而非弹丸被移除后仍可能推进的位置。 */
@EventBusSubscriber(modid = LoadoutMod.ID)
public final class SeekerHitProbe {
  public static final Map<UUID, Vec3> HITS = new HashMap<>();

  @SubscribeEvent
  public static void hit(ProjectileHitEvent.HitBlock e) {
    HITS.putIfAbsent(e.getProjectile().getUUID(), e.getHitVec());
  }
}
