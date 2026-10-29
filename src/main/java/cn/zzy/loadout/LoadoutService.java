package cn.zzy.loadout;

import cn.zzy.loadout.api.*;
import cn.zzy.loadout.data.*;
import cn.zzy.loadout.data.LoadoutState.Entry;
import cn.zzy.loadout.guidance.*;
import cn.zzy.loadout.network.LoadoutNetwork;
import com.atsuishio.superbwarfare.entity.projectile.*;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class LoadoutService {
  public static final String KEY = "SBWCustomLoadout";
  private static final Map<ServerPlayer, Long> EMPTY_MESSAGES = new WeakHashMap<>();

  public static boolean infinite(ServerPlayer p) {
    return p.isCreative() || LoadoutMod.INFINITE.get().contains(p.getUUID().toString());
  }

  public static boolean supported(VehicleEntity v) {
    return LoadoutData.spec(v) != null;
  }

  public static LoadoutState state(VehicleEntity v) {
    CompoundTag root = v.getPersistentData();
    if (!root.contains(KEY)) {
      LoadoutState s = new LoadoutState();
      LoadoutSpec spec = LoadoutData.spec(v);
      if (spec != null)
        for (String id : spec.defaults()) {
          int slot = LoadoutState.firstFree(s.entries, spec, LoadoutData.ammo(), id);
          if (slot >= 0) s.entries.add(new Entry(UUID.randomUUID(), id, slot));
        }
      save(v, s);
      return s;
    }
    return LoadoutState.read(root.getCompound(KEY));
  }

  public static void save(VehicleEntity v, LoadoutState s) {
    v.getPersistentData().put(KEY, s.tag());
  }

  public static boolean editable(ServerPlayer p, VehicleEntity v) {
    if (p.isSpectator()
        || !v.isAlive()
        || v.isWreck()
        || p.level() != v.level()
        || p.distanceToSqr(v) > 64) return false;
    for (Entity e : v.getPassengers()) if (e != p) return false;
    return supported(v);
  }

  public static void message(ServerPlayer p, String text) {
    p.displayClientMessage(Component.literal(text), true);
  }

  public static Map<String, Integer> counts(ServerPlayer p) {
    Map<String, Integer> n = new HashMap<>();
    for (ItemStack stack : p.getInventory().items) {
      String id = LoadoutMod.ammoId(stack);
      if (!id.isEmpty()) n.merge(id, stack.getCount(), Integer::sum);
    }
    String off = LoadoutMod.ammoId(p.getOffhandItem());
    if (!off.isEmpty()) n.merge(off, p.getOffhandItem().getCount(), Integer::sum);
    return n;
  }

  private static void take(ServerPlayer p, String id, int amount) {
    for (int i = 0; i < p.getInventory().getContainerSize() && amount > 0; i++) {
      ItemStack s = p.getInventory().getItem(i);
      if (id.equals(LoadoutMod.ammoId(s))) {
        int n = Math.min(amount, s.getCount());
        s.shrink(n);
        amount -= n;
      }
    }
    if (amount != 0) throw new IllegalStateException("Inventory changed during server transaction");
  }

  private static void give(ServerPlayer p, String id, int amount) {
    while (amount > 0) {
      int n = Math.min(16, amount);
      ItemStack s = LoadoutMod.stack(id, n);
      p.getInventory().add(s);
      if (!s.isEmpty()) p.drop(s, false);
      amount -= n;
    }
  }

  public static boolean loadOne(ServerPlayer p, VehicleEntity v, ItemStack held) {
    if (!editable(p, v)) {
      message(p, "该载具暂时无法装填");
      return false;
    }
    String id = LoadoutMod.ammoId(held);
    AmmoType ammo = LoadoutApi.get(id);
    LoadoutSpec spec = LoadoutData.spec(v);
    if (ammo == null) {
      message(p, "弹种不存在");
      return false;
    }
    if (!spec.allowed().contains(id)) {
      message(p, "该载具不允许此弹种");
      return false;
    }
    LoadoutState s = state(v);
    int slot = LoadoutState.firstFree(s.entries, spec, LoadoutData.ammo(), id);
    if (slot < 0) {
      message(p, "挂点不足");
      return false;
    }
    s.entries.add(new Entry(UUID.randomUUID(), id, slot));
    s.revision++;
    if (!infinite(p)) held.shrink(1);
    save(v, s);
    sync(v);
    return true;
  }

  /** 主线程内先验证整次会话，再按槽位回滚缺货项，最后一次性结算物品差额。 */
  public static boolean commit(
      ServerPlayer p, VehicleEntity v, long expected, List<Entry> original, List<Entry> draft) {
    if (!editable(p, v)) {
      message(p, "目标已离开、被占用或被摧毁，整次提交取消");
      return false;
    }
    LoadoutState s = state(v);
    if (s.revision != expected || !s.entries.equals(original)) {
      message(p, "挂载已被修改或开火，整次提交取消，请重新打开");
      return false;
    }
    LoadoutSpec spec = LoadoutData.spec(v);
    try {
      LoadoutState.validate(draft, spec, LoadoutData.ammo());
    } catch (Exception e) {
      message(p, "挂载配置已变化，整次提交取消");
      return false;
    }
    Map<UUID, Entry> old = new HashMap<>();
    original.forEach(e -> old.put(e.token(), e));
    for (Entry e : draft)
      if (old.containsKey(e.token()) && !old.get(e.token()).equals(e)) {
        message(p, "无效挂载身份，整次提交取消");
        return false;
      }
    Set<UUID> kept = new HashSet<>();
    draft.forEach(e -> kept.add(e.token()));
    Map<String, Integer> refund = new HashMap<>();
    for (Entry e : original) if (!kept.contains(e.token())) refund.merge(e.ammo(), 1, Integer::sum);
    Map<String, Integer> available = counts(p);
    refund.forEach((id, n) -> available.merge(id, n, Integer::sum));
    Map<String, Integer> cost = new HashMap<>();
    List<Entry> accepted = new ArrayList<>();
    for (Entry e : draft) {
      if (old.containsKey(e.token())) {
        accepted.add(e);
        continue;
      }
      int n = available.getOrDefault(e.ammo(), 0);
      if (infinite(p) || n > 0) {
        accepted.add(e);
        available.put(e.ammo(), n - 1);
        cost.merge(e.ammo(), 1, Integer::sum);
      } else message(p, "弹药不足，" + (e.slot() + 1) + " 号挂点未装载");
    }
    if (!infinite(p)) {
      Set<String> ids = new HashSet<>(cost.keySet());
      ids.addAll(refund.keySet());
      for (String id : ids) {
        int net = cost.getOrDefault(id, 0) - refund.getOrDefault(id, 0);
        if (net > 0) take(p, id, net);
        else if (net < 0) give(p, id, -net);
      }
    }
    s.entries.clear();
    s.entries.addAll(accepted);
    s.revision++;
    save(v, s);
    p.getInventory().setChanged();
    p.inventoryMenu.broadcastChanges();
    sync(v);
    return true;
  }

  public static JsonObject snapshot(VehicleEntity v) {
    JsonObject j = new JsonObject();
    j.addProperty("entity", v.getId());
    j.addProperty("uuid", v.getUUID().toString());
    LoadoutSpec spec = LoadoutData.spec(v);
    if (spec != null) {
      j.add("spec", spec.json());
      LoadoutState s = state(v);
      j.add("entries", LoadoutState.json(s.entries));
      j.addProperty("revision", s.revision);
    }
    return j;
  }

  public static void sync(VehicleEntity v) {
    LoadoutNetwork.tracking(v, snapshot(v));
  }

  public static boolean pilot(ServerPlayer p, VehicleEntity v) {
    return !p.isSpectator()
        && p.getVehicle() == v
        && v.getSeatIndex(p) == 0
        && v.isAlive()
        && !v.isWreck();
  }

  /** 复用 SBW 弹丸实体。Guidance 是弹药语义；实体/位置目标通过原生导引接口传递。 */
  public static Vec3 launchOrigin(VehicleEntity v) {
    return v.position().add(0, v.getBbHeight() * .45, 0)
        .add(v.getViewVector(1).normalize().scale(v.getBbWidth() * .5 + 1));
  }

  public static void fire(
      ServerPlayer p, VehicleEntity v, UUID requestedTarget, Vec3 requestedPos) {
    if (!pilot(p, v) || !supported(v)) return;
    LoadoutState s = state(v);
    long now = v.level().getGameTime();
    if (now < s.nextFire) return;
    if (s.entries.isEmpty()) {
      if (now - EMPTY_MESSAGES.getOrDefault(p, now - 20) >= 20) {
        message(p, "挂载已空");
        EMPTY_MESSAGES.put(p, now);
      }
      return;
    }
    Entry first = s.entries.getFirst();
    AmmoType a = LoadoutApi.get(first.ammo());
    if (a == null) {
      message(p, "弹药定义已移除，请恢复数据包");
      return;
    }
    Vec3 dir = v.getViewVector(1).normalize();
    Vec3 pos = launchOrigin(v);
    boolean bomb = BombRelease.isBomb(a);
    SeekerMode seekerMode = SeekerMode.of(a.guidance());
    Vec3 targetPos = null;
    UUID target = null;
    ServerLevel level = (ServerLevel) v.level();
    Entity candidate = requestedTarget == null ? null : level.getEntity(requestedTarget);
    if (!bomb && seekerMode == SeekerMode.OTHER
        && candidate != null
        && candidate != v
        && candidate.isAlive()
        && validTarget(p, pos, dir, candidate.getBoundingBox().getCenter(), a)) {
      target = candidate.getUUID();
      targetPos = candidate.getBoundingBox().getCenter();
    } else if (!bomb && seekerMode == SeekerMode.OTHER
        && requestedPos != null
        && validTarget(p, pos, dir, requestedPos, a)) targetPos = requestedPos;
    // 红外弹种没有原生武器槽也能按 JSON 的距离/角度搜索可见实体。
    if (!bomb && a.guidance().equalsIgnoreCase("IR") && target == null) {
      List<Entity> candidates =
          new ArrayList<>(
              level.getEntities(
                  v,
                  v.getBoundingBox().inflate(a.seekRange()),
                  e ->
                      (e instanceof LivingEntity || e instanceof VehicleEntity)
                          && e.isAlive()
                          && e.getRootVehicle() != v
                          && e != p));
      candidates.sort(
          Comparator.comparingDouble(
              e -> -dir.dot(e.getBoundingBox().getCenter().subtract(pos).normalize())));
      for (Entity e : candidates)
        if (validTarget(p, pos, dir, e.getBoundingBox().getCenter(), a)) {
          target = e.getUUID();
          targetPos = e.getBoundingBox().getCenter();
          break;
        }
    }

    if (!bomb && targetPos == null
        && seekerMode == SeekerMode.OTHER
        && !a.guidance().equalsIgnoreCase("IR")) {
      HitResult ray =
          level.clip(
              new ClipContext(
                  p.getEyePosition(),
                  p.getEyePosition().add(p.getLookAngle().scale(a.seekRange())),
                  ClipContext.Block.COLLIDER,
                  ClipContext.Fluid.NONE,
                  p));
      if (ray.getType() != HitResult.Type.MISS && validTarget(p, pos, dir, ray.getLocation(), a))
        targetPos = ray.getLocation();
    }
    if (a.guidance().equalsIgnoreCase("IR") && target == null) targetPos = null;
    Entity infraredTarget = null;
    var radarProvider = RadarLink.provider();
    if (a.category().equalsIgnoreCase("AirToAirMissile") && a.guidance().equalsIgnoreCase("IR")
        && radarProvider != null && radarProvider.hasInfraredControl()) {
      infraredTarget = radarProvider.infraredTarget(v);
      if (!RadarLocks.valid(p,v,a,infraredTarget)) {
        message(p,"红外导引头未取得有效目标，未消耗弹药");
        return;
      }
      target=infraredTarget.getUUID();targetPos=infraredTarget.getBoundingBox().getCenter();
    }
    if (a.guidance().equalsIgnoreCase("None")) {
      target = null;
      targetPos = null;
    }
    Entity radarTarget = null;
    if (seekerMode == SeekerMode.RADAR) {
      radarTarget = RadarLocks.current(p, v, a);
      if (radarTarget == null && !bomb) {
        message(p, "雷达未锁定：使用 TRK / TWS 有效航迹，或独立安装时按 K 锁定，未消耗弹药");
        return;
      }
      if (radarTarget != null && !RadarGuidance.canLaunch(v, a)) {
        message(p, "雷达照射通道已满，未消耗弹药");
        return;
      }
      if (radarTarget != null) {
        target = radarTarget.getUUID();
        targetPos = radarTarget.getBoundingBox().getCenter();
      }
    }
    LaserDesignations.Spot laser = null;
    TelevisionLocks.Lock television = null;
    if (seekerMode == SeekerMode.LASER) {
      laser = LaserDesignations.select(p, a.laserCode(), pos, dir, a.seekRange(), a.seekAngle());
      if (laser == null && !bomb) {
        message(p, "没有匹配编码的可见激光照射点，未消耗弹药");
        return;
      }
    }
    if (BombRelease.acceptsPointLock(a)) {
      if ((requestedTarget != null || requestedPos != null)
          && !TelevisionLocks.acquire(p, v, a, requestedTarget, requestedPos)) {
        message(p, "电视目标未通过锁定校验，未消耗弹药");
        return;
      }
      television = TelevisionLocks.current(p, v, a);
      if (television == null && !bomb) {
        message(p, "电视导引头未锁定：按 K 锁定，未消耗弹药");
        return;
      }
    }
    Vec3 cruisePoint = television == null ? targetPos : television.point();
    if (a.cruise().enabled() && !AimSupport.finite(cruisePoint)) {
      message(p, "低空巡航弹需要有效目标，未消耗弹药");
      return;
    }
    boolean guidedBomb = bomb && (laser != null || television != null || radarTarget != null);
    double releaseSpread = BombRelease.spread(a, guidedBomb);
    // 没有锁定时必须使用真正的炸弹物理，不能让复用 AGM65 的航弹继续推进或自动指向射线落点。
    int shots = 0;
    for (int burst = 0; burst < a.burst(); burst++) {
      Entry entry = s.entries.isEmpty() ? null : s.entries.getFirst();
      if (entry == null || !entry.ammo().equals(a.id())) break;
      if (radarTarget != null && !RadarGuidance.canLaunch(v,a)) break;
      Entity entity =
          BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(a.projectile())).create(level);
      if (bomb && !guidedBomb && entity instanceof MissileProjectile)
        entity = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("superbwarfare:mk_82")).create(level);
      if (!(entity instanceof Projectile projectile)
          || !(entity instanceof IBulletProperties properties)) {
        message(p, "弹丸未实现 SBW 弹道属性，未消耗弹药");
        break;
      }
      if (((!bomb && seekerMode != SeekerMode.OTHER) || guidedBomb)
          && (!(projectile instanceof DestroyableProjectile)
              || !projectile
                  .getType()
                  .is(
                      com.atsuishio.superbwarfare.init.ModTags.EntityTypes
                          .DESTROYABLE_PROJECTILE))) {
        message(p, "该弹丸未接入可拦截导引头接口，未消耗弹药");
        break;
      }
      if (a.renderProjectileModel()
          && projectile instanceof cn.zzy.loadout.api.AmmoModelEntity model)
        model.loadoutAmmoModel(a.id());
      projectile.setOwner(p);
      projectile.setPos(pos);
      properties.setDamage((float) a.damage());
      properties.setExplosionDamage((float) a.explosionDamage());
      properties.setExplosionRadius((float) a.explosionRadius());
      properties.setVelocity((float) a.velocity());
      if (entity instanceof ITrackableProjectile guided) {
        guided.setGuideType(target != null ? 0 : 1);
        guided.setTargetUUID(target == null ? "none" : target.toString());
        guided.setTargetPos(targetPos);
      }
      projectile.shoot(dir.x, dir.y, dir.z, (float) a.velocity(), (float) releaseSpread);
      projectile.setDeltaMovement(projectile.getDeltaMovement().add(v.getDeltaMovement()));
      if (a.cruise().enabled())
        GuidanceApi.cruise(projectile,a,v.getUUID(),
            television == null ? target : television.target(),cruisePoint);
      else if (laser != null)
        GuidanceApi.laser(
            projectile,
            laser.source(),
            a.laserCode(),
            laser.point(),
            a.seekRange(),
            a.seekAngle(),
            a.turnRate());
      else if (television != null)
        GuidanceApi.television(
            projectile,
            television.target(),
            television.point(),
            a.seekRange(),
            a.seekAngle(),
            a.turnRate());
      if (radarTarget != null)
        RadarGuidance.arm(projectile,p,v,radarTarget,a);
      if (level.addFreshEntity(projectile)) {
        if (infraredTarget != null)
          radarProvider.infraredLaunched(projectile,p,v,infraredTarget,a);
        if (!infinite(p)) s.entries.removeFirst();
        shots++;
      } else projectile.discard();
    }
    if (shots > 0) {
      s.nextFire = now + a.cooldown();
      s.revision++;
      save(v, s);
      sync(v);
      level.playSound(
          null,
          v.blockPosition(),
          SoundEvents.FIREWORK_ROCKET_LAUNCH,
          SoundSource.PLAYERS,
          1,
          0.6f);
    }
  }

  private static boolean validTarget(
      ServerPlayer p, Vec3 origin, Vec3 direction, Vec3 target, AmmoType a) {
    if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z))
      return false;
    Vec3 d = target.subtract(origin);
    if (d.lengthSqr() < 1
        || d.lengthSqr() > a.seekRange() * a.seekRange()
        || direction.dot(d.normalize()) < Math.cos(Math.toRadians(a.seekAngle()))) return false;
    HitResult hit =
        p.level()
            .clip(
                new ClipContext(
                    origin, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
    return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(target) < 4;
  }
}
