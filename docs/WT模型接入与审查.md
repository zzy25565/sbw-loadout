# WT 模型接入与审查（1.2.1）

2026-10-26：从用户提供的审查包及其对应 OBJ 导入 21 个完整三角网格，新增 21 个数据弹种，总可用弹种变为 29 个。新资源统一使用灰色材质：本次输入未提供可核验的弹药图片和材质映射。原有 8 个物品和原模型保持不变，Kh-29L 新版本命名为 kh29l_wt。

## 获取和挂载

在“SBW 自定义挂载”创造栏获取，或使用通用弹药物品：

```mcfunction
/give @s sbw_loadout:ammo[minecraft:custom_data={Ammo:"sbw_loadout:pl12"}] 1
```

先在目标载具的完整配置中把新弹种 ID 加入 `Loadout.Allowed`，再按原流程装填。不要用只有 Loadout 的 JSON 覆盖正式载具文件。

```json
"Loadout": {
  "Slots": 6,
  "Allowed": ["sbw_loadout:pl12", "sbw_loadout:r73", "sbw_loadout:kab500l"],
  "Default": []
}
```

激光弹沿用 L/吊舱和激光编码1688；电视弹 K 锁定；雷达弹沿用雷达附属 TRK（独立安装时 K 锁定）。这些是现有游戏制导的复用，未声称还原每种实物的导引与气动。

## 新弹种

全部 ID 的命名空间为 `sbw_loadout`。

| ID | 显示名 | 游戏制导 | 三角面 | 长度/格 |
|---|---|---|---:|---:|
| `ls500j` | LS-500J 激光制导航弹 | LASER | 8,748 | 3.599 |
| `fx1400` | FX 1400 制导航弹（游戏点制导） | INS | 6,020 | 3.340 |
| `gbu58` | GBU-58 激光制导航弹 | LASER | 17,273 | 2.892 |
| `spice1000` | SPICE 1000（游戏电视制导） | TV | 14,375 | 3.548 |
| `pl12` | PL-12（游戏雷达制导） | RADAR | 6,782 | 3.942 |
| `pl12a` | PL-12A（游戏雷达制导） | RADAR | 6,740 | 3.942 |
| `kab1500l` | KAB-1500L 激光制导航弹 | LASER | 30,407 | 4.620 |
| `kab500l` | KAB-500L 激光制导航弹 | LASER | 9,432 | 3.068 |
| `kh29l_wt` | Kh-29L 激光导弹（WT 网格） | LASER | 14,337 | 3.895 |
| `r40rd` | R-40RD（游戏雷达制导） | RADAR | 30,354 | 6.198 |
| `r40td` | R-40TD 红外导弹 | IR | 29,880 | 5.990 |
| `r73` | R-73 红外导弹 | IR | 15,938 | 2.899 |
| `r73l` | R-73L 红外导弹 | IR | 14,984 | 2.899 |
| `r77_1` | R-77-1（游戏雷达制导） | RADAR | 16,901 | 3.710 |
| `r77` | R-77（游戏雷达制导） | RADAR | 16,545 | 3.600 |
| `brimstone_dual` | Brimstone Dual（游戏激光制导） | LASER | 17,800 | 1.804 |
| `gbu31` | GBU-31（游戏点制导） | INS | 27,131 | 3.845 |
| `gbu53b` | GBU-53/B（游戏电视制导） | TV | 12,942 | 1.757 |
| `gbu38` | GBU-38（游戏点制导） | INS | 10,710 | 2.352 |
| `aim54a` | AIM-54A（游戏雷达制导） | RADAR | 2,936 | 3.946 |
| `aim54c` | AIM-54C（游戏雷达制导） | RADAR | 2,904 | 3.946 |

## 转换及功能边界

positions 每源米写 16 模型单位；+X 旋转到 +Z，模型居中。独立去重位置、法线、UV，按对象和材质分组，不减面、不体素化。UV 从 OBJ 恢复，只翻转 V，不逐角取模；无贴图时以中性灰色表示，不虚构涂装。

雷达弹种复用 sbw_loadout:9m336_radar；红外复用 superbwarfare:ru_9m336_missile；其他导弹复用 AGM-65，激光/电视航弹复用 MK-82，点制导航弹复用 AGM-65（MK-82 不实现 ITrackableProjectile）。雷达末段搜索、半主动/主动雷达差别、复合导引、FX1400无线电遥控、GBU气动没有新增专用实现。新弹药的伤害和初速属于游戏平衡初值。

源 OBJ 保存在 source_models/wt_review；源/导出角点摘要在 model_review/wt_import_conversion.json；序列化资产验证在 validation/wt_import_checks.json。GBU-31 源有 3 个退化面，仍保留原始几何作为来源证据。载具独立交付，没有加入此模组。
