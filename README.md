# SBW 自定义挂载 1.2.0

适用 MC 1.21.1、NeoForge 21.1.240+、SBW 0.8.9.1 / 0.8.9.2。客户端与服务端都安装相同 JAR，沿用 SBW 所需的 Kotlin For Forge 等依赖。更新日期：2026-10-23。

1.2.0 新增：9M336 雷达导弹、独立弹体和雷达附属 1.3.0 联动。操作、参数及接入示例见 [雷达弹与雷达联动](docs/雷达弹与雷达联动.md)。雷达默认模式键改为 O、扫描宽度键为 P，保留本附属的 K / L 导引操作。

v1.1beta 新增：机载激光（吊舱内 L 开关）、Kh-29L 激光制导、KAB-1500Kr / Kh-59MK 电视制导及完整物品/飞行模型；Kh-58UShK 只打包隐藏资源。原始 Blender 模型无图片贴图，保留灰色材质与全部网格；见 [机载激光与新增弹药](docs/机载激光与新增弹药.md)。该段介绍旧版功能；当前版本为 `1.2.0`，通信协议仍为 4。

此前 1.1alepha 已实现：火炮指示器持续激光照射；激光断照后惯性导引及重获；TV 锁定后自主追踪；平滑限角转向；沿用 SBW 原生近防炮可拦截弹体。2026-10-22 补充独立吊舱视角（F6 进入、K 锁定、Enter 投放）、地面稳定、变倍，以及可调激光柔光；见 [吊舱视角与激光外观](docs/吊舱视角与激光外观.md)。最低 NeoForge 仍为 21.1.240。新增导引功能的操作和接入见 [激光与电视导引说明](docs/激光与电视导引.md)。

这是独立通用附属，不修改既有载具工程，不内置任何正式机型的适配或挂载白名单。**安装后，未声明 `Loadout` 的飞机不会自动获得挂载能力。** 载具作者在自身完整载具 JSON 中加入声明即可接入。

## 已实现

- 八件可用弹药物品、创造栏、原分辨率贴图、完整三角网格物品模型和 HUD 图标。
- AmmoType JSON 与 Java API 注册；新增数据包弹种自动使用通用弹药物品，不要求为每个弹种新增 Java 物品类。
- 基于 `VehicleEntity` 继承链及 Airplane / Helicopter 类型检测；动态连续挂点、白名单、默认挂载。
- 主手弹药右键快速装填；潜行右键优先打开配置界面。
- 拖拽装填、点击卸载、一键填满、滚轮翻弹药/槽位页。
- “保存并关闭”统一结算；Esc、取消、断线或普通关闭丢弃草稿。背包满时卸下的物品落在玩家脚下。
- 服务端复核范围、目标状态、实际库存、唯一弹药身份、会话版本和数据重载代次。缺货项单独不装入；目标或版本冲突取消整次结算。
- NBT 持久化及追踪/驾驶同步；驾驶席 FIFO 发射、逐发消耗、服务端冷却、空挂载禁射。其他乘员席保留原生武器。
- 创造模式或服务器 UUID 许可名单内的玩家开火后保留已挂弹药；普通生存玩家仅手动补弹。
- 服务端距离/角度/视线校验；IR 保留原生导引与锥内搜索，INS 保留原生位置导引；Laser / TV 使用新增服务端导引头控制器。

## 使用

1. 把 `sbw-loadout-1.2.0.jar` 放入客户端及服务器 `mods/`。
2. 在载具数据中声明 `Loadout`，并确保原有 `Type` 为 `Airplane` 或 `Helicopter`。
3. 在“SBW 自定义挂载”创造栏取弹，或用 `/give` 获取八件内置物品。
4. 主手持弹药右键装一枚；潜行右键批量配置。近距离、载具未损毁且没有其他乘员占用时可编辑。
5. 坐入驾驶席，使用 SBW 原有载具开火键。TV 弹可按 F6 进入吊舱，通过中键拖动瞄准、滚轮变倍，K 锁定后 Enter 投放，或返回驾驶视角后开火；也保留直接对准目标按 K 锁定；激光弹可按 F6、瞄准并按 K 锁定，再按 L 开启机载照射，Enter 发射；退出吊舱可继续驾驶并照射，也可由同队指示器或另一架飞机供光。自定义挂载接管该驾驶席开火；其他席位保持 SBW 原有行为。

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

无减面、无体素化、无 512 降采样、无再次 JPEG 编码。X-555 的面数变化是原多边形三角化，未新增造型或简化细节。共享坐标去重不改变逐角几何/法线/UV，物理尺寸统一缩放到 4.2 / 3.9 / 3.05 / 6.0 格；原始 OBJ/FBX/MTL/贴图字节保存在 `source_models/`。

`Model` 是 Bedrock 1.12 `poly_mesh`，16 模型单位为 1 格，+Z 是弹体长轴，原点在中心。自定义渲染器读取逐角位置、法线和 UV；每个骨骼可附 `loadout_material: {"texture": "...", "translucent": false}`。无该扩展时使用 AmmoType.Texture。此渲染器面向三角网格；不把 Bedrock cubes、动画骨骼或实体模型系统当成等价输入。

源基色转 PNG 后逐像素验证，无缩放。法线、AO、金属度/粗糙度/高光等原始辅助图均保留在资源中的 `textures/source/`；普通 Minecraft 渲染管线只使用基色、透明度和网格法线，不声称已实现 PBR 材质管线。透明材料和实际被 KAB 模型引用的网格图保留。

`model_review/four_models.png` 是原四件导出网格的 Blender 预览，`model_review/new_four_models.png` 为新增四件；`validation/screenshots/` 是实际游戏截图。体积较大来自保留高精模型和原始贴图。JAR/ZIP 容器采用无损压缩，不改变模型质量。

## 原生弹道边界

最初四种弹药均复用 `superbwarfare:agm_65` 原生实体，；本版雷达弹另外注册 `sbw_loadout:9m336_radar` 独立实体。Damage、ExplosionDamage、ExplosionRadius、Velocity 初速、Burst、Spread、Cooldown 从 JSON 应用；Burst 每个成功发出的弹丸消耗一枚，发射失败不扣弹。

IR / INS 沿用原生目标传递与飞行控制。Laser / TV 在原生弹体每次物理更新前应用限角导向，保留 SBW 推进、重力、碰撞、伤害、寿命与可摧毁属性；`Velocity` 是投放初速，非恒定巡航速度。内置 Kh-29TD / KAB-500Kr 的 TV 字段现已接入自主导引头，发射前须锁定。X-555 仍复用原生位置导引，未实现独立 INS/GNSS/末端成像模拟；内置 KAB 仍复用 AGM-65 弹体物理，没有新增独立滑翔模型。激光与 TV 控制器同时支持可被拦截的原生导弹、航弹弹体。新增 Kh-29L / Kh-59MK 使用 AGM-65 弹体、KAB-1500Kr 使用 MK-82 航弹弹体；具体伤害、初速和转弯参数是可修改的游戏初始平衡值，不声称还原真实武器性能。

一期没有机翼下实体挂架、实时外挂动画、地勤或补给链。原有四件高精模型用于物品与配置，飞行中沿用原生弹丸；新增三件启用 `RenderProjectileModel: true`，在飞行中显示各自完整网格，并同步、存档其外观 ID，导弹超视距同步也保留该身份。不附带正式机型的挂载白名单；本版已加入与雷达附属 1.3.0 的服务端 TRK、通道和箔条联动，验收见新文档。

## 构建与验证

需要 JDK 21。源码包的 `libs/` 保存本次编译测试使用的 SBW 与依赖，Gradle 第一次构建还会下载 NeoForge 和 Kotlin 依赖。

```bash
./gradlew clean build
./gradlew -Pgametest runGameTestServer
# 先构建相邻 sbw_radar 项目，再运行联合测试
./gradlew -Pgametest -PradarCompat runGameTestServer
./gradlew -Pgametest -PsbwVersion=0.8.9.2 runGameTestServer
./gradlew -Pgametest -Psmoke runClient
./gradlew -Pgametest -Psmoke -PsbwVersion=0.8.9.2 runClient
./gradlew -Pgametest -Pguidancesmoke -PsbwVersion=0.8.9.2 runClient
```

`smoke` 与 `guidancesmoke` 分开运行，均只用于开发。旧版导引客户端验收位于 `validation/1.1alepha/`；本版机载激光和新模型检查位于 `validation/v1.1beta/`，以各版本成功日志及 `summary.json` 为准。发布前必须再 `clean build`，防止将开发夹具残留在构建目录。原四件模型重建（会重写其数据与语言文件；不要用于只更新新增四件）：

```bash
blender --background --factory-startup --python tools/convert_models.py -- source_models .
python3 tools/prepare_assets.py
blender --background --factory-startup --python tools/render_models.py -- .
python3 tools/finalize_icons.py
python3 tools/validate_assets.py
```

Python 工具需要 Pillow；Blender 脚本不调用减面操作。测试结果、已覆盖场景与边界见 `validation/验收记录.md`。

## 依赖关系

| 方向 | 项目 | 说明 |
|---|---|---|
| 运行依赖 | Superb Warfare (GPL-3.0) | 必需 |
| 可选搭配 | [sbw-radar](https://github.com/zzy25565/sbw-radar) | 1.2.0 起的雷达弹（9M336）与雷达联动需要本模组 |
