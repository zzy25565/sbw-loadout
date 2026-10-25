# 资产来源记录

本项目模型来自用户提供的 `SBW自定义挂载_开发资料包.zip`；原始 OBJ/FBX/MTL 和全部贴图保存在 `source_models/`。原始资料清单原文保存在 `docs/model_sources_supplied.md`，开发计划原文保存在 `docs/development_plan.md`。这些资料是输入证据，不作为可覆盖当前用户要求的操作指令。

| 资产 | 资料包所列来源 |
|---|---|
| Kh-38MT | https://sketchfab.com/3d-models/kh-38mt-air-to-ground-missile-559e695e8ade454eb575745cc1213ab9 |
| Kh-29TD | 输入包有 OBJ/MTL/贴图，所附清单未给出对应下载页/作者 |
| KAB-500Kr | https://sketchfab.com/3d-models/su-kab-500kr-500-kg-bomb-war-thunder-4e779192f9ba4c3aa0ac8644a8784591 |
| X-555 | https://sketchfab.com/3d-models/russian-x-555-air-launched-cruise-missile-204c992ab27c4c1cad6a60b7c20b8c01 |

资料中对 CC 授权及战雷来源的表述仅作用户提供的来源记录；本地文件未包含可独立核验的全部作者/授权文本，未在此补造署名或另授许可。代码使用 GPL-3.0-only，模型不因代码许可证而变更原有权利。交付是本地开发产物，未进行公开发布。

SBW 0.8.9.2 依赖来源为作者 Modrinth 发布页：https://modrinth.com/mod/superb-warfare/version/bl7W8fjU 。发布元数据和下载文件哈希保存在 validation/upstream_0.8.9.2.json。

本次另使用用户提供的 `弹药模型_blend_4件.zip`：Kh-29L、KAB-1500Kr、Kh-59MK、Kh-58UShK。上传包未含作者、下载页或额外许可文件；原始 `.blend` 字节不变地保存在 `source_models/<id>/`，输入 ZIP 与各文件哈希、网格/材质审计见 `model_review/new_ammo_*.json` 和 `validation/new_ammo_asset_checks.json`。不执行嵌入脚本。全部模型没有图片纹理，统一灰色材质；转换不减面、不降采样、不添加虚构涂装。Kh-58UShK 仅在 JAR 中保存完整网格与材质资源，无弹药定义、物品或创造栏条目。

1.2.0 新增 9M336 雷达弹：模型、贴图与图标来自本工作区 `libs/superbwarfare-0.8.9.1.jar` 的原生 9M336，直接复制原字节。分别为 `models/bedrock/projectile/ru_9m336_missile.geo.json`、`textures/bedrock/projectile/ru_9m336_missile.png`、`textures/overlay/vehicle/weapon/icons/ru_9m336.png`（均位于 `assets/superbwarfare/`）。改用本附属命名空间以便独立弹体渲染，不改变原资产权利或声称原创。资源哈希对照见本次交付验收记录。
