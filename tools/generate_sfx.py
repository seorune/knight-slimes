"""Reproducible stdlib-only game SFX pack and output contract tests."""
import unittest

REQUIRED = tuple('step_stone_01 step_stone_02 jump land_light land_heavy sword_swing melee_push slime_windup slime_melee slime_upper spell_charge spell_line_hit parry just_parry player_hit wound_recover player_death slime_stun slime_to_brick brick_push clone_spawn clone_disappear resurrect time_stop time_resume boundary_cross exit_open stage_clear ui_confirm ui_pause'.split())

import argparse
import hashlib
import json
import math
import random
import struct
import wave
from pathlib import Path

RATE = 44100
# Event: start, duration, initial Hz, final Hz, gain, timbre, decay.
def e(start, duration, hz, end=None, gain=.5, timbre='sine', decay=5):
    return (start, duration, hz, hz if end is None else end, gain, timbre, decay)


def recipes():
    """Dry chip tones, short stone transients and inharmonic shield modes."""
    return {
        'step_stone_01': (.14, .52, [e(0,.085,95,55,.8,'noise',8),e(0,.07,175,95,.5,'sine',7)]),
        'step_stone_02': (.15, .49, [e(0,.095,115,63,.8,'noise',8),e(.003,.065,210,120,.45,'sine',7)]),
        'jump': (.24, .60, [e(0,.19,220,620,.6,'pulse',4),e(0,.045,120,70,.25,'noise',7)]),
        'land_light': (.18, .62, [e(0,.13,105,48,.7,'sine',7),e(0,.07,1200,300,.5,'noise',8)]),
        'land_heavy': (.29, .78, [e(0,.23,78,35,.9,'sine',6),e(.008,.14,650,170,.65,'noise',7),e(.045,.08,170,60,.25,'metal',8)]),
        'sword_swing': (.21, .62, [e(0,.16,3400,550,.75,'noise',3),e(.015,.12,780,260,.25,'pulse',5)]),
        'melee_push': (.22, .68, [e(0,.12,150,55,.8,'sine',6),e(0,.085,800,250,.6,'noise',7),e(.015,.14,270,120,.3,'metal',6)]),
        'slime_windup': (.38, .54, [e(0,.31,110,250,.6,'bubble',2),e(.18,.14,230,360,.25,'pulse',3)]),
        'slime_melee': (.21, .68, [e(0,.15,260,60,.7,'bubble',6),e(.008,.095,450,100,.4,'noise',6)]),
        'slime_upper': (.28, .70, [e(0,.20,95,480,.7,'bubble',4),e(.015,.13,600,1900,.3,'noise',6)]),
        'spell_charge': (.56, .59, [e(0,.48,300,900,.45,'riser',.7),e(.12,.35,450,1350,.3,'riser',.8),e(.35,.15,1700,1900,.18,'metal',3)]),
        'spell_line_hit': (.19, .78, [e(0,.055,3000,600,.75,'noise',9),e(0,.14,1600,400,.6,'metal',7),e(0,.085,110,70,.5,'sine',6)]),
        'parry': (.33, .73, [e(0,.028,2400,1400,.4,'noise',8),e(0,.26,780,760,.8,'metal',6),e(0,.20,310,280,.35,'sine',6)]),
        'just_parry': (.62, .86, [e(0,.023,4200,2200,.5,'noise',8),e(0,.48,1320,1320,.7,'metal',4),e(.025,.52,2640,2640,.4,'sine',4),e(.055,.45,1980,1980,.35,'sine',4),e(0,.12,220,180,.35,'sine',6)]),
        'player_hit': (.28, .76, [e(0,.16,240,65,.8,'pulse',6),e(0,.09,900,180,.6,'noise',7)]),
        'wound_recover': (.59, .57, [e(0,.22,392,392,.4,'triangle',4),e(.13,.25,494,494,.4,'triangle',4),e(.27,.28,659,659,.45,'sine',4)]),
        'player_death': (.87, .73, [e(0,.35,330,180,.5,'pulse',4),e(.20,.38,220,90,.5,'triangle',4),e(.45,.38,110,35,.5,'sine',5),e(.02,.3,600,90,.22,'noise',5)]),
        'slime_stun': (.37, .57, [e(0,.20,320,130,.6,'bubble',5),e(.10,.20,520,240,.35,'triangle',5)]),
        'slime_to_brick': (.43, .70, [e(0,.17,340,80,.5,'bubble',5),e(.09,.17,800,170,.6,'noise',7),e(.12,.23,260,180,.65,'metal',6),e(.18,.16,95,50,.5,'sine',6)]),
        'brick_push': (.27, .55, [e(0,.20,330,180,.65,'noise',2),e(.025,.18,95,62,.4,'triangle',5),e(.1,.11,460,250,.25,'noise',5)]),
        'clone_spawn': (.51, .59, [e(0,.32,220,660,.45,'triangle',3),e(.08,.32,330,990,.35,'sine',3),e(.22,.25,1320,1320,.25,'metal',5)]),
        'clone_disappear': (.36, .52, [e(0,.29,990,160,.5,'triangle',4),e(.02,.24,1500,120,.3,'noise',5)]),
        'resurrect': (.94, .72, [e(0,.38,130,260,.45,'triangle',3),e(.18,.38,392,392,.4,'sine',3),e(.34,.42,523.25,523.25,.4,'triangle',4),e(.49,.41,784,784,.4,'metal',5)]),
        'time_stop': (.53, .69, [e(0,.18,1700,280,.4,'metal',4),e(.04,.40,600,55,.6,'pulse',3),e(.12,.3,1500,130,.3,'noise',4)]),
        'time_resume': (.49, .65, [e(0,.30,100,650,.45,'pulse',3),e(.20,.25,880,880,.4,'metal',5),e(.23,.19,1320,1320,.2,'sine',5)]),
        'boundary_cross': (.67, .61, [e(0,.46,180,720,.4,'triangle',2),e(.10,.47,720,180,.4,'sine',3),e(.28,.32,1046.5,1046.5,.25,'metal',5)]),
        'exit_open': (.74, .66, [e(0,.19,165,110,.35,'noise',5),e(.08,.29,330,330,.45,'metal',5),e(.23,.32,494,494,.4,'triangle',4),e(.39,.30,659,659,.4,'sine',4)]),
        'stage_clear': (1.25, .73, [e(0,.24,523.25,523.25,.45,'triangle',4),e(.16,.24,659.25,659.25,.45,'triangle',4),e(.32,.25,784,784,.45,'triangle',4),e(.5,.65,1046.5,1046.5,.45,'triangle',4),e(.55,.62,1318.5,1318.5,.25,'sine',4),e(.55,.62,1568,1568,.2,'sine',4)]),
        'ui_confirm': (.20, .47, [e(0,.085,660,660,.5,'triangle',5),e(.065,.12,990,990,.5,'sine',5)]),
        'ui_pause': (.23, .43, [e(0,.10,660,660,.5,'triangle',5),e(.08,.12,440,440,.5,'triangle',5)]),
    }


PURPOSES = dict(zip(REQUIRED, [
    '돌바닥 발걸음 A', '돌바닥 발걸음 B', '도약', '가벼운 착지', '무거운 착지',
    '검 휘두르기', '근접 밀치기 충격', '슬라임 근접 예고', '슬라임 근접 타격', '슬라임 올려치기',
    '원거리 슬라임 직선 즉발 공격 예고', '직선 즉발 명중; 투사체 비행음 아님',
    '일반 방패 패링: 짧고 낮은 금속 울림', '저스트 패링: 높은 금속 배음과 긴 밝은 링',
    '플레이어 피격', '상처 회복', '플레이어 사망', '슬라임 기절', '슬라임 사망 후 벽돌화',
    '이동식 벽돌 플랫폼 밀기', '보라색 분신 생성', '사망한 분신 소멸', '경계 통과 후 부활',
    '시간 정지', '시간 재개', '온난 폐성/한랭 역성 경계 통과', '출구 열림', '스테이지 완료',
    'UI 확인', 'UI 일시정지',
]))


def synthesize(name, duration, peak, events):
    rng = random.Random('pixel-knight-v1:' + name)
    mix = [0.0] * round(duration * RATE)
    for start, length, hz, end, gain, timbre, decay in events:
        phase = 0.0
        low = 0.0
        count = round(length * RATE)
        offset = round(start * RATE)
        for j in range(min(count, len(mix)-offset)):
            u = j / max(1, count-1)
            phase += 2 * math.pi * (hz + (end-hz)*u) / RATE
            if timbre == 'noise':
                # Filtered noise: low-pass follows the event's frequency sweep.
                alpha = 1 - math.exp(-2*math.pi*max(80, hz+(end-hz)*u)/RATE)
                low += alpha * (rng.uniform(-1,1)-low)
                value = low * 2.2
            elif timbre == 'metal':
                value = sum(math.sin(phase*r)*a for r,a in ((1,.55),(1.483,.25),(2.117,.14),(3.937,.06)))
            elif timbre == 'pulse':
                value = (math.sin(phase)+math.sin(3*phase)/3+math.sin(5*phase)/5)/1.53
            elif timbre == 'triangle':
                value = (math.sin(phase)-math.sin(3*phase)/9+math.sin(5*phase)/25)/1.1512
            elif timbre == 'bubble':
                value = math.sin(phase + 1.6*math.sin(phase*.5)*(1-u))
            elif timbre == 'riser':
                value = (.7*math.sin(phase)+.3*math.sin(phase*2)) * (.15+.85*u)
            else:
                value = math.sin(phase)
            attack = min(1.0, j/(RATE*.0015))
            release = min(1.0, (count-1-j)/(RATE*.006))
            mix[offset+j] += gain * value * math.exp(-decay*u) * attack * release
    # Gentle DC blocker, then 2 ms in / 8 ms out. End samples exactly zero.
    previous = state = 0.0
    for i,x in enumerate(mix):
        state = x-previous+.995*state
        previous = x
        mix[i] = state * min(1,i/(RATE*.002)) * min(1,(len(mix)-1-i)/(RATE*.008))
    scale = peak * 32767 / max(abs(x) for x in mix)
    return [round(x*scale) for x in mix]


def inspect_wav(path):
    with wave.open(str(path), 'rb') as wav:
        channels, width, rate, frames = wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getnframes()
        if (channels,width,rate,wav.getcomptype()) != (1,2,RATE,'NONE'):
            raise ValueError('Invalid WAV contract: '+str(path))
        raw = wav.readframes(frames)
    samples = struct.unpack('<'+'h'*(len(raw)//2), raw)
    peak = max(abs(x) for x in samples)/32768
    rms = math.sqrt(sum(x*x for x in samples)/len(samples))/32768
    if not (0 < peak <= .9 and rms > 0 and samples[0] == samples[-1] == 0):
        raise ValueError('Invalid signal: '+str(path))
    return dict(channels=channels, sample_width_bits=width*8, sample_rate_hz=rate,
                frames=frames, duration_seconds=frames/rate, sample_peak=peak, rms=rms,
                peak_dbfs=20*math.log10(peak), rms_dbfs=20*math.log10(rms),
                first_sample=samples[0], last_sample=samples[-1],
                sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def generate(target):
    target = Path(target)
    target.mkdir(parents=True, exist_ok=True)
    sounds = []
    definitions = recipes()
    if set(definitions) != set(REQUIRED) or len(REQUIRED) != 30:
        raise ValueError('Recipe count mismatch')
    for name in REQUIRED:
        duration, peak, events = definitions[name]
        samples = synthesize(name, duration, peak, events)
        path = target / (name+'.wav')
        with wave.open(str(path), 'wb') as wav:
            wav.setparams((1,2,RATE,0,'NONE','not compressed'))
            wav.writeframes(struct.pack('<'+'h'*len(samples), *samples))
        sounds.append(dict(id=name, file=path.name, purpose_ko=PURPOSES[name], loop=False,
                           **inspect_wav(path)))
    manifest = dict(pack='pixel_knight', version=1, generator='tools/generate_sfx.py',
                    seed_scheme='pixel-knight-v1:<id>; Python random.Random string seed',
                    count=len(sounds), ambient_loop_count=0, auditioned=False,
                    format='mono PCM16 little-endian WAV, 44100 Hz', sounds=sounds)
    (target/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return manifest


class OutputContract(unittest.TestCase):
    def test_complete_pack(self):
        self.assertTrue(callable(globals().get('generate')), 'missing pack generator')
        import tempfile
        import json
        import wave
        import struct
        import math
        from pathlib import Path
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp)
            generate(target)
            manifest = json.loads((target / 'manifest.json').read_text(encoding='utf-8'))
            self.assertEqual(manifest['count'], 30)
            self.assertEqual(set(p.stem for p in target.glob('*.wav')), set(REQUIRED))
            self.assertEqual(len(manifest['sounds']), len(REQUIRED))
            self.assertEqual({s['id'] for s in manifest['sounds']}, set(REQUIRED))
            hashes = []
            for sound in manifest['sounds']:
                with self.subTest(sound=sound['id']):
                    with wave.open(str(target / sound['file']), 'rb') as wav:
                        self.assertEqual((wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getcomptype()), (1, 2, 44100, 'NONE'))
                        samples = struct.unpack('<' + 'h' * wav.getnframes(), wav.readframes(wav.getnframes()))
                    self.assertGreater(len(samples), 100)
                    self.assertGreater(max(abs(v) for v in samples), 0)
                    self.assertLessEqual(max(abs(v) for v in samples) / 32768, .9)
                    self.assertGreater(math.sqrt(sum(v*v for v in samples)/len(samples)), 0)
                    self.assertEqual((samples[0], samples[-1]), (0, 0))
                    self.assertAlmostEqual(sound['duration_seconds'], len(samples)/44100, places=7)
                    hashes.append(sound['sha256'])
            self.assertEqual(len(set(hashes)), 30, 'all cues must be unique')
            original = {p.name: p.read_bytes() for p in target.iterdir()}
            generate(target)
            self.assertEqual(original, {p.name: p.read_bytes() for p in target.iterdir()})

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--self-test', action='store_true', help='Run contract and reproducibility tests in scratch storage')
    parser.add_argument('--verify', action='store_true', help='Read existing files; do not synthesize')
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1]/'assets/audio/pixel_knight')
    args = parser.parse_args()
    if args.self_test:
        unittest.main(argv=['generate_sfx.py'], exit=True)
    if args.verify:
        manifest = json.loads((args.output/'manifest.json').read_text(encoding='utf-8'))
        if manifest['count'] != 30 or len(manifest['sounds']) != 30:
            raise ValueError('Manifest count mismatch')
        if {p.stem for p in args.output.glob('*.wav')} != set(REQUIRED):
            raise ValueError('Unexpected or missing WAV files')
        if {s['id'] for s in manifest['sounds']} != set(REQUIRED):
            raise ValueError('Unexpected or duplicate manifest IDs')
        for sound in manifest['sounds']:
            if sound['file'] != sound['id']+'.wav':
                raise ValueError('Unexpected filename')
            actual = inspect_wav(args.output/sound['file'])
            if any(sound[key] != value for key,value in actual.items()):
                raise ValueError('Manifest differs from WAV: '+sound['id'])
    else:
        manifest = generate(args.output)
    for sound in manifest['sounds']:
        print('{id:20s} {duration_seconds:5.2f}s peak={sample_peak:.6f} rms={rms:.6f} {channels}ch {sample_rate_hz}Hz'.format(**sound))
    print('PASS: {} unique one-shots; mono PCM16 44100 Hz; peak <= 0.9; nonzero RMS; zero endpoints.'.format(manifest['count']))


if __name__ == '__main__':
    main()
