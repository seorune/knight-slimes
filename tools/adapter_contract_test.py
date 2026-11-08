"""Adapter contract checks complement the executable pure-model tests."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
class AdapterContract(unittest.TestCase):
    def test_fixed_step_adapter_and_smoke_capture(self):
        src = '\n'.join((ROOT / ('core/src/main/java/io/jbnu/test/' + name)).read_text(encoding='utf-8') for name in ['Main.java', 'StageRenderer.java'])
        self.assertNotIn('isK***', src)
        self.assertRegex(src, r'PixmapIO\.writePNG\([^;]+true\s*\)')
        for token in ['StageModel.DT', 'stage1.smoke', 'stage1.screenshot', 'PixmapIO.writePNG', 'FitViewport(1280,720', 'model.step']:
            self.assertIn(token, src)
    def test_wound_hud_replaces_hp(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/StageRenderer.java').read_text(encoding='utf-8')
        self.assertNotIn('model.hp', src)
        self.assertIn('model.woundTime', src)
        self.assertIn('model.dead', src)
        self.assertIn('WOUNDED', src)
        self.assertIn('STABLE', src)
    def test_parry_result_and_window_have_distinct_feedback(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('model.combatFeedbackTime', src)
        self.assertIn('model.combatFeedback', src)
        self.assertIn('StageModel.JUST_WINDOW', src)
        self.assertIn('just?"JUST":"GUARD"', src)
        self.assertIn('model.guarding', src)
        self.assertIn('GUARD', src)
        self.assertIn('text(model.combatFeedback,', src)
    def test_world_palette_is_split_at_center_not_camera(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/StageRenderer.java').read_text(encoding='utf-8')
        terrain = ROOT / 'assets/art/terrain_knight'
        self.assertTrue((terrain / 'terrain_warm/sheet.png').exists())
        self.assertTrue((terrain / 'terrain_cool/sheet.png').exists())
        self.assertIn('model.worldHeight', src)
        all_src='\n'.join(p.read_text(encoding='utf-8') for p in (ROOT/'core/src/main/java/io/jbnu/test').glob('*.java'))
        self.assertIn('art/terrain_knight', all_src)
        self.assertIn('art/pixel_knight', all_src)
    def test_smoke_scenes_exercise_actual_combat(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('stage1.smokeScene', src)
        self.assertIn('"just".equals(smokeScene)', src)
        self.assertIn('"parry".equals(smokeScene)', src)
        self.assertIn('"wounded".equals(smokeScene)', src)
        self.assertIn('"ranged"', src)
        self.assertIn('woundTime', src)
    def test_held_guard_and_readable_motion_scenes(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('pending.guard', src)
        self.assertIn('isKeyPressed(Input.Keys.K)', src)
        self.assertIn('model.guarding', src)
        for name in ('guard','attack','enemywindup','enemyattack'):
            self.assertIn('"'+name+'"', src)
        self.assertIn('deathReason', src)
    def test_second_stage_uses_dynamic_world_and_transition(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('model.worldWidth', src)
        self.assertIn('model.worldHeight', src)
        self.assertIn('model.hasNextStage()', src)
        self.assertIn('model.stageTitle', src)
        for scene in ('stage2start','stage2middle','stage2top','stage2wrap','transition'):
            self.assertIn('"'+scene+'"', src)
    def test_boss_stage_renders_model_driven_fight_and_fixtures(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('model.boss', src)
        self.assertIn('bossAttackBox()', src)
        for field in ('bossSeals=', 'bossPhase=', 'bossPrimed=', 'bossExposure=', 'bossDefeated='):
            self.assertIn(field, src)
        for scene in ('bossstart','bosswindup','bossattack','bossexposed','bossclear','bosswrapx','bosswrapy','transition3'):
            self.assertIn('"'+scene+'"', src)
    def test_boss_level_has_no_framework_dependencies(self):
        path = ROOT / 'core/src/main/java/io/jbnu/test/StageThreeLevel.java'
        self.assertTrue(path.exists(), 'Missing third-stage boss arena')
        self.assertNotIn('com.badlogic', path.read_text(encoding='utf-8'))
    def test_gradle_check_runs_boss_route_and_campaign(self):
        src = (ROOT / 'core/build.gradle').read_text(encoding='utf-8')
        self.assertIn('io.jbnu.test.StageThreeRouteTest', src)
        self.assertRegex(src, r"dependsOn[^\n]*verifyBossCampaign")
    def test_boss_uses_dedicated_state_art_manifest(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/PixelAssets.java').read_text(encoding='utf-8')
        self.assertIn('art/paradox_warden/manifest.json', src)
        self.assertTrue((ROOT / 'assets/art/paradox_warden/manifest.json').exists())
    def test_palette_axis_and_actual_boss_damage_scenes_are_observable(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/StageRenderer.java').read_text(encoding='utf-8')
        for field in ('paletteAxis=', 'bossArtState=', 'timeFrozen='):
            self.assertIn(field, src)
        for scene in ('bossarmed','bosssealedjust','bosssealbreak','bossdefeat'):
            self.assertIn('"'+scene+'"', src)
    def test_echo_delivery_puzzle_and_locked_doors_are_observable(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/StageRenderer.java').read_text(encoding='utf-8')
        self.assertIn('model.echoTarget', src)
        for field in ('targetRevealed=', 'targetSolved=', 'doorUnlocked=', 'targetVisible=', 'targetGravity='):
            self.assertIn(field, src)
        for scene in ('targetreveal1','targetreveal2','targetreveal3','targethidden2','doorlocked1','dooropen1','doorlocked2','dooropen2','bossdelivery'):
            self.assertIn('"'+scene+'"', src)
    def test_boss_mark_and_post_thaw_push_are_observable(self):
        src = (ROOT / 'core/src/main/java/io/jbnu/test/StageRenderer.java').read_text(encoding='utf-8')
        for field in ('bossTargetActive=', 'bossTargetGravity=', 'bossTargetSerial=', 'bossTargetX=', 'bossTargetY='):
            self.assertIn(field, src)
        for scene in ('bossmarked','bosstargetpush'):
            self.assertIn('"'+scene+'"', src)
    def test_gradle_runs_echo_rules_and_seeded_campaigns(self):
        src = (ROOT / 'core/build.gradle').read_text(encoding='utf-8')
        self.assertIn('io.jbnu.test.EchoTargetTest', src)
        self.assertIn('io.jbnu.test.EchoRouteTest', src)
        self.assertRegex(src, r"dependsOn[^\n]*verifyEchoTargets")
        self.assertRegex(src, r"dependsOn[^\n]*verifyEchoCampaign")
    def test_launcher_viewport_size(self):
        src = (ROOT / 'lwjgl3/src/main/java/io/jbnu/test/lwjgl3/Lwjgl3Launcher.java').read_text(encoding='utf-8')
        self.assertIn('setWindowedMode(1280, 720)', src)
    def test_model_has_no_framework_dependencies(self):
        for name in ('StageModel.java','StageOneLevel.java','StageTwoLevel.java'):
            path=ROOT / 'core/src/main/java/io/jbnu/test' / name
            self.assertTrue(path.exists(), 'Missing redesigned level data: '+name)
            self.assertNotIn('com.badlogic', path.read_text(encoding='utf-8'))
    def test_runtime_assets_audio_and_events_are_connected(self):
        files=(ROOT / 'core/src/main/java/io/jbnu/test').glob('*.java')
        src='\n'.join(p.read_text(encoding='utf-8') for p in files)
        self.assertIn('JsonReader', src)
        self.assertIn('model.events', src)
        self.assertIn('newSound', src)
        self.assertIn('TextureFilter.Nearest', src)
if __name__ == '__main__':
    unittest.main()
