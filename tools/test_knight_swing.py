"""Real-pixel regression checks; run python tools/test_knight_swing.py."""
import unittest
from pathlib import Path
from build_pixel_assets import decode_png, crop
import build_knight_swing as swing

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'assets/art/pixel_knight/knight/frame_00.png'

def pixel(image,x,y):
    return image[2][(y*image[0]+x)*4:(y*image[0]+x+1)*4]

class SwingTest(unittest.TestCase):
    def test_real_eight_poses_keep_body_but_articulate_wrist_and_blade(self):
        source=decode_png(SOURCE.read_bytes())
        frames,poses=swing.make_frames(source)
        self.assertEqual(len(frames),8,'Must author eight real poses, not empty/duplicate effect frames')
        self.assertEqual(len({f[2] for f in frames}),8)
        self.assertEqual(len({tuple(p['wrist']) for p in poses}),8)
        self.assertEqual(len({tuple(p['elbow']) for p in poses}),8)
        self.assertEqual(len({tuple(p['tip']) for p in poses}),8)
        for frame,pose in zip(frames,poses):
            self.assertEqual(frame[:2],(96,96))
            self.assertEqual((pose['root_x'],pose['baseline']),(42,70))
            # Legs/boots and torso stay exact; only authored foreground pixels
            # in the two low-pose shield-interior windows may occlude source.
            foreground=bytearray(96*96*4)
            swing.draw_arm(foreground,pose['elbow'],pose['wrist'],pose['tip'])
            for x0,y0,x1,y1 in [(0,41,64,64),(24,38,30,41),(35,30,44,40)]:
                for y in range(y0,y1):
                    for x in range(x0,x1):
                        if pixel(source,x,y)[3]:
                            fx,fy=x+16,y+16
                            overlay=bytes(foreground[(fy*96+fx)*4:(fy*96+fx+1)*4])
                            occluded=pose['index'] in (4,5) and 52<=fx<60 and 46<=fy<56 and overlay[3]
                            expected=overlay if occluded else pixel(source,x,y)
                            self.assertEqual(pixel(frame,fx,fy),expected,(pose['index'],x,y))
            # No original static blade pixels outside body in any new frame.
            # New moving blade may cross this area but may not preserve the source texture.
            old=[(x,y) for y in range(12,27) for x in range(43,54) if pixel(source,x,y)[3]]
            self.assertFalse(all(pixel(frame,x+16,y+16)==pixel(source,x,y) for x,y in old))
            for x,y in [pose['wrist'],pose['tip']]:
                self.assertGreater(pixel(frame,x,y)[3],0)
            for y in range(96):
                for x in range(96):
                    rgba=pixel(frame,x,y)
                    self.assertIn(rgba[3],(0,255))
                    if x in (0,95) or y in (0,95): self.assertEqual(rgba[3],0,'No clipping')
                    if not rgba[3]: self.assertEqual(rgba,b'\0'*4)

    def test_low_swing_hand_hilt_and_blade_root_are_visible(self):
        frames,_=swing.make_frames(decode_png(SOURCE.read_bytes()))
        # Native-pixel cues: hand -> dark grip -> gold guard -> blade root.
        # In the old shield-last composition these became unrelated shield pixels.
        cues={4:[(52,49,swing.STEEL),(53,51,swing.GRIP),(55,50,swing.GOLD)],
              5:[(48,48,swing.STEEL),(52,50,swing.GOLD),(52,54,swing.LIGHT)]}
        for index,points in cues.items():
            for x,y,color in points:
                self.assertEqual(pixel(frames[index],x,y),bytes(color),(index,x,y,'hidden grip cue'))

    def test_low_grip_occlusion_keeps_every_other_pixel_and_shield_rim_exact(self):
        source=decode_png(SOURCE.read_bytes());body,_=swing.body_layer(source)
        frames,_=swing.make_frames(source)
        # Reconstruct the original shield-last composition independently.
        for i,(_,elbow,wrist,tip) in enumerate(swing.POSES):
            legacy=bytearray(body[2]);swing.draw_arm(legacy,elbow,wrist,tip)
            for y in range(29,41):
                for x in range(35,45):
                    rgba=pixel(source,x,y)
                    if rgba[3]: swing.put(legacy,x+16,y+16,rgba)
            changed=[]
            for y in range(96):
                for x in range(96):
                    before=bytes(legacy[(y*96+x)*4:(y*96+x+1)*4])
                    after=pixel(frames[i],x,y)
                    if after!=before:
                        changed.append((x,y))
                        self.assertIn(i,(4,5))
                        self.assertTrue(52<=x<60 and 46<=y<56,(i,x,y))
                        self.assertEqual(after[3],before[3],'Shield silhouette must not change')
            if i in (4,5): self.assertGreater(len(changed),0)
            else: self.assertEqual(changed,[])
        # The byte-copied underlying shield remains original even under overlap.
        for y in range(29,41):
            for x in range(35,45):
                self.assertEqual(pixel(body,x+16,y+16),pixel(source,x,y))

    def test_one_consistent_physical_sword_and_no_old_tip_stub(self):
        import math
        source=decode_png(SOURCE.read_bytes());body,_=swing.body_layer(source)
        self.assertEqual(pixel(body,42+16,27+16),b'\0'*4,'Old sword base stub must be erased')
        _,poses=swing.make_frames(source)
        lengths=[math.dist(p['wrist'],p['tip']) for p in poses]
        self.assertLess(max(lengths)-min(lengths),1.5,'Same physical sword must not stretch between poses')

    def test_pack_manifest_real_png_sheet_and_reproducibility(self):
        import tempfile,json,hashlib,os
        with tempfile.TemporaryDirectory(dir=os.environ['TMPDIR']) as temp:
            root=Path(temp)
            source=root/swing.SOURCE;source.parent.mkdir(parents=True)
            source.write_bytes(SOURCE.read_bytes())
            self.assertTrue(callable(getattr(swing,'build_pack',None)),'Missing pack builder')
            swing.build_pack(root)
            path=root/swing.OUT/'manifest.json'
            manifest=json.loads(path.read_text(encoding='utf-8'))
            group=manifest['groups']['knight_swing']
            self.assertEqual((group['frame_count'],group['cell_size'],group['sheet_size']),(8,[96,96],[384,192]))
            self.assertEqual(group['clips']['melee']['frames'],list(range(8)))
            self.assertEqual(group['clips']['melee']['frame_duration_seconds'],.035)
            self.assertIs(group['clips']['melee']['loop'],False)
            sheet=decode_png((root/group['sheet']).read_bytes())
            for f in group['frames']:
                self.assertEqual(decode_png((root/f['path']).read_bytes()),crop(sheet,*f['rect']))
                self.assertEqual((f['root_x'],f['baseline']),(42,70))
            hashes={f['path']:hashlib.sha256((root/f['path']).read_bytes()).hexdigest() for f in manifest['files']}
            for f in manifest['files']:
                self.assertEqual(f['sha256'],hashes[f['path']])
                image=decode_png((root/f['path']).read_bytes())
                self.assertEqual(f['opaque_pixels'],image[2][3::4].count(255))
            before=path.read_bytes()
            swing.build_pack(root)
            self.assertEqual(before,path.read_bytes())
            self.assertEqual(hashes,{p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in hashes})

if __name__=='__main__': unittest.main(verbosity=2)
