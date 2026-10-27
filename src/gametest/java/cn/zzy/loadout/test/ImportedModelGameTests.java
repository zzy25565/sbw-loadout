package cn.zzy.loadout.test;

import cn.zzy.loadout.*;
import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.LoadoutData;
import com.atsuishio.superbwarfare.entity.projectile.IBulletProperties;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.gametest.*;

/** Imported model identities must survive generic inventory stacks and all projectile backends. */
@GameTestHolder("sbw_loadout")
@PrefixGameTestTemplate(false)
public final class ImportedModelGameTests {
  private static final List<String> IDS = List.of(
      "ls500j", "fx1400", "gbu58", "spice1000", "pl12", "pl12a", "kab1500l",
      "kab500l", "kh29l_wt", "r40rd", "r40td", "r73", "r73l", "r77_1", "r77",
      "brimstone_dual", "gbu31", "gbu53b", "gbu38", "aim54a", "aim54c");

  @GameTest(template="empty")
  public static void everyImportedAmmoLoadsAsDistinctGenericStack(GameTestHelper h) {
    for (String shortId : IDS) {
      String id = "sbw_loadout:" + shortId;
      var a = LoadoutData.ammo().get(id);
      h.assertTrue(a != null && a.renderProjectileModel(), "loaded full mesh definition " + id);
      var stack = LoadoutMod.stack(id, 3);
      h.assertTrue(stack.getItem() == LoadoutMod.GENERIC.get()
          && LoadoutMod.ammoId(stack.copy()).equals(id) && stack.getCount() == 3,
          "generic inventory stack preserves " + id);
      for (String resource : List.of(a.model(), a.texture(), a.icon())) {
        String[] parts = resource.split(":", 2);
        String path = "/assets/" + parts[0] + "/" + parts[1];
        h.assertTrue(ImportedModelGameTests.class.getResource(path) != null,
            "packaged client asset exists " + path);
      }
    }
    h.succeed();
  }

  @GameTest(template="empty")
  public static void everyImportedProjectilePreservesModelOnSaveLoad(GameTestHelper h) {
    for (String shortId : IDS) {
      String id = "sbw_loadout:" + shortId;
      var a = LoadoutData.ammo().get(id);
      var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(a.projectile()));
      var entity = type.create(h.getLevel());
      h.assertTrue(entity instanceof Projectile && entity instanceof IBulletProperties
          && entity instanceof AmmoModelEntity, "supported projectile backend " + id);
      if (a.guidance().equals("INS"))
        h.assertTrue(entity instanceof com.atsuishio.superbwarfare.entity.projectile.ITrackableProjectile,
            "point guidance backend can receive a target position " + id);
      ((AmmoModelEntity) entity).loadoutAmmoModel(id);
      CompoundTag saved = new CompoundTag();
      entity.saveWithoutId(saved);
      var restored = type.create(h.getLevel());
      restored.load(saved);
      h.assertTrue(((AmmoModelEntity) restored).loadoutAmmoModel().equals(id),
          "saved projectile appearance survives reload " + id);
      entity.discard(); restored.discard();
    }
    h.succeed();
  }
}
