package cn.zzy.loadout.test;

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** The fixture tests inspect blast values, not terrain destruction. Keep live rounds from
 * deleting later test structures, and remove residual entities at batch boundaries. */
@GameTestHolder("sbw_loadout")
@EventBusSubscriber(modid="sbw_loadout")
public final class TestIsolation {
  @SubscribeEvent public static void diagnostic(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
    if(e.getServer().getTickCount()%20!=0) return;
    try {
      var f=net.minecraft.gametest.framework.GameTestTicker.class.getDeclaredField("testInfos");f.setAccessible(true);
      for(Object value:(java.util.Collection<?>)f.get(net.minecraft.gametest.framework.GameTestTicker.SINGLETON)) {
        var test=(net.minecraft.gametest.framework.GameTestInfo)value;
        if(!test.isDone() && !test.hasStarted() && test.getStructureBlockEntity()!=null) {
          var box=test.getStructureBounds().inflate(32);
          for(int x=((int)Math.floor(box.minX))>>4;x<=(((int)Math.floor(box.maxX))>>4);x++)
            for(int z=((int)Math.floor(box.minZ))>>4;z<=(((int)Math.floor(box.maxZ))>>4);z++)
              test.getLevel().setChunkForced(x,z,true);
        }
        if(!test.isDone() && e.getServer().getTickCount()%1000==0) com.mojang.logging.LogUtils.getLogger().info("TEST_PENDING {} started={} structure={}",
            test.getTestName(),test.hasStarted(),test.getStructureBlockEntity()!=null);
      }
    }catch(ReflectiveOperationException ex) {throw new RuntimeException(ex);}
  }
  @SubscribeEvent public static void added(EntityJoinLevelEvent e) {
    if(!e.getLevel().isClientSide() && e.getEntity() instanceof FastThrowableProjectile p)
      p.setExplosionDestroy(false);
  }
  private static void clear(ServerLevel level) {
    var entities=new java.util.ArrayList<Entity>();level.getAllEntities().forEach(entities::add);
    for(Entity e:entities) if(e instanceof Projectile || e instanceof VehicleEntity || e instanceof Mob) e.discard();
  }
  @AfterBatch(batch="bombRelease") public static void bombs(ServerLevel l) {clear(l);}
  @AfterBatch(batch="defaultBatch") public static void defaults(ServerLevel l) {clear(l);}
  @AfterBatch(batch="radar") public static void radar(ServerLevel l) {clear(l);}
  @AfterBatch(batch="radarCompat") public static void compat(ServerLevel l) {clear(l);}
  @AfterBatch(batch="airToAir") public static void air(ServerLevel l) {clear(l);}
}
