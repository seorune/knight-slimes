"""Build a self-contained, local-only explorer from real pack files."""
from pathlib import Path
import base64
import json
import re
import struct
import wave

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'assets/art/pixel_knight'
AUDIO = ROOT / 'assets/audio/pixel_knight'
if not ART.exists():
    raise SystemExit('Art pack is not built yet')
PACK_ROOTS = [p for p in (ART, ROOT / 'assets/art/terrain_knight', ROOT / 'assets/art/knight_swing') if p.exists()]
images = []
for pack in PACK_ROOTS:
    for path in sorted(pack.rglob('*.png')):
        raw = path.read_bytes()
        if raw[:8] != b'\x89PNG\r\n\x1a\n':
            raise RuntimeError('Invalid PNG: ' + str(path))
        w, h = struct.unpack('>II', raw[16:24])
        rel = path.relative_to(ROOT / 'assets/art').as_posix()
        group = path.parent.relative_to(ROOT / 'assets/art').as_posix()
        images.append({'name': rel, 'group': group, 'w': w, 'h': h,
                       'src': 'data:image/png;base64,' + base64.b64encode(raw).decode('ascii')})
sounds = []
for path in sorted(AUDIO.glob('*.wav')):
    with wave.open(str(path), 'rb') as wav:
        duration = wav.getnframes() / wav.getframerate()
        channels = wav.getnchannels()
    sounds.append({'name': path.stem, 'duration': duration, 'channels': channels,
                   'src': 'data:audio/wav;base64,' + base64.b64encode(path.read_bytes()).decode('ascii')})
if not images or not sounds:
    raise SystemExit('Both real image and sound files must exist before exporting preview')
by_name = {image['name']: image for image in images}
animations = []
for pack in PACK_ROOTS:
    manifest = json.loads((pack / 'manifest.json').read_text(encoding='utf-8'))
    for name, group in manifest['groups'].items():
        if not group.get('clips'):
            continue
        frames = [by_name[Path(frame['path']).relative_to('assets/art').as_posix()] for frame in group['frames']]
        animations.append({'name': pack.name + '/' + name, 'frames': frames,
                           'clips': group['clips']})
data = json.dumps({'images': images, 'sounds': sounds, 'animations': animations}, ensure_ascii=False).replace('<', '\\u003c')
html = r'''<!doctype html>
<html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>기사와 슬라임 · 에셋 작업대</title>
<style>
:root{color-scheme:dark;--ink:#eee9df;--muted:#a9aaa7;--line:#384047;--surface:#182129;--accent:#edb56e}
body{background:#0d151d;color:var(--ink);font:15px/1.55 'Segoe UI','Malgun Gothic',sans-serif;margin:0}
header,main{padding:24px clamp(16px,4vw,48px)}header{border-bottom:1px solid var(--line)}
h1{font-size:26px;font-weight:650;margin:0 0 8px}p{margin:8px 0;color:var(--muted)}
.label{font:12px Consolas,monospace;color:var(--accent);letter-spacing:.15em}
.tools{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin:20px 0}button,select,input{font:inherit;color:var(--ink);background:var(--surface);border:1px solid var(--line);padding:9px 13px;border-radius:4px;min-height:42px}button{cursor:pointer}button.active{border-color:var(--accent);color:var(--accent)}button:focus-visible,select:focus-visible,input:focus-visible{outline:2px solid var(--accent);outline-offset:3px}input{min-width:220px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:16px}.item{min-width:0;border:1px solid var(--line);padding:14px;background:#111c25}.stage{height:224px;display:flex;align-items:center;justify-content:center;background-color:#313640;background-image:linear-gradient(45deg,#252b33 25%,transparent 25%),linear-gradient(-45deg,#252b33 25%,transparent 25%),linear-gradient(45deg,transparent 75%,#252b33 75%),linear-gradient(-45deg,transparent 75%,#252b33 75%);background-size:24px 24px;background-position:0 0,0 12px,12px -12px,-12px 0}.stage.warm{background:#e1bd95}.stage.cold{background:#153341}.stage img{image-rendering:pixelated;max-width:100%;max-height:100%;object-fit:contain}h2{font-size:15px;overflow-wrap:anywhere;margin:12px 0 4px}.small{font-size:12px;color:var(--muted);overflow-wrap:anywhere}.actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px}.actions select{width:100%;min-width:0}.audio-list{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:10px}audio{width:100%;margin-top:10px}.audio-item{border-bottom:1px solid var(--line);padding:12px}.note{max-width:950px}#summary{font-family:Consolas,monospace}a{color:var(--accent)}
</style>
<header><div class="label">ASSET PREVIEW</div><h1>기사와 슬라임 · 에셋 작업대</h1><p class="note">스프라이트 애니메이션, PNG 이미지, WAV 효과음을 확인할 수 있습니다.</p><p id="summary"></p></header>
<main><div class="tools"><button data-tab="animation" class="active">애니메이션</button><button data-tab="images">전체 PNG</button><button data-tab="audio">효과음</button><input id="search" placeholder="이름으로 찾기" aria-label="이름으로 찾기"><select id="surface" aria-label="스프라이트 배경"><option value="stage">체커보드</option><option value="stage warm">따뜻한 배경</option><option value="stage cold">차가운 배경</option></select><button id="motion">재생 / 정지</button></div><div id="content"></div></main>
<script>const DATA=__DATA__;
let tab='animation',playing=!matchMedia('(prefers-reduced-motion:reduce)').matches,clock=0,frameTimes=[];
const content=document.querySelector('#content'),search=document.querySelector('#search'),surface=document.querySelector('#surface');
document.querySelector('#summary').textContent=`PNG ${DATA.images.length} · WAV ${DATA.sounds.length} · 외부 네트워크 요청 없음`;
function natural(a,b){return a.name.localeCompare(b.name,undefined,{numeric:true})}
function ranges(name,n){const low=name.toLowerCase();if((low.includes('fx')||low.includes('effect'))&&n>=16)return {slash:[0,1],parry:[2,3],just_parry:[4,5],time_stop:[6,7],ranged_warning:[8,9],ranged_hit:[10,11],clone:[12,13],revive:[14,15]};if(low.includes('knight')&&!low.includes('slime'))return {idle:[0,1],run:[2,3,4,5],jump:[6],fall:[7],melee:[8,9,10],parry:[11,12],hit:[13],death:[14,15]};if(low.includes('slime')||low.includes('melee')||low.includes('ranged')){let r={idle:[0,1],move:[2,3],windup:[4,5],attack:[6,7],upper:[8,9],stun:[10,11]};if(n>=16){r.death=[12,13];r.brick=[14,15]}return r}return {'전체 프레임':Array.from({length:n},(_,i)=>i)}}
function download(item){const a=document.createElement('a');a.href=item.src;a.download=item.name.split('/').pop();a.textContent='PNG 다운로드';return a}
function stage(img){const box=document.createElement('div');box.className=surface.value;box.append(img);return box}
function render(){frameTimes=[];content.replaceChildren();const term=search.value.toLowerCase();if(tab==='audio'){content.className='audio-list';for(const item of DATA.sounds.filter(x=>x.name.toLowerCase().includes(term))){const el=document.createElement('section');el.className='audio-item';const title=document.createElement('h2');title.textContent=item.name;const meta=document.createElement('div');meta.className='small';meta.textContent=`${item.duration.toFixed(3)}s · ${item.channels===1?'mono':'stereo'} · PCM WAV`;const audio=document.createElement('audio');audio.controls=true;audio.preload='none';audio.src=item.src;el.append(title,meta,audio);content.append(el)}return}
content.className='grid';if(tab==='images'){for(const item of DATA.images.filter(x=>x.name.toLowerCase().includes(term))){const el=document.createElement('section');el.className='item';const img=new Image();img.src=item.src;img.alt=item.name;img.style.width=Math.min(220,item.w*3)+'px';const title=document.createElement('h2');title.textContent=item.name;const meta=document.createElement('div');meta.className='small';meta.textContent=`${item.w} × ${item.h}`;el.append(stage(img),title,meta,download(item));content.append(el)}return}
for(const group of DATA.animations){const name=group.name;if(!name.toLowerCase().includes(term))continue;const frames=group.frames;if(frames.length<2)continue;const el=document.createElement('section');el.className='item';const img=new Image();img.src=frames[0].src;img.style.width=Math.min(220,frames[0].w*3)+'px';img.alt=name;const title=document.createElement('h2');title.textContent=name;const meta=document.createElement('div');meta.className='small';meta.textContent=`${frames.length} PNG · 클립별 속도 · 비반복 동작은 끝에서 정지`;const select=document.createElement('select');select.setAttribute('aria-label',name+' 동작');const clips=group.clips;for(const [key,clip] of Object.entries(clips)){if(clip.frames.some(i=>i>=frames.length))continue;const op=document.createElement('option');op.value=key;op.textContent=key;select.append(op)}const state={img,frames,select,clips,elapsed:0};select.onchange=()=>{state.elapsed=0;state.img.src=state.frames[state.clips[select.value].frames[0]].src};frameTimes.push(state);const actions=document.createElement('div');actions.className='actions';actions.append(select);const replay=document.createElement('button');replay.textContent='이 동작 다시 재생';replay.onclick=()=>{state.elapsed=0;state.img.src=state.frames[state.clips[select.value].frames[0]].src};actions.append(replay);el.append(stage(img),title,meta,actions);content.append(el)}
if(!content.children.length){const p=document.createElement('p');p.textContent='이 조건의 항목이 없습니다. 전체 PNG 탭에서 개별 파일을 확인할 수 있습니다.';content.append(p)}}
let lastTick=performance.now();setInterval(()=>{const now=performance.now(),delta=Math.min(100,now-lastTick);lastTick=now;if(!playing)return;for(const s of frameTimes){s.elapsed+=delta;const clip=s.clips[s.select.value];const ids=clip.frames;let frame=Math.floor(s.elapsed/(clip.frame_duration_seconds*1000));frame=clip.loop?frame%ids.length:Math.min(frame,ids.length-1);s.img.src=s.frames[ids[frame]].src}},16);
document.querySelectorAll('[data-tab]').forEach(b=>b.onclick=()=>{tab=b.dataset.tab;document.querySelectorAll('[data-tab]').forEach(x=>x.classList.toggle('active',x===b));render()});search.oninput=render;surface.onchange=render;document.querySelector('#motion').onclick=()=>{playing=!playing;document.querySelector('#motion').textContent=playing?'재생 중 / 정지':'정지 중 / 재생'};render();
</script></html>'''
html = html.replace('__DATA__', data)
output = ROOT / 'Asset-Preview.html'
output.write_text(html, encoding='utf-8')
print(json.dumps({'preview': str(output), 'png_count': len(images), 'wav_count': len(sounds), 'html_bytes': output.stat().st_size}, ensure_ascii=False))
