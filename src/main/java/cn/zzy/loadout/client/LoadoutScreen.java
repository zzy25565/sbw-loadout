package cn.zzy.loadout.client;

import cn.zzy.loadout.data.*;
import cn.zzy.loadout.data.LoadoutState.Entry;
import cn.zzy.loadout.menu.LoadoutMenu;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** 所有按钮/拖拽只发送意图，服务端返回草稿后才刷新；Esc 不发送提交。 */
public final class LoadoutScreen extends AbstractContainerScreen<LoadoutMenu> {
  private String dragging;
  private int page;
  private int firstSlot;
  private int visibleSlots;

  public LoadoutScreen(LoadoutMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 360;
    imageHeight = 240;
  }

  private JsonObject data() {
    JsonObject j = ClientLoadouts.menu;
    return j != null
            && j.get("menu").getAsInt() == menu.containerId
            && j.get("entity").getAsInt() == menu.entityId
        ? j
        : null;
  }

  @Override
  protected void init() {
    imageWidth = Math.min(400, width - 12);
    imageHeight = Math.min(310, height - 12);
    super.init();
    int y = topPos + imageHeight - 24;
    int bw = (imageWidth - 32) / 3;
    addRenderableWidget(
        Button.builder(Component.literal("一键填满"), b -> act("fill", "", -1))
            .bounds(leftPos + 8, y, bw, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("保存并关闭"), b -> act("commit", "", -1))
            .bounds(leftPos + 16 + bw, y, bw, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("取消"), b -> onClose())
            .bounds(leftPos + 24 + 2 * bw, y, bw, 20)
            .build());
    visibleSlots = Math.max(8, ((imageHeight - 130) / 26) * 8);
  }

  private void act(String op, String ammo, int slot) {
    ClientLoadouts.action(menu.containerId, op, ammo, slot);
  }

  private List<String> allowed() {
    JsonObject j = data();
    if (j == null) return List.of();
    List<String> out = new ArrayList<>();
    for (JsonElement e : j.getAsJsonObject("spec").getAsJsonArray("Allowed")) {
      String id = e.getAsString();
      boolean available =
          j.get("infinite").getAsBoolean()
              || j.getAsJsonObject("inventory").has(id)
                  && j.getAsJsonObject("inventory").get(id).getAsInt() > 0;
      Set<java.util.UUID> retained = new HashSet<>();
      for (Entry entry : draft()) retained.add(entry.token());
      for (Entry entry : LoadoutState.fromJson(j.getAsJsonArray("entries")))
        if (entry.ammo().equals(id) && !retained.contains(entry.token())) available = true;
      if (available) out.add(id);
    }
    return out;
  }

  private List<Entry> draft() {
    JsonObject j = data();
    return j == null ? List.of() : LoadoutState.fromJson(j.getAsJsonArray("draft"));
  }

  private int slotAt(double x, double y) {
    int col = (int) ((x - leftPos - 8) / ((imageWidth - 16) / 8)),
        row = (int) ((y - topPos - 104) / 26);
    if (x < leftPos + 8 || x >= leftPos + imageWidth - 8 || y < topPos + 104 || row < 0) return -1;
    int s = firstSlot + row * 8 + col;
    JsonObject j = data();
    return j != null
            && s < j.getAsJsonObject("spec").get("Slots").getAsInt()
            && s < firstSlot + visibleSlots
        ? s
        : -1;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xf0151b23);
    g.fill(leftPos, topPos, leftPos + imageWidth, topPos + 30, 0xff283749);
    JsonObject j = data();
    if (j == null) {
      g.drawString(font, "正在读取服务端挂载…", leftPos + 10, topPos + 40, 0xffffff);
      return;
    }
    var entries = draft();
    int total = j.getAsJsonObject("spec").get("Slots").getAsInt();
    g.drawString(
        font,
        font.plainSubstrByWidth(j.get("name").getAsString() + " · 挂载配置", imageWidth - 110),
        leftPos + 8,
        topPos + 10,
        0xffffff);
    g.drawString(
        font,
        "已用 " + LoadoutState.used(entries, LoadoutData.clientAmmo()) + "/" + total,
        leftPos + imageWidth - 88,
        topPos + 10,
        0xf3cb82);
    g.drawString(font, "可挂弹药 · 拖入空槽（滚轮翻页）", leftPos + 8, topPos + 36, 0xaab8c9);
    List<String> allowed = allowed();
    int tile = (imageWidth - 16) / 4;
    for (int i = 0; i < 4 && page * 4 + i < allowed.size(); i++) {
      String id = allowed.get(page * 4 + i);
      var a = LoadoutData.clientAmmo().get(id);
      if (a == null) continue;
      int x = leftPos + 8 + i * tile, y = topPos + 50;
      g.fill(x, y, x + tile - 4, y + 42, 0xff253341);
      g.blit(ResourceLocation.parse(a.icon()), x + 4, y + 3, 0, 0, 20, 20, 16, 16);
      int count =
          j.getAsJsonObject("inventory").has(id)
              ? j.getAsJsonObject("inventory").get(id).getAsInt()
              : 0;
      g.drawString(
          font, j.get("infinite").getAsBoolean() ? "∞" : "×" + count, x + 28, y + 7, 0xdce7ed);
      g.drawString(
          font, font.plainSubstrByWidth(a.displayName(), tile - 8), x + 4, y + 27, 0xffffff);
    }
    int cell = (imageWidth - 16) / 8;
    for (int s = firstSlot; s < Math.min(total, firstSlot + visibleSlots); s++) {
      int x = leftPos + 8 + ((s - firstSlot) % 8) * cell,
          y = topPos + 104 + ((s - firstSlot) / 8) * 26;
      Entry current = null;
      for (Entry entry : entries) {
        var a = LoadoutData.clientAmmo().get(entry.ammo());
        if (a != null && s >= entry.slot() && s < entry.slot() + a.slots()) current = entry;
      }
      g.fill(x, y, x + cell - 3, y + 23, current == null ? 0xff28323c : 0xff42668b);
      g.drawString(font, "" + (s + 1), x + 2, y + 2, 0x9cabb8);
      if (current != null && s == current.slot()) {
        var a = LoadoutData.clientAmmo().get(current.ammo());
        if (a != null)
          g.blit(ResourceLocation.parse(a.icon()), x + cell - 21, y + 3, 0, 0, 18, 18, 16, 16);
      } else if (current != null) g.drawString(font, "↔", x + cell / 2 - 3, y + 10, 0xe9d6a0);
    }
    g.drawString(font, "点击挂载可卸下；保存才扣/退物品；Esc 取消", leftPos + 8, topPos + imageHeight - 38, 0xaab8c9);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int x, int y) {}

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
    if (dragging != null) {
      var a = LoadoutData.clientAmmo().get(dragging);
      if (a != null)
        g.blit(ResourceLocation.parse(a.icon()), mx - 10, my - 10, 0, 0, 20, 20, 16, 16);
    }
    int s = slotAt(mx, my);
    if (s >= 0)
      for (Entry entry : draft()) {
        var a = LoadoutData.clientAmmo().get(entry.ammo());
        if (a != null && s >= entry.slot() && s < entry.slot() + a.slots())
          g.renderTooltip(
              font,
              Component.literal(a.displayName() + " · 占用 " + a.slots() + " 槽 · 点击卸下"),
              mx,
              my);
      }
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    if (button == 0 && data() != null) {
      if (y >= topPos + 50 && y < topPos + 92 && x >= leftPos + 8 && x < leftPos + imageWidth - 8) {
        int i = page * 4 + (int) ((x - leftPos - 8) / ((imageWidth - 16) / 4));
        List<String> list = allowed();
        if (i < list.size()) {
          dragging = list.get(i);
          return true;
        }
      }
      int s = slotAt(x, y);
      if (s >= 0) {
        act("remove", "", s);
        return true;
      }
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (dragging != null) {
      int s = slotAt(x, y);
      if (s >= 0) act("add", dragging, s);
      dragging = null;
      return true;
    }
    return super.mouseReleased(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double dx, double dy) {
    if (data() == null) return true;
    if (y < topPos + 100) {
      int pages = Math.max(1, (allowed().size() + 3) / 4);
      page = Math.max(0, Math.min(pages - 1, page + (dy < 0 ? 1 : -1)));
    } else {
      int total = data().getAsJsonObject("spec").get("Slots").getAsInt();
      firstSlot =
          Math.max(
              0,
              Math.min(
                  Math.max(0, ((total - visibleSlots + 7) / 8) * 8),
                  firstSlot + (dy < 0 ? 8 : -8)));
    }
    return true;
  }

  @Override
  public void onClose() {
    act("cancel", "", -1);
    super.onClose();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
