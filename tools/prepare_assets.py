"""Convert diffuse containers to PNG without resizing. Preserve all original maps byte for byte."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import json,shutil,hashlib
P=Path(__file__).resolve().parents[1];A=P/'src/main/resources/assets/sbw_loadout';source=P/'source_models';manifest=json.loads((P/'model_review/conversion_manifest.json').read_text());report={}
base={'kh38mt':('Kh-38MT 空地导弹','Missile',1,620,320,8,3.2,'IR',1024),'kh29td':('Kh-29TD 空地导弹','Missile',1,560,300,7,2.6,'TV',768),'kab500kr':('KAB-500Kr 制导炸弹','GuidedBomb',2,480,420,11,1.4,'TV',512),'x555':('X-555 空射巡航导弹','CruiseMissile',3,900,700,16,2.0,'INS+GNSS+EO',2048)}
for name,m in manifest.items():
 textures=[]
 for material in m['materials'].values():
  path=A/material['texture'].split(':',1)[1];path.parent.mkdir(parents=True,exist_ok=True)
  f=Path(material['source_color']) if material['source_color'] else None
  if f:
   f=source/name/'textures'/f.name;im=Image.open(f).convert('RGBA');original=im.copy()
  else:
   rgba=[round(max(0,min(1,v))*255) for v in material['rgba']];im=Image.new('RGBA',(4,4),tuple(rgba));original=None
  alpha=im.getchannel('A')
  if material['source_alpha']:
   f=source/name/'textures'/Path(material['source_alpha']).name;mask=Image.open(f).convert('L')
   if mask.size!=im.size:raise ValueError('Alpha resolution mismatch')
   from PIL import ImageChops
   alpha=ImageChops.multiply(alpha,mask)
  opacity=material['opacity'];alpha=alpha.point(lambda v:round(v*opacity));im.putalpha(alpha);im.save(path)
  if original and not material['source_alpha'] and opacity==1:
   assert im.tobytes()==original.tobytes()
  textures.append({'path':str(path.relative_to(A)),'size':im.size,'decoded_diffuse_pixels_identical':bool(original and not material['source_alpha'] and opacity==1)})
 # Material map archive inside resources: normals/specular/AO/4K originals are not resampled.
 original_maps=A/'textures/source'/name;original_maps.mkdir(parents=True,exist_ok=True)
 for f in (source/name/'textures').iterdir():shutil.copy2(f,original_maps/f.name)
 title,category,slots,damage,explosion,radius,velocity,guidance,seek=base[name]
 ammo={'DisplayName':title,'Category':category,'Icon':f'sbw_loadout:textures/overlay/weapon/icons/{name}.png','Model':f'sbw_loadout:models/bedrock/ammo/{name}.geo.json','Texture':f'sbw_loadout:textures/bedrock/ammo/{name}/material_00.png','Slots':slots,'Projectile':'superbwarfare:agm_65','Damage':damage,'ExplosionDamage':explosion,'ExplosionRadius':radius,'Velocity':velocity,'Burst':1,'Spread':0.3,'Guidance':guidance,'SeekRange':seek,'SeekAngle':20,'Cooldown':40}
 f=P/f'src/main/resources/data/sbw_loadout/ammo/{name}.json';f.parent.mkdir(parents=True,exist_ok=True);f.write_text(json.dumps(ammo,ensure_ascii=False,indent=2))
 for item in ['ammo_'+name]:
  f=A/f'models/item/{item}.json';f.parent.mkdir(parents=True,exist_ok=True);f.write_text(json.dumps({'parent':'minecraft:builtin/entity','gui_light':'front','textures':{'particle':f'sbw_loadout:bedrock/ammo/{name}/material_00'}}))
 report[name]={'polygons':m['source_polygons'],'triangles':m['triangles'],'textures':textures,'source_sha256':{str(f.relative_to(source/name)):hashlib.sha256(f.read_bytes()).hexdigest() for f in (source/name).rglob('*') if f.is_file()}}
f=A/'models/item/ammo.json';f.write_text(json.dumps({'parent':'minecraft:builtin/entity','textures':{'particle':'sbw_loadout:bedrock/ammo/kh38mt/material_00'}}))
(A/'lang').mkdir(exist_ok=True);(A/'lang/zh_cn.json').write_text(json.dumps({'item.sbw_loadout.ammo':'自定义弹药'},ensure_ascii=False));(A/'lang/en_us.json').write_text(json.dumps({'item.sbw_loadout.ammo':'Custom Ammo'}))
(P/'model_review/asset_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print('ASSETS_OK',[(k,v['triangles']) for k,v in report.items()])
