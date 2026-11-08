"""Inspect the built desktop JAR; no build, downloads, or asset mutations.

Run after: gradlew --offline :lwjgl3:jar
"""
import json
from pathlib import Path
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
BACKGROUNDS = {
    "art/pixel_knight/background/warm_lower.png",
    "art/pixel_knight/background/cold_upper.png",
}


def development_only(path):
    return (
        path.startswith(("art/source/", "art-direction/", "art/knight_swing/provenance/"))
        or "/proof/" in path
        or path in {"block.png", "flag.png", "libgdx.png", "player.png"}
        or (path.startswith("art/") and path.endswith(".png")
            and not path.endswith("/sheet.png") and path not in BACKGROUNDS)
    )


class ReleaseResourcesTest(unittest.TestCase):
    def test_runtime_assets_and_index_match_the_archive(self):
        jars = list((ROOT / "lwjgl3/build/libs").glob("Project2-*.jar"))
        self.assertEqual(1, len(jars), "Build the single desktop release JAR first")
        asset_files = {
            p.relative_to(ROOT / "assets").as_posix(): p
            for p in (ROOT / "assets").rglob("*") if p.is_file()
        }
        expected = {name for name in asset_files
                    if name != "assets.txt" and not development_only(name)}
        with ZipFile(jars[0]) as jar:
            names = {info.filename for info in jar.infolist() if not info.is_dir()}
            leaks = sorted(name for name in names if development_only(name))
            self.assertEqual([], leaks, "Development assets leaked into release")
            listed = jar.read("assets.txt").decode("utf-8").splitlines()
            self.assertEqual(sorted(expected), listed, "Index must exactly list shipped assets")
            self.assertEqual(expected, names.intersection(asset_files) - {"assets.txt"})
            self.assertEqual(listed, (ROOT / "build/generated/release-assets/assets.txt")
                             .read_text(encoding="utf-8").splitlines())
            for name in expected:
                self.assertEqual(asset_files[name].read_bytes(), jar.read(name), name)
            for manifest in sorted(expected):
                if manifest.startswith("art/") and manifest.endswith("/manifest.json"):
                    data = json.loads(jar.read(manifest))
                    for group in data["groups"].values():
                        sheet = group["sheet"].removeprefix("assets/")
                        self.assertIn(sheet, expected, manifest)
                        self.assertIn(sheet, names, manifest)
            self.assertTrue(any(name.endswith(".wav") for name in expected))
            self.assertTrue(any(name.endswith(".fnt") for name in names), "Keep dependency fonts")
            resources = ROOT / "lwjgl3/src/main/resources"
            for resource in resources.rglob("*"):
                if resource.is_file():
                    name = resource.relative_to(resources).as_posix()
                    self.assertEqual(resource.read_bytes(), jar.read(name), name)
            print(f"Release verified: {len(expected)} assets; source/proof leaks: {len(leaks)}; {jars[0]}")


if __name__ == "__main__":
    unittest.main()
