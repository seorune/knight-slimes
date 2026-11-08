"""Stdlib-only contract tests for the independently authored side-view pack."""
import importlib.util
from pathlib import Path
import unittest

class TerrainTests(unittest.TestCase):
    def test_stone_tile_contract(self):
        import build_terrain_assets as b
        self.assertTrue(callable(getattr(b, 'terrain', None)), 'Terrain renderer missing')
        from build_pixel_assets import alpha_bbox
        required = {'stone_fill','surface_top','wall_left','wall_right','platform',
                    'outside_tl','outside_tr','outside_bl','outside_br',
                    'inside_tl','inside_tr','inside_bl','inside_br','bottom','seam'}
        self.assertEqual(set(b.TILE_NAMES), required)
        for palette in ('warm','cool'):
            for name in b.TILE_NAMES:
                image = b.terrain(palette, name)
                self.assertEqual(image[:2], (32,32))
                self.assertIsNotNone(alpha_bbox(image))
            w,h,p = b.terrain(palette, 'stone_fill')
            self.assertTrue(all(a == 255 for a in p[3::4]))
            self.assertEqual(p[:w*4], p[-w*4:])
            self.assertEqual(b''.join(p[y*w*4:y*w*4+4] for y in range(h)),
                             b''.join(p[(y*w+w-1)*4:(y*w+w)*4] for y in range(h)))
            self.assertGreater(len(set(tuple(p[i:i+4]) for i in range(0,len(p),4))), 6)
            platform = b.terrain(palette, 'platform')
            self.assertEqual(alpha_bbox(platform), (0,0,32,8))
            self.assertEqual(platform[2][3:32*8*4:4], bytes([255])*256)

    def test_bricks_gateway_hud(self):
        import build_terrain_assets as b
        from build_pixel_assets import alpha_bbox
        self.assertTrue(callable(getattr(b, 'brick', None)), 'Flat corpse brick renderer missing')
        for palette in b.PALETTES:
            for core in ('round','diamond'):
                image=b.brick(palette, core)
                self.assertEqual(image[:2], (32,16))
                self.assertEqual(alpha_bbox(image), (0,0,32,16))
                self.assertEqual(image[2][3:32*4:4], bytes([255])*32)
                self.assertTrue(all(a==255 for a in image[2][3::4]))
            images=[b.gateway(palette,state,pulse) for state,pulse in
                    [('closed',0),('open',0),('open',1),('open',2)]]
            self.assertEqual(len(set(im[2] for im in images)),4)
            for image in images:
                self.assertEqual(image[:2],(64,96))
                self.assertEqual(image[2][3::4],images[0][2][3::4])
                for y in range(96):
                    for x in range(64):
                        if not b.gateway_interior(x,y):
                            off=(y*64+x)*4
                            self.assertEqual(image[2][off:off+4], images[0][2][off:off+4])
        expected={'parry','just','melee','timestop','wound','heal','pause','restart','exit','gravity'}
        self.assertEqual(set(b.ICON_NAMES),expected)
        icons=[b.icon(n) for n in b.ICON_NAMES]
        self.assertEqual(len(set(im[2] for im in icons)),10)
        for image in icons:
            self.assertEqual(image[:2],(16,16))
            self.assertIsNotNone(alpha_bbox(image))
            self.assertEqual(set(image[2][3::4]),{0,255})

    def test_manifest_atlases_reproducibility_safety(self):
        import build_terrain_assets as b
        import hashlib, json, tempfile
        from build_pixel_assets import decode_png, crop, alpha_bbox
        self.assertTrue(callable(getattr(b, 'build_pack', None)), 'Pack writer missing')
        parent=Path(__file__).resolve().parents[1]/'assets/art/terrain_knight'
        parent.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='.test_',dir=parent) as temp:
            root=Path(temp)
            manifest=b.build_pack(root)
            base=root/'assets/art/terrain_knight'
            self.assertEqual(manifest['counts'],{'groups':7,'frames':52,'png_files':59})
            self.assertEqual(manifest['provenance'], b.PROVENANCE)
            self.assertEqual(manifest['world_layout'],{'above':'cool','below':'warm'})
            self.assertEqual(len(manifest['files']),59)
            snapshots={}
            for record in manifest['files']:
                path=root/record['path']; data=path.read_bytes()
                snapshots[record['path']]=data
                self.assertEqual(hashlib.sha256(data).hexdigest(),record['sha256'])
                w,h,p=decode_png(data)
                self.assertEqual((w,h),(record['width'],record['height']))
                self.assertEqual(list(alpha_bbox((w,h,p))),record['alpha_bbox'])
                self.assertEqual(p[3::4].count(0),record['transparent_pixels'])
                self.assertEqual(p[3::4].count(255),record['opaque_pixels'])
                self.assertEqual(data[24:26],b'\x08\x06')
                self.assertEqual(len(p),w*h*4)
            for group in manifest['groups'].values():
                sheet=decode_png((root/group['sheet']).read_bytes())
                self.assertEqual(sheet[:2],tuple(group['sheet_size']))
                self.assertEqual(group['columns'],4)
                self.assertEqual(group['gutter'],0)
                self.assertEqual(group['filter'],'nearest')
                self.assertEqual(group['frame_count'],len(group['frames']))
                for frame in group['frames']:
                    self.assertEqual(crop(sheet,*frame['rect']),decode_png((root/frame['path']).read_bytes()))
                    for rect in frame['collision_rects']:
                        x,y,w,h=rect
                        self.assertGreater(w*h,0)
                        self.assertTrue(0<=x<x+w<=group['cell_size'][0])
                        self.assertTrue(0<=y<y+h<=group['cell_size'][1])
                for clip in group['clips'].values():
                    self.assertTrue(all(0<=n<group['frame_count'] for n in clip['frames']))
                    self.assertAlmostEqual(clip['duration_seconds'],len(clip['frames'])*clip['frame_duration_seconds'])
            for palette in b.PALETTES:
                g=manifest['groups']['bricks_'+palette]
                self.assertEqual(g['spawn_policy'],'original_only; clones vanish without brick')
                self.assertTrue(all(f['collision_rects']==[[0,0,32,16]] for f in g['frames']))
                g=manifest['groups']['exit_'+palette]
                self.assertEqual(g['clips']['open']['frames'],[1,2,3,2])
            old=(base/'manifest.json').read_bytes()
            self.assertEqual(b.build_pack(root),manifest)
            self.assertEqual((base/'manifest.json').read_bytes(),old)
            self.assertEqual({p:(root/p).read_bytes() for p in snapshots},snapshots)
            victim=root/manifest['files'][0]['path']
            victim.write_bytes(b'local user edit')
            with self.assertRaisesRegex(ValueError,'modified|overwrite'):
                b.build_pack(root)
            self.assertEqual(victim.read_bytes(),b'local user edit')
            self.assertEqual((base/'manifest.json').read_bytes(),old)

    def test_builder_available(self):
        self.assertIsNotNone(importlib.util.find_spec('build_terrain_assets'),
                             'Independent side-view terrain builder is missing')

if __name__ == '__main__':
    unittest.main(verbosity=2)
