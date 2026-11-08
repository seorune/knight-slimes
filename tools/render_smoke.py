"""Windows render smoke after gradlew lwjgl3:classes; no installs or downloads."""
from pathlib import Path
import argparse
import json
import re
import os
import subprocess
import sys
parser = argparse.ArgumentParser()
parser.add_argument('--scene', choices=['route', 'just', 'parry', 'wounded', 'inverted', 'title', 'ranged', 'attack', 'guard', 'enemywindup', 'enemyattack', 'stage2start', 'stage2middle', 'stage2top', 'stage2wrap', 'transition', 'bossstart', 'bosswindup', 'bossattack', 'bossexposed', 'bossclear', 'bosswrapx', 'bosswrapy', 'transition3', 'bossarmed', 'bosssealedjust', 'bosssealbreak', 'bossdefeat', 'targetreveal1', 'targetreveal2', 'targetreveal3', 'targethidden2', 'doorlocked1', 'dooropen1', 'doorlocked2', 'dooropen2', 'bossdelivery', 'bossmarked', 'bosstargetpush', 'bossmarkthawed', 'bossarrival'], default='route')
parser.add_argument('--frames', type=int, default=None, help='Override bounded capture frame count')
args = parser.parse_args()
if args.frames is not None and not 1 <= args.frames <= 3600:
    parser.error('--frames must be between 1 and 3600')
root = Path(__file__).resolve().parents[1]
java_home = os.environ.get('JAVA_HOME')
candidates = ([Path(java_home) / 'bin/java.exe'] if java_home else []) + list((root / '.tools').glob('**/bin/java.exe'))
java = next((p for p in candidates if p.exists()), None)
if not java:
    sys.exit('Portable JDK unavailable')
cache = root / '.tools/gradle-home/caches/modules-2/files-2.1'
jars = [p for p in cache.glob('**/*.jar') if 'natives-' not in p.name or 'natives-windows.jar' in p.name or 'natives-desktop.jar' in p.name]
paths = [root / 'core/build/classes/java/main', root / 'lwjgl3/build/classes/java/main', root / 'lwjgl3/build/resources/main', root / 'assets'] + jars
name = 'stage1-smoke.png' if args.scene == 'route' else 'stage1-' + args.scene + '.png'
if args.frames is not None:
    name = 'stage1-' + args.scene + '-' + str(args.frames) + '.png'
screenshot = root / 'build' / name
if screenshot.exists():
    screenshot.unlink()
cmd = [str(java), '-Dstage1.smoke=true', '-Dstage1.mute=true', '-Dstage1.smokeScene=' + args.scene, '-Dstage1.screenshot=' + str(screenshot), '-cp', os.pathsep.join(map(str, paths)), 'io.jbnu.test.lwjgl3.Lwjgl3Launcher']
if args.frames is not None:
    cmd.insert(1, '-Dstage1.smokeFrames=' + str(args.frames))
result = subprocess.run(cmd, cwd=root, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=60)
print(result.stdout)
print(result.stderr, file=sys.stderr)
(root / 'build' / ('stage1-' + args.scene + '.log')).write_text(result.stdout + '\n' + result.stderr, encoding='utf-8')
if result.returncode:
    sys.exit(result.returncode)
if 'STAGE1_SMOKE_OK' not in result.stdout:
    sys.exit('FAIL: application did not confirm smoke completion')
expected = {'just': 'feedback=JUST PARRY', 'ranged': 'feedback=JUST PARRY', 'parry': 'feedback=PARRY', 'wounded': 'feedback=HIT'}
if args.scene in expected and expected[args.scene] not in result.stdout:
    sys.exit('FAIL: real combat outcome missing: ' + expected[args.scene])
if args.scene in ('just', 'ranged') and 'wound=0.0 ' not in result.stdout:
    sys.exit('FAIL: successful just parry did not clear the pre-existing wound')
if args.scene == 'guard' and 'guarding=true' not in result.stdout:
    sys.exit('FAIL: held guard did not remain active beyond the tap window')
if args.scene.startswith('stage2') or args.scene == 'transition':
    for expected_field in ('stage=2', 'wrapX=true', 'wrapY=false', 'paletteAxis=X'):
        if expected_field not in result.stdout:
            sys.exit('FAIL: second-stage state missing: ' + expected_field)
    fields = {}
    for key in ('worldWidth','worldHeight','cameraX','cameraY'):
        match = re.search(r'\b' + key + r'=([-+0-9.eE]+)', result.stdout)
        if not match:
            sys.exit('FAIL: second-stage diagnostic missing: ' + key)
        fields[key] = float(match.group(1))
    if fields['worldWidth'] <= 0 or fields['worldHeight'] <= 720:
        sys.exit('FAIL: tower does not have a genuinely taller world')
    if args.scene in ('stage2middle','stage2top') and fields['cameraY'] <= 360:
        sys.exit('FAIL: tower camera did not follow the player upward')
if args.scene.startswith('boss') or args.scene == 'transition3':
    for expected_field in ('stage=3 ', 'wrapX=true', 'wrapY=true'):
        if expected_field not in result.stdout:
            sys.exit('FAIL: boss-stage state missing: ' + expected_field)
    fields = {}
    for key in ('worldWidth','worldHeight','cameraX','cameraY','bossSeals','bossPhase','bossExposure'):
        match = re.search(r'\b' + key + r'=([-+0-9.eE]+)', result.stdout)
        if not match:
            sys.exit('FAIL: boss-stage diagnostic missing: ' + key)
        fields[key] = float(match.group(1))
    if (fields['worldWidth'], fields['worldHeight'], fields['cameraX'], fields['cameraY']) != (1280,720,640,360):
        sys.exit('FAIL: boss room does not stay in one fixed screen')
    if not 0 <= fields['bossSeals'] <= 3:
        sys.exit('FAIL: invalid boss seal count')
    if args.frames is None and args.scene in ('bosswindup','bossattack'):
        expected_phase = 1 if args.scene == 'bosswindup' else 2
        if fields['bossPhase'] != expected_phase:
            sys.exit('FAIL: boss attack screenshot missed its authoritative phase')
    if args.scene in ('bossexposed','bosstargetpush') and ('bossPrimed=true' not in result.stdout or fields['bossExposure'] <= 0):
        sys.exit('FAIL: exposed boss fixture is not actually primed')
    if args.scene in ('bossclear','bossdefeat') and (fields['bossSeals'] != 0 or 'bossDefeated=true' not in result.stdout):
        sys.exit('FAIL: boss clear fixture is not defeated')
    if 'paletteAxis=Y' not in result.stdout:
        sys.exit('FAIL: boss arena palette axis changed')
    if args.scene in ('bossarmed','bossmarked','bossmarkthawed','bossexposed','bosstargetpush'):
        frozen = 'true' if args.scene in ('bossarmed','bossmarked') else 'false'
        if 'timeFrozen=' + frozen not in result.stdout:
            sys.exit('FAIL: armed/frozen and exposed/thawed states are not distinct')
    if args.scene in ('bossarmed','bossmarked','bossmarkthawed'):
        for expected_field in ('bossTargetActive=true','bossPrimed=false','targetRevealed=true','targetSolved=false','targetVisible=true'):
            if expected_field not in result.stdout:
                sys.exit('FAIL: freeze marker must remain pending, not expose boss: ' + expected_field)
        for key in ('bossTargetX','bossTargetY','bossTargetGravity','bossTargetSerial'):
            match = re.search(r'\b' + key + r'=([-+0-9.eE]+)', result.stdout)
            if not match:
                sys.exit('FAIL: marked boss objective diagnostic missing: ' + key)
            fields[key] = float(match.group(1))
        if not (0 <= fields['bossTargetX'] <= 1280 and 0 <= fields['bossTargetY'] <= 720 and fields['bossTargetGravity'] in (-1,1) and fields['bossTargetSerial'] > 0):
            sys.exit('FAIL: marked boss objective lies outside valid arena/gravity')
    if args.scene == 'bosstargetpush':
        for expected_field in ('bossTargetActive=false','targetSolved=true','timeFrozen=false'):
            if expected_field not in result.stdout:
                sys.exit('FAIL: thawed clone push did not complete the boss objective: ' + expected_field)
    if args.scene == 'bosssealedjust' and (fields['bossSeals'] != 3 or 'bossPrimed=false' not in result.stdout):
        sys.exit('FAIL: sealed just-parry fixture unexpectedly damaged a seal')
    if args.scene == 'bosssealbreak' and (fields['bossSeals'] != 2 or 'bossPrimed=false' not in result.stdout or 'bossArtState=break' not in result.stdout):
        sys.exit('FAIL: actual seal break did not consume a charge and display cracked art')
    if args.scene in ('bossclear','bossdefeat') and 'bossArtState=defeated' not in result.stdout:
        sys.exit('FAIL: boss defeat must use its actual defeated art')
    if args.scene in ('bosswrapx','bosswrapy'):
        gravity = '-1' if args.scene == 'bosswrapx' else '1'
        if 'gravity=' + gravity + ' ' not in result.stdout or 'dead=false' not in result.stdout:
            sys.exit('FAIL: boss-room portal lost gravity or killed the fixture player')
if args.scene.startswith(('targetreveal','targethidden','doorlocked','dooropen')) or args.scene == 'bossdelivery':
    stage = 3 if args.scene == 'bossdelivery' else int(args.scene[-1])
    if 'stage=' + str(stage) + ' ' not in result.stdout:
        sys.exit('FAIL: echo puzzle fixture loaded the wrong stage')
    required = []
    if args.scene.startswith('targetreveal'):
        required = ['targetRevealed=true','targetSolved=false','targetVisible=true','timeFrozen=true']
    elif args.scene.startswith('targethidden'):
        required = ['targetRevealed=true','targetSolved=false','targetVisible=false','timeFrozen=false']
    elif args.scene.startswith('doorlocked'):
        required = ['doorUnlocked=false','targetSolved=false']
    elif args.scene.startswith('dooropen'):
        required = ['doorUnlocked=true','targetSolved=true','targetVisible=false','timeFrozen=false']
    else:
        required = ['targetSolved=true','bossPrimed=true','targetVisible=false','timeFrozen=false']
    for expected_field in required:
        if expected_field not in result.stdout:
            sys.exit('FAIL: actual echo delivery state missing: ' + expected_field)
    if args.scene.startswith('target') or args.scene == 'bossdelivery':
        for key in ('echoTargetX','echoTargetY','targetGravity'):
            match = re.search(r'\b' + key + r'=([-+0-9.eE]+)', result.stdout)
            if not match or (key != 'targetGravity' and float(match.group(1)) < 0):
                sys.exit('FAIL: valid echo target coordinates missing: ' + key)
if args.scene == 'bossarrival':
    for expected_field in ('actualJ=true', 'inside=true', 'bossPrimed=true', 'targetSolved=true', 'bossSeals=3 ', 'timeFrozen=false', 'bossArtState=exposed'):
        if expected_field not in result.stdout:
            sys.exit('FAIL: inside-mark real J arrival did not expose the boss: ' + expected_field)
if 'ASSET_RUNTIME_OK' not in result.stdout:
    sys.exit('FAIL: real asset runtime did not report initialization')
expected_textures = {'art/pixel_knight/background/warm_lower.png', 'art/pixel_knight/background/cold_upper.png'}
for manifest, actors in [('art/pixel_knight/manifest.json',True), ('art/terrain_knight/manifest.json',False), ('art/knight_swing/manifest.json',False), ('art/paradox_warden/manifest.json',False)]:
    groups = json.loads((root/'assets'/manifest).read_text(encoding='utf-8'))['groups']
    expected_textures.update(re.sub(r'^assets/', '', group['sheet']) for name,group in groups.items() if not (actors and name.startswith('tiles_')))
if not re.search(r'textures=' + str(len(expected_textures)) + r'\b', result.stdout) or 'missing=0' not in result.stdout:
    sys.exit('FAIL: dedicated boss texture/resource initialization does not match manifests')
sound_count = json.loads((root/'assets/audio/pixel_knight/manifest.json').read_text(encoding='utf-8'))['count']
if not re.search(r'sounds=' + str(sound_count) + r'\b', result.stdout):
    sys.exit('FAIL: audio resource count does not match the actual sound pack')
if not screenshot.exists() or screenshot.stat().st_size < 1000:
    sys.exit('FAIL: screenshot not produced')
from build_pixel_assets import decode_png
image = decode_png(screenshot.read_bytes())
print('PASS render smoke; PNG:', screenshot, 'bytes:', screenshot.stat().st_size, 'dimensions:', image[:2])
