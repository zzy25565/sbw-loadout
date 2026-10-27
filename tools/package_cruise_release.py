"""Package a verified release, source update files, measured flight evidence and instructions."""
from pathlib import Path
import hashlib, json, shutil, zipfile

P=Path(__file__).resolve().parents[1];V=P/'validation/low_altitude_1.3.0'
OUT=P.parent/'交付_低空巡航_1.3.0';OUT.mkdir(exist_ok=True)

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    summary=json.loads((V/'summary.json').read_text(encoding='utf-8'))
    assert summary['successful_contacts']==summary['scored_contacts']==26
    for version in ('0.8.9.1','0.8.9.2'):
        assert 'All 51 required tests passed' in (V/f'gametest_{version}.log').read_text(encoding='utf-8',errors='replace')
    client=(V/'client_flight.log').read_text(encoding='utf-8',errors='replace')
    assert 'CRUISE_CLIENT_COMPLETE target hit, four stages, full model, bounded speed' in client
    assert 'CRUISE_CLIENT_FAIL' not in client and 'Loadout model failed' not in client
    assert 'BUILD SUCCESSFUL' in (V/'release_build.log').read_text(encoding='utf-8',errors='replace')
    jar=P/'build/libs/sbw-loadout-1.3.0.jar'
    baseline=P.parent/'交付_20261026/sbw-loadout-1.2.1.jar'
    with zipfile.ZipFile(jar) as z,zipfile.ZipFile(baseline) as old:
        assert z.testzip() is None
        names=set(z.namelist())
        assert not any('/test/' in name or 'CruiseSmoke' in name or 'FixtureRenderer' in name
                       or 'ImportedModelsSmoke' in name or '/sbw/vehicles/' in name for name in names)
        assert 'cn/zzy/loadout/guidance/CruiseMissileEntity.class' in names
        assert 'cn/zzy/loadout/guidance/CruiseGuidance.class' in names
        assert 'version="1.3.0"' in z.read('META-INF/neoforge.mods.toml').decode('utf-8')
        ammo=[n for n in names if n.startswith('data/sbw_loadout/ammo/') and n.endswith('.json')]
        assert len(ammo)==29
        profiles=[]
        for name in ammo:
            data=json.loads(z.read(name));assert z.read(name)==(P/'src/main/resources'/name).read_bytes()
            if 'Cruise' in data:
                profiles.append(name.split('/')[-1].removesuffix('.json'))
                assert data['Projectile']=='sbw_loadout:cruise_missile'
                assert data['RenderProjectileModel'] is True
        assert sorted(profiles)==['kh59mk','x555']
        assets=[n for n in old.namelist() if n.startswith('assets/') and not n.endswith('/')]
        assert all(z.read(name)==old.read(name) for name in assets),'existing model/texture changed'
    shutil.copy2(jar,OUT/jar.name)
    shutil.copy2(P/'docs/低空巡航弹道.md',OUT/'低空巡航弹道.md')
    for name in ('验证结果.md','轨迹验证.png','碰撞验证明细.csv','summary.json'):
        shutil.copy2(V/name,OUT/name)
    shutil.copytree(V/'screenshots',OUT/'游戏截图',dirs_exist_ok=True)
    logs=OUT/'验证日志';logs.mkdir(exist_ok=True)
    for name in ('gametest_0.8.9.1.log','gametest_0.8.9.2.log','client_flight.log','release_build.log',
                 'trajectory_metrics_0.8.9.1.json','trajectory_metrics_0.8.9.2.json'):
        shutil.copy2(V/name,logs/name)
    files=[
      'build.gradle','README.md','src/main/resources/META-INF/neoforge.mods.toml',
      'src/main/resources/data/superbwarfare/tags/entity_type/destroyable_projectile.json',
      'src/main/resources/data/sbw_loadout/ammo/kh59mk.json','src/main/resources/data/sbw_loadout/ammo/x555.json',
      'src/main/java/cn/zzy/loadout/api/AmmoType.java','src/main/java/cn/zzy/loadout/api/CruiseSettings.java',
      'src/main/java/cn/zzy/loadout/api/GuidanceApi.java','src/main/java/cn/zzy/loadout/LoadoutMod.java',
      'src/main/java/cn/zzy/loadout/LoadoutService.java','src/main/java/cn/zzy/loadout/client/ClientSetup.java',
      'src/main/java/cn/zzy/loadout/network/LoadoutNetwork.java','src/main/java/cn/zzy/loadout/mixin/SeekerTickMixin.java',
      'src/main/java/cn/zzy/loadout/guidance/SeekerController.java',
      'src/main/java/cn/zzy/loadout/guidance/CruiseGuidance.java','src/main/java/cn/zzy/loadout/guidance/CruiseMissileEntity.java',
      'src/gametest/java/cn/zzy/loadout/test/CruiseGameTests.java','src/gametest/java/cn/zzy/loadout/test/CruiseHitProbe.java',
      'src/gametest/java/cn/zzy/loadout/test/AirborneLaserGameTests.java',
      'src/guidancesmoke/java/cn/zzy/loadout/client/AirModelsSmoke.java',
      'src/cruisesmoke/java/cn/zzy/loadout/client/CruiseSmoke.java',
      'src/cruisesmoke/java/cn/zzy/loadout/client/FixtureRenderer.java',
      'docs/低空巡航弹道.md','tools/summarize_cruise.py','tools/package_cruise_release.py']
    source_zip=OUT/'低空巡航源码更新_1.3.0.zip'
    with zipfile.ZipFile(source_zip,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name in files:z.write(P/name,'sbw_loadout/'+name)
        z.writestr('使用说明.txt','这是基于原 sbw_loadout 工程的源代码更新包，不是完整独立工程。当前工作目录中的完整工程已经更新；此包用于审阅和迁移，按相对路径覆盖旧工程前请保存自己的修改。')
    with zipfile.ZipFile(source_zip) as z:assert z.testzip() is None
    checks={**summary,'jar':jar.name,'jar_bytes':jar.stat().st_size,'jar_sha256':sha(jar),
      'jar_crc_valid':True,'development_classes_excluded':True,'existing_assets_unchanged':len(assets),
      'enabled_profiles':profiles,'network_protocol':5,'source_update_sha256':sha(source_zip)}
    (OUT/'交付校验.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    (V/'release_checks.json').write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding='utf-8')
    readme='''# 低空巡航 1.3.0

Kh-59MK 和 X-555 已启用独立低空巡航：下降、前视越障、低空稳定飞行、末段拦截。其他弹药维持原轨迹。

安装：客户端与服务端均移出旧版 sbw-loadout JAR，放入本包的 sbw-loadout-1.3.0.jar，保留原有 MC1.21.1 / NeoForge / SBW / Kotlin For Forge 依赖。协议为5，两端同时更新。原有弹药ID及载具Allowed写法不变。

默认目标净空8格，最大速度1.4格/刻，最大转向3度/刻，前视96格，末段范围96格。它们是游戏参数，不等同于真实武器性能。

两个SBW版本均51项GameTest通过，包含每版本13种实际飞行场景；真实客户端通过原挂载入口发射并命中原电视锁定目标，四阶段截图在“游戏截图”。完整边界、参数和计分方式见低空巡航弹道.md与验证结果.md。

未加载地形时暂停推进，不生成新区块；不具备横向迷宫寻路，也不承诺任意地形或机动目标下100%命中。完整开发工程已在相邻sbw_loadout目录更新；源码更新ZIP用于审阅和迁移。
'''
    (OUT/'README.md').write_text(readme,encoding='utf-8')
    print(json.dumps({'delivery':str(OUT),'jar':checks['jar'],'sha256':checks['jar_sha256'],
      'tests_each_version':51,'flight_contacts':'26/26','source_files':len(files)},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
