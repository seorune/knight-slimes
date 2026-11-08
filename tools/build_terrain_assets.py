"""Locally authored procedural pixel artwork; stdlib only, no network."""
from pathlib import Path
import argparse
import hashlib
import json
from build_pixel_assets import encode_png, decode_png, alpha_bbox

PROVENANCE = 'locally authored procedural pixel artwork'
PALETTES = {
    'warm': ('241e24','45313a','67464a','895c53','af7d61','d4ad7d','f5d9a0','f3a849'),
    'cool': ('161e30','28344c','3a4c65','526b83','718d9e','9eb8bf','d4e1d7','65c9e2'),
}
TILE_NAMES = ('stone_fill','surface_top','wall_left','wall_right','platform',
              'outside_tl','outside_tr','outside_bl','outside_br',
              'inside_tl','inside_tr','inside_bl','inside_br','bottom','seam')

def rgba(value):
    return bytes.fromhex(value)+b'\xff'

class Canvas:
    def __init__(self,w,h):
        self.w,self.h=w,h
        self.p=bytearray(w*h*4)
    def dot(self,x,y,c):
        if 0<=x<self.w and 0<=y<self.h:
            self.p[(y*self.w+x)*4:(y*self.w+x+1)*4]=c
    def rect(self,x,y,w,h,c):
        for yy in range(y,y+h):
            for xx in range(x,x+w):
                self.dot(xx,yy,c)
    def line(self,x0,y0,x1,y1,c):
        dx,dy=abs(x1-x0),-abs(y1-y0)
        sx,sy=1 if x0<x1 else -1,1 if y0<y1 else -1
        e=dx+dy
        while True:
            self.dot(x0,y0,c)
            if (x0,y0)==(x1,y1): break
            e2=2*e
            if e2>=dy: e+=dy; x0+=sx
            if e2<=dx: e+=dx; y0+=sy
    def image(self):
        return self.w,self.h,bytes(self.p)

def stone(palette):
    """Periodic 31-pixel field: first/last rows and columns match exactly."""
    p=list(map(rgba,PALETTES[palette]))
    c=Canvas(32,32)
    for y in range(32):
        for x in range(32):
            u,v=x%31,y%31
            row=v//8
            joint=(u+(8 if row%2 else 0))%16
            shade=2+(((u//3)*13+(v//2)*7+row*3)%3)
            if v%8==0 or joint==0: shade=0
            elif v%8==1 or joint==1: shade=5
            elif v%8==7: shade=1
            # Sparse chunky chips and stepped cracks, not random white noise.
            if (u+row*5)%19 in (4,5,6) and v%8==4: shade=1
            if joint in (10,11) and v%8==3: shade=5
            if joint==11 and v%8==2: shade=6
            c.dot(x,y,p[shade])
    return c

def terrain(palette,name):
    if name not in TILE_NAMES: raise ValueError(name)
    p=list(map(rgba,PALETTES[palette]))
    c=stone(palette)
    if name=='stone_fill': return c.image()
    if name=='seam':
        c=Canvas(32,32)
        for x in range(32):
            c.dot(x,15,p[7]); c.dot(x,16,p[1])
            if x%8 in (2,3): c.dot(x,14,p[5]); c.dot(x,17,p[3])
        return c.image()
    # All exposed surfaces are axis-aligned, with no slanted top faces.
    def top():
        c.rect(0,0,32,1,p[6]); c.rect(0,1,32,2,p[5]); c.rect(0,3,32,1,p[1])
        for x in (4,13,23): c.rect(x,1,2,1,p[3])
    def bottom():
        c.rect(0,29,32,1,p[3]); c.rect(0,30,32,2,p[0])
    def left():
        c.rect(0,0,1,32,p[6]); c.rect(1,0,2,32,p[4]); c.rect(3,0,1,32,p[1])
    def right():
        c.rect(28,0,1,32,p[4]); c.rect(29,0,2,32,p[1]); c.rect(31,0,1,32,p[0])
    if name in ('surface_top','platform'): top()
    elif name=='bottom': bottom()
    elif name=='wall_left': left()
    elif name=='wall_right': right()
    elif name.startswith('outside_'):
        corner=name[-2:]
        (left if corner[1]=='l' else right)()
        (top if corner[0]=='t' else bottom)()
    elif name.startswith('inside_'):
        # A 4x4 empty notch in the indicated corner: concave cavity boundary.
        corner=name[-2:]; ox=0 if corner[1]=='l' else 28; oy=0 if corner[0]=='t' else 28
        c.rect(ox,oy,4,4,bytes(4))
        xx=4 if ox==0 else 27; yy=4 if oy==0 else 27
        c.rect(xx,oy,1,5,p[5]); c.rect(ox,yy,5,1,p[5])
    if name=='platform':
        c.rect(0,6,32,2,p[0]); c.rect(0,8,32,24,bytes(4))
    return c.image()


def brick(palette,core):
    if core not in ('round','diamond'): raise ValueError(core)
    p=list(map(rgba,PALETTES[palette])); c=Canvas(32,16)
    # Flat full-footprint collision block; skull/core carving stays below top.
    src=stone(palette).image()[2]
    c.p[:]=src[:32*16*4]
    c.rect(0,0,32,1,p[6]); c.rect(0,1,32,2,p[5]); c.rect(0,14,32,2,p[0])
    c.rect(1,4,2,9,p[5]); c.rect(29,4,2,9,p[1])
    c.rect(8,4,16,9,p[0]); c.rect(9,5,14,7,p[2])
    for y in range(5,12):
        for x in range(12,21):
            dx,dy=abs(x-16),abs(y-8)
            inside=(dx*dx+dy*dy<=10) if core=='round' else dx+dy<=4
            if inside: c.dot(x,y,p[7 if y>7 else 6])
    c.dot(15,7,p[0]); c.dot(18,7,p[0]); c.rect(15,10,3,1,p[1])
    c.rect(4,7,2,3,p[3]); c.rect(26,7,2,3,p[3])
    return c.image()


def gateway_interior(x,y):
    # Stepped symmetrical arch opening, fixed for every state and pulse.
    if not 18<=x<46 or not 20<=y<88: return False
    inset=8 if y<24 else 4 if y<28 else 2 if y<32 else 0
    return 18+inset<=x<46-inset


def gateway(palette,state,pulse=0):
    if state not in ('closed','open') or pulse not in (0,1,2): raise ValueError((state,pulse))
    p=list(map(rgba,PALETTES[palette])); c=Canvas(64,96)
    for y in range(8,92):
        inset=16 if y<12 else 10 if y<16 else 6 if y<20 else 2 if y<28 else 0
        for x in range(8+inset,56-inset):
            shade=2+((x//4+y//5)%3)
            if y%12 in (0,1) or (x+(y//12%2)*6)%12==0: shade=0
            if x==8+inset or y==8: shade=5
            c.dot(x,y,p[shade])
    c.rect(4,88,56,4,p[5]); c.rect(4,92,56,4,p[0])
    c.rect(8,80,10,8,p[1]); c.rect(46,80,10,8,p[1])
    c.rect(10,82,6,2,p[5]); c.rect(48,82,6,2,p[5])
    # Interior only changes; outline, arch masonry and alpha stay invariant.
    glow=rgba(('a85e32','dd9046','ffd18b')[pulse] if palette=='warm' else
              ('286d9e','3c9fce','82d9e8')[pulse])
    for y in range(20,88):
        for x in range(18,46):
            if not gateway_interior(x,y): continue
            if state=='closed':
                color=p[1 if x%7==0 else 2]
                if y in (42,43,68,69): color=p[0]
                if (x,y) in ((36,53),(37,53),(36,54),(37,54)): color=p[7]
            else:
                color=p[0]
                if x in (18,19,44,45) or y>=84 or y<34: color=glow
                if (x-31)**2//3+(y-57)**2//8<32: color=glow
                if (x+y*3)%29==0: color=p[6]
            c.dot(x,y,color)
    c.rect(28,11,8,5,p[0]); c.rect(30,12,4,3,p[7])
    return c.image()


ICON_NAMES=('parry','just','melee','timestop','wound','heal','pause','restart','exit','gravity')

def icon(name):
    if name not in ICON_NAMES: raise ValueError(name)
    c=Canvas(16,16)
    dark=rgba('172238'); light=rgba('dae8dd'); steel=rgba('809db0')
    gold=rgba('f4c56d'); red=rgba('da6575'); blue=rgba('67cbdc')
    # Common beveled, clipped-corner medallion makes glyphs readable on both worlds.
    c.rect(2,1,12,14,dark); c.rect(1,2,14,12,dark)
    c.line(3,1,12,1,steel); c.line(1,3,1,12,steel)
    if name in ('parry','just'):
        c.rect(4,4,8,5,steel); c.rect(5,9,6,2,steel); c.rect(6,11,4,1,steel)
        c.rect(7,4,2,7,gold if name=='just' else light)
        if name=='just':
            c.line(10,2,10,5,gold); c.line(8,3,12,3,gold); c.dot(12,6,gold)
    elif name=='melee':
        c.line(5,11,11,5,light); c.line(6,11,12,5,steel)
        c.line(4,8,8,12,gold); c.line(3,12,5,10,gold); c.dot(12,4,light)
    elif name=='timestop':
        c.rect(5,3,6,1,gold); c.rect(5,12,6,1,gold)
        c.line(5,4,10,11,blue); c.line(10,4,5,11,blue)
        c.rect(7,9,2,3,gold)
    elif name=='wound':
        c.line(5,4,9,7,red); c.line(9,7,6,9,red); c.line(6,9,10,12,red)
        c.line(9,3,12,6,light); c.line(4,9,7,12,light)
    elif name=='heal':
        c.rect(6,4,4,9,red); c.rect(3,7,10,3,red)
        c.rect(7,5,2,7,light); c.rect(4,8,8,1,light)
    elif name=='pause':
        c.rect(4,4,3,8,gold); c.rect(9,4,3,8,gold)
    elif name=='restart':
        c.line(4,5,11,5,blue); c.line(11,5,12,10,blue)
        c.line(12,10,5,12,blue); c.line(5,12,3,9,blue)
        c.line(4,3,4,7,gold); c.line(4,7,8,7,gold)
    elif name=='exit':
        c.rect(3,3,7,10,steel); c.rect(5,5,4,7,dark)
        c.line(7,8,13,8,gold); c.line(11,6,13,8,gold); c.line(11,10,13,8,gold)
    else:
        c.line(5,3,5,11,blue); c.line(3,9,5,11,blue); c.line(7,9,5,11,blue)
        c.line(10,12,10,4,gold); c.line(8,6,10,4,gold); c.line(12,6,10,4,gold)
    return c.image()


def collision_rects(name):
    if name=='seam': return []
    if name=='platform': return [[0,0,32,8]]
    if name.startswith('inside_'):
        side=name[-2:]; x=4 if side[1]=='l' else 0
        if side[0]=='t': return [[x,0,28,4],[0,4,32,28]]
        return [[0,0,32,28],[x,28,28,4]]
    return [[0,0,32,32]]


def assemble(frames):
    w,h=frames[0][:2]
    c=Canvas(w*4,h*((len(frames)+3)//4))
    for i,(iw,ih,p) in enumerate(frames):
        if (iw,ih)!=(w,h): raise ValueError('Inconsistent frame dimensions')
        dx,dy=(i%4)*w,(i//4)*h
        for y in range(h):
            start=((dy+y)*c.w+dx)*4
            c.p[start:start+w*4]=p[y*w*4:(y+1)*w*4]
    return c.image()


def build_pack(root):
    """Build only terrain_knight. Refuse overwriting unowned or modified files."""
    root=Path(root).resolve(); base=root/'assets/art/terrain_knight'
    manifest={'schema_version':1,'status':'locally_authored_sideview_first_pass',
              'provenance':PROVENANCE,'logical_pixel_scale':1,
              'world_layout':{'above':'cool','below':'warm'},
              'sources':[], 'groups':{},'files':[],
              'warnings':['No game integration or arbitrary terrain adjacency claim.',
                          'Only stone_fill horizontal/vertical edge equality is verified.',
                          'Open exit pulse timings are prototype timings, not gameplay events.',
                          'No actor sprites, sounds or projectile sprites are included.'],
              'gameplay_contract':{'corpse_brick':'original slimes only; clones vanish',
                                   'ranged':'straight instantaneous direct hit; no projectile; just parry heals'}}
    pending={}
    def save(relative,image):
        data=encode_png(*image)
        if decode_png(data)!=image: raise ValueError('RGBA PNG roundtrip failed')
        path=(base/relative).relative_to(root).as_posix(); pending[path]=data
        w,h,p=image
        manifest['files'].append({'path':path,'width':w,'height':h,'color_type':'RGBA8',
            'sha256':hashlib.sha256(data).hexdigest(),'transparent_pixels':p[3::4].count(0),
            'opaque_pixels':p[3::4].count(255),'has_alpha':True,'alpha_bbox':list(alpha_bbox(image)),
            'provenance':PROVENANCE})
        return path
    def group(name,labels,frames,collisions,clips=None,**metadata):
        w,h=frames[0][:2]; sheet=assemble(frames)
        g={'frame_count':len(frames),'cell_size':[w,h],'sheet_size':list(sheet[:2]),
           'columns':4,'gutter':0,'filter':'nearest','logical_pixel_scale':1,
           'sheet':save(name+'/sheet.png',sheet),'frames':[],'clips':{},**metadata}
        for i,(label,image,rects) in enumerate(zip(labels,frames,collisions)):
            g['frames'].append({'index':i,'name':label,'path':save(name+'/'+label+'.png',image),
                'rect':[(i%4)*w,(i//4)*h,w,h],'alpha_bbox':list(alpha_bbox(image)),
                'collision_rects':rects,'collision_coordinate_system':'top-left x,y,width,height'})
        for label,indices in (clips or {n:[i] for i,n in enumerate(labels)}).items():
            seconds=.16 if label=='open' else .14
            g['clips'][label]={'frames':indices,'frame_duration_seconds':seconds,
                'duration_seconds':round(seconds*len(indices),4),'loop':label=='open',
                'timing_provenance':'suggested prototype timing; not gameplay-integrated'}
        manifest['groups'][name]=g
    for palette in PALETTES:
        group('terrain_'+palette,TILE_NAMES,[terrain(palette,n) for n in TILE_NAMES],
              [collision_rects(n) for n in TILE_NAMES],
              palette=palette,world_region='below' if palette=='warm' else 'above',
              seam_verification={'stone_fill':['left_equals_right','top_equals_bottom'],
                                 'arbitrary_adjacencies_verified':False})
        group('bricks_'+palette,('corpse_round','corpse_diamond'),
              [brick(palette,n) for n in ('round','diamond')],[[[0,0,32,16]]]*2,
              spawn_policy='original_only; clones vanish without brick',
              core_mapping={'round':'melee_original','diamond':'ranged_original'})
        group('exit_'+palette,('closed','open_0','open_1','open_2'),
              [gateway(palette,'closed')]+[gateway(palette,'open',i) for i in range(3)],
              [[]]*4,{'closed':[0],'open':[1,2,3,2]},
              arch_silhouette='fixed across states; only interior illumination changes',
              trigger_rect=[18,32,28,56],trigger_is_collision=False)
    group('hud',ICON_NAMES,[icon(n) for n in ICON_NAMES],[[] for _ in ICON_NAMES])
    manifest['counts']={'groups':len(manifest['groups']),
                        'frames':sum(g['frame_count'] for g in manifest['groups'].values()),
                        'png_files':len(manifest['files'])}
    # Validate ownership before ANY writes. Never silently replace edited artwork.
    mp=base/'manifest.json'; old={}
    if mp.exists():
        previous=json.loads(mp.read_text(encoding='utf-8'))
        if previous.get('provenance')!=PROVENANCE: raise ValueError('Refusing manifest overwrite')
        old={r['path']:r['sha256'] for r in previous['files']}
    for path in pending:
        target=root/path
        if target.exists():
            if path not in old or hashlib.sha256(target.read_bytes()).hexdigest()!=old[path]:
                raise ValueError('Refusing overwrite of modified or unowned file: '+path)
    for path,data in pending.items():
        target=root/path; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(data)
    mp.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return manifest


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    args=parser.parse_args()
    result=build_pack(args.root)
    print(json.dumps({'output':'assets/art/terrain_knight/manifest.json',**result['counts']},ensure_ascii=False))
