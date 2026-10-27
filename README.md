# SBW 自定义挂载 1.3.3

适用 MC 1.21.1、NeoForge 21.1.240+、SBW 0.8.9.1 / 0.8.9.2。客户端与服务端都安装相同 JAR，沿用 SBW 所需的 Kotlin For Forge 等依赖。更新日期：2026-10-27。

**下载：[1.3.3 正式版](https://github.com/zzy25565/sbw-loadout/releases/tag/v1.3.3)** · [最新发布](https://github.com/zzy25565/sbw-loadout/releases/latest) · [验收记录](docs/验收_1.3.3.md)

本版包含 29 种弹药、11 种航弹，支持机腹光电吊舱、原生热成像、TV/激光/坐标制导、雷达弹，以及 Kh-59MK / X-555 独立低空巡航。航弹可未锁定投放并带随机散布；有效锁定后取消本次投放散布。

1.3.3 新增：全部 11 种航弹在未锁定投放时加入随机散布，TV、激光、坐标制导航弹取得服务端有效目标后取消本次投放散布。未锁定的制导航弹可按普通炸弹投放；坐标航弹新增 K / 吊舱固定点锁定。HUD 区分普通投放与制导投放。散布参数及规则见 [航弹投放散布](docs/航弹投放散布.md)。导弹发射规则、1.3.2 伤害、模型和热成像保留，协议仍为 5。

1.3.2 调整：全部 29 种弹药按游戏 TNT 等效值缩放直击、爆炸伤害与爆炸半径。空地/巡航导弹使用原生 AGM-65 基准，其余弹药使用原生 MK82 基准；MK82 本身独立直击为 0，本附属采用其爆炸伤害 650 作为直击换算基数。伤害按当量比，半径按当量比的立方根。参考值和暂定估值逐项标注，详见 [弹药伤害与当量](docs/弹药伤害与当量.md)。

1.3.1 新增：吊舱锁定界面接入 SBW 原生热成像。点击左上角 EO / IR 或使用原生热成像键切换；默认 K 与锁定冲突，因此吊舱内用 **Shift+K** 切换、K 仍锁定。改键后界面会显示实际快捷键。退出恢复此前的原生热成像状态。详见 [吊舱热成像](docs/吊舱热成像.md)。通信协议仍为 5，低空巡航与模型资源沿用 1.3.0。

1.3.0 新增：Kh-59MK、X-555 使用独立低空巡航弹体，具备限速、地形前视、持续低空巡航、末段拦截和完整飞行状态存档。其他弹药保留原弹道。参数、测试和边界见 [低空巡航弹道](docs/低空巡航弹道.md)。通信协议升级为 5，客户端与服务端须同时更新。

1.2.1 新增：21 个审查包弹药网格与数据弹种，包含物品/飞行模型及图标，总可用弹种 29 个。新模型目前为灰色材质；源图片和材质映射缺失。获取方式、完整清单、游戏制导简化及审查见 [WT 模型接入与审查](docs/WT模型接入与审查.md)。9 台载具另行交付。

1.2.0 新增：9M336 雷达导弹、独立弹体和雷达附属 1.3.0 联动。操作、参数及接入示例见 [雷达弹与雷达联动](docs/雷达弹与雷达联动.md)。雷达默认模式键改为 O、扫描宽度键为 P，保留本附属的 K / L 导引操作。

v1.1beta 新增：机载激光（吊舱内 L 开关）、Kh-29L 激光制导、KAB-1500Kr / Kh-59MK 电视制导及完整物品/飞行模型；Kh-58UShK 只打包隐藏资源。原始 Blender 模型无图片贴图，保留灰色材质与全部网格；见 [机载激光与新增弹药](docs/机载激光与新增弹药.md)。该段介绍旧版功能；当前版本为 `1.3.3`，通信协议为 5。

此前 1.1alepha 已实现：火炮指示器持续激光照射；激光断照后惯性导引及重获；TV 锁定后自主追踪；平滑限角转向；沿用 SBW 原生近防炮可拦截弹体。2026-10-22 补充独立吊舱视角（F6 进入、K 锁定、Enter 投放）、地面稳定、变倍，以及可调激光柔光；见 [吊舱视角与激光外观](docs/吊舱视角与激光外观.md)。最低 NeoForge 仍为 21.1.240。新增导引功能的操作和接入见 [激光与电视导引说明](docs/激光与电视导引.md)。

这是独立通用附属，不修改既有载具工程，不内置任何正式机型的适配或挂载白名单。**安装后，未声明 `Loadout` 的飞机不会自动获得挂载能力。** 载具作者在自身完整载具 JSON 中加入声明即可接入。

## 已实现

- 八件固定物品与 21 个通用物品承载的新弹种、创造栏、原分辨率贴图、完整三角网格物品模型和 HUD 图标。
- AmmoType JSON 与 Java API 注册；新增数据包弹种自动使用通用弹药物品，不要求为每个弹种新增 Java 物品类。
- 基于 `VehicleEntity` 继承链及 Airplane / Helicopter 类型检测；动态连续挂点、白名单、默认挂载。
- 主手弹药右键快速装填；潜行右键优先打开配置界面。
- 拖拽装填、点击卸载、一键填满、滚轮翻弹药/槽位页。
- “保存并关闭”统一结算；Esc、取消、断线或普通关闭丢弃草稿。背包满时卸下的物品落在玩家脚下。
- 服务端复核范围、目标状态、实际库存、唯一弹药身份、会话版本和数据重载代次。缺货项单独不装入；目标或版本冲突取消整次结算。
- NBT 持久化及追踪/驾驶同步；驾驶席 FIFO 发射、逐发消耗、服务端冷却、空挂载禁射。其他乘员席保留原生武器。
- 创造模式或服务器 UUID 许可名单内的玩家开火后保留已挂弹药；普通生存玩家仅手动补弹。
- 服务端距离、射界和视线校验；IR 保留原生导引与锥内搜索，Laser / TV 使用服务端导引头控制器，坐标航弹支持显式固定点锁定。
- 航弹未锁定时可普通投放并有随机散布；有效 TV、坐标锁定或匹配编码的可见激光照射取消释放瞬间的散布。
- 吊舱 EO / IR 切换，直接复用 SBW 原生热成像；退出恢复进入前的成像和相机状态。
- Kh-59MK / X-555 独立低空巡航，包含限速、地形前视、低空保持、末段拦截和状态存档。
- 29 种弹药按两组游戏当量基准调整直击、爆炸伤害和半径，参考值与暂定游戏估值分别标注。

## 使用

1. 把 `sbw-loadout-1.3.3.jar` 放入客户端及服务器 `mods/`，移出旧版挂载 JAR。
2. 在载具数据中声明 `Loadout`，并确保原有 `Type` 为 `Airplane` 或 `Helicopter`。
3. 在“SBW 自定义挂载”创造栏取弹，或用 `/give` 获取内置弹药（原八件固定物品及新增通用弹种）。
4. 主手持弹药右键装一枚；潜行右键批量配置。近距离、载具未损毁且没有其他乘员占用时可编辑。
5. 坐入驾驶席，使用 SBW 原有载具开火键。TV 弹可按 F6 进入吊舱，通过中键拖动瞄准、滚轮变倍，K 锁定后 Enter 投放，或返回驾驶视角后开火；也保留直接对准目标按 K 锁定；激光弹可按 F6、瞄准并按 K 锁定，再按 L 开启机载照射，Enter 发射；退出吊舱可继续驾驶并照射，也可由同队指示器或另一架飞机供光。自定义挂载接管该驾驶席开火；其他席位保持 SBW 原有行为。

航弹不要求先锁定才能投放。未锁定、失锁或无有效激光时，界面显示 **BOMB / 普通投放 · 有散布**；获得有效目标后显示 **GUIDED / 无投放散布**。坐标航弹可按 K 或通过吊舱锁定固定世界坐标。导弹继续遵守各自原有发射条件，不能按未锁定航弹的方式发射。

| 吊舱操作 | 默认按键 |
|---|---|
| 进入 / 返回驾驶视角 | F6，Esc 也可返回 |
| 转动镜头 | 中键拖动或方向键 |
| 变倍 | 滚轮或 +/-，50° / 25° / 12° / 6° |
| 锁定 / 解除 | K 或左键 |
| 稳定地面点 / 解除 | G 或右键 |
| 机载激光开关 | L |
| 投放当前队首弹药 | Enter |
| 回正 | R |
| 热成像切换 | Shift+K，或点击左上角 EO / IR |

热成像使用 SBW 原生按键映射。其默认 K 与本模组锁定键冲突，因此在吊舱内加 Shift；改到不冲突的按键后可直接使用新键，界面显示实际组合键。

服务器世界的 `serverconfig/sbw_loadout-server.toml` 新增 `bombReleaseSpread = 2.0`：未锁定航弹取该值与弹种 `Spread` 的较大值，有效锁定时为 0。单位沿用原生 Spread，不是角度或固定米数。具体规则见 [航弹投放散布](docs/航弹投放散布.md)。

八件内置物品：`sbw_loadout:ammo_kh38mt`、`ammo_kh29td`、`ammo_kab500kr`、`ammo_x555`、`ammo_kh29l`、`ammo_kab1500kr`、`ammo_kh59mk`、`ammo_9m336_radar`，每组 16 枚。Kh-58UShK 没有物品或弹药数据注册，仅保留转换后的模型资源。

无限弹药例外在世界的 `serverconfig/sbw_loadout-server.toml` 中配置 `infiniteAmmoPlayers = ["玩家 UUID"]`，默认为空。装填顺序就是发射顺序；多槽弹药一次占用连续 2 或 3 个槽位。创造/无限状态下装卸不做物品扣退，避免免费装填后退回实体物品。

## 通用载具接入

在载具原有的 `data/<载具命名空间>/sbw/vehicles/<载具id>.json` **完整文件内**增加以下字段，保留其余已有参数。不要拿只包含该段的文件覆盖正式载具数据，否则会覆盖整份上游配置。

```json
"Loadout": {
  "Slots": 6,
  "Allowed": [
    "sbw_loadout:kh38mt",
    "sbw_loadout:kh29td",
    "sbw_loadout:kab500kr",
    "sbw_loadout:x555",
    "sbw_loadout:kh29l",
    "sbw_loadout:kab1500kr",
    "sbw_loadout:kh59mk",
    "sbw_loadout:9m336_radar"
  ],
  "Default": []
}
```

也支持实体 `Override` JSON 中的 `Loadout`，优先于资源声明。槽位允许 1–64；Allowed 顺序用于一键填满。`Default` 是载具第一次建立挂载状态时的一次性出厂装载，重开 UI 或读取存档不会重置。修改 Default 不会重写已有存档。

本交付的生产资源中没有任何 `sbw/vehicles` 配置。`src/gametest` 内只有通用测试夹具，发布 JAR 不包含这些实体或测试数据。

## 新弹种：纯数据方式

新增 `data/mymod/ammo/my_missile.json`，内容结构参照 `src/main/resources/data/sbw_loadout/ammo/kh38mt.json`，并在载具 Allowed 中加入 `mymod:my_missile`。图标、Model、Texture 指向资源包内真实资源。重载数据后，该弹种出现在本附属创造栏；服务端仍为最终弹药定义来源。

纯数据弹种使用通用物品 + 自定义数据标记，例如：

```mcfunction
/give @s sbw_loadout:ammo[minecraft:custom_data={Ammo:"mymod:my_missile"}] 1
```

Java 侧获取物品：`LoadoutApi.stack("mymod:my_missile", 1)`。如第三方自行注册物品，可使用公开的 `AmmoItem("mymod:my_missile")`。

Java 注册在 common setup 的 `enqueueWork` 内执行：

```java
LoadoutApi.register(AmmoType.builder("mymod:my_missile")
    .displayName("My Missile")
    .projectile("superbwarfare:agm_65")
    .damage(600).velocity(3.0).slots(1)
    .model("mymod:models/bedrock/ammo/my_missile.geo.json")
    .texture("mymod:textures/bedrock/ammo/my_missile.png")
    .build());
```

同名数据包定义优先于 Java 注册，`/reload` 会重新读取。定义错误会使本次资源重载失败并保留旧快照，不会静默使用一半数据。修改弹药定义后，正在编辑的会话取消。已有挂载引用被移除的弹种时，恢复其数据定义后再卸载，避免擅自丢弃存档弹药。

## 模型质量与资源格式

新增四件全部保留源网格、逐角法线/UV 和米制比例，只作旋转、居中；三角面分别为 Kh-29L 14,337、KAB-1500Kr 33,967、Kh-59MK 23,740、Kh-58UShK 24,766。四个 `.blend` 没有图片贴图，原始线性灰材质以 sRGB 色块表示，没有虚构涂装或对源贴图降采样。

| 模型 | 源多边形 | 导出三角面 | 基色贴图 |
|---|---:|---:|---|
| Kh-38MT | 13,754 | 13,754 | 1024×1024 |
| Kh-29TD | 14,569 | 14,569 | 1024×1024 |
| KAB-500Kr | 9,392 | 9,392 | 1024×512；网格材质 256×256 |
| X-555 | 9,557 | 19,282 | 4096×4096 |

无减面、无体素化、无 512 降采样、无再次 JPEG 编码。X-555 的面数变化是原多边形三角化，未新增造型或简化细节。共享坐标去重不改变逐角几何/法线/UV，物理尺寸统一缩放到 4.2 / 3.9 / 3.05 / 6.0 格。Git 仓库保存完整运行资源；原始 OBJ/FBX/MTL/贴图和 Blender 工程由开发工作区本地保存，不随源码仓库重复上传。

`Model` 是 Bedrock 1.12 `poly_mesh`，16 模型单位为 1 格，+Z 是弹体长轴，原点在中心。自定义渲染器读取逐角位置、法线和 UV；每个骨骼可附 `loadout_material: {"texture": "...", "translucent": false}`。无该扩展时使用 AmmoType.Texture。此渲染器面向三角网格；不把 Bedrock cubes、动画骨骼或实体模型系统当成等价输入。

源基色转 PNG 后逐像素验证，无缩放。法线、AO、金属度/粗糙度/高光等原始辅助图均保留在资源中的 `textures/source/`；普通 Minecraft 渲染管线只使用基色、透明度和网格法线，不声称已实现 PBR 材质管线。透明材料和实际被 KAB 模型引用的网格图保留。

`model_review/four_models.png` 是原四件导出网格的 Blender 预览，`model_review/new_four_models.png` 为新增四件；`validation/screenshots/` 是实际游戏截图。体积较大来自保留高精模型和原始贴图。JAR/ZIP 容器采用无损压缩，不改变模型质量。

## 原生弹道边界

| 类型 | 当前行为与边界 |
|---|---|
| 未锁定航弹 | 带释放瞬间随机误差，继承飞机速度并受重力下落；不附加导引。原先复用导弹弹体的航弹在此状态下改用 MK82 物理，保留本弹种伤害和模型身份。 |
| 已锁定航弹 | 无额外投放散布；TV 跟踪目标，坐标制导保持固定点，激光制导沿用持续照射与断照惯性。 |
| 普通导弹 | 仍使用原生弹体与对应导引逻辑；TV / 激光 / 雷达弹必须满足原有有效目标条件。 |
| Kh-59MK / X-555 | 使用独立 `sbw_loadout:cruise_missile`，执行低空巡航与末段拦截，保留原弹药 ID。 |
| 9M336 雷达弹 | 使用独立雷达弹体；独立安装可 K 锁定，安装雷达附属后接受其服务端 TRK。 |

`Damage`、`ExplosionDamage`、`ExplosionRadius`、`Velocity` 初速、`Burst`、`Spread`、`Cooldown` 来自服务端弹药定义；每个成功发出的弹丸消耗一枚。1.3.3 只改变航弹的释放规则，保留 1.3.2 的全部伤害参数。

“无投放散布”不等于强制命中；飞机速度、目标机动、地形、转向能力和照射状态仍会影响结果。低空巡航不会加载新区块，不提供横向迷宫寻路。具体边界见 [低空巡航](docs/低空巡航弹道.md)、[航弹散布](docs/航弹投放散布.md) 与 [当量换算](docs/弹药伤害与当量.md)。这些是游戏参数和简化规则，不宣称还原现实武器性能。

尚未提供机翼下实体挂架、实时外挂动画、地勤或补给链。机型须由作者通过 `Loadout` 声明接入。

## 构建与验证

需要 JDK 21。Git 仓库不携带第三方依赖 JAR，先按 [libs/README.md](libs/README.md) 准备本地依赖，再执行构建；Gradle 会下载 NeoForge 和 Kotlin For Forge。

```bash
./gradlew clean build
./gradlew -Pgametest runGameTestServer
# 先构建相邻 sbw_radar 项目，再运行联合测试
./gradlew -Pgametest -PradarCompat runGameTestServer
./gradlew -Pgametest -PsbwVersion=0.8.9.2 runGameTestServer
./gradlew -Pgametest -Psmoke runClient
./gradlew -Pgametest -Psmoke -PsbwVersion=0.8.9.2 runClient
./gradlew -Pgametest -Pguidancesmoke -PsbwVersion=0.8.9.2 runClient
./gradlew -Pgametest -Pbombsmoke runClient
./gradlew -Pgametest -Pbombsmoke -PsbwVersion=0.8.9.2 runClient
./gradlew -Pgametest -Pthermalsmoke runClient
./gradlew -Pgametest -Pcruisesmoke runClient
```

Windows PowerShell 使用 `.\gradlew.bat`，版本参数建议写为 `'-PsbwVersion=0.8.9.2'`。每次只选择一种 `*smoke` 验收源集；它们会创建独立测试世界并自动退出客户端。`radarCompat` 需要相邻项目已构建对应 JAR。

1.3.3 在两个 SBW 版本上各通过 **60 项 GameTest、10 项真实客户端检查**。发布前再执行不带测试参数的 `clean build`，防止开发夹具残留。可公开的验收摘要、校验和与截图见 [验收记录](docs/验收_1.3.3.md)；完整原始日志随 GitHub Release 的验收附件提供，开发工作区的 `validation/` 不提交到 Git。

以下为维护者重建原始四件模型的流程，需要本地原始模型文件和 Blender；会重写这些弹种的数据与语言文件，普通构建、更新航弹散布或伤害时不应执行：

```bash
blender --background --factory-startup --python tools/convert_models.py -- source_models .
python3 tools/prepare_assets.py
blender --background --factory-startup --python tools/render_models.py -- .
python3 tools/finalize_icons.py
python3 tools/validate_assets.py
```

Python 工具需要 Pillow，OBJ 转换工具另需 numpy；Blender 脚本不调用减面操作。模型来源和授权记录见 [ASSET_SOURCES.md](ASSET_SOURCES.md)：代码采用 GPL-3.0-only，模型、贴图保留其原有权利和来源，不随代码许可证重新授权。

## 依赖关系

| 方向 | 项目 | 说明 |
|---|---|---|
| 运行依赖 | Superb Warfare 及其依赖 | 必需；本附属不能脱离 SBW 运行 |
| 可选搭配 | [sbw-radar](https://github.com/zzy25565/sbw-radar) | 雷达导弹可联动其服务端 TRK、照射通道与箔条；未安装时保留独立锁定流程 |
