"""Check each uploaded Blender model against its full runtime triangle-corner stream."""
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
import json,hashlib,math,struct
P=Path(__file__).resolve().parents[1];A=P/'src/main/resources/assets/sbw_loadout';report={}
m=json.loads((P/'model_review/new_ammo_conversion.json').read_text())
input_zip=P.parents[1]/'弹药模型_blend_4件.zip'
with ZipFile(input_zip) as z:
 for name,v in m.items():
  source=P/f'source_models/{name}/{name}.blend';data=source.read_bytes()
  entries=[x for x in z.namelist() if Path(x).name==name+'.blend' and not x.startswith('__MACOSX/')]
  assert len(entries)==1 and data==z.read(entries[0]),name
  assert hashlib.sha256(data).hexdigest()==v['source_sha256']
  geo=json.loads((A/f'models/bedrock/ammo/{name}.geo.json').read_text())['minecraft:geometry'][0]
  digest=hashlib.sha256();triangles=0;allz=[]
  for bone in geo['bones']:
   pm=bone['poly_mesh'];triangles+=len(pm['polys']);allz.extend(p[2]/16 for p in pm['positions'])
   for group in ['positions','normals','uvs']:
    for coord in pm[group]:assert all(math.isfinite(x) for x in coord)
   for n in pm['normals']:assert abs(sum(x*x for x in n)-1)<2e-5
   for face in pm['polys']:
    assert len(face)==3
    for pi,ni,ti in face:
     assert 0<=pi<len(pm['positions']) and 0<=ni<len(pm['normals']) and 0<=ti<len(pm['uvs'])
     digest.update(struct.pack('<8d',*pm['positions'][pi],*pm['normals'][ni],*pm['uvs'][ti]))
  assert triangles==v['triangles']==v['source_polygons']==v['covered_source_polygons']
  assert digest.hexdigest()==v['corner_sha256']==v['export_corner_sha256']
  assert abs(max(allz)-min(allz)-v['length_m'])<1e-5
  assert v['uniform_scale']==1 and not v['decimation'] and v['images_in_source']==0
  for material in v['materials'].values():
   image=Image.open(A/material['texture'].split(':')[1]).convert('RGBA');assert image.size==(1,1) and list(image.getpixel((0,0)))==material['rgba_srgb8']
  active=name!='kh58ushk';jpath=P/f'src/main/resources/data/sbw_loadout/ammo/{name}.json'
  if active:
   ammo=json.loads(jpath.read_text());assert ammo['Guidance']==('LASER' if name=='kh29l' else 'TV');assert ammo['RenderProjectileModel']
   for field in ['Model','Texture','Icon']:assert (A/ammo[field].split(':')[1]).is_file()
   assert (A/f'models/item/ammo_{name}.json').is_file()
  else:
   assert not jpath.exists() and not (A/'models/item/ammo_kh58ushk.json').exists()
  image=Image.open(P/f'model_review/{name}.png');assert image.size==(800,480)
  report[name]={'passed':True,'playable':active,'source_vertices':v['source_vertices'],'all_source_triangles_preserved':triangles,'corner_stream_sha256':digest.hexdigest(),'source_blend_identical_to_uploaded_zip':True,'length_blocks':v['length_m'],'source_images':0,'material_swatches_srgb_verified':True,'no_decimation':True,'no_texture_downsampling':True}
report['input_zip_sha256']=hashlib.sha256(input_zip.read_bytes()).hexdigest()
(P/'validation/new_ammo_asset_checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('ALL FOUR NEW MODEL CHECKS PASSED', {k:v['all_source_triangles_preserved'] for k,v in report.items() if isinstance(v,dict)})
