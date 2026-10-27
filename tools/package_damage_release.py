"""Check exactly the intended three ammo fields changed, then package release 1.3.2."""
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

P = Path(__file__).resolve().parents[1]
V = P / "validation/damage_1.3.2"
OUT = P.parent / "交付_弹药伤害_1.3.2"


def main():
    audit = json.loads((V / "balance_audit.json").read_text(encoding="utf-8"))
    assert audit["ammo_count"] == 29 and audit["other_properties_unchanged"]
    assert audit["monotonic_within_each_basis"]
    for version in ("0.8.9.1", "0.8.9.2"):
        log = (V / f"gametest_{version}.log").read_text(encoding="utf-8", errors="replace")
        assert "All 52 required tests passed" in log and "BUILD SUCCESSFUL" in log
    assert "BUILD SUCCESSFUL" in (V / "release_build.log").read_text(encoding="utf-8", errors="replace")
    jar = P / "build/libs/sbw-loadout-1.3.2.jar"
    baseline = P.parent / "交付_吊舱热成像_1.3.1/sbw-loadout-1.3.1.jar"
    with zipfile.ZipFile(jar) as z, zipfile.ZipFile(baseline) as old:
        assert z.testzip() is None
        names = {n for n in z.namelist() if not n.endswith("/")}
        assert names == {n for n in old.namelist() if not n.endswith("/")}
        assert not any("/test/" in n or "Smoke" in n or "Fixture" in n for n in names)
        changed = {n for n in names if old.read(n) != z.read(n)}
        ammo = {"data/sbw_loadout/ammo/" + row["id"] + ".json" for row in audit["rows"]}
        assert changed == ammo | {"META-INF/neoforge.mods.toml"}, changed - ammo
        assert 'version="1.3.2"' in z.read("META-INF/neoforge.mods.toml").decode("utf-8")
        for name in ammo:
            data, before = json.loads(z.read(name)), json.loads(old.read(name))
            assert data == json.loads((P / "src/main/resources" / name).read_text(encoding="utf-8"))
            for key in set(data) | set(before):
                if key not in audit["changed_fields"]: assert data.get(key) == before.get(key)
        classes = sum(n.endswith(".class") for n in names)
        assets = sum(n.startswith("assets/") for n in names)
    OUT.mkdir(exist_ok=True)
    shutil.copy2(jar, OUT / jar.name)
    shutil.copy2(P / "docs/弹药伤害与当量.md", OUT / "弹药伤害与当量.md")
    shutil.copy2(V / "弹药伤害对照.csv", OUT / "弹药伤害对照.csv")
    shutil.copy2(P / "tools/ammo_equivalents.json", OUT / "游戏当量输入与来源.json")
    logs = OUT / "验证日志"; logs.mkdir(exist_ok=True)
    for name in ("gametest_0.8.9.1.log", "gametest_0.8.9.2.log", "release_build.log", "balance_audit.json"):
        shutil.copy2(V / name, logs / name)
    files = ["build.gradle", "README.md", "src/main/resources/META-INF/neoforge.mods.toml",
        "src/gametest/java/cn/zzy/loadout/test/DamageBalanceGameTests.java",
        "src/gametest/java/cn/zzy/loadout/test/LoadoutGameTests.java",
        "tools/ammo_equivalents.json", "tools/balance_ammo.py", "tools/package_damage_release.py",
        "docs/弹药伤害与当量.md"]
    files += ["src/main/resources/" + n for n in sorted(ammo)]
    with zipfile.ZipFile(OUT / "伤害调整源码更新_1.3.2.zip", "w", zipfile.ZIP_DEFLATED) as z:
        for name in files: z.write(P / name, "sbw_loadout/" + name)
        z.writestr("使用说明.txt", "这是相对 1.3.1 的源码更新包，不是完整工程。完整开发工程位于 D:/自定义挂载/sbw_loadout。")
    checks = {"version": "1.3.2", "jar": jar.name, "bytes": jar.stat().st_size,
        "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(), "ammo_count": 29,
        "changed_fields": audit["changed_fields"], "gametests_each_sbw_version": 52,
        "network_protocol": 5, "all_runtime_classes_unchanged": classes,
        "all_assets_unchanged": assets, "other_ammo_properties_unchanged": True,
        "native_mk82_values": {"Damage": 0, "ExplosionDamage": 650, "ExplosionRadius": 22},
        "native_agm65_values": {"Damage": 1100, "ExplosionDamage": 180, "ExplosionRadius": 12}}
    for path in (OUT / "交付校验.json", V / "release_checks.json"):
        path.write_text(json.dumps(checks, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT / "README.md").write_text("""# 弹药伤害调整 1.3.2

29 种弹药按选定游戏当量调整直击伤害、爆炸伤害与爆炸半径。空地和巡航导弹使用 AGM-65 基准，其余弹种使用 MK82 基准。伤害按当量比例增长，半径按当量比例的立方根增长。

原生 MK82 只有爆炸伤害650、半径22，独立直击为0；本附属明确以650作为该类弹药的直击换算基数。原生 AGM-65 使用直击1100、爆炸伤害180、半径12。不能确认当量的弹种已标明“游戏估值”或“同系列估值”，不宣称为现实精确性能。

具体参数和来源见弹药伤害与当量.md；新旧参数见弹药伤害对照.csv；所有比例输入见游戏当量输入与来源.json。较小当量弹药可能比旧版更弱，两个基准之间不直接按伤害大小排序。

客户端与服务端替换旧版 sbw-loadout JAR，安装 sbw-loadout-1.3.2.jar。MC1.21.1 / NeoForge21.1.240+ / SBW0.8.9.1或0.8.9.2，保留原有依赖。通信协议仍为5。

两个 SBW 版本各52项 GameTest全部通过，包括原生基准检查和挂载发射的属性应用。正式JAR已核对：只有29个弹药JSON的三项参数和版本声明发生变化，运行时类和模型资源字节一致，因此此前热成像、模型、制导和低空巡航功能保留。源码工程已更新，相邻ZIP用于审阅和迁移。
""", encoding="utf-8")
    print(json.dumps(checks, ensure_ascii=False, indent=2))


if __name__ == "__main__": main()
