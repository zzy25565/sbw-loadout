package cn.zzy.loadout.test;

import com.atsuishio.superbwarfare.entity.projectile.IBulletProperties;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;

/** 核对实际安装的 SBW 基准值，防止只根据旧文档或构造类名误读。 */
@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class DamageBalanceGameTests {
  @GameTest(template = "empty")
  public static void nativeDamageReferences(GameTestHelper h) {
    var bomb = (IBulletProperties) BuiltInRegistries.ENTITY_TYPE
        .get(ResourceLocation.parse("superbwarfare:mk_82")).create(h.getLevel());
    var missile = (IBulletProperties) BuiltInRegistries.ENTITY_TYPE
        .get(ResourceLocation.parse("superbwarfare:agm_65")).create(h.getLevel());
    h.assertTrue(bomb.getDamage() == 0 && bomb.getExplosionDamage() == 650
        && bomb.getExplosionRadius() == 22, "native Mk82: independent impact 0, blast 650, radius 22");
    h.assertTrue(missile.getDamage() == 1100 && missile.getExplosionDamage() == 180
        && missile.getExplosionRadius() == 12, "native AGM65: impact 1100, blast 180, radius 12");
    h.succeed();
  }
}
