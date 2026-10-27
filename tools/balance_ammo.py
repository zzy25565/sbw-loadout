"""Write or check the three damage fields; preserve every other ammo property."""
import argparse
import csv
import json
from pathlib import Path
import zipfile

P = Path(__file__).resolve().parents[1]
V = P / "validation/damage_1.3.2"
BASELINE = P.parent / "交付_吊舱热成像_1.3.1/sbw-loadout-1.3.1.jar"
FIELDS = ("Damage", "ExplosionDamage", "ExplosionRadius")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    cfg = json.loads((P / "tools/ammo_equivalents.json").read_text(encoding="utf-8"))
    ammo = P / "src/main/resources/data/sbw_loadout/ammo"
    assert {p.stem for p in ammo.glob("*.json")} == set(cfg["ammo"])
    rows = []
    with zipfile.ZipFile(BASELINE) as old:
        for id, entry in cfg["ammo"].items():
            path = ammo / (id + ".json")
            data = json.loads(path.read_text(encoding="utf-8"))
            before = json.loads(old.read("data/sbw_loadout/ammo/" + id + ".json"))
            assert {k:v for k,v in data.items() if k not in FIELDS} == {k:v for k,v in before.items() if k not in FIELDS}, id
            air_ground = id in {"brimstone_dual", "kh29l", "kh29l_wt", "kh29td", "kh38mt", "kh59mk", "x555"}
            assert entry["basis"] == ("AGM65" if air_ground else "MK82"), id
            base = cfg["baselines"][entry["basis"]]
            ratio = entry["equivalent"] / base["equivalent"]
            values = (round(base["damage"] * ratio, 2), round(base["explosion_damage"] * ratio, 2),
                      round(base["radius"] * ratio ** (1/3), 3))
            assert 0 < values[2] <= 128
            if args.write:
                for key, value in zip(FIELDS, values): data[key] = value
                path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            assert all(data[k] == v for k,v in zip(FIELDS, values)), id
            rows.append({"id": id, "弹种": data["DisplayName"], "基准": entry["basis"],
                         "游戏当量_kg": entry["equivalent"], "数据性质": entry["status"],
                         "比例": round(ratio, 6),
                         **{k: data[k] for k in FIELDS},
                         **{"旧"+k: before[k] for k in FIELDS},
                         "备注": entry.get("note", ""), "来源": cfg["sources"].get(entry.get("source"), "")})
    for i, a in enumerate(rows):
        for b in rows[i+1:]:
            if a["基准"] != b["基准"]: continue
            for key in FIELDS:
                if a["游戏当量_kg"] == b["游戏当量_kg"]: assert a[key] == b[key]
                else: assert (a[key]-b[key]) * (a["游戏当量_kg"]-b["游戏当量_kg"]) > 0
    V.mkdir(exist_ok=True)
    with (V / "弹药伤害对照.csv").open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)
    (V / "balance_audit.json").write_text(json.dumps({"ammo_count": len(rows), "changed_fields": list(FIELDS),
        "monotonic_within_each_basis": True, "other_properties_unchanged": True,
        "rows": rows}, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = ["# 弹药伤害与当量（1.3.2）", "",
        "全部 29 种弹药已统一调整。以下当量是本模组选定的游戏平衡输入；WT 参考采用所列页面的固定数值，不宣称是现实武器的精确性能或最新 WT 数值。无法确认具体战斗部的弹种使用明确标注的暂定游戏估值，后续可单独修订。整弹重量、战斗部总重与炸药当量不能混用。", "",
        "## 换算", "",
        "设 q = 弹药游戏当量 / 对应基准当量：直击伤害 = 基准直击 × q；爆炸伤害 = 基准爆炸伤害 × q；爆炸半径 = 基准半径 × q^(1/3)。这是游戏缩放规则。两类基准分别保证同类中当量越大，三项参数越大；跨基准的伤害数值不作直接排序。", "",
        "| 适用弹种 | 基准 | 游戏当量 kg TNT等效 | 直击换算基数 | 爆炸伤害基数 | 半径基数（格） |",
        "|---|---|---:|---:|---:|---:|",
        "| 空地、巡航导弹 | AGM-65 | 51.19 | 1100 | 180 | 12 |",
        "| 炸弹、空空及其余弹种 | MK82 | 117.6 | 650 | 650 | 22 |", "",
        "SBW 0.8.9.1 / 0.8.9.2 的原生 MK82 独立 Damage 实际为 0，ExplosionDamage 为 650、ExplosionRadius 为 22。为了满足直击随当量增大，本附属明确采用原生爆炸伤害 650 作为炸弹直击的换算基数，并非声称原版 MK82 有 650 的独立直击。AGM-65 的三项原值为 1100 / 180 / 12。原生载具 A-10A 的 Bomb / Missile 配置与对应弹体的运行时基准均已核对；基准测试见 DamageBalanceGameTests.nativeDamageReferences。", "",
        "AGM-65 未在 SBW 中区分具体子型，本轮选用 WT AGM-65A/B 的 51.19 作为比例尺；GBU-31 采用普通 Mk84 弹体映射，GBU-38 采用 Mk82 弹体映射。型号名相同但只是替换网格的 Kh-29L 两个版本使用一致威力。", "",
        "伤害保留两位小数、半径保留三位小数。最大半径约 39.9 格。实际命中损伤仍受 SBW 的距离衰减、遮挡和目标减伤规则影响。", "",
        "## 全弹种参数", "",
        "| ID | 游戏当量 | 基准 | 直击伤害 | 爆炸伤害 | 爆炸半径 | 数据性质 |",
        "|---|---:|---|---:|---:|---:|---|"]
    for row in rows:
        lines.append(f'| {row["id"]} | {row["游戏当量_kg"]:g} | {row["基准"]} | {row["Damage"]:g} | {row["ExplosionDamage"]:g} | {row["ExplosionRadius"]:g} | {row["数据性质"]} |')
    lines += ["", "## 估值与映射备注", ""]
    for row in rows:
        if row["备注"]: lines.append(f'- **{row["id"]}**（{row["数据性质"]}）：{row["备注"]}')
    lines += ["", "## 参考来源", "",
        "本模组伤害基数来自本地 SBW JAR：data/superbwarfare/sbw/vehicles/a_10a.json，以及 Mk82Entity / FastThrowableProjectile / Agm65Entity。游戏当量参考如下；全部暂定估值均是本附属自己的平衡选择，没有引用为已证实的真实装药量。", ""]
    for key, url in cfg["sources"].items(): lines.append(f'- [{key}]({url})')
    lines += ["", "## 修改范围与复核", "",
        "仅改每个弹种 JSON 中的 Damage、ExplosionDamage、ExplosionRadius。原模型、热成像、制导、速度、挂点、锁定距离和低空巡航参数保持原值；通信协议仍为 5。较小当量弹药可能比旧版更弱，这是统一当量尺度的结果。", "",
        "tools/ammo_equivalents.json 保存全部输入、来源和估值标记；python tools/balance_ammo.py 校验现有配置，添加 --write 才重写三项参数。脚本核对每种弹药的其他属性与 1.3.1 正式 JAR 一致，并逐对验证同一基准下的威力单调性。新旧数值及来源见交付的弹药伤害对照.csv。", "",
        "最终测试结果与正式包校验见交付目录 README.md / 交付校验.json。"]
    (P / "docs/弹药伤害与当量.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({"ammo_count": len(rows), "other_properties_unchanged": True,
                      "radius_range": [min(r["ExplosionRadius"] for r in rows), max(r["ExplosionRadius"] for r in rows)]}))


if __name__ == "__main__": main()
