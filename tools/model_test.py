"""Compile/run the pure Java suite using only a project-local portable JDK."""
from pathlib import Path
import os
import subprocess
import sys
root = Path(__file__).resolve().parents[1]
java_home = os.environ.get('JAVA_HOME')
candidates = ([Path(java_home) / 'bin' / 'javac.exe'] if java_home else [])
candidates += list((root / '.tools').glob('**/javac.exe'))
compiler = next((p for p in candidates if p.exists()), None)
if compiler is None:
    sys.exit('BLOCKED: portable JDK not yet ready; set JAVA_HOME or put JDK in .tools')
out = root / 'core' / 'build' / 'model-test'
out.mkdir(parents=True, exist_ok=True)
files = [root / ('core/src/main/java/io/jbnu/test/' + name + '.java') for name in ['StageModel', 'StageOneLevel', 'StageTwoLevel', 'StageThreeLevel']]
files += sorted((root / 'core/src/test/java/io/jbnu/test').glob('*.java'))
cmd = [str(compiler), '-encoding', 'UTF-8', '--release', '8', '-d', str(out)] + list(map(str, files))
r = subprocess.run(cmd)
if r.returncode:
    sys.exit(r.returncode)
for test in (sys.argv[1:] or ['io.jbnu.test.EchoTargetTest', 'io.jbnu.test.StageModelTest', 'io.jbnu.test.StageThreeRouteTest', 'io.jbnu.test.EchoRouteTest']):
    result = subprocess.run([str(compiler.with_name('java.exe')), '-ea', '-cp', str(out), test])
    if result.returncode:
        sys.exit(result.returncode)
