"""Compact HUD contracts and executable label checks."""
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'core/src/main/java/io/jbnu/test'

class CompactUI(unittest.TestCase):
    def test_play_screen_has_no_tutorial_paragraphs(self):
        text = (SRC / 'StageRenderer.java').read_text(encoding='utf-8')
        for phrase in ('3 PLAYABLE STAGES', 'COUNTER / WOUND CLEARED / INVULNERABLE',
                       'AVOID ANOTHER HIT', 'LAND BEFORE CROSSING AGAIN',
                       'All physics, attacks and cooldowns are paused.',
                       'L: VIEW SAME MARK / THAW:', 'SEAL BROKEN / L FOR NEXT MARK'):
            self.assertNotIn(phrase, text)
        self.assertIn('PixelAssets.hudObjective(', text)
        self.assertIn('model.woundTime', text)
        self.assertIn('model.boundaryChain', text)
        self.assertIn('drawBossTarget()', text)
        self.assertIn('bossChargeProgress(', text)

    def test_labels_are_short_and_keep_the_puzzle_states(self):
        compiler = next((ROOT / '.tools').glob('**/javac.exe'))
        jars = list((ROOT / '.tools/gradle-home').rglob('gdx-1*.jar'))
        self.assertTrue(jars)
        out = ROOT / 'build/ui-test'
        out.mkdir(parents=True, exist_ok=True)
        harness = out / 'CompactLabels.java'
        harness.write_text('''import io.jbnu.test.PixelAssets;
public class CompactLabels {
 public static void main(String[] args) throws Exception {
  java.lang.reflect.Method f=PixelAssets.class.getMethod("hudObjective",int.class,boolean.class,boolean.class,boolean.class,boolean.class);
  for(int stage=0;stage<3;stage++)for(boolean pending:new boolean[]{false,true})for(boolean frozen:new boolean[]{false,true})for(boolean primed:new boolean[]{false,true})for(boolean open:new boolean[]{false,true}){
   String text=(String)f.invoke(null,stage,pending,frozen,primed,open);
   if(text.isEmpty() || text.length()>16)throw new AssertionError("HUD label is not compact: "+text);
  }
  if(!f.invoke(null,2,true,true,false,false).equals("WAIT"))throw new AssertionError("Frozen mark must not invite pushing");
  if(!f.invoke(null,2,true,false,false,false).equals("J / PUSH"))throw new AssertionError("Thawed mark needs a push");
  if(!f.invoke(null,2,true,false,true,false).equals("K / PARRY"))throw new AssertionError("Exposed boss needs a parry");
  if(!f.invoke(null,1,false,false,false,true).equals("EXIT"))throw new AssertionError("Open door needs exit guidance");
 }
}''', encoding='utf-8')
        cp = os.pathsep.join(map(str,jars))
        compile_result = subprocess.run([str(compiler),'-encoding','UTF-8','--release','8','-cp',cp,'-d',str(out),str(SRC/'PixelAssets.java'),str(harness)],capture_output=True,text=True)
        self.assertEqual(compile_result.returncode,0,compile_result.stderr)
        result = subprocess.run([str(compiler.with_name('java.exe')),'-ea','-cp',str(out)+os.pathsep+cp,'CompactLabels'],capture_output=True,text=True)
        self.assertEqual(result.returncode,0,result.stderr)

if __name__ == '__main__':
    unittest.main()
