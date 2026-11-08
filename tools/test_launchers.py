"""Windows launchers must work without an untracked local setup script."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

@unittest.skipUnless(os.name == 'nt', 'Windows batch launchers')
class PortableLaunchers(unittest.TestCase):
    def run_stage(self, stage):
        output = ROOT / 'build/launcher-test'
        output.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=output) as folder:
            folder = Path(folder)
            launcher = f'Play-Stage{stage}.bat'
            shutil.copy2(ROOT / launcher, folder / launcher)
            (folder / 'gradlew.bat').write_text(
                '@echo off\necho LAUNCHER_OK %JAVA_TOOL_OPTIONS%\nexit /b 0\n',
                encoding='ascii')
            env = os.environ.copy()
            env.pop('JAVA_TOOL_OPTIONS', None)
            try:
                result = subprocess.run(
                    ['cmd.exe', '/d', '/c', launcher], cwd=folder, env=env,
                    input='\n', capture_output=True, text=True,
                    encoding='utf-8', errors='replace', timeout=10)
            except subprocess.TimeoutExpired:
                self.fail('Launcher waits for a private setup script instead of running Gradle')
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('LAUNCHER_OK', result.stdout)
            if stage == 1:
                self.assertNotIn('game.startStage=', result.stdout)
            else:
                self.assertIn(f'-Dgame.startStage={stage}', result.stdout)

    def test_stage_one_without_local_toolchain_setup(self):
        self.run_stage(1)

    def test_stage_two_without_local_toolchain_setup(self):
        self.run_stage(2)

    def test_stage_three_without_local_toolchain_setup(self):
        self.run_stage(3)

if __name__ == '__main__':
    unittest.main()
