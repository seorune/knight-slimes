"""Reproducible stdlib-only pixel asset builder. No image generation or network."""
import argparse
import colorsys
import hashlib
import json
from pathlib import Path
import struct
import zlib

def green_key(image):
    """Flood green edges plus bright matte holes; preserve dark enclosed greens."""
    w,h,pixels = image
    out = bytearray(pixels)
    candidates=bytearray(w*h)
    for n in range(w*h):
        r,g,b,a=pixels[n*4:n*4+4]
        candidates[n] = not a or (g > 75 and g > r*1.28 and g > b*1.28 and g-max(r,b) > 35)
    stack=list(range(w))+list(range((h-1)*w,h*w))+[y*w for y in range(h)]+[y*w+w-1 for y in range(h)]
    while stack:
        n=stack.pop()
        if not candidates[n]:
            continue
        candidates[n]=0
        out[n*4:n*4+4]=bytes(4)
        x,y=n%w,n//w
        if x: stack.append(n-1)
        if x+1<w: stack.append(n+1)
        if y: stack.append(n-w)
        if y+1<h: stack.append(n+w)
    for n in range(w*h):
        r,g,b,a=out[n*4:n*4+4]
        # Enclosed holes inside rings are background too. Only the bright,
        # highly green matte is removed globally; dark spell greens stay.
        if not a or (g >= 180 and r < g*.5 and b < g*.5):
            out[n*4:n*4+4]=bytes(4)
    return w,h,bytes(out)


def strip_gutters(image,gutter):
    w,h,pixels=image
    out=bytearray(pixels)
    for y in range(h):
        for x in range(w):
            if min(x,y,w-1-x,h-1-y)<gutter:
                out[(y*w+x)*4:(y*w+x+1)*4]=bytes(4)
    return w,h,bytes(out)


def crop(image,x,y,w,h):
    iw,ih,pixels = image
    if x < 0 or y < 0 or x+w > iw or y+h > ih or min(w,h) <= 0:
        raise ValueError('Crop outside image')
    return w,h,b''.join(pixels[((y+j)*iw+x)*4:((y+j)*iw+x+w)*4] for j in range(h))


def resize_nearest(image,w,h):
    iw,ih,pixels = image
    if min(w,h) <= 0:
        raise ValueError('Invalid resize')
    out = bytearray(w*h*4)
    for y in range(h):
        sy = min(ih-1, int((y+.5)*ih/h))
        for x in range(w):
            sx = min(iw-1, int((x+.5)*iw/w))
            out[(y*w+x)*4:(y*w+x+1)*4] = pixels[(sy*iw+sx)*4:(sy*iw+sx+1)*4]
    return w,h,bytes(out)


def alpha_bbox(image):
    w,h,pixels = image
    xs,ys = [],[]
    for y in range(h):
        for x in range(w):
            if pixels[(y*w+x)*4+3]:
                xs.append(x); ys.append(y)
    return (min(xs),min(ys),max(xs)+1,max(ys)+1) if xs else None


def normalize_sheet(image,size=64,baseline=True,gutter=0):
    w,h,_ = image
    if w != h or w%4:
        raise ValueError('Expected square 4x4 sheet')
    cell=w//4
    cells=[green_key(strip_gutters(crop(image,(i%4)*cell,(i//4)*cell,cell,cell),gutter)) for i in range(16)]
    boxes=[alpha_bbox(c) for c in cells]
    if any(b is None for b in boxes):
        raise ValueError('Empty source frame')
    # One scale for the entire sheet; never stretch frames independently.
    limit_h=50 if baseline else size-4
    scale=min((size-6)/max(b[2]-b[0] for b in boxes),limit_h/max(b[3]-b[1] for b in boxes),size/cell)
    frames,metadata=[],[]
    for frame,box in zip(cells,boxes):
        x0,y0,x1,y1=box
        sw=max(1,round((x1-x0)*scale)); sh=max(1,round((y1-y0)*scale))
        small=resize_nearest(crop(frame,x0,y0,x1-x0,y1-y0),sw,sh)
        dx=(size-sw)//2
        dy=(min(54,size-2)+1-sh) if baseline else (size-sh)//2
        if min(dx,dy)<0 or dx+sw>size or dy+sh>size:
            raise ValueError('Normalization would clip content')
        out=bytearray(size*size*4)
        for y in range(sh):
            out[((dy+y)*size+dx)*4:((dy+y)*size+dx+sw)*4]=small[2][y*sw*4:(y+1)*sw*4]
        result=(size,size,bytes(out))
        if alpha_bbox(result) is None:
            raise ValueError('Reduction erased frame')
        frames.append(result)
        metadata.append({'source_bbox':list(box),'source_gutter_pixels':gutter,'scale':scale,'offset':[dx,dy], 'alpha_bbox':list(alpha_bbox(result)), 'clipped':False})
    return frames,metadata


def recolor(image,mode):
    w,h,pixels=image
    out=bytearray(pixels)
    for i in range(0,len(out),4):
        r,g,b,a=out[i:i+4]
        hue,sat,val=colorsys.rgb_to_hsv(r/255,g/255,b/255)
        if a and sat > .24 and (hue < .19 or hue > .97):
            hue = .77 if mode == 'purple' else .57
            r,g,b=colorsys.hsv_to_rgb(hue,sat,val)
            out[i:i+3]=bytes(round(c*255) for c in (r,g,b))
    return w,h,bytes(out)


SIGNATURE = b'\x89PNG\r\n\x1a\n'


def decode_png(data):
    """Decode non-interlaced 8-bit RGB/RGBA, verifying every chunk CRC."""
    if not data.startswith(SIGNATURE):
        raise ValueError('Not a PNG')
    pos, payload, header, ended = 8, bytearray(), None, False
    while pos < len(data):
        if pos + 12 > len(data):
            raise ValueError('Truncated PNG chunk')
        size = struct.unpack_from('>I', data, pos)[0]
        tag = data[pos+4:pos+8]
        end = pos + 12 + size
        if end > len(data):
            raise ValueError('Truncated PNG payload')
        body = data[pos+8:pos+8+size]
        crc = struct.unpack_from('>I', data, pos+8+size)[0]
        if zlib.crc32(tag+body) != crc:
            raise ValueError('PNG CRC mismatch: '+repr(tag))
        if header is None and tag != b'IHDR':
            raise ValueError('IHDR must be first')
        if tag == b'IHDR':
            if header is not None or size != 13:
                raise ValueError('Invalid IHDR')
            header = struct.unpack('>IIBBBBB', body)
        elif tag == b'IDAT':
            payload.extend(body)
        elif tag == b'IEND':
            if size or end != len(data):
                raise ValueError('Invalid IEND/trailing bytes')
            ended = True
            break
        elif not tag[0] & 32:
            raise ValueError('Unsupported critical PNG chunk '+repr(tag))
        pos = end
    if not ended or not header:
        raise ValueError('Incomplete PNG')
    w,h,depth,ct,compression,filter_method,interlace = header
    if depth != 8 or ct not in (2,6) or compression or filter_method or interlace:
        raise ValueError('Requires non-interlaced RGB8/RGBA8 PNG')
    if not w or not h or w*h > 40000000:
        raise ValueError('Invalid/oversized dimensions')
    channels = 3 if ct == 2 else 4
    stride = w*channels
    inflater = zlib.decompressobj()
    raw = inflater.decompress(bytes(payload), (stride+1)*h+1)
    if len(raw) != (stride+1)*h or not inflater.eof or inflater.unused_data or inflater.unconsumed_tail:
        raise ValueError('Invalid decompressed PNG length/stream')
    previous = bytearray(stride)
    output = bytearray()
    for y in range(h):
        start = y*(stride+1)
        ft = raw[start]
        if ft > 4:
            raise ValueError('Invalid PNG filter')
        row = bytearray(raw[start+1:start+1+stride])
        for i in range(stride):
            a = row[i-channels] if i >= channels else 0
            b = previous[i]
            c = previous[i-channels] if i >= channels else 0
            p = a+b-c
            ds = (abs(p-a),abs(p-b),abs(p-c))
            predictor = (0,a,b,(a+b)//2,(a,b,c)[ds.index(min(ds))])[ft]
            row[i] = (row[i]+predictor)&255
        if channels == 4:
            output.extend(row)
        else:
            for i in range(0,stride,3):
                output.extend(row[i:i+3]); output.append(255)
        previous = row
    return w,h,bytes(output)


def encode_png(w,h,pixels):
    if w <= 0 or h <= 0 or len(pixels) != w*h*4:
        raise ValueError('Invalid RGBA dimensions/payload')
    def chunk(tag, body):
        return struct.pack('>I',len(body))+tag+body+struct.pack('>I',zlib.crc32(tag+body))
    scan = b''.join(b'\0'+pixels[y*w*4:(y+1)*w*4] for y in range(h))
    return SIGNATURE+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(scan,9))+chunk(b'IEND',b'')


SOURCE_NAMES = ('knight_sheet','melee_slime_sheet','ranged_slime_sheet','fx_sheet','tiles_sheet','dual_castle_background','fx_combat_sheet')
KNIGHT_CLIPS = {'idle':[0,1], 'run':[2,3,4,5], 'jump':[6], 'fall':[7], 'melee':[8,9,10], 'parry':[11,12], 'hit':[13], 'death':[14,15]}
SLIME_CLIPS = {'idle':[0,1], 'move':[2,3], 'windup':[4,5], 'attack':[6,7], 'upper':[8,9], 'stun':[10,11], 'death':[12,13], 'brick':[14,15]}
FX_CLIPS = {name:[i*2,i*2+1] for i,name in enumerate(('slash','parry','just_parry','time_stop','ranged_warning','ranged_hit','clone','revive'))}
TILE_NAMES = ('ground_top','ground_fill','wall','platform','pillar_base','pillar_mid','arch_left','arch_right','brick_corpse_round','brick_corpse_diamond','door_closed','door_open','seam_marker','checkpoint','button_down','button_up')


def assemble_sheet(frames,columns=4):
    size=frames[0][0]
    if any(f[:2] != (size,size) for f in frames):
        raise ValueError('Sheet frames must be equal squares')
    w,h=columns*size,((len(frames)+columns-1)//columns)*size
    out=bytearray(w*h*4)
    for n,(_,_,pixels) in enumerate(frames):
        dx,dy=(n%columns)*size,(n//columns)*size
        for y in range(size):
            out[((dy+y)*w+dx)*4:((dy+y)*w+dx+size)*4]=pixels[y*size*4:(y+1)*size*4]
    return w,h,bytes(out)


def build_pack(root):
    root=Path(root).resolve()
    base=root/'assets/art/pixel_knight'
    sources={}
    manifest={'schema_version':1,'status':'pixel_assets', 'logical_pixel_scale':1,
              'background_seamless_verified':False,'sources':[],'groups':{},'files':[],
              'warnings':['Animation labels describe intended clips, not verified pose quality.',
                          'Source tile art is isometric props; not seamless modular terrain.',
                          'Checkpoint source looks like a shovel; button labels are DOWN then UP.',
                          'Door props have different silhouettes and are not an animation pair.',
                          'Original sword-icon FX are excluded; combat FX replaces them. Time-stop transition remains rough.',
                          'Ranged original death clip remapped to source frames 12,14; frame13 is living.',
                          'Background seamlessness is not verified.']}
    # Resolve and validate ALL sources before any output write.
    for name in SOURCE_NAMES:
        path=root/'assets/art/source'/f'{name}.png'
        data=path.read_bytes()
        image=decode_png(data)
        sources[name]=image
        manifest['sources'].append({'path':path.relative_to(root).as_posix(), 'sha256':hashlib.sha256(data).hexdigest(),
                                    'width':image[0],'height':image[1], 'provenance':'source image'})
    base.mkdir(parents=True,exist_ok=True)
    def save(relative,image,source,operation):
        path=base/relative
        path.parent.mkdir(parents=True,exist_ok=True)
        data=encode_png(*image)
        if decode_png(data)!=image:
            raise ValueError('Output PNG roundtrip mismatch')
        path.write_bytes(data)
        w,h,pixels=image
        record={'path':path.relative_to(root).as_posix(),'width':w,'height':h,'color_type':'RGBA8',
                'sha256':hashlib.sha256(data).hexdigest(), 'transparent_pixels':pixels[3::4].count(0),
                'opaque_pixels':pixels[3::4].count(255),'has_alpha':True,'alpha_bbox':alpha_bbox(image),
                'source_path':f'assets/art/source/{source}.png','provenance':'processed sprite','operation':operation}
        manifest['files'].append(record)
        return record['path']
    def group(name,frames,meta,source,clips,names=None,operation='green flood key, uniform sheet scale, nearest reduction, baseline alignment'):
        size=frames[0][0]
        sheet=assemble_sheet(frames)
        result={'frame_count':len(frames),'cell_size':[size,size],'sheet_size':list(sheet[:2]),'logical_pixel_scale':1,
                'sheet':save(f'{name}/sheet.png',sheet,source,operation),'frames':[],'clips':{},'source_path':f'assets/art/source/{source}.png'}
        for i,(image,info) in enumerate(zip(frames,meta)):
            label=names[i] if names else f'frame_{i:02d}'
            path=save(f'{name}/{label}.png',image,source,operation)
            result['frames'].append({'index':i,'name':label,'path':path,'rect':[(i%4)*size,(i//4)*size,size,size],**info})
        for label,indices in clips.items():
            seconds=.10 if label in ('run','move','melee','attack') else .14
            result['clips'][label]={'frames':indices,'frame_duration_seconds':seconds,'duration_seconds':round(seconds*len(indices),4),
                                    'loop':label in ('idle','run','move','brick'),'timing_provenance':'animation clip timing'}
        manifest['groups'][name]=result
        return result
    frames,meta=normalize_sheet(sources['knight_sheet'],gutter=12)
    group('knight',frames,meta,'knight_sheet',KNIGHT_CLIPS)
    for kind in ('melee','ranged'):
        source=f'{kind}_slime_sheet'
        frames,meta=normalize_sheet(sources[source],gutter=12 if kind=='melee' else 0)
        clips={k:list(v) for k,v in SLIME_CLIPS.items()}
        if kind=='ranged': clips['death']=[12,14]
        group(f'{kind}_original',frames,meta,source,clips)
        clones=[recolor(frame,'purple') for frame in frames[:12]]
        clone=group(f'{kind}_clone',clones,meta[:12],source,{k:v for k,v in clips.items() if k not in ('death','brick')},operation='same extracted silhouette and alpha; warm hue to purple HSV transform')
        clone['death_behavior']='vanish; use clone FX, no corpse/brick frames'
        for n,frame in enumerate(frames[14:16]):
            box=alpha_bbox(frame)
            brick=resize_nearest(crop(frame,box[0],box[1],box[2]-box[0],box[3]-box[1]),32,16)
            save(f'bricks/{kind}_{n}.png',brick,source,'derived: source brick bounding box aspect-compressed to 32x16 flat logical canvas; not hand-painted')
    frames,meta=normalize_sheet(sources['fx_combat_sheet'],baseline=False,gutter=12)
    combat=group('fx',frames,meta,'fx_combat_sheet',FX_CLIPS,operation='12px source gutters cleared; connected green key preserving cyan; uniform sheet nearest scale; center alignment')
    combat['ranged_hit_behavior']='instant full line hit; not a moving projectile'
    old_frames,old_meta=normalize_sheet(sources['fx_sheet'],baseline=False,gutter=12)
    selected=[8,9,14,15]
    utility_meta=[dict(old_meta[i],source_frame_index=i) for i in selected]
    group('fx_utilities',[old_frames[i] for i in selected],utility_meta,'fx_sheet',{'time_resume':[0,1],'exit':[2,3]},operation='selected original FX frames 8,9,14,15 only; connected green key; centered nearest reduction')
    frames,meta=normalize_sheet(sources['tiles_sheet'],size=32,baseline=False)
    for mode in ('warm','cold'):
        selected=frames if mode=='warm' else [recolor(frame,'cold') for frame in frames]
        props=group(f'tiles_{mode}',selected,meta,'tiles_sheet',{},TILE_NAMES,operation='derived 32px isometric props; common nearest scale; '+('original warm colors' if mode=='warm' else 'warm hue to cold blue HSV recolor'))
        props['usage']='props_only_not_seamless_terrain'
        props['doors_are_separate_props']=True
    background=resize_nearest(sources['dual_castle_background'],512,288)
    save('background/dual_castle.png',background,'dual_castle_background','nearest reduction to 512x288; no chroma key')
    save('background/cold_upper.png',crop(background,0,0,512,144),'dual_castle_background','upper crop of derived 512x288 background; not verified seamless')
    save('background/warm_lower.png',crop(background,0,144,512,144),'dual_castle_background','lower crop of derived 512x288 background; not verified seamless')
    manifest['counts']={'png_files':len(manifest['files']), 'sources':len(manifest['sources']), 'groups':len(manifest['groups']),
                        'single_frames':sum(g['frame_count'] for g in manifest['groups'].values()),'sheets':len(manifest['groups'])}
    # Remove only obsolete build-owned PNGs, e.g. an old clone corpse export.
    expected={r['path'] for r in manifest['files']}
    for path in base.rglob('*.png'):
        if path.relative_to(root).as_posix() not in expected:
            path.unlink()
    validate_pack(root,manifest)
    (base/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return manifest


def validate_pack(root,manifest):
    """Read back and validate all output CRCs, source hashes, alpha, rects and clips."""
    root=Path(root).resolve()
    records=manifest['files']
    if manifest['counts']['png_files']!=len(records) or len({r['path'] for r in records})!=len(records):
        raise ValueError('PNG count/duplicate mismatch')
    if manifest['counts']['sources']!=len(manifest['sources']) or manifest['counts']['groups']!=len(manifest['groups']):
        raise ValueError('Source/group count mismatch')
    images={}
    for record in manifest['sources']+records:
        path=Path(record['path'])
        if path.is_absolute() or '..' in path.parts:
            raise ValueError('Paths must be repository-relative')
        data=(root/path).read_bytes()
        if hashlib.sha256(data).hexdigest()!=record['sha256']:
            raise ValueError('SHA256 mismatch: '+str(path))
        image=decode_png(data)
        if list(image[:2])!=[record['width'],record['height']]:
            raise ValueError('PNG dimensions mismatch')
        if 'transparent_pixels' in record:
            if image[2][3::4].count(0)!=record['transparent_pixels'] or image[2][3::4].count(255)!=record['opaque_pixels']:
                raise ValueError('Alpha counts mismatch')
            if list(alpha_bbox(image) or [])!=list(record['alpha_bbox'] or []):
                raise ValueError('Alpha bbox mismatch')
            images[record['path']]=image
    for name,group in manifest['groups'].items():
        if group['frame_count']!=len(group['frames']):
            raise ValueError('Frame count mismatch')
        sheet=images[group['sheet']]
        if list(sheet[:2])!=group['sheet_size']:
            raise ValueError('Sheet dimensions mismatch')
        for frame in group['frames']:
            image=images[frame['path']]
            if crop(sheet,*frame['rect'])!=image:
                raise ValueError('Frame rectangle mismatch')
            box=alpha_bbox(image)
            if box is None or list(box)!=frame['alpha_bbox'] or frame['clipped']:
                raise ValueError('Empty/clipped frame or bbox mismatch')
            if name in ('knight','melee_original','ranged_original','melee_clone','ranged_clone') and box[3]!=55:
                raise ValueError('Character baseline mismatch')
        for clip in group['clips'].values():
            if not clip['frames'] or any(i<0 or i>=group['frame_count'] for i in clip['frames']):
                raise ValueError('Clip index outside sheet')
        if name.endswith('_clone'):
            if group['frame_count']!=12 or 'death' in group['clips'] or 'brick' in group['clips']:
                raise ValueError('Clone corpse policy violation')
            original=manifest['groups'][name.replace('_clone','_original')]
            for frame,source in zip(group['frames'],original['frames']):
                if images[frame['path']][2][3::4]!=images[source['path']][2][3::4]:
                    raise ValueError('Clone silhouette alpha changed')
    if manifest['counts']['single_frames']!=sum(g['frame_count'] for g in manifest['groups'].values()):
        raise ValueError('Single frame count mismatch')
    return True


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parent.parent)
    args=parser.parse_args()
    manifest=build_pack(args.root)
    print(json.dumps({'manifest':'assets/art/pixel_knight/manifest.json',**manifest['counts']},ensure_ascii=False))


if __name__ == '__main__':
    main()
