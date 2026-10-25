"""Blender 5: retain every source polygon, corner normal and UV; triangulation only, no decimation."""
import bpy,json,pathlib,sys,hashlib,collections,math
from mathutils import Vector,Matrix
args=sys.argv[sys.argv.index('--')+1:];source=pathlib.Path(args[0]).resolve();project=pathlib.Path(args[1]).resolve()
assets=project/'src/main/resources/assets/sbw_loadout';report={}
lengths={'kh38mt':4.2,'kh29td':3.9,'kab500kr':3.05,'x555':6.0}
for name,length in lengths.items():
 bpy.ops.wm.read_factory_settings(use_empty=True)
 folder=source/name;f=next(folder.glob('*.obj'),None) or next(folder.glob('*.fbx'))
 if f.suffix=='.obj':bpy.ops.wm.obj_import(filepath=str(f))
 else:bpy.ops.import_scene.fbx(filepath=str(f))
 objects=[o for o in bpy.context.scene.objects if o.type=='MESH'];lookup={t.name.lower():t for t in (folder/'textures').iterdir()}
 for im in bpy.data.images:
  base=pathlib.Path(im.filepath.replace('\\','/')).name.lower()
  if base in lookup:im.filepath=str(lookup[base]);im.reload()
 if name=='x555':
  for o in objects:
   for slot in o.material_slots:
    m=slot.material
    if not m:continue
    m.use_nodes=True;bs=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED');im=m.node_tree.nodes.new('ShaderNodeTexImage');im.image=bpy.data.images.load(str(lookup['x-555_color.png']),check_existing=True);m.node_tree.links.new(im.outputs['Color'],bs.inputs['Base Color'])
 points=[o.matrix_world@v.co for o in objects for v in o.data.vertices]
 lo=Vector([min(v[i] for v in points) for i in range(3)]);hi=Vector([max(v[i] for v in points) for i in range(3)]);center=(lo+hi)/2;axis=max(range(3),key=lambda i:hi[i]-lo[i]);extent=hi[axis]-lo[axis];scale=length/extent
 # Canonical game axis +Z follows source longest axis; rigid axis permutation preserves geometry.
 other=[i for i in range(3) if i!=axis]
 def point(v):v=(v-center)*scale;return [v[other[0]],v[other[1]],v[axis]]
 def normal(v):return [v[other[0]],v[other[1]],v[axis]]
 bones=[];materials={};triangles=0;sourcefaces=sum(len(o.data.polygons) for o in objects)
 def material(m):
  key=m.name if m else '_none'
  if key in materials:return materials[key]
  index=len(materials);image=None;alpha=None;opacity=1;rgba=[.3,.3,.3,1]
  if m and m.use_nodes:
   bs=next((n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED'),None)
   if bs:
    rgba=list(bs.inputs['Base Color'].default_value);opacity=bs.inputs['Alpha'].default_value
    for link in m.node_tree.links:
     if link.from_node.type=='TEX_IMAGE' and link.from_node.image:
      if link.to_socket.name=='Base Color':image=link.from_node.image
      if link.to_socket.name=='Alpha':alpha=link.from_node.image
  display=f'textures/bedrock/ammo/{name}/material_{index:02}.png'
  value={'texture':'sbw_loadout:'+display,'source_color':str(pathlib.Path(image.filepath).resolve()) if image else None,'source_alpha':str(pathlib.Path(alpha.filepath).resolve()) if alpha else None,'opacity':opacity,'rgba':rgba,'translucent':opacity<.99 or alpha is not None}
  materials[key]=value;return value
 for o in objects:
  mesh=o.data;mesh.calc_loop_triangles();uv=mesh.uv_layers.active;normalmat=o.matrix_world.to_3x3().inverted().transposed();groups=collections.defaultdict(list)
  for tri in mesh.loop_triangles:groups[tri.material_index].append(tri)
  for mi,tris in groups.items():
   mat=material(o.material_slots[mi].material if mi<len(o.material_slots) else None)
   positions=[];normals=[];uvs=[];polys=[];pmaps={};nmaps={};umaps={}
   def index(value,values,cache):
    key=tuple(value)
    if key not in cache:cache[key]=len(values);values.append(list(value))
    return cache[key]
   cornerhash=hashlib.sha256()
   for tri in tris:
    polygon=[]
    for vi,li in zip(tri.vertices,tri.loops):
     p=point(o.matrix_world@mesh.vertices[vi].co);n=normal((normalmat@mesh.corner_normals[li].vector).normalized());t=uv.data[li].uv if uv else Vector((0,0));coord=[t.x,1-t.y]
     polygon.append([index([x*16 for x in p],positions,pmaps),index(n,normals,nmaps),index(coord,uvs,umaps)])
    polys.append(polygon)
   bones.append({'name':f'part_{len(bones):03}','pivot':[0,0,0],'poly_mesh':{'normalized_uvs':True,'positions':positions,'normals':normals,'uvs':uvs,'polys':polys},'loadout_material':{'texture':mat['texture'],'translucent':mat['translucent']}});triangles+=len(tris)
 geo={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.sbw_loadout.'+name,'texture_width':1,'texture_height':1,'visible_bounds_width':length+2,'visible_bounds_height':length+2,'visible_bounds_offset':[0,0,0]},'bones':bones}]}
 out=assets/f'models/bedrock/ammo/{name}.geo.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(geo,separators=(',',':')))
 report[name]={'source_polygons':sourcefaces,'triangles':triangles,'vertices':sum(len(o.data.vertices) for o in objects),'length_m':length,'source_axis':axis,'uniform_scale':scale,'source_bounds':[list(lo),list(hi)],'materials':materials,'modifiers_used':[],'decimation':False,'texture_resizing':False}
 # Retain imported scene for review; geometry/material source unchanged.
 blend=project/f'model_review/{name}.blend';blend.parent.mkdir(parents=True,exist_ok=True);bpy.ops.wm.save_as_mainfile(filepath=str(blend))
 print('LOADOUT_MODEL',name,sourcefaces,triangles,flush=True)
(project/'model_review/conversion_manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
