import bpy,json,pathlib,sys,math
from mathutils import Vector
P=pathlib.Path(sys.argv[sys.argv.index('--')+1]).resolve();A=P/'src/main/resources/assets/sbw_loadout'
for name in (sys.argv[sys.argv.index('--')+2:] or ['kh29l','kab1500kr','kh59mk','kh58ushk']):
 bpy.ops.wm.read_factory_settings(use_empty=True)
 geo=json.loads((A/f'models/bedrock/ammo/{name}.geo.json').read_text())['minecraft:geometry'][0]
 for bone in geo['bones']:
  pm=bone['poly_mesh'];vertices=[(-p[0]/16,p[2]/16,p[1]/16) for p in pm['positions']];faces=[[c[0] for c in poly] for poly in pm['polys']]
  mesh=bpy.data.meshes.new(bone['name']);mesh.from_pydata(vertices,[],faces);mesh.update();ob=bpy.data.objects.new(bone['name'],mesh);bpy.context.collection.objects.link(ob)
  uv=mesh.uv_layers.new(name='UVMap')
  for poly,indices in zip(mesh.polygons,pm['polys']):
   for li,c in zip(poly.loop_indices,indices):u,v=pm['uvs'][c[2]];uv.data[li].uv=(u,1-v)
  normals=[(-pm['normals'][c[1]][0],pm['normals'][c[1]][2],pm['normals'][c[1]][1]) for poly in pm['polys'] for c in poly]
  for poly in mesh.polygons:poly.use_smooth=True
  mesh.normals_split_custom_set(normals)
  material=bpy.data.materials.new(bone['name']);material.use_nodes=True;bs=material.node_tree.nodes.get('Principled BSDF');texture=material.node_tree.nodes.new('ShaderNodeTexImage');texture.image=bpy.data.images.load(str(A/bone['loadout_material']['texture'].split(':')[1]));material.node_tree.links.new(texture.outputs['Color'],bs.inputs['Base Color']);material.node_tree.links.new(texture.outputs['Alpha'],bs.inputs['Alpha']);bs.inputs['Roughness'].default_value=1;bs.inputs['Metallic'].default_value=0;ob.data.materials.append(material)
 length=json.loads((P/'model_review/new_ammo_conversion.json').read_text())[name]['length_m']
 scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=16;scene.render.resolution_x=800;scene.render.resolution_y=480;scene.render.resolution_percentage=100;scene.render.film_transparent=True
 scene.view_settings.view_transform='Standard';scene.view_settings.look='None';scene.world=bpy.data.worlds.new('World');scene.world.use_nodes=True;scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.28,.32,.4,1);scene.world.node_tree.nodes['Background'].inputs[1].default_value=.7
 for loc,power,size in [((length,-length,length),900,6),((-length,0,length*.4),500,5),((0,length,length),700,4)]:
  data=bpy.data.lights.new('studio','AREA');data.energy=power;data.shape='DISK';data.size=size;ob=bpy.data.objects.new('studio',data);scene.collection.objects.link(ob);ob.location=loc;ob.rotation_euler=(-ob.location).to_track_quat('-Z','Y').to_euler()
 data=bpy.data.cameras.new('camera');camera=bpy.data.objects.new('camera',data);scene.collection.objects.link(camera);scene.camera=camera;camera.location=(length*.85,-length*.65,length*.55);camera.rotation_euler=(-camera.location).to_track_quat('-Z','Y').to_euler();data.type='ORTHO';data.ortho_scale=length*1.08
 scene.render.filepath=str(P/f'model_review/{name}.png');bpy.ops.render.render(write_still=True)
 print('RENDERED',name,flush=True)
