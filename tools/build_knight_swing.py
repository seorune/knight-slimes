"""Local pixel-part derivative; no Pillow, numpy, image model, or effect arc."""
import math
from pathlib import Path
from build_pixel_assets import decode_png, encode_png, assemble_sheet, alpha_bbox, resize_nearest
ROOT=Path(__file__).resolve().parents[1]
SOURCE='assets/art/pixel_knight/knight/frame_00.png'
OUT='assets/art/knight_swing'
POSES=[('ready_raising',(33,36),(43,33),(59,12)),
       ('overhead_ready',(28,33),(37,29),(26,4)),
       ('early_downcut',(43,35),(52,32),(73,15)),
       ('horizontal_strike',(44,41),(54,43),(81,43)),
       ('low_followthrough',(43,46),(51,49),(74,63)),
       ('lower_end',(42,49),(48,48),(63,70)),
       ('retract',(31,45),(35,43),(54,24)),
       ('return_ready',(33,39),(43,37),(60,16))]
INK=(6,5,18,255); DARK=(72,71,83,255); STEEL=(139,140,152,255)
LIGHT=(207,211,218,255); EDGE=(243,240,241,255)
NAVY=(25,29,86,255); GOLD=(196,160,108,255); GRIP=(64,56,70,255)

def put(buf,x,y,color):
    x,y=int(x),int(y)
    if not (0<=x<96 and 0<=y<96): raise ValueError('Clipped authored limb')
    buf[(y*96+x)*4:(y*96+x+1)*4]=bytes(color)

def line(buf,a,b,r,color):
    ax,ay=a;bx,by=b;dx,dy=bx-ax,by-ay;length=dx*dx+dy*dy
    for y in range(math.floor(min(ay,by)-r),math.ceil(max(ay,by)+r)+1):
        for x in range(math.floor(min(ax,bx)-r),math.ceil(max(ax,bx)+r)+1):
            t=max(0,min(1,((x-ax)*dx+(y-ay)*dy)/length)) if length else 0
            if (x-ax-t*dx)**2+(y-ay-t*dy)**2<=r*r: put(buf,x,y,color)

def polygon(buf,points,color):
    for y in range(math.floor(min(p[1] for p in points)),math.ceil(max(p[1] for p in points))+1):
        for x in range(math.floor(min(p[0] for p in points)),math.ceil(max(p[0] for p in points))+1):
            inside=False;j=len(points)-1
            for i,(xi,yi) in enumerate(points):
                xj,yj=points[j]
                if (yi>y+.5)!=(yj>y+.5) and x+.5<(xj-xi)*(y+.5-yi)/(yj-yi)+xi: inside=not inside
                j=i
            if inside: put(buf,x,y,color)

def body_layer(source):
    if source[:2]!=(64,64): raise ValueError('Expected original 64px knight')
    p=source[2];out=bytearray(96*96*4);removed=[]
    for y in range(64):
        for x in range(64):
            blade=(y<27 and x>=40) or (y==27 and x>=41) or (y==28 and x>=44)
            arm=(24<=y<=29 and x<23) or (30<=y<=38 and x<20)
            if blade or arm:
                if p[(y*64+x)*4+3]: removed.append([x,y])
            else: put(out,x+16,y+16,p[(y*64+x)*4:(y*64+x+1)*4])
    return (96,96,bytes(out)),removed

def draw_arm(buf,elbow,wrist,tip):
    shoulder=(36,41)
    line(buf,shoulder,elbow,3.2,INK);line(buf,shoulder,elbow,2.2,DARK)
    line(buf,(35,40),(elbow[0]-1,elbow[1]-1),1.1,STEEL)
    line(buf,elbow,elbow,2.5,INK);line(buf,elbow,elbow,1.4,NAVY)
    line(buf,elbow,wrist,2.9,INK);line(buf,elbow,wrist,1.9,STEEL)
    line(buf,(elbow[0]-1,elbow[1]-1),(wrist[0]-1,wrist[1]-1),.8,LIGHT)
    dx,dy=tip[0]-wrist[0],tip[1]-wrist[1];length=math.hypot(dx,dy)
    ux,uy=dx/length,dy/length;nx,ny=-uy,ux
    guard=(wrist[0]+ux*4,wrist[1]+uy*4)
    base=(guard[0]+ux*1.8,guard[1]+uy*1.8)
    line(buf,(wrist[0]-ux*3,wrist[1]-uy*3),guard,1.4,INK)
    line(buf,wrist,guard,.7,GRIP)
    polygon(buf,[(base[0]+nx*1.8,base[1]+ny*1.8),tip,(base[0]-nx*1.8,base[1]-ny*1.8)],DARK)
    polygon(buf,[(base[0]+nx,base[1]+ny),tip,base],LIGHT)
    polygon(buf,[base,tip,(base[0]-nx,base[1]-ny)],EDGE)
    line(buf,base,tip,.6,LIGHT)
    put(buf,*tip,EDGE)
    line(buf,(guard[0]-nx*4,guard[1]-ny*4),(guard[0]+nx*4,guard[1]+ny*4),1.2,INK)
    line(buf,(guard[0]-nx*3,guard[1]-ny*3),(guard[0]+nx*3,guard[1]+ny*3),.5,GOLD)
    line(buf,wrist,wrist,2.6,INK);line(buf,wrist,wrist,1.7,STEEL)
    put(buf,wrist[0]-1,wrist[1]-1,LIGHT);put(buf,wrist[0],wrist[1]+1,DARK)

def make_frames(source):
    body,_=body_layer(source);frames=[];metadata=[]
    for i,(name,elbow,wrist,tip) in enumerate(POSES):
        pixels=bytearray(body[2]);draw_arm(pixels,elbow,wrist,tip)
        # Only the low grip crosses in front of the shield's interior. Keep
        # its outer rim, anchors, and the underlying source layer untouched.
        foreground=bytearray(96*96*4)
        if i in (4,5): draw_arm(foreground,elbow,wrist,tip)
        for y in range(29,41):
            for x in range(35,45):
                rgba=source[2][(y*64+x)*4:(y*64+x+1)*4]
                if rgba[3]: put(pixels,x+16,y+16,rgba)
        if i in (4,5):
            for y in range(46,56):
                for x in range(52,60):
                    rgba=foreground[(y*96+x)*4:(y*96+x+1)*4]
                    if rgba[3]: put(pixels,x,y,rgba)
        frames.append((96,96,bytes(pixels)))
        metadata.append({'index':i,'pose':name,'shoulder':[36,41],'elbow':list(elbow),
                         'wrist':list(wrist),'tip':list(tip),'root_x':42,'baseline':70,
                         'body_offset':[16,16],'active':i in (2,3,4),'clipped':False})
    return frames,metadata


def build_pack(root=ROOT):
    import hashlib,json
    root=Path(root).resolve();data=(root/SOURCE).read_bytes();source=decode_png(data)
    frames,poses=make_frames(source);out=root/OUT;out.mkdir(parents=True,exist_ok=True)
    manifest={'schema_version':1,'status':'pixel_animation',
      'logical_pixel_scale':1,'sources':[{'path':SOURCE,'sha256':hashlib.sha256(data).hexdigest(),
      'provenance':'source image with articulated arm and blade'}],
      'groups':{},'files':[],'warnings':['Eight-frame sword swing animation.',
      'Source handedness ambiguous; exposed screen-left arm assigned sword; original screen-right shield retained.',
      'Planted torso; no hip/foot anticipation.']}
    def save(name,image):
        path=out/name;path.parent.mkdir(parents=True,exist_ok=True);png=encode_png(*image)
        if decode_png(png)!=image: raise ValueError('PNG roundtrip failed')
        path.write_bytes(png)
        manifest['files'].append({'path':path.relative_to(root).as_posix(),'width':image[0],'height':image[1],
          'sha256':hashlib.sha256(png).hexdigest(),'alpha_bbox':alpha_bbox(image),'has_alpha':True,
          'transparent_pixels':image[2][3::4].count(0),'opaque_pixels':image[2][3::4].count(255)})
        return path.relative_to(root).as_posix()
    sheet=assemble_sheet(frames)
    group={'frame_count':8,'cell_size':[96,96],'sheet_size':[384,192],'logical_pixel_scale':1,
      'source_path':SOURCE,'sheet':save('sheet.png',sheet),'frames':[],
      'clips':{'melee':{'frames':list(range(8)),'frame_duration_seconds':.035,'duration_seconds':.28,
      'loop':False,'active_start_seconds':.07,'timing_provenance':'animation clip timing; hit timing is controlled by gameplay'}}}
    for i,(frame,pose) in enumerate(zip(frames,poses)):
        name=f'frame_{i:02d}'
        group['frames'].append({'index':i,'name':name,'path':save(name+'.png',frame),
            'rect':[(i%4)*96,(i//4)*96,96,96],**pose})
    manifest['groups']['knight_swing']=group
    body,removed=body_layer(source);save('provenance/body_without_sword.png',body)
    checker=bytearray(sheet[2])
    for y in range(sheet[1]):
        for x in range(sheet[0]):
            k=(y*sheet[0]+x)*4
            if not checker[k+3]: checker[k:k+4]=bytes((30,35,46,255) if (x//8+y//8)%2 else (38,44,57,255))
    save('proof/poses_zoom_4x.png',resize_nearest((sheet[0],sheet[1],bytes(checker)),1536,768))
    save('proof/overhead_zoom_8x.png',resize_nearest(frames[1],768,768))
    manifest['edit']={'removed_source_pixels':removed,'source_translation':[16,16],
      'method':'body byte-copy except arm/blade masks; local new plated limb, gauntlet, physical straight sword',
      'no_effects':True,'body_scale':1,
      'foreground_shield_occlusion':{'frames':[4,5],'interior_rect':[52,46,8,10],
        'method':'existing authored limb/hilt/blade pixels only; source shield layer and outer rim unchanged'}}
    manifest['counts']={'runtime_frames':8,'runtime_sheets':1,'png_files':len(manifest['files'])}
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    return manifest

if __name__=='__main__':
    import json
    print(json.dumps(build_pack()['counts']))

