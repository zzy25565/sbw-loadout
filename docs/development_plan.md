# 自定义挂载模组（sbw_loadout）开发说明

> 版本：v1 开发说明（2026-10-20）
> 平台：NeoForge 21.1.249 / MC 1.21.1 / 卓越前线 0.8.9.1 与 0.8.9.2
> 本文件同时是**四件已获取模型的接入规格书**，开发时以本文档为准

---

## 一、目标与范围

### 目标
给固定翼飞机与直升机提供"挂载槽位"，玩家按机型挑选不同弹药；弹种由数据驱动，第三方附属可零代码扩展。

### 已定稿的需求口径
1. **每架飞机单独挑挂载**：载具 JSON 声明挂载清单（槽位数、弹种白名单、占用数）；
2. **弹药物料 = JSON + 模型**：新增弹种只需一份弹药 JSON + 一个模型；
3. **手动换弹**：手持弹药物品对载具右键换弹；**取消自动换弹**；
4. **无限弹药例外**：创造模式 / 无限弹药状态下保留自动补弹；
5. **接口优先**：弹种可能很多，注册表驱动，核心开火/渲染代码不随弹种增加而改动；
6. **兼容第三方载具**：探测与挂载能力都走 `VehicleEntity` 继承链。

### 一期不做
- 挂点的实时物理外挂渲染（弹药模型贴到机翼下的动画挂架）——二期；
- 弹药补给链、地勤加油流程。

---

## 二、已获取模型清单（本期四件）

存放目录：`sbw_loadout_models/`（已解压，含 model/ 与 textures/）

| 弹药 id | 型号 | 文件 | 面数 | 贴图 | 类型 | 制导 | 建议挂点占用 |
|---|---|---|---|---|---|---|---|
| `sbw_loadout:kh38mt` | **Kh-38MT** 空地导弹 | `Kh-85MT.obj` + `.mtl` | 13,754 | 4 张 jpeg（c / n_n / n_s / n_ao） | 导弹 | 红外成像 / 主动雷达 | 1 |
| `sbw_loadout:kh29td` | **Kh-29TD** 空地导弹 | `su_kh_29td.obj` + `.mtl` | 14,569 | 4 张 jpeg（c / n_s / n_n / n_ao） | 导弹 | 电视制导 | 1 |
| `sbw_loadout:kab500kr` | **KAB-500Kr** 制导炸弹 | `su_kab_500kr.obj` + `.mtl` | 9,392 | 4 张 jpeg（c / n_s / n_n / n_ao） | 制导炸弹 | 电视制导 | 2 |
| `sbw_loadout:x555` | **X-555** 空射巡航导弹 | `x-555.fbx`（**FBX，需先导入 Blender**） | 待测（导入后统计） | 5 张 4K PNG（color / normal / ao / metallic / roughness） | 巡航导弹 | 惯导 + 卫星 + 末端成像 | 3（战略级，仅轰炸机/重型挂架） |

### 命名与目录规范
```
assets/sbw_loadout/
├── models/bedrock/ammo/kh38mt.geo.json
├── models/bedrock/ammo/kh29td.geo.json
├── models/bedrock/ammo/kab500kr.geo.json
├── models/bedrock/ammo/x555.geo.json
├── textures/bedrock/ammo/kh38mt.png
├── textures/bedrock/ammo/kh29td.png
├── textures/bedrock/ammo/kab500kr.png
├── textures/bedrock/ammo/x555.png
└── textures/overlay/weapon/icons/*.png     ← HUD 弹药图标

data/sbw_loadout/ammo/kh38mt.json
data/sbw_loadout/ammo/kh29td.json
data/sbw_loadout/ammo/kab500kr.json
data/sbw_loadout/ammo/x555.json
```

### 已知坑
- Kh-38MT 包内模型名叫 `Kh-85MT.obj`（作者沿用旧工程名），**id 统一用 `kh38mt`**；
- X-555 只有 FBX，无 OBJ，需 Blender 导入后再导出（脚本见第五节）；
- KAB-500Kr 包里附带的 `su_kh_29_missile_grid_c.*` 是 Kh-29 的网格贴图，跟这件炸弹无关，可忽略；
- 四件都是战雷扒模，贴图带 JPEG 压缩痕迹，转 atlas 时建议重采样到 512×512 以内。

---

## 三、弹药数据规范（AmmoType）

### JSON 结构（示例：kh38mt.json）
```json
{
  "DisplayName": "Kh-38MT 空地导弹",
  "Category": "Missile",
  "Icon": "sbw_loadout:textures/overlay/weapon/icons/kh38mt.png",
  "Model": "sbw_loadout:models/bedrock/ammo/kh38mt.geo.json",
  "Texture": "sbw_loadout:textures/bedrock/ammo/kh38mt.png",
  "Slots": 1,
  "Projectile": "superbwarfare:agm_65",
  "Damage": 620,
  "ExplosionDamage": 320,
  "ExplosionRadius": 8,
  "Velocity": 3.2,
  "Burst": 1,
  "Spread": 0.3,
  "Guidance": "IR",
  "SeekRange": 1024,
  "SeekAngle": 20,
  "Cooldown": 40
}
```

### 四件弹药的基准数值建议

| 字段 | kh38mt | kh29td | kab500kr | x555 |
|---|---|---|---|---|
| Category | Missile | Missile | GuidedBomb | CruiseMissile |
| Slots | 1 | 1 | 2 | 3 |
| Damage | 620 | 560 | 480 | 900 |
| ExplosionDamage | 320 | 300 | 420 | 700 |
| ExplosionRadius | 8 | 7 | 11 | 16 |
| Velocity | 3.2 | 2.6 | 1.4（投放后滑翔） | 2.0 |
| Guidance | IR | TV | TV | INS+GNSS+EO |
| SeekRange | 1024 | 768 | 512 | 2048 |
| 适用机型 | 歼-16 / 苏-24 / 苏-30 | 歼-10 / 歼-16 / 米格 | 歼-16 / 苏-34 类 | 仅重型挂架 |

> 数值以主模组现有 `superbwarfare:agm_65` / `large_anti_ground_missile` 为参照，先复用原生弹丸实体，避免自造弹丸带来的反序列化风险；后期需要独立弹道时再换成自定义 Projectile。

### 弹药物品（GUI 挂载的前提）
每个弹种除了数据定义，还要注册一个**物品形态**才能在物品栏里出现、拖进配置界面：
`sbw_loadout:ammo_kh38mt` / `ammo_kh29td` / `ammo_kab500kr` / `ammo_x555`。
物品的逻辑与普通物品一致（可堆叠 16、创造栏可见），只是多带一个"弹药 id"引用，右键载具时由该引用决定装填何种弹药。

---

## 四、载具挂载声明

载具 JSON 增加 `Loadout` 段（第三方载具加字段即接入，无需注册）：

```json
"Loadout": {
  "Slots": 6,
  "Allowed": [
    "sbw_loadout:kh38mt",
    "sbw_loadout:kh29td",
    "sbw_loadout:kab500kr"
  ],
  "Default": ["sbw_loadout:kh38mt", "sbw_loadout:kh38mt"]
}
```

- `Slots`：总挂点数（按机型配置，如歼-16=6、苏-24=8、直升机=4）；
- `Allowed`：白名单，未列出的弹种不能挂；
- `Default`：出厂挂载（可空），用于快速起飞；
- 校验顺序：弹种存在 → 在 Allow 内 → 剩余槽位 ≥ Slots → 装入。

**VLS 附属机型的默认挂载建议**：j16(6)、j11(6)、j10c(4)、su24m(6)、q5(4)、z10a/z10me(4)、mi24(4)、uh60(2)。

---

## 五、资源管线（四件模型转 bedrock geo）

### 步骤
```bash
# 1) 减面（OBJ 直接进；X-555 需先 import FBX 再导出 OBJ）
blender --background --factory-startup --python decimate.py -- <in.obj> <out.obj> <ratio>

# 2) 转 bedrock geo + atlas
python obj_to_geo.py -- <out.obj> <out_dir>
```

### 各件建议 ratio（目标 ≤ 1600 面）

| 模型 | 原始面数 | ratio | 预计面数 |
|---|---|---|---|
| kh38mt | 13,754 | 0.11 | ~1,500 |
| kh29td | 14,569 | 0.10 | ~1,450 |
| kab500kr | 9,392 | 0.16 | ~1,500 |
| x555 | 导入后统计 | 按同标准 | ~1,500 |

### 缩放
`obj_to_geo.py` 的 `SCALE` 默认 0.1（为 VLS 炮塔设的）。弹药按真实尺寸接入：**1 米 ≈ 1 格**，改 `SCALE = 1.0`：
- Kh-38MT 4.2m → ~4.2 格；Kh-29TD 3.9m → ~3.9 格；
- KAB-500Kr 3.05m → ~3 格；X-555 6m → ~6 格（挂重型轰炸机才协调）。

---

## 六、交互与开火规则

### 换弹（两条路径并存）
**路径 A：手持弹药右键（快速单发装填）**
1. 玩家手持弹药物品（`sbw_loadout:ammo_kh38mt` 等），对载具右键；
2. 服务端校验：是飞机/直升机 → 有 `Loadout` → 弹种在 Allow → 槽位够；
3. 成功：写入挂载状态并消耗物品；失败：提示原因（不扣物品）；
4. **装载顺序**按点击顺序占槽，HUD 显示"已用 3/6"。

**路径 B：shift + 右键打开挂载配置界面（批量，见第七节）**
- 冲突规则：**手持弹药 + 不按 shift** → 走路径 A；**按 shift + 右键** → 无论手持什么，一律打开配置界面（避免误开界面打断装填）。

### 自动换弹（仅无限弹药）
- 判定：`player.isCreative()` 或服务端配置的无限弹药权限；
- 行为：开火后自动补满已装载弹种，**不需要手持物品**；
- 生存模式玩家永远只能手动换。

### 开火
- 按当前挂载顺序发射（先装的先打）；
- 每发消耗该挂载 1 枚；该挂载耗尽后自动切下一个，全部耗尽则禁止开火并提示"挂载已空"；
- 弹种参数（伤害/爆炸/初速/散布/连发）由 AmmoType 提供，接管点优先走主模组现有 `VehicleWeapon` 发射入口。

---

## 七、挂载配置界面（战雷式，shift + 右键）

### 交互入口
玩家**潜行（shift）+ 右键**飞机/直升机 → 打开 `LoadoutScreen`（战雷 Loadout 界面风格）。

### 界面布局
```
┌───────────────────────────────────────────────┐
│  歼-16  挂载配置            已用 3/6    [×]   │
├───────────────────────────────────────────────┤
│  可挂弹药（来自背包，自动筛选）                 │
│  ┌──────┐ ┌──────┐ ┌──────┐                  │
│  │KH38MT│ │KH29TD│ │KAB500│  ← 拖拽源（列表）  │
│  │ ×3   │ │ ×2   │ │ ×1   │                  │
│  └──────┘ └──────┘ └──────┘                  │
├───────────────────────────────────────────────┤
│  挂点槽位                                       │
│  [ 1 ] [ 2 ] [ 3 ] [ 4 ] [ 5 ] [ 6 ]          │
│  KH38  KH38  KAB  空    空    空               │
└───────────────────────────────────────────────┘
```

### 操作规则
1. **拖拽装载**：把左侧弹药列表里的弹药拖到某个空槽位 → 校验 Allow 白名单 / 槽位数（背包数量在**关闭界面提交时**才校验并扣除）；
2. **占用多槽的弹药**（如 KAB-500Kr 占 2 格、X-555 占 3 格）：拖入时自动向后占用连续的 N 个空槽，不足则拒绝并提示"挂点不足"；
3. **卸下**：点击已挂载槽位 → 弹药物品退回背包（背包满则掉落到脚下）；
4. **批量操作**："一键填满"按钮 = 按 Allow 顺序自动装满剩余槽位（仍是逐件校验与扣物品）；
5. **服务端权威**：界面只发"意图"（装/卸某个槽位），一切扣物品与状态变更在服务端完成，客户端仅显示；
6. **空载具/非飞机**：右键时若无 `Loadout` 声明 → 提示"该载具不支持自定义挂载"。

### 提交时机与一致性（已定稿）
**关闭界面时统一提交；再次打开不重置，必须手动卸下。**

| 场景 | 行为 |
|---|---|
| 打开界面 | 读取当前 `LoadoutState` 原样显示：已挂的槽位显示弹种且**保持占用**，不会自动清空/重置 |
| 界面内操作 | 只在客户端与服务端内存里暂存"意图"（装/卸哪一格），**不移动、不扣除任何物品** |
| 点击已挂槽位 | 执行卸载：该弹药从挂载中移除，槽位变空（仍是暂存，关界面才结算） |
| 关闭界面（正常提交） | 服务端统一结算：① 按意图校验背包实际数量；② 扣除新装入的弹药、退还卸下的弹药（背包满则掉落脚下）；③ 写入 `LoadoutState` 并同步；④ 某项校验失败则该槽位回滚并提示"弹药不足，X 号挂点未装载" |
| 按 Esc / 直接关界面视为取消 | 丢弃全部暂存意图，挂载状态与背包**保持打开前的样子**，零变化 |
| 断线 / 客户端崩溃 | 同取消：不提交、不扣物品，**不会出现物品丢失或半装状态** |
| 界面开着时飞机被开走 / 被摧毁 | 关闭提交时目标校验失败 → 整次提交回滚并提示 |

**为什么不在界面里预扣物品**：预扣需要把弹药搬进临时容器，玩家中途断线就可能出现"物品在虚空中"的状态；改成提交时统一校验扣除，物品始终在背包里，断线天然安全，也避免了靠反复开关界面刷弹药的漏洞。

**为什么再次打开要保留而非重置**：飞机挂载是持久状态（存 NBT），重置会让玩家每次开界面都被清空、白扣弹药。想改配置就手动卸下——这也是战雷的行为。

### 技术实现要点
| 组件 | 说明 |
|---|---|
| 触发 | 拦截载具 `interact`（shift 分支），服务端 `player.openMenu(provider)` |
| Menu | `LoadoutMenu extends AbstractContainerMenu`：背包槽 + 7 个挂载槽（自定义 `Slot`，仅接受 `sbw_loadout:ammo_*`） |
| Screen | `LoadoutScreen extends AbstractContainerScreen`：左侧弹药列表（按背包内容动态生成）+ 中间挂点矩阵 + 用 `GuiGraphics` 画槽位框与占用跨度 |
| 同步 | `ContainerData` 同步槽位数量 / 已用数；挂载状态本体走 `LoadoutState` 的 NBT + 更新包 |
| 物品前提 | 四件弹药需注册为**物品**（`sbw_loadout:ammo_kh38mt` 等），才能在物品栏里出现、被拖拽与扣减 |

### 与原规则的关系
- 小批量/战场上补一发 → 手持右键（路径 A，快）；
- 起飞前整机配弹 → shift+右键（路径 B，直观）；
- 两者操作的是同一份 `LoadoutState`，不产生两套状态。

---

## 八、客户端表现
- HUD（右下角）：`KH38MT ×2  KAB500 ×1  已用 3/6`；
- 弹药图标来自 `textures/overlay/weapon/icons/`；
- 二期：机翼下挂架模型渲染（按槽位绑定 geo，mortar 动画可复用）。

---

## 九、对外接口（第三方接入）

```java
LoadoutApi.register(AmmoType.builder("mymod:my_missile")
        .displayName("My Missile")
        .projectile("superbwarfare:agm_65")
        .damage(600).velocity(3.0).slots(1)
        .model("mymod:models/bedrock/ammo/my_missile.geo.json")
        .texture("mymod:textures/bedrock/ammo/my_missile.png")
        .build());
```

载具侧零代码：JSON 加 `"Loadout"` 即接入。旧载具未声明 = 不可挂载（行为明确、可预期）。

---

## 十、开发阶段与工期

| 阶段 | 内容 | 工期 |
|---|---|---|
| B1 弹药系统分析 | 梳理 VehicleWeapon → Projectile 发射链；核对 0.8.9.1/0.8.9.2 差异 | 1 天 |
| B2 弹药接口 | AmmoType 注册表 + 四件弹药 JSON + 图标 | 1-1.5 天 |
| B3 挂载状态 | LoadoutSpec 解析 / LoadoutState NBT / 同步包 | 1.5-2 天 |
| B4 换弹交互 | 手持物品换弹 + 无限弹药自动补弹 | 1-1.5 天 |
| B5 开火接管 | 弹种发射 / 余弹 / 空挂载禁射 | 1.5-2 天 |
| B6 客户端 HUD | 挂载与余弹显示 | 1 天 |
| B6.5 挂载配置界面 | shift+右键打开 Menu+Screen、拖拽装载/卸下/一键填满、服务端校验 | 2-3 天 |
| B7 测试 | 机型 × 弹种矩阵、存档、多人、与雷达联动 | 1-1.5 天 |
| **模型管线** | 四件减面 → geo → atlas（可与 B2 并行） | 0.5-1 天 |

**合计约 9-12 个工作日**（含挂载配置界面；B1 若需 Mixin 接管开火链再 +1-2 天）。

---

## 十一、验收清单

1. 四件弹药都能在创造栏找到，图标与模型正确；
2. 歼-16（6 挂点）可做 2×Kh-38MT + 2×KAB-500Kr 组合，超出槽位被拒绝；
3. 手持弹药右键换弹生效，生存模式不会自动补弹；
4. 创造模式开火后自动补满；
5. 挂载耗尽后禁止开火并提示；
6. 服务器重启后挂载状态恢复；
7. 第三方附属仅加 `LoadoutApi.register` + JSON `Loadout` 即可挂载，不改本模组代码；
8. shift+右键能打开配置界面，拖拽弹药到槽位完成挂载，物品从背包正确扣除；
9. 占用 2-3 格的弹药（KAB-500Kr / X-555）在槽位不足时被拒绝并给出提示；卸下能退回背包，界面状态与服务端一致；
10. 关闭界面才结算：Esc 取消无任何变化；重开界面原有挂载保留不重置，需手动卸下；断线/崩溃不丢物品、不留半装状态。
