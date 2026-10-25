"""Register only the three requested active munitions; Kh-58UShK remains assets only."""
from pathlib import Path
from PIL import Image,ImageDraw
import json
P=Path(__file__).resolve().parents[1];A=P/'src/main/resources/assets/sbw_loadout'
defs={
 'kh29l':('Kh-29L 激光制导导弹','Missile',1,560,300,7,2.6,'LASER',768,'agm_65'),
 'kab1500kr':('KAB-1500Kr 电视制导航弹','GuidedBomb',3,750,800,18,1.4,'TV',768,'mk_82'),
 'kh59mk':('Kh-59MK 电视制导导弹','CruiseMissile',2,680,500,12,2.2,'TV',1536,'agm_65')}
for name,values in defs.items():
 title,category,slots,damage,explosion,radius,velocity,guidance,seek,projectile=values
 j={'DisplayName':title,'Category':category,'Icon':f'sbw_loadout:textures/overlay/weapon/icons/{name}.png','Model':f'sbw_loadout:models/bedrock/ammo/{name}.geo.json','Texture':f'sbw_loadout:textures/bedrock/ammo/{name}/material_00.png','RenderProjectileModel':True,'Slots':slots,'Projectile':'superbwarfare:'+projectile,'Damage':damage,'ExplosionDamage':explosion,'ExplosionRadius':radius,'Velocity':velocity,'Burst':1,'Spread':0.3,'Guidance':guidance,'SeekRange':seek,'SeekAngle':25,'TurnRate':4 if name=='kab1500kr' else 5,'LaserCode':1688,'Cooldown':40}
 (P/f'src/main/resources/data/sbw_loadout/ammo/{name}.json').write_text(json.dumps(j,ensure_ascii=False,indent=2)+'\n')
 (A/f'models/item/ammo_{name}.json').write_text(json.dumps({'parent':'minecraft:builtin/entity','gui_light':'front','textures':{'particle':f'sbw_loadout:bedrock/ammo/{name}/material_00'}})+'\n')
 for lang in ['zh_cn','en_us']:
  f=A/f'lang/{lang}.json';l=json.loads(f.read_text());l[f'item.sbw_loadout.ammo_{name}']=title if lang=='zh_cn' else name;l[f'key.sbw_loadout.pod_view']='光电吊舱视角' if lang=='zh_cn' else 'Targeting pod view';f.write_text(json.dumps(l,ensure_ascii=False,indent=2)+'\n')
 # Keep the native SBW HUD 16x16 icon footprint; the original 3D model is never downsized.
 im=Image.open(P/f'model_review/{name}.png').convert('RGBA');box=im.getbbox();im=im.crop(box);im.thumbnail((16,14),Image.Resampling.LANCZOS);icon=Image.new('RGBA',(16,16));icon.alpha_composite(im,((16-im.width)//2,(16-im.height)//2));icon.save(A/f'textures/overlay/weapon/icons/{name}.png')
# Hidden resource catalogue is outside data/*/ammo, so /reload cannot expose this round.
f=A/'models/bedrock/ammo/reserved.json';f.write_text(json.dumps({'kh58ushk':{'model':'sbw_loadout:models/bedrock/ammo/kh58ushk.geo.json','texture':'sbw_loadout:textures/bedrock/ammo/kh58ushk/material_00.png','registered':False,'reason':'Reserved for future anti-radiation guidance'}},ensure_ascii=False,indent=2)+'\n')
im=Image.new('RGB',(1000,650),'#233142');d=ImageDraw.Draw(im)
for n,name in enumerate(['kh29l','kab1500kr','kh59mk','kh58ushk']):
 a=Image.open(P/f'model_review/{name}.png');a.thumbnail((500,300));x=n%2*500;y=n//2*325;im.paste(a,(x,y+20),a);d.text((x+12,y+8),name+(' (reserved)' if name=='kh58ushk' else ''),fill='white')
im.save(P/'model_review/new_four_models.png')
print('ACTIVE',list(defs),'RESERVED kh58ushk')
