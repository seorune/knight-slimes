"""Reproducible final-boss atlas from the source image and pixel edits.
No network, packages, renderer changes, or per-pose rescaling.
"""
import hashlib
import json
from pathlib import Path
import sys

from build_pixel_assets import decode_png, encode_png, crop, green_key, resize_nearest, alpha_bbox

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'assets/art/paradox_warden'
STATES = ('sealed','exposed','charge','attack','break','defeated')


def polygon(pixels, points, color):
    for y in range(max(0,min(p[1] for p in points)), min(96,max(p[1] for p in points)+1)):
        for x in range(max(0,min(p[0] for p in points)), min(96,max(p[0] for p in points)+1)):
            inside = False
            j = len(points)-1
            for i,(px,py) in enumerate(points):
                qx,qy = points[j]
                if (py>y)!=(qy>y) and x < (qx-px)*(y-py)/(qy-py)+px:
                    inside = not inside
                j=i
            if inside: pixels[(y*96+x)*4:(y*96+x+1)*4] = bytes(color)


def build():
    source = (ART/'source.png').read_bytes()
    image = decode_png(source)
    if image[:2] != (1024,576): raise ValueError('Unexpected archived source dimensions')
    frames=[]
    origins=[(174,267),(169,267),(168,267),(174,258),(168,258),(155,263)]
    rects=[]
    for i,state in enumerate(STATES):
        left,right=[(0,341),(341,683),(683,1024)][i%3]
        rect=[left,i//3*288,right-left,288]
        rects.append(rect)
        keyed=green_key(crop(image,*rect))
        sw,sh=round(rect[2]*.27),round(288*.27)
        small=resize_nearest(keyed,sw,sh)
        ox,oy=origins[i]
        dx,dy=48-round(ox*.27),90-round(oy*.27)
        out=bytearray(96*96*4)
        for y in range(sh):
            for x in range(sw):
                tx,ty=dx+x,dy+y
                if not (3<=tx<93 and 3<=ty<91): continue
                p=small[2][(y*sw+x)*4:(y*sw+x+1)*4]
                r,g,b,a=p
                # Remove low-level chroma spill as well as the flood-key matte.
                if not a or (g>r*1.18 and g>b*1.18): continue
                rgb=[min(255,round(v/8)*8) for v in (r,g,b)]
                if state=='charge' and b>r*1.25 and g>r*1.25:
                    rgb=[248,176,48]
                out[(ty*96+tx)*4:(ty*96+tx+1)*4]=bytes(rgb+[255])
        # Ground-contact pixels are a fixed layer, not regenerated per animation.
        if i in (1,2,3,4):
            out[78*96*4:91*96*4]=frames[0][2][78*96*4:91*96*4]
        if state=='sealed':
            polygon(out,[(37,46),(59,46),(62,54),(58,66),(38,66),(34,54)],(24,24,32,255))
            polygon(out,[(39,47),(57,47),(60,54),(56,64),(40,64),(36,54)],(144,88,32,255))
            polygon(out,[(39,48),(47,48),(47,63),(41,62),(38,54)],(232,184,80,255))
            polygon(out,[(49,48),(56,48),(58,54),(55,62),(49,63)],(192,136,48,255))
            polygon(out,[(40,49),(46,49),(46,51),(40,52)],(255,224,144,255))
        elif state!='defeated':
            color={'exposed':(64,224,248,255),'charge':(248,176,48,255),'attack':(248,56,48,255),'break':(128,216,232,255)}[state]
            polygon(out,[(48,45),(57,54),(48,66),(39,54)],(8,16,32,255))
            polygon(out,[(48,47),(54,54),(48,63),(42,54)],color)
            polygon(out,[(48,50),(51,54),(48,59),(45,54)],(248,255,255,255) if state=='exposed' else (255,224,168,255))
            if state=='break':
                # Physically missing shoulder plate and cold splintered reactor,
                # rather than an orange casting pose masquerading as damage.
                polygon(out,[(29,36),(37,36),(36,45),(29,45)],(0,0,0,0))
                for n in range(96*96):
                    r,g,b,a=out[n*4:n*4+4]
                    if a and r>190 and r>b*1.8 and 40<n//96<78:
                        out[n*4:n*4+4]=bytes((112,200,224,255))
                for points in [[(38,43),(42,48),(40,52),(44,57),(42,62),(39,55)],[(59,40),(56,46),(59,50),(55,56),(58,62),(53,54)],[(45,48),(49,53),(46,57),(49,63),(43,58)]]:
                    polygon(out,points,(8,8,16,255))
                for x,y in [(34,43),(62,52),(38,67),(59,68)]:
                    polygon(out,[(x,y),(x+3,y+2),(x+1,y+5),(x-2,y+2)],(160,240,248,255))
        if state!='defeated':
            # A broken clock crown, not the source concept's anonymous dark arch.
            # Detached upper arcs and minute marks stay outside the helmet.
            import math
            for lo,hi in [(184,214),(229,262),(278,314),(331,356)]:
                for degree in range(lo,hi+1):
                    angle=math.radians(degree)
                    for radius in (14,15):
                        x,y=round(48+radius*math.cos(angle)),round(31+radius*math.sin(angle))
                        out[(y*96+x)*4:(y*96+x+1)*4]=bytes((176,120,48,255))
            for x,y in [(48,16),(48,17),(35,24),(61,24),(38,19),(58,19)]:
                out[(y*96+x)*4:(y*96+x+1)*4]=bytes((248,200,96,255))
            polygon(out,[(47,22),(49,22),(49,30),(47,30)],(248,200,96,255))
            polygon(out,[(48,29),(54,24),(55,26),(49,31)],(248,200,96,255))
        # Quantization can push a borderline chroma edge back over the matte
        # predicate. Validate and clean the final palette, not only source RGB.
        for n in range(96*96):
            r,g,b,a=out[n*4:n*4+4]
            if a and g>r*1.18 and g>b*1.18:
                out[n*4:n*4+4]=bytes(4)
        frames.append((96,96,bytes(out)))
    sheet=bytearray(288*192*4)
    records=[]
    for i,frame in enumerate(frames):
        for y in range(96):
            start=((i//3*96+y)*288+i%3*96)*4
            sheet[start:start+96*4]=frame[2][y*96*4:(y+1)*96*4]
        records.append({'index':i,'name':STATES[i],'rect':[i%3*96,i//3*96,96,96],
                        'anchor':[48,90],'body_offset':[0,0], 'baseline':90,
                        'source_rect':rects[i], 'source_root':list(origins[i]),
                        'alpha_bbox':list(alpha_bbox(frame)),
                        'opaque_pixels':sum(bool(a) for a in frame[2][3::4]),
                        'rgba_sha256':hashlib.sha256(frame[2]).hexdigest()})
    png=encode_png(288,192,bytes(sheet))
    (ART/'sheet.png').write_bytes(png)
    manifest={'schema_version':1,'status':'processed_boss_sprites',
        'provenance':{'generator':'image','source_path':'assets/art/paradox_warden/source.png',
        'source_sha256':hashlib.sha256(source).hexdigest(),'source_dimensions':[1024,576],
        'operations':['explicit unequal source rectangles','green matte flood key and spill cleanup','uniform 0.27 nearest-neighbor scale','fixed root translation','binary alpha and 8-step palette','shared standing foot layer','closed gold chest, energy cores, cracks, core fragments and broken clock crown with ticks/hands'],
        'note':'Clockwork warden sprites use a separate source image from the slime sprites.'},
        'groups':{'paradox_warden':{'sheet':'assets/art/paradox_warden/sheet.png',
        'sheet_sha256':hashlib.sha256(png).hexdigest(),'frame_count':6,'cell_size':[96,96],
        'sheet_size':[288,192],'world_draw_size':[192,192],'anchor':[48,90],
        'world_anchor_offset':[96,180],'gameplay_box_changed':False,'frames':records,
        'clips':{s:{'frames':[i],'frame_duration':0.1,'frame_duration_seconds':0.1,'loop':False} for i,s in enumerate(STATES)}}}}
    (ART/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print('Built six 96x96 boss states; sheet 288x192; sha256 '+hashlib.sha256(png).hexdigest())

if __name__=='__main__': build()
