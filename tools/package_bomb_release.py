"""Package verified release scatter behavior without changing any ammo balance or model assets."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import zipfile

P=Path(__file__).resolve().parents[1]
V=P/'validation/bomb_release_1.3.3'
OUT=P.parent/'交付_航弹散布_1.3.3'


def main():
    clients={}
    spans={}
    for version in ('0.8.9.1','0.8.9.2'):
        log=(V/f'client_and_gametest_{version}.log').read_text(encoding='utf-8',errors='replace')
        assert 'All 60 required tests passed' in log and 'BUILD SUCCESSFUL' in log
        assert 'BOMB_CLIENT_FAIL' not in log and ' failed at ' not in log
        count=int(re.search(r'BOMB_CLIENT_COMPLETE (\d+) checks',log)[1])
        assert count==10 and log.count('BOMB_CLIENT_PASS ')==count
        clients[version]=count
        x,y=re.search(r'BOMB_RELEASE_SAMPLES n=32 spanX=([0-9.Ee+-]+) spanY=([0-9.Ee+-]+)',log).groups()
        spans[version]={'samples':32,'initial_x_velocity_span':float(x),'initial_y_velocity_span':float(y)}
    assert 'BUILD SUCCESSFUL' in (V/'release_build.log').read_text(encoding='utf-8',errors='replace')
    jar=P/'build/libs/sbw-loadout-1.3.3.jar'
    baseline=P.parent/'交付_弹药伤害_1.3.2/sbw-loadout-1.3.2.jar'
    with zipfile.ZipFile(jar) as z,zipfile.ZipFile(baseline) as old:
        assert z.testzip() is None
        names=set(z.namelist())
        assert not any('/test/' in n or 'Smoke' in n or 'Fixture' in n or 'fixture_' in n for n in names)
        assert 'version="1.3.3"' in z.read('META-INF/neoforge.mods.toml').decode('utf-8')
        resources=[n for n in old.namelist() if n.startswith(('assets/','data/')) and not n.endswith('/')]
        assert set(resources)=={n for n in names if n.startswith(('assets/','data/')) and not n.endswith('/')}
        assert all(z.read(n)==old.read(n) for n in resources),'model, texture or damage definition changed'
        preserved=[
            'guidance/CruiseGuidance','guidance/CruiseMissileEntity','guidance/RadarGuidance',
            'guidance/RadarMissileEntity','guidance/SeekerController',
            'client/PodThermalVision','mixin/PodThermalMixin']
        for stem in preserved:
            n='cn/zzy/loadout/'+stem+'.class'
            assert z.read(n)==old.read(n),n
        ammo=[n for n in resources if n.startswith('data/sbw_loadout/ammo/') and n.endswith('.json')]
        assert len(ammo)==29
        bombs=[n.rsplit('/',1)[-1].removesuffix('.json') for n in ammo if json.loads(z.read(n))['Category']=='GuidedBomb']
        assert len(bombs)==11
    OUT.mkdir(exist_ok=True)
    shutil.copy2(jar,OUT/jar.name)
    shutil.copy2(P/'docs/航弹投放散布.md',OUT/'航弹投放散布.md')
    logs=OUT/'验证日志';logs.mkdir(exist_ok=True)
    for version in clients:
        shutil.copy2(V/f'client_and_gametest_{version}.log',logs/f'client_and_gametest_{version}.log')
        shutil.copytree(V/f'screenshots_{version}',OUT/'游戏截图'/version,dirs_exist_ok=True)
    shutil.copy2(V/'release_build.log',logs/'release_build.log')
    files=['build.gradle','README.md','.gitignore','.gitattributes','ASSET_SOURCES.md',
        'libs/README.md','docs/验收_1.3.3.md','docs/release-checks-1.3.3.json',
        'docs/images/bomb-unlocked.png','docs/images/bomb-locked-ir.png',
        'src/main/resources/META-INF/neoforge.mods.toml',
        'src/main/java/cn/zzy/loadout/LoadoutMod.java','src/main/java/cn/zzy/loadout/LoadoutService.java',
        'src/main/java/cn/zzy/loadout/guidance/BombRelease.java',
        'src/main/java/cn/zzy/loadout/guidance/TelevisionLocks.java',
        'src/main/java/cn/zzy/loadout/guidance/TargetingPod.java',
        'src/main/java/cn/zzy/loadout/guidance/GuidanceEvents.java',
        'src/main/java/cn/zzy/loadout/client/GuidanceClient.java',
        'src/main/java/cn/zzy/loadout/client/TargetingPodScreen.java',
        'src/main/java/cn/zzy/loadout/network/LoadoutNetwork.java',
        'src/gametest/java/cn/zzy/loadout/test/BombReleaseGameTests.java',
        'src/gametest/java/cn/zzy/loadout/test/RadarGameTests.java',
        'src/gametest/resources/data/sbw_loadout/sbw/vehicles/fixture_aircraft.json',
        'src/bombsmoke/java/cn/zzy/loadout/client/BombSmoke.java',
        'src/bombsmoke/java/cn/zzy/loadout/client/FixtureRenderer.java',
        'docs/航弹投放散布.md','tools/package_bomb_release.py']
    files += [p.relative_to(P).as_posix() for p in (P/'src/gametest/resources/data/fixture_bombs/ammo').glob('*.json')]
    with zipfile.ZipFile(OUT/'航弹散布源码更新_1.3.3.zip','w',zipfile.ZIP_DEFLATED) as z:
        for name in files:z.write(P/name,'sbw_loadout/'+name)
        z.writestr('使用说明.txt','这是基于1.3.2的源码更新包，用于审阅与迁移，不是独立完整工程。当前完整工程位于D:/自定义挂载/sbw_loadout。')
    checks={'version':'1.3.3','jar':jar.name,'bytes':jar.stat().st_size,
        'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'bomb_count':len(bombs),
        'bombs':sorted(bombs),'default_bomb_release_spread':2.0,
        'gametests_each_sbw_version':60,'client_checks':clients,'release_samples':spans,
        'model_and_data_resources_unchanged':len(resources),'ammo_definitions_unchanged':29,
        'preserved_controllers':preserved,'development_classes_excluded':True,'network_protocol':5}
    for path in (OUT/'交付校验.json',V/'release_checks.json'):
        path.write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'README.md').write_text('''# 航弹投放散布 1.3.3

全部11种航弹在未锁定投放时加入随机散布，未锁定的制导航弹可以按普通炸弹投放。TV、激光和坐标航弹获得服务端有效锁定/照射后，本次释放散布为0。失锁或解除锁定后，再次投放恢复普通投放。

TV/坐标航弹用K或F6吊舱内K/G锁定；激光航弹需要吊舱L或同队指示器的匹配编码有效光斑。界面显示BOMB/普通投放·有散布或GUIDED/无投放散布。坐标航弹锁定后保存世界坐标，不跟随原实体移动。

默认最低散布为2.0，沿用原生Spread单位；未锁定时取弹种Spread和服务器bombReleaseSpread的较大值，锁定后为0。参数和边界见航弹投放散布.md。无投放散布不代表强制命中；已投下的普通炸弹不会因后来锁定而突然获得制导。

客户端与服务端替换旧版挂载JAR为sbw-loadout-1.3.3.jar。支持MC1.21.1、NeoForge21.1.240+、SBW0.8.9.1/0.8.9.2，保留原有依赖；协议仍为5。

两个SBW版本各通过60项服务端GameTest和10项真实客户端检查。新增航弹测试使用独立批次，避免测试实体影响并行雷达场景；雷达生产控制器保持原字节。随机样本、日志和游戏截图随包附上。正式包已核对：29份伤害配置和全部模型/贴图资源与1.3.2逐字节一致，低空巡航、雷达及热成像控制器保留，不含开发测试类。

完整工程已更新，源码更新ZIP用于审阅和迁移；文件SHA256和审计结果见交付校验.json。
''',encoding='utf-8')
    print(json.dumps(checks,ensure_ascii=False,indent=2))


if __name__=='__main__':main()
