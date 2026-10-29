"""Audit and package the paired air-to-air release after the final real-runtime checks."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import subprocess
import zipfile

P=Path(__file__).resolve().parents[1]
R=P.parent/'sbw_radar'
V=P/'validation/air_to_air_1.3.4'
OUT=P.parent/'交付_空空适配_1.3.4'
BASE=P.parent/'交付_航弹散布_1.3.3/sbw-loadout-1.3.3.jar'
IDS=set(json.loads((P/'tools/ammo_equivalents.json').read_text(encoding='utf-8'))['balance_overrides']['air_to_air']['ids'])

def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def log(path): return path.read_text(encoding='utf-8',errors='replace')
def changes(root,base):
    tracked=subprocess.check_output(['git','diff','--name-only','-z',base],cwd=root).decode().split('\0')
    new=subprocess.check_output(['git','ls-files','--others','--exclude-standard','-z'],cwd=root).decode().split('\0')
    return sorted(n for n in set(tracked+new) if n and (root/n).is_file())

def main():
    assert 'RADAR_STANDALONE_BOOT_PASS radar=1.11.1 loadout=absent' in log(V/'radar_standalone_boot.log')
    for version in ('0.8.9.1','0.8.9.2'):
        text=log(V/f'final_{version}.log')
        assert 'All 74 required tests passed' in text and 'BUILD SUCCESSFUL' in text,version
        assert 'AIR_CLIENT_COMPLETE 14 checks' in text and text.count('AIR_CLIENT_PASS ')==14,version
        assert not any(s in text for s in (' failed at ','AIR_CLIENT_FAIL','Failed to start the minecraft server')),version
        text=log(V/f'standalone_{version}.log')
        assert 'All 65 required tests passed' in text and 'BUILD SUCCESSFUL' in text,version
        assert ' failed at ' not in text and 'Failed to start the minecraft server' not in text,version
    for name in ('release_build.log','radar_build.log'):
        assert 'BUILD SUCCESSFUL' in log(V/name),name
    jar=P/'build/libs/sbw-loadout-1.3.4.jar'
    radar=R/'build/libs/sbw_radar-1.11.1.jar'
    with zipfile.ZipFile(jar) as z,zipfile.ZipFile(BASE) as old:
        assert z.testzip() is None
        names=set(z.namelist())
        assert not any('/test/' in n or 'Smoke' in n or 'Fixture' in n or 'fixture_' in n or 'TestIsolation' in n for n in names)
        assert 'version="1.3.4"' in z.read('META-INF/neoforge.mods.toml').decode()
        resources={n for n in names if n.startswith(('assets/','data/')) and not n.endswith('/')}
        assert resources=={n for n in old.namelist() if n.startswith(('assets/','data/')) and not n.endswith('/')}
        ammo={n for n in resources if n.startswith('data/sbw_loadout/ammo/') and n.endswith('.json')}
        assert len(ammo)==29
        changed=[]
        for name in resources:
            if name in ammo:
                id=Path(name).stem;a=json.loads(z.read(name));b=json.loads(old.read(name))
                if id in IDS:
                    assert a['ExplosionDamage']==round(b['ExplosionDamage']*.5,3),id
                    assert a['Category']=='AirToAirMissile',id
                    assert {k:v for k,v in a.items() if k not in ('ExplosionDamage','Category')}=={k:v for k,v in b.items() if k not in ('ExplosionDamage','Category')},id
                    changed.append(id)
                else: assert z.read(name)==old.read(name),name
            else: assert z.read(name)==old.read(name),name
        preserved=['guidance/CruiseGuidance','guidance/CruiseMissileEntity','guidance/SeekerController',
            'guidance/RadarMissileEntity','guidance/BombRelease','client/PodThermalVision','mixin/PodThermalMixin']
        for stem in preserved:
            name='cn/zzy/loadout/'+stem+'.class';assert z.read(name)==old.read(name),name
    with zipfile.ZipFile(radar) as z:
        assert z.testzip() is None
        assert 'Implementation-Version: 1.11.1' in z.read('META-INF/MANIFEST.MF').decode()
        assert not any(n.startswith('cn/zzy/loadout/') or 'Test' in n or 'Smoke' in n or 'Fixture' in n for n in z.namelist())
    OUT.mkdir(exist_ok=True)
    artifacts=[]
    for path in (jar,radar):
        shutil.copy2(path,OUT/path.name)
        artifacts.append({'file':path.name,'bytes':path.stat().st_size,'sha256':digest(path)})
    checks={'date':'2026-10-29','version':'1.3.4','radar_version':'1.11.1','network_protocol':5,
        'radar_without_loadout_client_boot':True,
        'gametests_each_sbw_version':74,'standalone_gametests_each_sbw_version':65,
        'client_checks_each_sbw_version':14,'air_to_air_count':len(changed),'air_to_air':sorted(changed),
        'air_to_air_blast_multiplier':0.5,'impact_and_radius_unchanged':True,
        'other_ammo_unchanged':29-len(changed),'model_and_other_data_resources_unchanged':len(resources)-len(ammo),
        'preserved_controllers':preserved,'development_classes_excluded':True,'jars':artifacts}
    for path in (V/'release_checks.json',P/'docs/release-checks-1.3.4.json',OUT/'交付校验.json',R/'docs/release-checks-1.11.1.json'):
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(checks,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for version in ('0.8.9.1','0.8.9.2'):
        for name,dest in (('01_radar_trk','air-radar-trk.png'),('02_ir_lock','air-ir-lock.png')):
            src=V/f'screenshots_{version}'/f'air_{name}.png';assert src.exists(),src
            if version=='0.8.9.2':
                shutil.copy2(src,P/'docs/images'/dest)
                (OUT/'images').mkdir(exist_ok=True);shutil.copy2(src,OUT/'images'/dest)
    shutil.copy2(P/'docs/空空导弹适配_1.3.4.md',OUT/'空空导弹适配_1.3.4.md')
    readme='''# 空空导弹联动交付

同时在客户端和服务器替换 sbw-loadout-1.3.4.jar、sbw_radar-1.11.1.jar，移出旧版。MC 1.21.1、NeoForge 21.1.248，支持 SBW 0.8.9.1 / 0.8.9.2；原依赖保留。

AIM-54、PL-12、R-40、R-73、R-77 的 10 个空空型号适配雷达/红外锁定与对应干扰，爆炸伤害为 1.3.3 的 50%，直击和半径保留。Kh-38MT 空地弹、航弹和巡航弹保留；既有 9M336 雷达弹继续联动，威力保留。

O 切雷达模式、J 切 TRK 目标、P 切宽度；G 热诱弹、H 箔条。旧 options.txt 键位须手动调整。雷达弹接受敌方 TRK / TWS 航迹，红外弹对准有效热目标后发射；原有载具 Loadout 白名单仍是接入前提。SBW 无队伍驾驶员视为中立雷达目标，需设置敌我队伍。

两个 SBW 版本各通过 74 项联合 GameTest、65 项挂载独立 GameTest 和 14 项真实客户端检查。模型/贴图字节、未涉及弹药及低空巡航/航弹散布/热成像控制器已核对。正式包不含开发夹具，文件哈希见交付校验.json；操作与边界见空空导弹适配_1.3.4.md。

source-update-1.3.4.zip 为基于挂载 v1.3.3 / 雷达 1.11.0（c75df9e）的双仓库源码更新，供审阅迁移；完整源码在各 GitHub 仓库与标签。acceptance-1.3.4.zip 提供最终日志与截图。
'''
    (OUT/'README.md').write_text(readme,encoding='utf-8')
    with zipfile.ZipFile(OUT/'source-update-1.3.4.zip','w',zipfile.ZIP_DEFLATED) as z:
        for root,base,prefix in ((P,'531c659','sbw_loadout'),(R,'c75df9e','sbw_radar')):
            for name in changes(root,base):
                assert not name.startswith(('validation/','build/','run/','.gradle/','libs/')) or name=='libs/README.md',name
                assert not name.endswith(('.jar','.pyc')),name
                z.write(root/name,prefix+'/'+name)
        z.writestr('README.txt',readme)
    with zipfile.ZipFile(OUT/'acceptance-1.3.4.zip','w',zipfile.ZIP_DEFLATED) as z:
        for path in sorted(V.rglob('*')):
            if path.is_file() and (path.name in ('final_0.8.9.1.log','final_0.8.9.2.log','standalone_0.8.9.1.log','standalone_0.8.9.2.log','radar_standalone_boot.log','release_build.log','radar_build.log','release_checks.json','弹药伤害对照.csv') or path.parent.name.startswith('screenshots_')):
                z.write(path,path.relative_to(V))
        z.write(OUT/'README.md','README.md');z.write(OUT/'空空导弹适配_1.3.4.md','空空导弹适配_1.3.4.md')
        for path in (OUT/'images').glob('*.png'):z.write(path,'images/'+path.name)
    print(json.dumps(checks,ensure_ascii=False))

if __name__=='__main__':main()
