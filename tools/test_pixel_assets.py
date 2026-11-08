"""stdlib-only regression suite; synthetic fixtures are tests, never deliverables."""
import importlib.util
import pathlib
import struct
import unittest
import zlib

MODULE = pathlib.Path(__file__).with_name('build_pixel_assets.py')
if MODULE.exists():
    spec = importlib.util.spec_from_file_location('pixel', MODULE)
    pixel = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(pixel)
else:
    pixel = None


def fixture(color_type, filter_type):
    channels = {2: 3, 6: 4}[color_type]
    rows = [bytes((i * 31 + y * 19) % 256 for i in range(3 * channels)) for y in range(2)]
    raw = bytearray()
    prev = bytes(len(rows[0]))
    for row in rows:
        raw.append(filter_type)
        for i, value in enumerate(row):
            a = row[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            p = a + b - c
            distances = [abs(p-a), abs(p-b), abs(p-c)]
            predictor = [0, a, b, (a+b)//2, [a,b,c][distances.index(min(distances))]][filter_type]
            raw.append((value-predictor) % 256)
        prev = row
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag+data))
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB',3,2,8,color_type,0,0,0)) + chunk(b'IDAT',zlib.compress(raw)) + chunk(b'IEND',b'')
    expected = b''.join(row[i:i+channels] + (b'\xff' if channels == 3 else b'') for row in rows for i in range(0,len(row),channels))
    return data, expected


class PNGTests(unittest.TestCase):
    def test_decode_rgb_rgba_all_filters_and_encode_roundtrip(self):
        self.assertIsNotNone(pixel, 'PNG implementation is missing')
        for ct in (2,6):
            for ft in range(5):
                with self.subTest(color=ct, filter=ft):
                    data, expected = fixture(ct, ft)
                    image = pixel.decode_png(data)
                    self.assertEqual(image, (3,2,expected))
                    self.assertEqual(pixel.decode_png(pixel.encode_png(*image)), image)

    def test_bad_crc_rejected(self):
        self.assertIsNotNone(pixel, 'PNG implementation is missing')
        data,_ = fixture(2,0)
        data = data[:-1] + bytes([data[-1]^1])
        with self.assertRaisesRegex(ValueError, 'CRC'):
            pixel.decode_png(data)


class ProcessingTests(unittest.TestCase):
    def test_key_keeps_gold_purple_and_clears_green(self):
        self.assertTrue(hasattr(pixel, 'green_key'), 'green_key missing')
        src = (3,1,bytes([0,255,0,255, 220,180,20,255, 150,20,180,255]))
        keyed = pixel.green_key(src)
        self.assertEqual(keyed[2][:4], bytes(4))
        self.assertEqual(keyed[2][4:], src[2][4:])

    def test_uniform_sheet_scale_and_baseline_without_clipping(self):
        self.assertTrue(hasattr(pixel, 'normalize_sheet'), 'normalize_sheet missing')
        w = 128
        data = bytearray(w*w*4)
        for frame in range(16):
            x0,y0=(frame%4)*32,(frame//4)*32
            height = 6 if frame >= 14 else 18
            for y in range(5,5+height):
                for x in range(10,22):
                    i=((y0+y)*w+x0+x)*4
                    data[i:i+4]=bytes([230,110,25,255])
        frames,meta = pixel.normalize_sheet((w,w,bytes(data)),64,True)
        self.assertEqual(len(frames),16)
        self.assertEqual(len({m['scale'] for m in meta}),1)
        for image,m in zip(frames,meta):
            self.assertEqual(image[:2],(64,64))
            box = pixel.alpha_bbox(image)
            self.assertEqual(box[3],55)
            self.assertGreater(box[0],0)
            self.assertLess(box[2],64)
            self.assertFalse(m['clipped'])
        clone = pixel.recolor(frames[0], 'purple')
        self.assertEqual(clone[2][3::4],frames[0][2][3::4])
        self.assertNotEqual(clone[2],frames[0][2])

    def test_bright_green_enclosed_holes_are_transparent_but_dark_effect_survives(self):
        data=bytearray(bytes([0,0,0,255])*25)
        data[(2*5+2)*4:(2*5+3)*4]=bytes([0,255,0,255])
        self.assertEqual(pixel.green_key((5,5,bytes(data)))[2][(2*5+2)*4+3],0)
        data[(2*5+2)*4:(2*5+3)*4]=bytes([0,100,0,255])
        self.assertEqual(pixel.green_key((5,5,bytes(data)))[2][(2*5+2)*4+3],255)

    def test_source_gutters_remove_grid_but_preserve_isolated_green(self):
        self.assertTrue(hasattr(pixel, 'strip_gutters'), 'strip_gutters missing')
        data=bytearray(bytes([0,255,0,255])*64*64)
        for y in range(20,28):
            for x in range(20,28):
                i=(y*64+x)*4
                data[i:i+4]=bytes([0,0,0,255])
        data[(23*64+23)*4:(23*64+24)*4]=bytes([0,100,0,255])
        keyed=pixel.green_key((64,64,bytes(data)))
        self.assertEqual(keyed[2][(23*64+23)*4+3],255)
        self.assertEqual(pixel.strip_gutters((64,64,bytes([0,0,0,255])*4096),2)[2][3],0)

    def test_resize_preserves_geometry_and_crop(self):
        self.assertTrue(hasattr(pixel, 'resize_nearest'), 'resize_nearest missing')
        src=(2,1,bytes([255,0,0,255,0,0,255,255]))
        resized=pixel.resize_nearest(src,4,2)
        self.assertEqual(resized[2][:8],bytes([255,0,0,255])*2)
        self.assertEqual(pixel.crop(resized,2,0,2,2)[2],bytes([0,0,255,255])*4)


class PackTests(unittest.TestCase):
    def test_real_pack_manifest_pngs_and_clone_policy(self):
        self.assertTrue(hasattr(pixel,'build_pack'), 'build_pack missing')
        root=MODULE.parent.parent
        required=['knight_sheet','melee_slime_sheet','ranged_slime_sheet','fx_sheet','tiles_sheet','dual_castle_background','fx_combat_sheet']
        if not all((root/'assets/art/source'/f'{n}.png').exists() for n in required):
            self.skipTest('Actual generated sources are not available')
        manifest=pixel.build_pack(root)
        self.assertEqual(len(manifest['sources']),7)
        self.assertIn('ranged_warning',manifest['groups']['fx']['clips'])
        self.assertIn('ranged_hit',manifest['groups']['fx']['clips'])
        self.assertTrue(manifest['groups']['fx']['source_path'].endswith('fx_combat_sheet.png'))
        self.assertIn('time_resume',manifest['groups']['fx_utilities']['clips'])
        self.assertEqual(manifest['counts']['png_files'],len(manifest['files']))
        for record in manifest['files']:
            path=root/record['path']
            data=path.read_bytes()
            image=pixel.decode_png(data)
            self.assertEqual(list(image[:2]),[record['width'],record['height']])
            self.assertEqual(__import__('hashlib').sha256(data).hexdigest(),record['sha256'])
            self.assertEqual(record['transparent_pixels'],image[2][3::4].count(0))
        for name in ['melee_clone','ranged_clone']:
            group=manifest['groups'][name]
            self.assertEqual(group['frame_count'],12)
            self.assertEqual(group['sheet_size'],[256,192])
            self.assertNotIn('brick',group['clips'])
            self.assertNotIn('death',group['clips'])
            original=manifest['groups'][name.replace('_clone','_original')]
            for frame,source in zip(group['frames'],original['frames']):
                clone=pixel.decode_png((root/frame['path']).read_bytes())
                orig=pixel.decode_png((root/source['path']).read_bytes())
                self.assertEqual(clone[2][3::4],orig[2][3::4])
        for name in ['knight','melee_original','ranged_original']:
            for f in manifest['groups'][name]['frames']:
                self.assertLessEqual(f['alpha_bbox'][3],55)
                self.assertFalse(f['clipped'])
        self.assertTrue(hasattr(pixel,'validate_pack'), 'validate_pack missing')
        pixel.validate_pack(root,manifest)
        broken=__import__('copy').deepcopy(manifest)
        broken['counts']['png_files']+=1
        with self.assertRaisesRegex(ValueError,'count'):
            pixel.validate_pack(root,broken)
        for name,group in manifest['groups'].items():
            sheet=pixel.decode_png((root/group['sheet']).read_bytes())
            for frame in group['frames']:
                rect=frame['rect']
                self.assertEqual(pixel.crop(sheet,*rect),pixel.decode_png((root/frame['path']).read_bytes()))
        self.assertFalse(manifest['background_seamless_verified'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
