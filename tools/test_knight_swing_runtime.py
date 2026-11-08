"""Actual-character sword swing runtime contracts; no FX substitutes."""
from pathlib import Path
import json
import os
import subprocess
import unittest
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'core/src/main/java/io/jbnu/test'

class SwingRuntime(unittest.TestCase):
    def test_renderer_uses_new_character_frames_not_slash_quad(self):
        src=(SRC/'StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('art.swing(',src)
        self.assertIn('PixelAssets.swingX(',src)
        self.assertIn('PixelAssets.swingY(',src)
        self.assertNotIn('swordVertices',src)
        self.assertNotIn('swordAlpha',src)
        asset=(SRC/'PixelAssets.java').read_text(encoding='utf-8')
        self.assertIn('art/knight_swing/manifest.json',asset)

    def test_actual_manifest_dimensions_and_no_particle_frames(self):
        manifest=ROOT/'assets/art/knight_swing/manifest.json'
        self.assertTrue(manifest.exists(),'Actual knight attack frames are missing')
        m=json.loads(manifest.read_text(encoding='utf-8'))
        g=m['groups']['knight_swing']
        self.assertEqual(g['cell_size'],[96,96])
        self.assertEqual(g['frame_count'],8)
        self.assertEqual(g['clips']['melee']['frames'],list(range(8)))
        self.assertFalse(g['clips']['melee']['loop'])
        for f in g['frames']:
            self.assertTrue((ROOT/f['path']).is_file())

    def test_swing_timing_and_body_anchors(self):
        out=ROOT/'build/knight-swing-runtime-test';out.mkdir(parents=True,exist_ok=True)
        java=ROOT/'.tools/jdk-17.0.21+9/bin'
        jar=next((ROOT/'.tools/gradle-home').rglob('gdx-1*.jar'))
        harness=out/'SwingSemantics.java'
        harness.write_text('''import io.jbnu.test.PixelAssets;
public class SwingSemantics {
 static void eq(float a,float b){if(Math.abs(a-b)>.001)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args)throws Exception {
  java.lang.reflect.Method frame,x,y;
  try{frame=PixelAssets.class.getMethod("swingFrame",float.class);x=PixelAssets.class.getMethod("swingX",float.class,float.class,int.class);y=PixelAssets.class.getMethod("swingY",float.class,float.class,int.class);}
  catch(NoSuchMethodException e){throw new AssertionError("Actual character-swing selection and anchors missing",e);}
  float[] ages={0,.04f,.07f,.14f,.24f,.28f};int[] ids={0,1,2,4,6,7};
  for(int i=0;i<ages.length;i++)eq(((Number)frame.invoke(null,ages[i])).floatValue(),ids[i]);
  for(float age:new float[]{-1,Float.NaN})eq(((Number)frame.invoke(null,age)).floatValue(),0);
  eq(((Number)x.invoke(null,100f,30f,1)).floatValue()+42,115);
  eq(((Number)x.invoke(null,100f,30f,-1)).floatValue()+96-42,115);
  eq(((Number)y.invoke(null,40f,44f,-1)).floatValue()+25,40);
  eq(((Number)y.invoke(null,600f,44f,1)).floatValue()+71,644);
  if(!PixelAssets.playerPose(false,true,0,.1f,true,0,0,-1).equals("melee"))throw new AssertionError("Accepted strike is hidden behind guard pose");
  if(!PixelAssets.playerPose(false,true,0,10,true,0,0,-1).equals("guard"))throw new AssertionError("Held guard lost after strike");
  System.out.println("CHARACTER_SWING_RUNTIME_OK");
 }
}''',encoding='utf-8')
        subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-cp',str(jar),'-d',str(out),str(SRC/'PixelAssets.java'),str(harness)],check=True)
        p=subprocess.run([str(java/'java.exe'),'-cp',str(out)+os.pathsep+str(jar),'SwingSemantics'],capture_output=True,text=True)
        self.assertEqual(p.returncode,0,p.stdout+p.stderr)
        self.assertIn('CHARACTER_SWING_RUNTIME_OK',p.stdout)

if __name__=='__main__':unittest.main(verbosity=2)
