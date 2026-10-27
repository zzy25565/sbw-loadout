package cn.zzy.loadout.client;

import cn.zzy.loadout.LoadoutMod;
import cn.zzy.loadout.data.LoadoutData;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in disposable client scene; excluded from release builds. */
@EventBusSubscriber(modid=LoadoutMod.ID, value=Dist.CLIENT)
public final class ImportedModelsSmoke {
  private static final List<String> IDS=List.of(
      "ls500j", "fx1400", "gbu58", "spice1000", "pl12", "pl12a", "kab1500l",
      "kab500l", "kh29l_wt", "r40rd", "r40td", "r73", "r73l", "r77_1", "r77",
      "brimstone_dual", "gbu31", "gbu53b", "gbu38", "aim54a", "aim54c");
  private static boolean started;
  private static int ticks;

  private static void check(boolean condition,String text) {
    if(!condition) throw new IllegalStateException("WT_MODEL_SMOKE_FAIL "+text);
    LogUtils.getLogger().info("WT_MODEL_SMOKE_PASS {}",text);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if(!Boolean.getBoolean("sbw.modelSmoke"))return;
    var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    if(!started && (mc.screen instanceof TitleScreen || (mc.screen!=null
        && mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")))) {
      started=true;
      mc.createWorldOpenFlows().createFreshLevel("wt_models_validation_"+System.currentTimeMillis(),
          new LevelSettings("WT models isolated validation",GameType.CREATIVE,false,
              Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
          new WorldOptions(20261026L,false,false),
          r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
              .value().createWorldDimensions(),new TitleScreen());
      return;
    }
    if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null)return;
    ticks++;
    if(ticks==60) {
      for(String id:IDS)check(LoadoutData.clientAmmo().containsKey("sbw_loadout:"+id),"client synced "+id);
      mc.setScreen(new Board(0));
    }
    if(ticks>=100 && ticks<=220 && (ticks-100)%40==0) {
      int page=(ticks-100)/40;
      Screenshot.grab(mc.gameDirectory,"wt_models_page_"+(page+1)+".png",mc.getMainRenderTarget(),
          message->LogUtils.getLogger().info("WT_MODEL_SCREENSHOT {}",page+1));
      if(page<3)mc.setScreen(new Board(page+1));
    }
    if(ticks==240) {
      for(String id:IDS)check(AmmoRenderer.renderedProjectileModels().contains("sbw_loadout:"+id),
          "full projectile mesh rendered "+id);
      LogUtils.getLogger().info("WT_MODEL_SMOKE_COMPLETE 21 items and projectile meshes, 4 pages");
    }
    if(ticks==260)mc.stop();
  }

  private static final class Board extends Screen {
    final int page;
    final Map<String,Entity> entities=new HashMap<>();
    Board(int page) {super(Component.literal("WT imported model verification"));this.page=page;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
      g.fill(0,0,width,height,0xff18222c);
      g.drawString(font,"WT models: real item + projectile renderer / page "+(page+1)+" of 4",12,12,0xffe2edf5);
      int cellW=(width-24)/3,cellH=(height-48)/2;
      for(int i=0;i<6;i++) {
        int index=page*6+i;if(index>=IDS.size())break;
        String shortId=IDS.get(index),id="sbw_loadout:"+shortId;
        var ammo=LoadoutData.clientAmmo().get(id);
        int x=12+(i%3)*cellW,y=32+(i/3)*cellH;
        g.fill(x+2,y+2,x+cellW-4,y+cellH-4,0xff253440);
        g.drawString(font,shortId,x+8,y+8,0xffd7e4ed);
        Entity entity=entities.computeIfAbsent(id,key->BuiltInRegistries.ENTITY_TYPE
            .get(ResourceLocation.parse(ammo.projectile())).create(minecraft.level));
        g.flush();Lighting.setupFor3DItems();
        g.pose().pushPose();g.pose().translate(x+cellW*.53,y+cellH*.47,200);
        // Item renderer and world renderer share the same full geometry, tested separately.
        float scale=(float)(cellW*.66/(shortId.startsWith("r40")?6.3:4.7));
        g.pose().scale(scale,-scale,scale);
        g.pose().mulPose(Axis.XP.rotationDegrees(18));g.pose().mulPose(Axis.YP.rotationDegrees(65));
        if(!AmmoRenderer.renderProjectile(entity,id,0,g.pose(),minecraft.renderBuffers().bufferSource(),
            LightTexture.FULL_BRIGHT))check(false,"draw "+id);
        minecraft.renderBuffers().bufferSource().endBatch();g.pose().popPose();
        g.pose().pushPose();g.pose().translate(x+14,y+cellH-46,240);g.pose().scale(2,2,2);
        g.renderItem(LoadoutMod.stack(id,1),0,0);g.pose().popPose();
        g.drawString(font,ammo.guidance(),x+56,y+cellH-28,0xff99bbc9);
      }
      g.flush();
    }
  }
}
