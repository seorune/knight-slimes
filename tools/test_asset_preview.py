"""Actual preview export contract: files, timings, alpha variants and audio."""
import base64
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import unittest

ROOT=Path(__file__).resolve().parents[1]

class PreviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        subprocess.run([sys.executable,str(ROOT/'tools/export_asset_preview.py')],cwd=ROOT,check=True,capture_output=True)
        cls.html=(ROOT/'Asset-Preview.html').read_text(encoding='utf-8')
        cls.data=json.loads(cls.html.split('const DATA=',1)[1].split(';\nlet tab=',1)[0])

    def test_real_file_bytes_and_pack_names(self):
        expected=[]
        for name in ('pixel_knight','terrain_knight','knight_swing'):
            root=ROOT/'assets/art'/name
            if root.exists():
                expected.extend(root.rglob('*.png'))
        images={i['name']:i for i in self.data['images']}
        self.assertEqual(len(images),len(expected))
        for path in expected:
            name=path.relative_to(ROOT/'assets/art').as_posix()
            self.assertIn(name,images)
            actual=base64.b64decode(images[name]['src'].split(',',1)[1])
            self.assertEqual(hashlib.sha256(actual).digest(),hashlib.sha256(path.read_bytes()).digest())

    def test_clips_retain_real_timing_and_loop_policy(self):
        self.assertTrue(self.data['animations'])
        for group in self.data['animations']:
            for name,clip in group['clips'].items():
                self.assertIsInstance(clip,dict,'Preview must preserve clip metadata, not just frame indices')
                self.assertGreater(clip['frame_duration_seconds'],0)
                self.assertIsInstance(clip['loop'],bool)
                self.assertTrue(clip['frames'])
                self.assertTrue(all(0<=i<len(group['frames']) for i in clip['frames']))
        knight=next(g for g in self.data['animations'] if g['name']=='pixel_knight/knight')
        self.assertFalse(knight['clips']['death']['loop'])
        self.assertTrue(knight['clips']['run']['loop'])

    def test_all_real_sounds_and_no_external_network(self):
        expected=list((ROOT/'assets/audio/pixel_knight').glob('*.wav'))
        self.assertEqual(len(expected),len(self.data['sounds']))
        sounds={s['name']:s for s in self.data['sounds']}
        for path in expected:
            decoded=base64.b64decode(sounds[path.stem]['src'].split(',',1)[1])
            self.assertEqual(decoded,path.read_bytes())
        self.assertNotIn('fetch(',self.html)
        self.assertNotIn('<script src=',self.html)

if __name__=='__main__':
    unittest.main(verbosity=2)
