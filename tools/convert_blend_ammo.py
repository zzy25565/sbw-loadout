"""Blender: preserve all evaluated triangle corners, normals and UVs; no decimation.
Only rigid right-handed axis rotation and recentering; one Blender metre = one block.
Original files have uniform materials, no images: export exact base-colour swatches.
"""
import bpy, json, pathlib, sys, hashlib, collections, math, struct, zlib
from mathutils import Vector
P=pathlib.Path(sys.argv[sys.argv.index('--')+1]).resolve()
A=P/'src/main/resources/assets/sbw_loadout'
report={}
def png(path, rgba):
 def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
 path.parent.mkdir(parents=True,exist_ok=True)
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,6,0,0,0))+chunk(b'sRGB',b'\0')+chunk(b'IDAT',zlib.compress(bytes([0,*rgba])))+chunk(b'IEND',b''))
def srgb(v):return round(255*(12.92*v if v<=.0031308 else 1.055*v**(1/2.4)-.055))
for name in ['kh29l','kab1500kr','kh59mk','kh58ushk']:
 f=P/f'source_models/{name}/{name}.blend'
 bpy.ops.wm.open_mainfile(filepath=str(f),load_ui=False,use_scripts=False)
 objects=[o for o in bpy.context.scene.objects if o.type=='MESH']
 assert all(not o.modifiers for o in objects),'Review modifiers before exporting'
 assert not list(bpy.data.images),'This converter handles only the audited untextured models'
 points=[o.matrix_world@v.co for o in objects for v in o.data.vertices]
 lo=Vector([min(v[i] for v in points) for i in range(3)]);hi=Vector([max(v[i] for v in points) for i in range(3)]);center=(lo+hi)/2
 # Nose is determined from review renders; +X becomes game +Z initially.
 sign=1
 def point(v):v=v-center;return [sign*v.y,v.z,sign*v.x]
 def normal(v):return [sign*v.y,v.z,sign*v.x]
 bones=[];materials={};triangles=0;covered=set();cornerhash=hashlib.sha256();exporthash=hashlib.sha256()
 def material(m):
  key=m.name if m else '_none'
  if key not in materials:
   bs=m.node_tree.nodes.get('Principled BSDF') if m and m.use_nodes else None
   rgba=list(bs.inputs['Base Color'].default_value if bs else (m.diffuse_color if m else (.8,.8,.8,1)))
   alpha=bs.inputs['Alpha'].default_value if bs else rgba[3]
   texture=f'sbw_loadout:textures/bedrock/ammo/{name}/material_{len(materials):02}.png'
   pixels=[srgb(max(0,min(1,x))) for x in rgba[:3]]+[round(alpha*255)]
   png(A/texture.split(':')[1],pixels)
   materials[key]={'texture':texture,'rgba_linear':rgba,'rgba_srgb8':pixels,'alpha':alpha,'metallic':float(bs.inputs['Metallic'].default_value) if bs else 0,'roughness':float(bs.inputs['Roughness'].default_value) if bs else 1}
  return materials[key]
 for o in objects:
  mesh=o.data;mesh.calc_loop_triangles();uv=mesh.uv_layers.active;nm=o.matrix_world.to_3x3().inverted().transposed();groups=collections.defaultdict(list)
  for tri in mesh.loop_triangles:groups[tri.material_index].append(tri);covered.add((o.name,tri.polygon_index))
  for mi,tris in groups.items():
   mat=material(o.material_slots[mi].material if mi<len(o.material_slots) else None)
   pos=[];norm=[];tex=[];polys=[];maps=[{},{},{}]
   def index(v,values,i):
    k=tuple(v)
    if k not in maps[i]:maps[i][k]=len(values);values.append(list(v))
    return maps[i][k]
   for tri in tris:
    poly=[]
    for vi,li in zip(tri.vertices,tri.loops):
     v=point(o.matrix_world@mesh.vertices[vi].co);n=normal((nm@mesh.corner_normals[li].vector).normalized());t=uv.data[li].uv if uv else Vector((0,0));t=[t.x,1-t.y]
     pp=[x*16 for x in v];poly.append([index(pp,pos,0),index(n,norm,1),index(t,tex,2)])
     cornerhash.update(struct.pack('<8d',*pp,*n,*t))
    polys.append(poly)
   pm={'normalized_uvs':True,'positions':pos,'normals':norm,'uvs':tex,'polys':polys}
   for poly in polys:
    for pi,ni,ti in poly:exporthash.update(struct.pack('<8d',*pos[pi],*norm[ni],*tex[ti]))
   bones.append({'name':f'part_{len(bones):03}','pivot':[0,0,0],'poly_mesh':pm,'loadout_material':{'texture':mat['texture'],'translucent':mat['alpha']<1}});triangles+=len(tris)
 faces=sum(len(o.data.polygons) for o in objects)
 assert len(covered)==faces and cornerhash.digest()==exporthash.digest()
 geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.sbw_loadout.'+name,'texture_width':1,'texture_height':1,'visible_bounds_width':float(hi.x-lo.x)+2,'visible_bounds_height':float(hi.x-lo.x)+2,'visible_bounds_offset':[0,0,0]},'bones':bones}]}
 out=A/f'models/bedrock/ammo/{name}.geo.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(geo,separators=(',',':')))
 report[name]={'source_sha256':hashlib.sha256(f.read_bytes()).hexdigest(),'source_vertices':sum(len(o.data.vertices) for o in objects),'source_polygons':faces,'covered_source_polygons':len(covered),'triangles':triangles,'length_m':float(hi.x-lo.x),'source_bounds':[list(lo),list(hi)],'rotation':'game=(sourceY,sourceZ,sourceX); det=+1','uniform_scale':1,'decimation':False,'images_in_source':0,'texture_resizing':False,'corner_sha256':cornerhash.hexdigest(),'export_corner_sha256':exporthash.hexdigest(),'materials':materials,'playable':name!='kh58ushk'}
 print('FULL_MESH_EXPORTED',name,faces,triangles,flush=True)
(P/'model_review/new_ammo_conversion.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
