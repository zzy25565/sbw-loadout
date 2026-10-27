"""Strict OBJ to loadout poly_mesh conversion. Requires numpy.
Preserves triangles and source UV continuity; does not infer missing textures.
"""
import argparse, collections, hashlib, json, math, pathlib
import numpy as np

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def parse_obj(path, split_material=True):
    arrays = [[], [], []]  # v, vn, vt: same order as renderer's [position, normal, uv]
    groups = collections.OrderedDict()
    group, material = 'root', '_missing'
    for line_no, line in enumerate(path.read_text(encoding='utf-8-sig').splitlines(), 1):
        p = line.split()
        if not p or p[0].startswith('#'): continue
        if p[0] in ('v', 'vn', 'vt'):
            slot = {'v': 0, 'vn': 1, 'vt': 2}[p[0]]
            size = 2 if slot == 2 else 3
            value = tuple(map(float, p[1:1+size]))
            if len(value) != size or not all(math.isfinite(x) for x in value):
                raise ValueError(f'{path}:{line_no}: invalid {p[0]}')
            arrays[slot].append(value)
        elif p[0] in ('g', 'o'): group = ' '.join(p[1:]) or 'root'
        elif p[0] == 'usemtl': material = ' '.join(p[1:])
        elif p[0] == 'f':
            if len(p) != 4: raise ValueError(f'{path}:{line_no}: non-triangle; triangulate explicitly')
            face = []
            for token in p[1:]:
                values = token.split('/')
                if len(values) != 3 or any(not x for x in values):
                    raise ValueError(f'{path}:{line_no}: missing position/UV/normal')
                corner = []
                for raw, slot in zip((values[0], values[2], values[1]), range(3)):
                    i = int(raw)
                    if i == 0: raise ValueError(f'{path}:{line_no}: zero OBJ index')
                    i = i - 1 if i > 0 else len(arrays[slot]) + i
                    # Positive indices may refer to arrays declared later; validate after parse.
                    corner.append(i)
                face.append(corner)
            groups.setdefault((group, material if split_material else '_all'), []).append(face)
    for key, faces in groups.items():
        a = np.asarray(faces, dtype=np.int64)
        for k in range(3):
            if (a[:,:,k] < 0).any() or (a[:,:,k] >= len(arrays[k])).any():
                raise ValueError(f'{path}: invalid {key} index, stream {k}')
    return arrays, groups

def convert_one(src, out, id, category, texture):
    arrays, groups = parse_obj(src)
    p, normals, uv = [np.asarray(a,dtype=float) for a in arrays]
    center=(p.min(axis=0)+p.max(axis=0))/2 if category=='ammo' else np.array([0.,0.,0.])
    # determinant +1: +X -> +Z, source +Y remains up. 16 geo units = 1 block.
    def point(value):
        v=(np.asarray(value)-center); return tuple(float(x) for x in (-16*v[2],16*v[1],16*v[0]))
    repaired_zero_normals=0
    def norm(value, fallback):
        nonlocal repaired_zero_normals
        v=np.asarray(value); length=np.linalg.norm(v)
        if length<1e-10:
            repaired_zero_normals+=1
            v=fallback; length=np.linalg.norm(v)
            if length<1e-10: v=np.array([0.,1.,0.]); length=1.
        v=v/length; return tuple(float(x) for x in (-v[2],v[1],v[0]))
    bones=[]; digest=hashlib.sha256(); output_digest=hashlib.sha256(); count=0
    for part_no,((group,mat),faces) in enumerate(groups.items()):
        values=[[],[],[]]; maps=[{}, {}, {}]; polys=[]
        for face in faces:
            poly=[]
            triangle=p[np.asarray(face)[:,0]]
            fallback=np.cross(triangle[1]-triangle[0],triangle[2]-triangle[0])
            for pi,ni,ti in face:
                v=[point(p[pi]),norm(normals[ni],fallback),(float(uv[ti][0]),float(1-uv[ti][1]))]
                v=[tuple(x if x != 0 else 0.0 for x in stream) for stream in v]
                digest.update(np.asarray([*v[0],*v[1],*v[2]],dtype='<f8').tobytes())
                corner=[]
                for k,item in enumerate(v):
                    if item not in maps[k]: maps[k][item]=len(values[k]); values[k].append(item)
                    corner.append(maps[k][item])
                poly.append(corner)
            polys.append(poly)
        for face in polys:
            for pi,ni,ti in face:
                output_digest.update(np.asarray([*values[0][pi],*values[1][ni],*values[2][ti]],dtype='<f8').tobytes())
        bones.append({'name':f'part_{part_no:03d}','pivot':[0,0,0], 'review_source':{'object':group,'material':mat,'mapping_status':'unresolved'},'loadout_material':{'texture':texture,'translucent':False},'poly_mesh':{'normalized_uvs':True,'positions':values[0],'normals':values[1],'uvs':values[2],'polys':polys}})
        count+=len(faces)
    assert digest.digest()==output_digest.digest(),'corner stream changed'
    points=np.array([point(x) for x in p])/16; lo=points.min(axis=0); hi=points.max(axis=0)
    doc={'format_version':'1.12.0','minecraft:geometry':[{'description':{'identifier':f'geometry.sbw_loadout.{id}' if category=='ammo' else f'geometry.review.{id}','texture_width':1,'texture_height':1,'visible_bounds_width':float(max((hi-lo)[0],(hi-lo)[2]))+2,'visible_bounds_height':float(hi[1]-lo[1])+2,'visible_bounds_offset':((hi+lo)/2).tolist()},'bones':bones}]}
    write_json(out,doc)
    return {'id':id,'source':str(src),'source_sha256':sha(src),'triangles':count,'material_groups':len(groups),'source_materials':sorted(set(m for _,m in groups)),'corner_sha256':digest.hexdigest(),'output_corner_sha256':output_digest.hexdigest(),'length_blocks':float(hi[2]-lo[2]),'bbox_blocks':(hi-lo).tolist(),'geometry_sha256':sha(out),'source_uv_transform':'(u,1-v), no modulo','scale_geo_units_per_source_metre':16,'decimation':False,'zero_normal_corner_repairs':repaired_zero_normals,'material_status':'neutral preview; original material-texture mapping unavailable'}

if __name__=='__main__':
    ap=argparse.ArgumentParser()
    ap.add_argument('source',type=pathlib.Path)
    ap.add_argument('output',type=pathlib.Path)
    ap.add_argument('--id',required=True)
    ap.add_argument('--category',choices=['ammo','vehicles'],default='ammo')
    ap.add_argument('--texture',default='sbw_loadout:textures/bedrock/ammo/wt_review/neutral.png')
    args=ap.parse_args()
    result=convert_one(args.source,args.output,args.id,args.category,args.texture)
    write_json(args.output.with_suffix('.conversion.json'),result)
    print(json.dumps(result,ensure_ascii=False,indent=2))
