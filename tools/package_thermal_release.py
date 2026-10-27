"""Audit and package the native thermal integration, excluding development fixtures."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import zipfile

P = Path(__file__).resolve().parents[1]
V = P / "validation/thermal_1.3.1"
OUT = P.parent / "交付_吊舱热成像_1.3.1"


def main():
    clients = {}
    for version in ("0.8.9.1", "0.8.9.2"):
        log = (V / f"client_{version}.log").read_text(encoding="utf-8", errors="replace")
        assert "THERMAL_CLIENT_FAIL" not in log
        assert "BUILD SUCCESSFUL" in log
        count = int(re.search(r"THERMAL_CLIENT_COMPLETE (\d+) checks", log)[1])
        assert count == 27 and log.count("THERMAL_CLIENT_PASS ") == count
        clients[version] = count
    assert "All 51 required tests passed" in (V / "client_0.8.9.1.log").read_text(encoding="utf-8", errors="replace")
    server = (V / "gametest_0.8.9.2.log").read_text(encoding="utf-8", errors="replace")
    assert "All 51 required tests passed" in server and "BUILD SUCCESSFUL" in server
    assert "BUILD SUCCESSFUL" in (V / "release_build.log").read_text(encoding="utf-8", errors="replace")

    jar = P / "build/libs/sbw-loadout-1.3.1.jar"
    baseline = P.parent / "交付_低空巡航_1.3.0/sbw-loadout-1.3.0.jar"
    with zipfile.ZipFile(jar) as z, zipfile.ZipFile(baseline) as old:
        assert z.testzip() is None
        names = set(z.namelist())
        assert not any("/test/" in n or "Smoke" in n or "Fixture" in n
                       or "/sbw/vehicles/" in n for n in names)
        assert 'version="1.3.1"' in z.read("META-INF/neoforge.mods.toml").decode()
        mixins = json.loads(z.read("sbw_loadout.mixins.json"))
        assert "PodThermalMixin" in mixins["client"]
        assert "PodThermalMixin" not in mixins["mixins"]
        resources = [n for n in old.namelist() if n.startswith(("assets/", "data/")) and not n.endswith("/")]
        assert set(resources) == {n for n in names if n.startswith(("assets/", "data/")) and not n.endswith("/")}
        assert all(z.read(n) == old.read(n) for n in resources), "existing asset or ammo data changed"
        guidance = [n for n in old.namelist() if n.startswith("cn/zzy/loadout/guidance/") and n.endswith(".class")]
        assert all(z.read(n) == old.read(n) for n in guidance), "guidance implementation changed"

    OUT.mkdir(exist_ok=True)
    shutil.copy2(jar, OUT / jar.name)
    shutil.copy2(P / "docs/吊舱热成像.md", OUT / "吊舱热成像.md")
    for version in clients:
        shutil.copytree(V / f"screenshots_{version}", OUT / "游戏截图" / version, dirs_exist_ok=True)
    logs = OUT / "验证日志"
    logs.mkdir(exist_ok=True)
    for name in ("client_0.8.9.1.log", "client_0.8.9.2.log", "gametest_0.8.9.2.log", "release_build.log"):
        shutil.copy2(V / name, logs / name)
    files = [
        "build.gradle", "README.md", "src/main/resources/META-INF/neoforge.mods.toml",
        "src/main/resources/sbw_loadout.mixins.json",
        "src/main/java/cn/zzy/loadout/client/PodThermalVision.java",
        "src/main/java/cn/zzy/loadout/client/TargetingPodScreen.java",
        "src/main/java/cn/zzy/loadout/client/TargetingPodClient.java",
        "src/main/java/cn/zzy/loadout/mixin/PodThermalMixin.java",
        "src/thermalsmoke/java/cn/zzy/loadout/client/ThermalSmoke.java",
        "src/thermalsmoke/java/cn/zzy/loadout/client/FixtureRenderer.java",
        "docs/吊舱热成像.md", "docs/吊舱视角与激光外观.md", "tools/package_thermal_release.py",
    ]
    with zipfile.ZipFile(OUT / "热成像源码更新_1.3.1.zip", "w", zipfile.ZIP_DEFLATED) as z:
        for name in files:
            z.write(P / name, "sbw_loadout/" + name)
        z.writestr("使用说明.txt", "这是基于 1.3.0 的源码更新包，用于审阅和迁移，不是独立完整工程。完整工程已在 D:/自定义挂载/sbw_loadout 中更新。运行测试需使用该工程已有的依赖和测试夹具。")
    checks = {
        "version": "1.3.1", "jar": jar.name, "bytes": jar.stat().st_size,
        "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "native_sbw_thermal_pipeline": True, "network_protocol": 5,
        "client_checks": clients, "gametests_each_version": 51,
        "unchanged_resources_from_1_3_0": len(resources),
        "unchanged_guidance_classes": len(guidance), "development_classes_excluded": True,
    }
    for destination in (OUT / "交付校验.json", V / "release_checks.json"):
        destination.write_text(json.dumps(checks, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT / "README.md").write_text("""# 吊舱热成像 1.3.1

已在吊舱锁定界面接入卓越前线原生热成像。F6 进入吊舱，默认 Shift+K 切换 EO / IR，K 仍锁定；也可点击左上角模式按钮。更改卓越前线热成像键后，界面会显示实际快捷键。退出恢复此前的成像状态。

客户端与服务端移出旧版 sbw-loadout JAR，安装本目录的 sbw-loadout-1.3.1.jar。适用 MC 1.21.1 / NeoForge 21.1.240+ / SBW 0.8.9.1 或 0.8.9.2，保留原有依赖。协议仍为 5。

两个 SBW 版本均通过 27 项真实客户端检查及 51 项服务端 GameTest。客户端覆盖原生热成像、默认键冲突、改键、锁定保持、变倍、界面重初始化、资源重载、退出、离席、原生座位状态恢复和开启 IR 时断开世界。真实游戏画面见“游戏截图”；日志见“验证日志”。

对比 1.3.0 正式 JAR，全部 assets/data 资源与制导类字节一致，包含此前弹药模型、Kh-59MK / X-555 低空巡航。正式包不含测试夹具或冒烟测试类。完整开发工程已更新；源码更新 ZIP 用于审阅和迁移。

详细操作与实现范围见“吊舱热成像.md”，文件 SHA256 与校验结果见“交付校验.json”。
""", encoding="utf-8")
    print(json.dumps(checks, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
