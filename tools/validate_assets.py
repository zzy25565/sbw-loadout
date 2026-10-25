"""Verify all four models separately: geometry, texture pixels and original source hashes."""
from pathlib import Path
from PIL import Image
import json,hashlib,math
P=Path(__file__).resolve().parents[1];A=P/'src/main/resources/assets/sbw_loadout';source=P/'source_models';manifest=json.loads((P/'model_review/conversion_manifest.json').read_text());provenance=json.loads((P/'docs/input_model_sha256.json').read_text());results={}
for path,sha in provenance.items():assert hashlib.sha256((source/path).read_bytes()).hexdigest()==sha,path
for name,m in manifest.items():
 geo=json.loads((A/f'models/bedrock/ammo/{name}.geo.json').read_text())['minecraft:geometry'][0];triangles=0;allz=[]
 for bone in geo['bones']:
  pm=bone['poly_mesh'];triangles+=len(pm['polys'])
  for group in ['positions','normals','uvs']:
   for coord in pm[group]:assert all(math.isfinite(x) for x in coord)
  for face in pm['polys']:
   assert len(face)==3
   for corner in face:
    for index,group in zip(corner,['positions','normals','uvs']):assert 0<=index<len(pm[group])
  allz.extend(p[2]/16 for p in pm['positions'])
 assert triangles==m['triangles'];assert abs(max(allz)-min(allz)-m['length_m'])<1e-5;assert m['modifiers_used']==[]
 tex=[]
 for material in m['materials'].values():
  image=Image.open(A/material['texture'].split(':')[1]).convert('RGBA')
  if material['source_color']:
   original=Image.open(source/name/'textures'/Path(material['source_color']).name).convert('RGBA');assert image.size==original.size
   if material['opacity']==1 and not material['source_alpha']:assert image.tobytes()==original.tobytes()
  tex.append({'path':material['texture'],'size':image.size})
 for original in (source/name/'textures').iterdir():assert hashlib.sha256(original.read_bytes()).digest()==hashlib.sha256((A/'textures/source'/name/original.name).read_bytes()).digest()
 ammo=json.loads((P/f'src/main/resources/data/sbw_loadout/ammo/{name}.json').read_text())
 for field in ['Model','Texture','Icon']:assert (A/ammo[field].split(':')[1]).is_file(),(name,field)
 assert (A/f'models/item/ammo_{name}.json').is_file()
 results[name]={'passed':True,'source_polygons':m['source_polygons'],'triangles':triangles,'length_blocks':max(allz)-min(allz),'textures':tex,'no_decimation':True,'all_source_files_match_input':True}
out=P/'validation/asset_checks.json';out.parent.mkdir(exist_ok=True);out.write_text(json.dumps(results,ensure_ascii=False,indent=2));print('ALL FOUR MODEL CHECKS PASSED')
