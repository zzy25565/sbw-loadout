"""Plot measured in-game trajectories and summarize actual collision results."""
from pathlib import Path
import json, csv
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.font_manager import FontProperties

P=Path(__file__).resolve().parents[1]
V=P/'validation/low_altitude_1.3.0'
FONT=FontProperties(fname=r'C:\Windows\Fonts\msyh.ttc')
LABELS={
 'flat_480_high':'高空投放 / 480 格','flat_320_low':'低空投放 / 320 格',
 'left_offset':'左偏置目标','right_offset':'右偏置目标','near_40':'近目标 / 40 格',
 'midflight_reload':'中途存档读档','x555_low_cruise':'X-555 / 480 格',
 'ridge_21_blocks':'21 格高山脊','thin_wall_1_block':'单格厚障碍',
 'water_surface':'水面高度变化','moving_crossing_target':'横向移动目标',
 'moving_crossing_reverse':'反向移动目标','moving_vehicle_obb':'移动 LAV-25 / 原生 OBB'}

def success(r):
    return r['hitEntity'] if r['scenario'].startswith('moving_') else r['hit'] and 0<=r['error']<=1.5

datasets={}
for version in ('0.8.9.1','0.8.9.2'):
    log=(V/f'gametest_{version}.log').read_text(encoding='utf-8',errors='replace')
    assert 'All 51 required tests passed' in log,version
    rows=json.loads((V/f'trajectory_metrics_{version}.json').read_text(encoding='utf-8'))
    assert len(rows)==13 and len({r['scenario'] for r in rows})==13
    assert all(success(r) for r in rows),version
    datasets[version]=rows

rows=datasets['0.8.9.1'];by_name={r['scenario']:r for r in rows}
plt.rcParams.update({'axes.unicode_minus':False,'font.family':FONT.get_name(),'font.size':10})
fig,axes=plt.subplots(2,3,figsize=(15,8.8),layout='constrained')
for ax,name in zip(axes.flat[:4],('flat_480_high','ridge_21_blocks','thin_wall_1_block','water_surface')):
    r=by_name[name];s=r['samples'];z=[x['z'] for x in s];y=[x['y'] for x in s];floor=[x['floor'] for x in s]
    ax.fill_between(z,0,floor,color='#b4a68b',step='mid',label='实际地形/水面')
    ax.plot(z,y,color='#156f9c',linewidth=2,label='实际飞行轨迹')
    ax.axhline(8,color='#7ca987',linestyle='--',linewidth=1,label='平地巡航目标高度')
    terminal=next((x for x in s if x['phase']=='TERMINAL'),None)
    if terminal:ax.axvline(terminal['z'],color='#c87531',linestyle=':',label='末段开始')
    ax.set_title(LABELS[name]);ax.set_xlabel('前向距离 / 格');ax.set_ylabel('相对基准地面高度 / 格');ax.grid(alpha=.15)
axes[0,0].legend(fontsize=8)
ax=axes[1,1]
for name,color in [('moving_crossing_target','#156f9c'),('moving_crossing_reverse','#b85935'),('moving_vehicle_obb','#398263')]:
    r=by_name[name];s=r['samples'];ax.plot([x['z'] for x in s],[x['x'] for x in s],color=color,label=LABELS[name])
    ax.scatter(s[-1]['z'],s[-1]['x'],color=color,marker='x',s=40)
ax.set_title('移动目标：真实接触命中');ax.set_xlabel('前向距离 / 格');ax.set_ylabel('横向位置 / 格');ax.legend(fontsize=8);ax.grid(alpha=.15)
ax=axes[1,2]
for name in ('flat_480_high','ridge_21_blocks','moving_crossing_target'):
    s=by_name[name]['samples'];ax.plot([x['tick']/20 for x in s],[x['speed'] for x in s],label=LABELS[name])
ax.axhline(1.4,color='#b85935',linestyle='--',linewidth=1);ax.set_ylim(0,1.65)
ax.set_title('独立发动机速度');ax.set_xlabel('游戏时间 / 秒（20 TPS）');ax.set_ylabel('速度 / 格每刻');ax.legend(fontsize=8);ax.grid(alpha=.15)
fig.suptitle('低空巡航：原生游戏碰撞与逐刻采样验证',fontproperties=FONT,fontsize=17)
fig.savefig(V/'轨迹验证.png',dpi=160)
plt.close(fig)

summary=[]
for version,data in datasets.items():
    for r in data:
        summary.append({'SBW':version,'场景':LABELS[r['scenario']], '命中':success(r),
            '目标类型':'实体接触' if r['scenario'].startswith('moving_') else '指定方块表面',
            '接触点距目标点或中心_格':r['error'],'峰值速度_格每刻':r['maxSpeed'],
            '峰值转向_度每刻':r['maxTurn'],'末段前最小净空_格':r['minPreTerminalClearance'],
            '飞行刻数':len(r['samples'])})
with (V/'碰撞验证明细.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=list(summary[0]));writer.writeheader();writer.writerows(summary)
report=['# 1.3.0 低空巡航验证结果','', '完成日期：2026-10-27。环境为 Minecraft 1.21.1、NeoForge 21.1.248、JDK 21。',
    '', 'SBW 0.8.9.1 与 0.8.9.2 均通过 51/51 项 GameTest，其中包含每个版本 13 个完整飞行场景；所有场景都在实际弹体 tick 和碰撞事件下通过，两个版本合计 26/26。',
    '', '下表的固定点误差是首次碰撞点与指定表面的距离；移动目标必须在事件中确认同一目标实体 UUID，所列距离是接触点到目标中心，不能把车辆外壳距中心约 4 格解释成脱靶。没有用扩大判定框或额外近炸计分。',
    '', '| 场景 | 0.8.9.1 | 0.8.9.2 | 接触点距离/格（0.8.9.1） |', '|---|---:|---:|---:|']
second={r['scenario']:r for r in datasets['0.8.9.2']}
for r in rows:
    report.append(f"| {LABELS[r['scenario']]} | {'命中' if success(r) else '失败'} | {'命中' if success(second[r['scenario']]) else '失败'} | {'<0.001' if r['error']<.001 else format(r['error'],'.3f')} |")
clearance=min(r['minPreTerminalClearance'] for r in rows if r['minPreTerminalClearance']>=0)
report+=['',f'记录中的巡航峰值速度为 {max(r["maxSpeed"] for r in rows):.3f} 格/刻，峰值转向为 {max(r["maxTurn"] for r in rows):.3f} 度/刻；长航段已实际进入持续低空巡航。末段前各场景实测最小净空为 {clearance:.3f} 格，目标净空为 8 格。',
    '', '额外回归覆盖：无效参数、普通弹药不启用巡航、完整 JSON 同步、存档/BVR 快照、外观 ID、无加载区块时暂停而不生成区块、寿命和维度异常清理、近防炮选中和击毁、普通移动身体与旧位置空间的真实/虚假碰撞区分。',
    '', '真实客户端隔离世界通过原挂载入口锁定、投放 Kh-59MK，驾驶员离座后继续飞行，记录四个阶段并命中原锁定实体；完整模型显示及速度上限断言通过。对应原始截图在 screenshots/，原始运行日志为 client_flight.log。',
    '', '这些是确定的游戏场景回归数据，不是随机地图的统计命中概率，也不代表任何地图、目标机动或未加载区域下的 100% 命中保证。前视高度规划不具备横向迷宫寻路能力；具体边界见《低空巡航弹道.md》。',
    '', '![轨迹验证](轨迹验证.png)']
(V/'验证结果.md').write_text('\n'.join(report)+'\n',encoding='utf-8')
(V/'summary.json').write_text(json.dumps({'date':'2026-10-27','gametest_per_version':51,
    'trajectory_scenarios_per_version':13,'scored_contacts':26,'successful_contacts':26,
    'max_speed':max(r['maxSpeed'] for r in rows),'max_turn_degrees':max(r['maxTurn'] for r in rows),
    'min_preterminal_clearance':clearance,'versions':list(datasets)},ensure_ascii=False,indent=2),encoding='utf-8')
print('CRUISE_REPORT_VERIFIED',len(summary),'scored flight scenarios')
