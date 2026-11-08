"""Independent output checks for the six-state boss atlas (stdlib only)."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import unittest
import zlib

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'assets/art/paradox_warden'
STATES = ['sealed', 'exposed', 'charge', 'attack', 'break', 'defeated']


def read_rgba(path):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    pos, compressed = 8, bytearray()
    while pos < len(data):
        n = int.from_bytes(data[pos:pos+4], 'big')
        tag, body = data[pos+4:pos+8], data[pos+8:pos+8+n]
        assert zlib.crc32(tag+body) == int.from_bytes(data[pos+8+n:pos+12+n], 'big')
        if tag == b'IHDR':
            w,h,depth,kind,_,_,interlace = struct.unpack('>IIBBBBB', body)
            assert (depth,kind,interlace) == (8,6,0)
        if tag == b'IDAT': compressed.extend(body)
        pos += 12+n
        if tag == b'IEND': break
    raw = zlib.decompress(compressed)
    assert len(raw) == (w*4+1)*h
    rows = []
    for y in range(h):
        row = raw[y*(w*4+1):(y+1)*(w*4+1)]
        assert row[0] == 0, 'Output deliberately uses filter zero for independent inspection'
        rows.append(row[1:])
    return w,h,b''.join(rows)


class BossAssetsTest(unittest.TestCase):
    def test_runtime_atlas_contract(self):
        self.assertTrue((ART/'manifest.json').exists(), 'New boss atlas not built')
        m = json.loads((ART/'manifest.json').read_text())
        g = m['groups']['paradox_warden']
        self.assertEqual(g['frame_count'], 6)
        self.assertEqual(g['cell_size'], [96,96])
        self.assertEqual(read_rgba(ROOT/g['sheet'])[:2], (288,192))
        for i,s in enumerate(STATES):
            self.assertEqual(g['clips'][s], {'frames':[i], 'frame_duration':0.1, 'frame_duration_seconds':0.1, 'loop':False})
            self.assertEqual(g['frames'][i]['rect'], [i%3*96,i//3*96,96,96])
            self.assertEqual(g['frames'][i]['anchor'], [48,90])

def frames():
    w,h,pixels=read_rgba(ART/'sheet.png')
    return [b''.join(pixels[((i//3*96+y)*w+i%3*96)*4:((i//3*96+y)*w+i%3*96+96)*4] for y in range(96)) for i in range(6)]


def area(frame,x0,y0,x1,y1):
    return [tuple(frame[(y*96+x)*4:(y*96+x+1)*4]) for y in range(y0,y1) for x in range(x0,x1)]


class BossPixelTest(unittest.TestCase):
    def test_shattered_clock_halo_has_fixed_gold_hour_mark(self):
        for p in frames()[:5]:
            self.assertEqual(area(p,48,16,49,17),[(248,200,96,255)])

    def test_broken_shoulder_has_actual_missing_armor(self):
        broken=frames()[4]
        self.assertTrue(all(p[3]==0 for p in area(broken,30,38,35,43)), 'Break must remove an armor chunk, not merely recolor it')

    def test_six_distinct_shapes_and_stable_standing_feet(self):
        f=frames()
        self.assertEqual(len(set(f)),6)
        self.assertEqual(len({p[3::4] for p in f}),6)
        for p in f[1:5]: self.assertEqual(p[78*96*4:91*96*4], f[0][78*96*4:91*96*4])
        for p in f:
            self.assertTrue(all(a in (0,255) for a in p[3::4]))
            self.assertTrue(all(q[3]==0 for q in area(p,0,0,96,3)+area(p,0,91,96,96)))
            self.assertFalse(any(g>r*1.18 and g>b*1.18 and a for r,g,b,a in zip(p[0::4],p[1::4],p[2::4],p[3::4])))

    def test_closed_opaque_gold_versus_open_cyan_core(self):
        sealed,exposed,charge,attack,broken,dead=frames()
        closed=area(sealed,42,50,54,61)
        opened=area(exposed,42,50,54,61)
        self.assertTrue(all(a==255 for r,g,b,a in closed))
        self.assertEqual(sum(b>160 and g>160 and a>0 for r,g,b,a in closed),0)
        self.assertGreater(sum(b>160 and g>160 and a>0 for r,g,b,a in opened),55)
        self.assertGreater(sum(r>200 and r>g*1.8 and a>0 for r,g,b,a in area(attack,42,48,55,64)),35)
        self.assertGreater(sum(r>200 and g>100 and b<100 and a>0 for r,g,b,a in area(charge,42,48,55,64)),35)
        # Defeat has no standing torso; its weight visibly collapses downward.
        self.assertLess(sum(q[3]>0 for q in area(dead,25,15,70,50)),sum(q[3]>0 for q in area(exposed,25,15,70,50))//2)

    def test_manifest_pixel_metadata_and_source_hash(self):
        m=json.loads((ART/'manifest.json').read_text())
        g=m['groups']['paradox_warden']
        self.assertEqual(hashlib.sha256((ART/'source.png').read_bytes()).hexdigest(),m['provenance']['source_sha256'])
        self.assertEqual(hashlib.sha256((ART/'sheet.png').read_bytes()).hexdigest(),g['sheet_sha256'])
        for record,p in zip(g['frames'],frames()):
            self.assertEqual(record['rgba_sha256'],hashlib.sha256(p).hexdigest())
            self.assertEqual(record['opaque_pixels'],sum(bool(a) for a in p[3::4]))
            xs,ys=zip(*[(x,y) for y in range(96) for x in range(96) if p[(y*96+x)*4+3]])
            self.assertEqual(record['alpha_bbox'],[min(xs),min(ys),max(xs)+1,max(ys)+1])

    def test_deterministic_rebuild(self):
        before={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [ART/'manifest.json',ART/'sheet.png']}
        subprocess.run([sys.executable,str(ROOT/'tools/build_boss_assets.py')],check=True,capture_output=True)
        self.assertEqual(before,{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [ART/'manifest.json',ART/'sheet.png']})


if __name__ == '__main__':
    unittest.main(verbosity=2)
