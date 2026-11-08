"""Renderer asset adapter contract + executable Java semantic tests (no GL window)."""
import json
from pathlib import Path
import subprocess
import os
import unittest

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'core/src/main/java/io/jbnu/test'

class RuntimeAssets(unittest.TestCase):
    def test_echo_target_render_contract(self):
        text=(SRC/'StageRenderer.java').read_text(encoding='utf-8')
        for token in ('PixelAssets.targetVisible(', 'PixelAssets.doorClip(model.doorUnlocked)',
                      'echoTargetX=', 'echoTargetY=', 'targetRevealed=', 'targetSolved=',
                      'doorUnlocked=', 'targetVisible=', 'targetGravity=', 'drawEchoTarget(',
                      'targetreveal1','targetreveal2','targetreveal3','targethidden2',
                      'doorlocked1','dooropen1','doorlocked2','dooropen2','bossdelivery',
                      'System.nanoTime()', 'PixelAssets.hudObjective(', 'MARK'):
            self.assertIn(token,text)
        self.assertNotIn('freeze on living echo',text)
        self.assertIn('!wasSolved && model.targetSolved',text)
        self.assertIn('deliveryPulse',text)
        self.assertIn('drawDeliveryPulse()',text)
        self.assertIn('w=44,h=t.h',text)
        self.assertIn('PixelAssets.hudObjective(',text)
        self.assertIn('WRAP 1/2',text)
        self.assertIn('model.targetGravity>0?y-36:y+h+49',text)
        self.assertIn('upperEcho.clone=true',text)
        self.assertIn('text(label,',text)
        self.assertNotIn('RECHARGE ON CLONE',text)
        self.assertNotIn('// Real L input on an explicitly positioned living clone charges exposure.',text)

    def test_boss_target_renderer_contract(self):
        text=(SRC/'StageRenderer.java').read_text(encoding='utf-8')
        for token in ('drawBossTarget(', 'bossTargetActive=', 'bossTargetX=', 'bossTargetY=',
                      'bossTargetGravity=', 'bossTargetSerial=', 'bossmarked', 'bosstargetpush',
                      'model.echoTarget', 'model.targetGravity', 'model.targetVersion'):
            self.assertIn(token,text)
        self.assertNotIn('b.targetActive',text)
        self.assertIn('if(model.boss!=null || !targetVisible())return;',text)
        self.assertIn('if(!model.targetSolved && !targetVisible())return;',text)

    def test_boss_marker_matches_the_actual_receiver_bounds(self):
        text=(SRC/'StageRenderer.java').read_text(encoding='utf-8')
        body=text.split('private void drawBossTarget(){',1)[1].split('private void drawDeliveryPulse(){',1)[0]
        self.assertNotIn('t.w-44',body, 'Boss marker must not draw a different, narrower receiver')
        self.assertRegex(body,r'x\s*=\s*t\.x')
        self.assertRegex(body,r'w\s*=\s*t\.w')
        self.assertIn('line(x,y,x+w,y,thick,c)',body, 'The full receiver edge must remain visible, not only small corner brackets')
    def test_audio_catalog(self):
        audio = SRC / 'GameAudio.java'
        self.assertTrue(audio.exists(), 'Real WAV audio adapter not implemented')
        text = audio.read_text(encoding='utf-8')
        import re
        ids = re.search(r'IDS="([^"]+)"', text).group(1).split()
        actual = {p.stem for p in (ROOT/'assets/audio/pixel_knight').glob('*.wav')}
        self.assertEqual(set(ids), actual)
        self.assertEqual(len(ids), 30)

    def test_real_pixel_body_axes_and_ground_baselines(self):
        from build_pixel_assets import decode_png
        # Reviewed torso axes in the real sheet, excluding weapon-inclusive bbox centers.
        roots = {0:26, 2:26, 3:25, 6:29, 7:29, 8:26, 9:24, 10:31, 12:27}
        for frame, root in roots.items():
            w,h,pixels = decode_png((ROOT/f'assets/art/pixel_knight/knight/frame_{frame:02d}.png').read_bytes())
            opaque_y = [y for y in range(h) for x in range(w) if pixels[(y*w+x)*4+3]]
            self.assertEqual(max(opaque_y), 54, f'frame {frame} foot baseline')
            torso = sum(pixels[(y*w+root)*4+3] > 0 for y in range(30,44))
            self.assertGreaterEqual(torso, 10, f'frame {frame} anchor leaves actual torso')

    def test_actual_sword_character_animation_replaces_effect(self):
        text = (SRC/'StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('art.swing(attackAge)', text)
        self.assertIn('PixelAssets.swingX(', text)
        self.assertNotIn('swordVertices', text)
        self.assertNotIn('swordAlpha', text)
        self.assertNotIn('.flip(', text, 'Do not mutate cached TextureRegion UVs')

    def test_direct_stage2_development_shortcut(self):
        text = (SRC/'StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('if(!smoke && "2".equals(System.getProperty("game.startStage")))model.loadStage(1);', text)

    def test_stage2_render_integration(self):
        text = (SRC/'StageRenderer.java').read_text(encoding='utf-8')
        for call in ('PixelAssets.follow(', 'PixelAssets.isCoolRegion(', 'PixelAssets.stageProgress(',
                     'PixelAssets.backgroundSpan(', 'PixelAssets.visible(', 'model.stageTitle',
                     'model.hasNextStage()', 'model.loadStage(1)', 'oldStage!=model.stageIndex',
                     'stage2start', 'stage2middle', 'stage2top', 'stage2wrap', 'transition',
                     'VICTORY'):
            self.assertIn(call, text)
        self.assertNotIn('StageModel.HEIGHT/2f', text)
        self.assertNotIn('player.x/StageModel.WIDTH', text)
        self.assertIn('boolean guard=pending.guard', text)
        self.assertIn('pending.guard=guard', text)

    def test_stage3_fixtures_and_shortcut(self):
        text = (SRC/'StageRenderer.java').read_text(encoding='utf-8')
        for scene in ('bossstart', 'bosswindup', 'bossattack', 'bossexposed', 'bossclear',
                      'bosswrapx', 'bosswrapy', 'transition3'):
            self.assertIn('"'+scene+'"', text)
        self.assertIn('if(!smoke && "3".equals(System.getProperty("game.startStage")))model.loadStage(2);', text)
        self.assertIn('bossDefeated=', text)
        self.assertIn('ENTER / START', text)
        self.assertNotIn('BOSS NOT YET AVAILABLE', text)

    def test_stage2_x_palette_integration(self):
        text=(SRC/'StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('PixelAssets.isCoolRegion(model.stageIndex,x,y,',text)
        self.assertIn('paletteAxis=',text)
        self.assertIn('crop.setU(',text)
        self.assertIn('x<seam() && x+w>seam()',text)

    def test_boss_visual_contract(self):
        text = (SRC/'StageRenderer.java').read_text(encoding='utf-8')
        for token in ('model.bossAttackBox()', 'art.frame("paradox_warden"', 'PixelAssets.bossState(',
                      'bossSeals=', 'bossPhase=', 'bossPrimed=', 'bossExposure=',
                      'SEALS / ', 'PixelAssets.hudObjective(', 'bossArtState=', 'timeFrozen='):
            self.assertIn(token, text)
        self.assertNotIn('boss.hp', text)
        self.assertNotIn('boss.health', text)
        self.assertIn('if(model.boss==null)sprite(art.frame("exit_', text)
        self.assertLess(text.index('StageModel.Box beam=model.bossAttackBox();'), text.index('for(StageModel.Enemy e:model.enemies){', text.index('private void drawWorld()')))
        self.assertNotIn('art.boss(', text)
        self.assertIn('art/paradox_warden/manifest.json',(SRC/'PixelAssets.java').read_text(encoding='utf-8'))
        for scene in ('bossarmed','bosssealedjust','bosssealbreak','bossdefeat'):
            self.assertIn('"'+scene+'"',text)
        self.assertIn('case "boss_seal_break":',text)
        self.assertIn('PixelAssets.bossChargeProgress(b.timer)',text)

    def test_boss_feedback_and_victory_layout(self):
        text=(SRC/'StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('text(model.combatFeedback,',text)
        self.assertNotIn('COUNTER / WOUND CLEARED / INVULNERABLE',text)
        self.assertIn('boolean finalBoss=model.boss!=null;',text)
        self.assertIn('float overlayY=model.won && model.boss!=null?460:150',text)
        self.assertIn('worldPainting=false;',text)
        self.assertIn('while(model.stopTime>0)tick(new StageModel.Input())',text)

    def test_dedicated_boss_manifest_pixels(self):
        from build_pixel_assets import decode_png
        import hashlib
        manifest=ROOT/'assets/art/paradox_warden/manifest.json'
        self.assertTrue(manifest.exists(), 'Dedicated boss manifest not delivered')
        group=json.loads(manifest.read_text(encoding='utf-8'))['groups']['paradox_warden']
        w,h,pixels=decode_png((ROOT/group['sheet']).read_bytes())
        states=('sealed','exposed','charge','attack','break','defeated')
        frames={f['index']:f['rect'] for f in group['frames']}
        digests=[]
        selected={}
        for state in states:
            clip=group['clips'][state]
            self.assertEqual(len(clip['frames']),1, state)
            x,y,fw,fh=frames[clip['frames'][0]]
            self.assertEqual((fw,fh),(96,96))
            self.assertTrue(0<=x<x+fw<=w and 0<=y<y+fh<=h)
            raw=b''.join(bytes(pixels[((y+row)*w+x)*4:((y+row)*w+x+fw)*4]) for row in range(fh))
            self.assertGreater(sum(raw[a]>0 for a in range(3,len(raw),4)),500,state)
            selected[state]=raw
            digests.append(hashlib.sha256(raw).hexdigest())
        self.assertEqual(len(set(digests)),6,'State names must select genuinely different PNG poses')
        def bright_core(raw):
            return sum(raw[i+3]>0 and raw[i]>150 and raw[i+1]>210 and raw[i+2]>210
                       for yy in range(25,65) for xx in range(30,66) for i in [(yy*96+xx)*4])
        self.assertGreater(bright_core(selected['exposed']),bright_core(selected['sealed']), 'Open core must brighten in actual exposed pixels')

    def test_adapter_semantics(self):
        asset = SRC / 'PixelAssets.java'
        self.assertTrue(asset.exists(), 'PixelAssets runtime adapter not implemented')
        jars = list((ROOT / '.tools/gradle-home').rglob('gdx-1*.jar'))
        self.assertTrue(jars, 'GDX jar required; run Gradle compile first')
        out = ROOT / 'build/asset-runtime-test'
        out.mkdir(parents=True, exist_ok=True)
        harness = out / 'AssetSemantics.java'
        harness.write_text('''import io.jbnu.test.PixelAssets;
public class AssetSemantics {
 static void eq(float a,float b){if(Math.abs(a-b)>.001f)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  if(!PixelAssets.playerPose(false,true,0,10,true,0,0,-1).equals("guard"))throw new AssertionError("Held guard expires with tap timer");
  if(!PixelAssets.playerPose(false,false,.1f,10,true,0,0,-1).equals("guard"))throw new AssertionError("Tap guard pose missing");
  if(!PixelAssets.playerPose(false,false,0,.2f,true,0,0,-1).equals("melee"))throw new AssertionError("Visual recovery missing");
  if(!PixelAssets.playerPose(false,false,0,.3f,true,0,0,-1).equals("idle"))throw new AssertionError("Recovery never ends");
  for(float t:new float[]{0,.02f,.1f,.5f})eq(PixelAssets.protectionAlpha(t),1);
  for(float t:new float[]{0,.12f,.5f,5f}){
   eq(PixelAssets.knightFrame("idle",t),0);
   eq(PixelAssets.knightFrame("guard",t),12);
  }
  eq(PixelAssets.knightFrame("run",.05f),2);
  eq(PixelAssets.knightFrame("run",.22f),3);
  eq(PixelAssets.frameIndex(0.19f,.1f,3,false),1);
  eq(PixelAssets.frameIndex(4f,.1f,3,false),2);
  eq(PixelAssets.frameIndex(.31f,.1f,3,true),0);
  for(int f:new int[]{0,2,3,6,7,8,9,10,12,14,15}){
   float root=PixelAssets.knightRoot(f);
   eq(PixelAssets.knightX(100,34,f,1)+root,117);
   eq(PixelAssets.knightX(100,34,f,-1)+64-root,117);
   eq(PixelAssets.spriteY(40,44,-1)+9,40);
   eq(PixelAssets.spriteY(600,44,1)+55,644);
  }
  eq(PixelAssets.knightRoot(10)-PixelAssets.knightRoot(0),5);
  eq(PixelAssets.enemyFrame(1,false,false,0),4);
  eq(PixelAssets.enemyFrame(1,false,false,100),4);
  eq(PixelAssets.enemyFrame(2,false,false,0),7);
  eq(PixelAssets.enemyFrame(2,true,false,0),9);
  eq(PixelAssets.enemyFrame(0,false,false,100),0);
  eq(PixelAssets.spriteY(40,44,-1),31);
  eq(PixelAssets.spriteY(600,44,1),589);
  if(PixelAssets.boundaryHudY(1)<=82)throw new AssertionError("Boundary warning collides with objective/footer");
  eq(PixelAssets.boundaryHudY(-1),530);
  eq(PixelAssets.floatingHudShift(-1),0);
  eq(PixelAssets.floatingHudShift(1),-500);
  try{
   java.lang.reflect.Method hud=PixelAssets.class.getMethod("floatingHudShift",int.class,int.class,float.class);
   eq(((Number)hud.invoke(null,-1,1,360f)).floatValue(),0);
   eq(((Number)hud.invoke(null,-1,1,1270f)).floatValue(),-500);
   eq(((Number)hud.invoke(null,-1,1,2200f)).floatValue(),-500);
   eq(((Number)hud.invoke(null,-1,0,360f)).floatValue(),0);
  }catch(ReflectiveOperationException e){throw new AssertionError("Tower HUD placement missing",e);}
  if(670+PixelAssets.floatingHudShift(1)>=496)throw new AssertionError("Inverted HUD covers cold shelf actor");
  try{
   java.lang.reflect.Method boss=PixelAssets.class.getMethod("bossFrame",int.class,boolean.class);
   eq(((Number)boss.invoke(null,0,false)).floatValue(),0);
   eq(((Number)boss.invoke(null,1,false)).floatValue(),4);
   eq(((Number)boss.invoke(null,2,false)).floatValue(),7);
   eq(((Number)boss.invoke(null,3,false)).floatValue(),0);
   eq(((Number)boss.invoke(null,1,true)).floatValue(),11);
  }catch(ReflectiveOperationException e){throw new AssertionError("Stable boss poses missing",e);}
  if(!PixelAssets.bossFeedbackHint("SEALED").contains("NO SEAL DAMAGE"))throw new AssertionError("Sealed just looks like damage");
  if(!PixelAssets.bossFeedbackHint("BLOCKED").contains("BLOCK ONLY"))throw new AssertionError("Held block looks like seal damage");
  if(!PixelAssets.bossFeedbackHint("SEAL BROKEN").contains("ONE SEAL REMOVED"))throw new AssertionError("Successful break indistinguishable");
  String[] states={"sealed","charge","attack","exposed","break","defeated"};
  String[] actual={PixelAssets.bossState(0,false,false,0),PixelAssets.bossState(1,false,false,0),PixelAssets.bossState(2,false,false,0),PixelAssets.bossState(2,true,false,0),PixelAssets.bossState(2,true,false,.1f),PixelAssets.bossState(2,true,true,.1f)};
  for(int n=0;n<states.length;n++)if(!states[n].equals(actual[n]))throw new AssertionError("Boss state priority "+actual[n]);
  if(!PixelAssets.bossHint(true,true,1,.01f).equals("ARMED / WAIT FOR THAW"))throw new AssertionError("Frozen false parry prompt");
  if(!PixelAssets.bossHint(true,false,1,.05f).equals("EXPOSED / TAP K NOW"))throw new AssertionError("Late charge timing prompt");
  if(!PixelAssets.bossHint(false,false,0,0).equals("L / REVEAL MARK, THEN THAW / GUIDE ECHO"))throw new AssertionError("Old standing-on-clone instruction remains");
  try{
   java.lang.reflect.Method stageVisible=PixelAssets.class.getMethod("targetVisible",int.class,float.class,boolean.class,boolean.class,boolean.class,boolean.class);
   if(!((Boolean)stageVisible.invoke(null,2,0f,true,false,true,false)))throw new AssertionError("Boss push target vanishes after thaw");
   if(((Boolean)stageVisible.invoke(null,1,0f,true,false,true,false)))throw new AssertionError("Tower memory marker must still hide after thaw");
   if(((Boolean)stageVisible.invoke(null,2,0f,true,true,true,false)))throw new AssertionError("Solved target must use separate matched rendering");
  }catch(ReflectiveOperationException e){throw new AssertionError("Stage-specific target visibility missing",e);}
  try{
   java.lang.reflect.Method visible=PixelAssets.class.getMethod("targetVisible",float.class,boolean.class,boolean.class,boolean.class,boolean.class);
   for(boolean paused:new boolean[]{false,true}){
    if(!((Boolean)visible.invoke(null,1f,true,false,true,paused)))throw new AssertionError("Frozen revealed target hidden");
    if((Boolean)visible.invoke(null,0f,true,false,true,paused))throw new AssertionError("Target leaks after thaw");
    if((Boolean)visible.invoke(null,1f,false,false,true,paused))throw new AssertionError("Unrevealed target leaks");
    if((Boolean)visible.invoke(null,1f,true,true,true,paused))throw new AssertionError("Solved target remains");
    if((Boolean)visible.invoke(null,1f,true,false,false,paused))throw new AssertionError("Null target visible");
   }
   java.lang.reflect.Method hudX=PixelAssets.class.getMethod("objectiveHudX",boolean.class,boolean.class,int.class);
   eq(((Number)hudX.invoke(null,true,true,1)).floatValue(),244);
   eq(((Number)hudX.invoke(null,true,false,1)).floatValue(),16);
   eq(((Number)hudX.invoke(null,true,true,-1)).floatValue(),16);
   eq(((Number)hudX.invoke(null,false,true,1)).floatValue(),16);
   java.lang.reflect.Method door=PixelAssets.class.getMethod("doorClip",boolean.class);
   if(!door.invoke(null,false).equals("closed") || !door.invoke(null,true).equals("open"))throw new AssertionError("Locked door shown open");
  }catch(ReflectiveOperationException e){throw new AssertionError("Target visibility / door state helpers missing",e);}
  try{
   java.lang.reflect.Method label=PixelAssets.class.getMethod("targetLabelX",float.class,float.class);
   eq(((Number)label.invoke(null,1214f,1280f)).floatValue(),1118);
   eq(((Number)label.invoke(null,16f,1280f)).floatValue(),8);
  }catch(ReflectiveOperationException e){throw new AssertionError("Target label safe margins missing",e);}
  try{
   java.lang.reflect.Method hint=PixelAssets.class.getMethod("bossHint",boolean.class,boolean.class,int.class,float.class,boolean.class,boolean.class);
   if(!hint.invoke(null,false,true,0,0f,true,true).equals("TARGET MARKED / WAIT FOR THAW"))throw new AssertionError("Frozen pending prompt");
   if(!hint.invoke(null,false,false,0,0f,true,true).equals("PUSH CLONE TO MARK / J"))throw new AssertionError("Thawed pending prompt");
   if(!hint.invoke(null,true,false,0,0f,false,true).equals("EXPOSED / TAP K ON HIT"))throw new AssertionError("Matched prompt");
   if(!hint.invoke(null,false,false,0,0f,false,false).equals("SEALED / L TO MARK CLONE TARGET"))throw new AssertionError("Unmarked prompt");
   if(hint.invoke(null,true,true,0,0f,false,true).toString().contains("TAP K"))throw new AssertionError("Frozen parry hint");
  }catch(ReflectiveOperationException e){throw new AssertionError("Boss target-aware hints missing",e);}
  eq(PixelAssets.bossChargeProgress(.8f),0);eq(PixelAssets.bossChargeProgress(.4f),.5f);eq(PixelAssets.bossChargeProgress(0),1);
  PixelAssets.Clocks c=new PixelAssets.Clocks();
  c.advance(.5f,true,false);eq(c.player,.5f);eq(c.world,.5f);
  c.advance(.5f,true,true);eq(c.player,1);eq(c.world,.5f);
  c.advance(.5f,false,false);eq(c.player,1);eq(c.world,.5f);
  // Stage 2 is a full-height X palette, independent of camera/player Y.
  for(float y:new float[]{0,360,1300,2560}){
   if(PixelAssets.isCoolRegion(1,639,y,1280,2560))throw new AssertionError("Left tower must stay warm");
   if(!PixelAssets.isCoolRegion(1,640,y,1280,2560))throw new AssertionError("Right tower must stay cool");
  }
  if(PixelAssets.isCoolRegion(0,4000,359,4200,720))throw new AssertionError("Stage1 palette changed");
  if(!PixelAssets.isCoolRegion(2,0,360,1280,720))throw new AssertionError("Arena upper palette changed");
  eq(PixelAssets.backgroundSpan(0,1280,1280,false)[1],640);
  eq(PixelAssets.backgroundSpan(0,1280,1280,true)[0],640);
  eq(PixelAssets.backgroundSpan(500,1280,400,false)[1],140);
  eq(PixelAssets.backgroundSpan(500,1280,400,true)[1],260);
  // Dynamic camera clamp: small worlds are centered, never clamped negative.
  eq(PixelAssets.follow(20,4200,1280),640);
  eq(PixelAssets.follow(4100,4200,1280),3560);
  eq(PixelAssets.follow(991.6f,4200,1280),992);
  eq(PixelAssets.follow(90,800,1280),400);
  eq(PixelAssets.follow(2000,1280,1280),640);
  eq(PixelAssets.follow(20,2600,720),360);
  eq(PixelAssets.follow(2400,2600,720),2240);
  if(PixelAssets.coolRegion(1299,2600))throw new AssertionError("Warm half moved");
  if(!PixelAssets.coolRegion(1300,2600))throw new AssertionError("Cold half missing");
  eq(PixelAssets.stageProgress(0,2100,900,4200,720),.5f);
  eq(PixelAssets.stageProgress(1,900,1300,1280,2600),.5f);
  eq(PixelAssets.stageProgress(1,0,-50,1280,2600),0);
  eq(PixelAssets.stageProgress(1,0,2700,1280,2600),1);
  eq(PixelAssets.backgroundSpan(0,2600,false)[0],0);
  eq(PixelAssets.backgroundSpan(0,2600,false)[1],720);
  eq(PixelAssets.backgroundSpan(1000,2600,false)[1],300);
  eq(PixelAssets.backgroundSpan(1000,2600,true)[0],1300);
  eq(PixelAssets.backgroundSpan(1000,2600,true)[1],420);
  eq(PixelAssets.backgroundSpan(1880,2600,true)[1],720);
  eq(PixelAssets.backgroundSpan(1880,2600,false)[1],0);
  if(PixelAssets.visible(0,0,32,32,0,1880))throw new AssertionError("Tower overdraw");
  if(!PixelAssets.visible(10,2000,32,32,0,1880))throw new AssertionError("Visible shelf culled");
  if(PixelAssets.visible(1400,2000,32,32,0,1880))throw new AssertionError("Horizontal overdraw");
  // Actual arm/blade frame timing and 96px anchors are tested in test_knight_swing_runtime.py.
  System.out.println("ASSET_SEMANTICS_OK");
 }
}''', encoding='utf-8')
        java = ROOT / '.tools/jdk-17.0.21+9/bin'
        cp = str(jars[0])
        subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(out),str(asset),str(harness)],check=True)
        result=subprocess.run([str(java/'java.exe'),'-cp',str(out)+os.pathsep+cp,'AssetSemantics'],capture_output=True,text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('ASSET_SEMANTICS_OK',result.stdout)

if __name__ == '__main__': unittest.main()
