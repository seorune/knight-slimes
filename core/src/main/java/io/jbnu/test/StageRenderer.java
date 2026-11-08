package io.jbnu.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

/** Input/graphics adapter. Simulation rules live exclusively in StageModel. */
public class StageRenderer extends ApplicationAdapter {
    private static final Color INK=Color.valueOf("090F1D"), PANEL=Color.valueOf("111E30"), STEEL=Color.valueOf("243449");
    private static final Color CYAN=Color.valueOf("58F5DD"), AMBER=Color.valueOf("FFBD69"), VIOLET=Color.valueOf("B895FF"), RED=Color.valueOf("FF647C");
    private float seam(){return (model.stageIndex==1?model.worldWidth:model.worldHeight)/2f;}
    private static final Color WARM_INK=Color.valueOf("F1D4B1"), COOL_INK=Color.valueOf("071F2A");
    private static final Color LOWER_INK=WARM_INK, UPPER_INK=COOL_INK;
    private static final Color WARM_PANEL=Color.valueOf("DFC09E"), WARM_STEEL=Color.valueOf("AC7656");
    private static final Color WARM_CYAN=Color.valueOf("00646D"), WARM_AMBER=Color.valueOf("9C391D"), WARM_VIOLET=Color.valueOf("603888"), WARM_RED=Color.valueOf("AE2045");
    private boolean worldPainting;
    private StageModel model;
    private PixelAssets art;
    private GameAudio audio;
    private static final class Effect {
        String group,clip;float x,y,age;boolean player;
        Effect(String group,String clip,float x,float y,boolean player){this.group=group;this.clip=clip;this.x=x;this.y=y;this.player=player;}
    }
    private final java.util.List<Effect> effects=new java.util.ArrayList<>();
    private final java.util.Map<StageModel.Enemy,Float> enemyClocks=new java.util.IdentityHashMap<>();
    private float deathAge,attackAge=10,poseAge,bossBreakTime,deliveryPulse,deliveryX,deliveryY;
    private String lastPose="idle";
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private OrthographicCamera world,hud;
    private FitViewport viewport;
    private StageModel.Input pending=new StageModel.Input();
    private float accumulator;
    private boolean smoke;
    private int smokeFrame;
    private String smokeScene="route";

    @Override public void create(){
        model=new StageModel(Boolean.getBoolean("stage1.smoke")?0L:System.nanoTime());shapes=new ShapeRenderer();batch=new SpriteBatch();font=new BitmapFont();
        world=new OrthographicCamera();world.setToOrtho(false,1280,720);
        hud=new OrthographicCamera();hud.setToOrtho(false,1280,720);
        viewport=new FitViewport(1280,720,world);viewport.update(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),false);
        smoke=Boolean.getBoolean("stage1.smoke");
        // Optional initial stage.
        if(!smoke && "2".equals(System.getProperty("game.startStage")))model.loadStage(1);
        if(!smoke && "3".equals(System.getProperty("game.startStage")))model.loadStage(2);
        art=new PixelAssets();audio=new GameAudio(smoke || Boolean.getBoolean("stage1.mute"));
        if(smoke)System.out.println("ASSET_RUNTIME_OK textures="+art.textureCount()+" "+audio.stats());
        if(smoke){
            smokeScene=System.getProperty("stage1.smokeScene","route");
            if(!"title".equals(smokeScene)){StageModel.Input i=new StageModel.Input();i.start=true;tick(i);}
            if("stage2start".equals(smokeScene)||"stage2middle".equals(smokeScene)||"stage2top".equals(smokeScene)||"stage2wrap".equals(smokeScene)){
                // Screenshot fixtures.
                model.loadStage(1);
                if("stage2middle".equals(smokeScene)||"stage2top".equals(smokeScene)||"stage2wrap".equals(smokeScene)){
                    model.player.x=90;
                    model.player.y="stage2top".equals(smokeScene)?model.worldHeight-250:model.worldHeight/2;
                    model.player.vx=0;model.player.vy=0;model.player.grounded=false;
                }
                if("stage2wrap".equals(smokeScene)){
                    model.player.x=-model.player.w-1;
                    tick(new StageModel.Input()); // Real portal resolution, no forced landing.
                }
            }else if("transition".equals(smokeScene)){
                model.loadStage(0);model.won=true; // Explicit clear-state UI fixture.
                StageModel.Input next=new StageModel.Input();next.start=true;tick(next);
            }
            if(java.util.Arrays.asList("bossstart","bosswindup","bossattack","bossexposed","bossarmed","bossmarked","bossmarkthawed","bosstargetpush","bossarrival","bossclear","bosswrapx","bosswrapy","bosssealedjust","bosssealbreak","bossdefeat").contains(smokeScene)){
                // Boss screenshot fixtures.
                model.loadStage(2);
                StageModel.Boss b=model.boss;
                if("bosswindup".equals(smokeScene)||"bossattack".equals(smokeScene)){
                    b.phase="bosswindup".equals(smokeScene)?1:2;b.timer=b.phase==1?.8f:.18f;
                    b.direction=-1;b.beamY=model.player.y+model.player.h/2;
                }
                if("bossarmed".equals(smokeScene)||"bossmarked".equals(smokeScene)||"bossmarkthawed".equals(smokeScene)){
                                    // Pending target fixture.
                                    StageModel.Input freeze=new StageModel.Input();freeze.stop=true;tick(freeze);
                                    if(model.echoTarget==null || !(model.targetRevealed && !model.targetSolved) || b.primed)throw new IllegalStateException("Pending boss mark missing");
                                    if("bossmarkthawed".equals(smokeScene))while(model.stopTime>0)tick(new StageModel.Input());
                }
                if("bossexposed".equals(smokeScene)||"bosstargetpush".equals(smokeScene)||"bossarrival".equals(smokeScene)||"bosssealbreak".equals(smokeScene)||"bossdefeat".equals(smokeScene)){
                    earnBossTargetFixture();
                    model.player.x=120;model.player.y=40;model.player.vx=model.player.vy=0;model.player.gravity=-1;model.player.grounded=true;
                }
                if("bosssealedjust".equals(smokeScene)||"bosssealbreak".equals(smokeScene)||"bossdefeat".equals(smokeScene)){
                    // Position-only combat fixture; K is resolved by the ordinary model tick.
                    model.enemies.clear();model.player.x=120;model.player.y=40;model.player.vx=model.player.vy=0;model.player.gravity=-1;model.player.grounded=true;
                    // Delivery used held guard. Release it and finish actual cooldown before a fresh K edge.
                    tick(new StageModel.Input());
                    for(int n=0;model.parryCooldown>0 && n<120;n++)tick(new StageModel.Input());
                    b.phase=2;b.timer=.18f;b.direction=-1;b.beamY=model.player.y+model.player.h/2;
                    if("bossdefeat".equals(smokeScene))b.seals=1;
                    StageModel.Input guard=new StageModel.Input();guard.parry=true;guard.guard=true;tick(guard);
                }
                if("bossclear".equals(smokeScene)){
                    // Clear screen fixture.
                    b.seals=0;b.defeated=true;model.won=true;
                }
                if("bosswrapx".equals(smokeScene)){
                    model.player.x=-model.player.w-1;model.player.y=180;model.player.vy=0;model.player.grounded=false;
                    tick(new StageModel.Input());
                }
                if("bosswrapy".equals(smokeScene)){
                    model.player.x=950;model.player.y=-model.player.h-1;model.player.vy=-600;model.player.grounded=false;
                    tick(new StageModel.Input());
                }
            }else if("transition3".equals(smokeScene)){
                model.loadStage(1);model.won=true; // Forced preceding clear, actual Enter transition.
                StageModel.Input next=new StageModel.Input();next.start=true;tick(next);
            }
            if(java.util.Arrays.asList("targetreveal1","targetreveal2","targetreveal3","targethidden2","doorlocked1","dooropen1","doorlocked2","dooropen2","bossdelivery").contains(smokeScene)){
                int stage="bossdelivery".equals(smokeScene)?2:smokeScene.charAt(smokeScene.length()-1)-'1';
                model.loadStage(stage);
                if(smokeScene.startsWith("dooropen") || "bossdelivery".equals(smokeScene))earnDeliveryFixture();
                else if(smokeScene.startsWith("target")){
                    if("targetreveal3".equals(smokeScene)){
                        // Explicit supported upper-clone position selects a valid inverted destination.
                        StageModel.Enemy upperEcho=new StageModel.Enemy(150,636,1);
                        upperEcho.clone=true;upperEcho.original=model.enemies.isEmpty()?null:model.enemies.get(0);upperEcho.grounded=true;model.enemies.add(upperEcho);
                    }
                    StageModel.Input freeze=new StageModel.Input();freeze.stop=true;tick(freeze);
                    if("targethidden2".equals(smokeScene))while(model.stopTime>0)tick(new StageModel.Input());
                }
                if("bossdelivery".equals(smokeScene)){
                    model.player.x=1080;model.player.y=40;model.player.vx=model.player.vy=0;model.player.gravity=-1;model.player.grounded=true;
                }
                if(smokeScene.startsWith("door")){
                    // Door appearance fixture.
                    model.player.x=model.exit.x-100;model.player.y=model.exit.y;model.player.vx=model.player.vy=0;
                }
            }
            if("just".equals(smokeScene) || "parry".equals(smokeScene) || "wounded".equals(smokeScene) || "ranged".equals(smokeScene)){
                // Combat feedback fixture.
                model.enemies.clear();model.platforms.clear();model.platforms.add(new StageModel.Box(0,0,StageModel.WIDTH,40));model.player.x=600;model.player.y=40;
                if("just".equals(smokeScene) || "ranged".equals(smokeScene))model.woundTime=2f;
                StageModel.Enemy e=new StageModel.Enemy("ranged".equals(smokeScene)?1000:674,40);e.direction=-1;e.ranged="ranged".equals(smokeScene);
                e.phase="parry".equals(smokeScene)?1:2;e.timer=.13f;model.enemies.add(e);
            }else if("attack".equals(smokeScene)||"guard".equals(smokeScene)||"enemywindup".equals(smokeScene)||"enemyattack".equals(smokeScene)){
                model.enemies.clear();model.platforms.clear();model.platforms.add(new StageModel.Box(0,0,StageModel.WIDTH,40));
                model.player.x=600;model.player.y=40;
                if(smokeScene.startsWith("enemy")){
                    StageModel.Enemy e=new StageModel.Enemy(720,40);e.direction=-1;
                    e.phase="enemywindup".equals(smokeScene)?1:2;e.timer=e.phase==1?.5f:.18f;model.enemies.add(e);
                }
            }else if("inverted".equals(smokeScene)){
                model.player.x=1100;model.player.y=-45;model.player.vy=-700;
            }
        }
    }
    /** Position-only delivery setup; reveal, natural thaw and J displacement use real ticks. */
    private void earnDeliveryFixture(){
        if(model.boss!=null){earnBossTargetFixture();return;}
        StageModel.Input freeze=new StageModel.Input();freeze.stop=true;tick(freeze);
        if(model.echoTarget==null || !model.targetRevealed)throw new IllegalStateException("Fixture could not reveal valid target");
        while(model.stopTime>0)tick(new StageModel.Input());
        StageModel.Box t=model.echoTarget;float center=t.x+t.w/2;
        float middle=model.stageIndex==0?1685:model.stageIndex==1?1140:model.targetGravity<0?1170:110;
        int direction=center<middle?-1:1;
        StageModel.Enemy original=model.enemies.isEmpty()?null:model.enemies.get(0);
        model.enemies.clear();
        StageModel.Enemy clone=new StageModel.Enemy(center-22-direction*110,t.y,model.targetGravity);
        clone.clone=true;clone.original=original;clone.grounded=true;clone.timer=2;model.enemies.add(clone);
        model.player.x=clone.x-direction*62;model.player.y=t.y;model.player.gravity=model.targetGravity;
        model.player.vx=model.player.vy=0;model.player.grounded=true;model.facing=direction;
        StageModel.Input push=new StageModel.Input();push.melee=true;push.guard=true;tick(push);
        for(int n=0;n<240 && !model.targetSolved && !model.dead;n++){
            StageModel.Input guide=new StageModel.Input();guide.guard=true;tick(guide);
        }
        if(!model.targetSolved)throw new IllegalStateException("Real J echo delivery failed: stage="+model.stageIndex+" target="+t.x+","+t.y+" clone="+clone.x+","+clone.y+" gravity="+clone.gravity);
        System.out.println("ECHO_DELIVERY_FIXTURE_OK actualJ=true targetSolved="+model.targetSolved);
    }
    /** Scenario-only positioning; actual L, natural expiry and post-mark J earn exposure. */
    private void earnBossTargetFixture(){
        StageModel.Input freeze=new StageModel.Input();freeze.stop=true;tick(freeze);
        StageModel.Boss b=model.boss;
        if(model.echoTarget==null || !(model.targetRevealed && !model.targetSolved))throw new IllegalStateException("Boss fixture could not mark target");
        while(model.stopTime>0)tick(new StageModel.Input());
        StageModel.Box t=model.echoTarget;float center=t.x+t.w/2;
        int direction=center<model.worldWidth/2?-1:1;
        StageModel.Enemy original=model.enemies.isEmpty()?null:model.enemies.get(0);
        model.enemies.clear();
        boolean inside="bossarrival".equals(smokeScene);
        float cloneX=inside?center-22:center-22-direction*90;
        float cloneY=inside && model.targetGravity>0?t.y+t.h-44:t.y;
        StageModel.Enemy clone=new StageModel.Enemy(cloneX,cloneY,model.targetGravity);
        if(inside && !clone.overlaps(t))throw new IllegalStateException("Inside-arrival fixture missed its target");
        clone.clone=true;clone.original=original;clone.grounded=true;clone.timer=2;model.enemies.add(clone);
        model.player.x=clone.x-direction*62;model.player.y=t.y;model.player.gravity=model.targetGravity;
        model.player.vx=model.player.vy=0;model.player.grounded=true;model.facing=direction;
        StageModel.Input push=new StageModel.Input();push.melee=true;push.guard=true;tick(push);
        for(int n=0;n<240 && !b.primed && !model.dead;n++){
            StageModel.Input hold=new StageModel.Input();hold.guard=true;tick(hold);
        }
        if(!b.primed || (model.targetRevealed && !model.targetSolved))throw new IllegalStateException("Post-thaw real J failed boss target: target="+t.x+","+t.y+" clone="+clone.x+","+clone.y);
        System.out.println("BOSS_TARGET_FIXTURE_OK actualJ=true serial="+model.targetVersion+" primed="+b.primed+" inside="+inside);
    }
    private boolean pressed(int key){return Gdx.input.isKeyJustPressed(key);}
    private void input(){
        if(pressed(Input.Keys.M))audio.toggle();
        pending.move=(Gdx.input.isKeyPressed(Input.Keys.RIGHT)||Gdx.input.isKeyPressed(Input.Keys.D)?1:0)-(Gdx.input.isKeyPressed(Input.Keys.LEFT)||Gdx.input.isKeyPressed(Input.Keys.A)?1:0);
        pending.jump|=pressed(Input.Keys.SPACE)||pressed(Input.Keys.W)||pressed(Input.Keys.UP);
        pending.melee|=pressed(Input.Keys.J);pending.parry|=pressed(Input.Keys.K);pending.guard=Gdx.input.isKeyPressed(Input.Keys.K);pending.stop|=pressed(Input.Keys.L);
        pending.pause|=pressed(Input.Keys.ESCAPE);pending.restart|=pressed(Input.Keys.R);pending.start|=pressed(Input.Keys.ENTER);pending.campaignRestart|=pressed(Input.Keys.N);
    }
    @Override public void render(){
        if(smoke){
            for(int n=0;n<("route".equals(smokeScene)?16:1);n++){
                StageModel.Input i=new StageModel.Input();i.move="route".equals(smokeScene)?1:0;
                i.parry="route".equals(smokeScene)?smokeFrame%5==0 && n==0:
                    smokeFrame==0 && ("just".equals(smokeScene) || "parry".equals(smokeScene) || "ranged".equals(smokeScene));
                i.melee="attack".equals(smokeScene) && smokeFrame==0;
                i.guard="guard".equals(smokeScene);
                if(i.guard && smokeFrame==0)i.parry=true;
                tick(i);
            }
            smokeFrame++;
        }else{
            input();accumulator+=Math.min(.1f,Gdx.graphics.getDeltaTime());
            while(accumulator>=StageModel.DT){
                tick(pending);int move=pending.move;boolean guard=pending.guard;pending=new StageModel.Input();pending.move=move;pending.guard=guard;accumulator-=StageModel.DT;
            }
        }
        world.position.set(PixelAssets.follow(model.stageIndex==0?model.player.x+150:model.player.x+model.player.w/2,model.worldWidth,1280),
            PixelAssets.follow(model.stageIndex==0?360:model.player.y+model.player.h/2,model.worldHeight,720),0);world.update();
        ScreenUtils.clear(INK);viewport.apply();drawWorld();drawHud();
        if(smoke && smokeFrame>=Integer.getInteger("stage1.smokeFrames","guard".equals(smokeScene)?90:("attack".equals(smokeScene)||"bossattack".equals(smokeScene)||"enemyattack".equals(smokeScene)||"route".equals(smokeScene))?12:24)){
            FileHandle target=Gdx.files.absolute(System.getProperty("stage1.screenshot","build/stage1-smoke.png"));
            target.parent().mkdirs();Pixmap image=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
            PixmapIO.writePNG(target,image,-1,true);image.dispose();System.out.println("STAGE1_SMOKE_OK screenshot="+target.path()+" dead="+model.dead+" wound="+model.woundTime+" feedback="+model.combatFeedback+" x="+model.player.x+" guarding="+model.guarding+" parryTime="+model.parryTime+" boundaryChain="+model.boundaryChain+" deathReason="+model.deathReason+" stage="+(model.stageIndex+1)+" worldWidth="+model.worldWidth+" worldHeight="+model.worldHeight+" cameraX="+world.position.x+" cameraY="+world.position.y+" wrapX="+model.wrapX+" wrapY="+model.wrapY+" y="+model.player.y+" gravity="+model.player.gravity+" paletteAxis="+(model.stageIndex==1?"X":"Y")+" bossSeals="+(model.boss==null?-1:model.boss.seals)+" bossPhase="+(model.boss==null?-1:model.boss.phase)+" bossPrimed="+(model.boss!=null && model.boss.primed)+" bossExposure="+(model.boss==null?0:model.boss.exposure)+" bossDefeated="+(model.boss!=null && model.boss.defeated)+" timeFrozen="+(model.stopTime>0)+" bossArtState="+bossArtState()+" bossTargetActive="+(model.boss!=null && (model.targetRevealed && !model.targetSolved))+" bossTargetX="+(model.boss==null || model.echoTarget==null?-1:model.echoTarget.x)+" bossTargetY="+(model.boss==null || model.echoTarget==null?-1:model.echoTarget.y)+" bossTargetGravity="+(model.boss==null?-1:model.targetGravity)+" bossTargetSerial="+(model.boss==null?0:model.targetVersion)+" echoTargetX="+(model.echoTarget==null?-1:model.echoTarget.x)+" echoTargetY="+(model.echoTarget==null?-1:model.echoTarget.y)+" targetRevealed="+model.targetRevealed+" targetSolved="+model.targetSolved+" doorUnlocked="+model.doorUnlocked+" targetVisible="+targetVisible()+" targetGravity="+model.targetGravity+" "+audio.stats());Gdx.app.exit();
        }
    }
    private Color worldColor(Color c,float x,float y){
        if(!worldPainting)return c;
        if(PixelAssets.isCoolRegion(model.stageIndex,x,y,model.worldWidth,model.worldHeight))return c==INK?UPPER_INK:c;
        if(c==INK)return LOWER_INK;if(c==PANEL)return WARM_PANEL;if(c==STEEL)return WARM_STEEL;
        if(c==CYAN)return WARM_CYAN;if(c==AMBER)return WARM_AMBER;if(c==VIOLET)return WARM_VIOLET;if(c==RED)return WARM_RED;
        if(c==Color.WHITE || c==Color.LIGHT_GRAY)return INK;
        return c;
    }
    private void rect(float x,float y,float w,float h,Color c){
        if(worldPainting && model.stageIndex==1 && x<seam() && x+w>seam()){
            shapes.setColor(worldColor(c,seam()-1,y));shapes.rect(x,y,seam()-x,h);
            shapes.setColor(worldColor(c,seam(),y));shapes.rect(seam(),y,x+w-seam(),h);
        }else if(worldPainting && model.stageIndex!=1 && y<seam() && y+h>seam()){
            shapes.setColor(worldColor(c,x,seam()-1));shapes.rect(x,y,w,seam()-y);
            shapes.setColor(worldColor(c,x,seam()));shapes.rect(x,seam(),w,y+h-seam());
        }else{shapes.setColor(worldColor(c,x+w/2,y+h/2));shapes.rect(x,y,w,h);}
    }
    private void line(float x,float y,float x2,float y2,float width,Color c){shapes.setColor(worldColor(c,(x+x2)/2,(y+y2)/2));shapes.rectLine(x,y,x2,y2,width);}
    private void arrow(float x,float y,int gravity,Color c){
        line(x,y-9*gravity,x,y+10*gravity,2,c);line(x,y+10*gravity,x-5,y+4*gravity,2,c);line(x,y+10*gravity,x+5,y+4*gravity,2,c);
    }
    /** Advance renderer clocks and consume transient events exactly once per fixed tick. */
    private void tick(StageModel.Input in){
        boolean running=model.started && !model.paused && !model.won && !model.dead;
        boolean frozen=model.stopTime>0;
        boolean restarting=in.restart && (model.paused || model.won || model.dead);
        int oldStage=model.stageIndex;
        boolean wasSolved=model.targetSolved;
        model.step(in);
        if(running)deliveryPulse=Math.max(0,deliveryPulse-StageModel.DT);
        if(!wasSolved && model.targetSolved && model.echoTarget!=null){deliveryPulse=.8f;deliveryX=model.echoTarget.x+model.echoTarget.w/2;deliveryY=model.echoTarget.y+22;}
        attackAge=model.meleeTime>0?StageModel.MELEE_DURATION-model.meleeTime:10;
        frozen=frozen || model.stopTime>0;
        if(restarting || oldStage!=model.stageIndex){bossBreakTime=0;deliveryPulse=0;effects.clear();enemyClocks.clear();deathAge=0;attackAge=10;poseAge=0;lastPose="idle";art.clocks.player=0;art.clocks.world=0;}
        running=running && !model.paused;
        art.clocks.advance(StageModel.DT,running,frozen);
        if(running)bossBreakTime=Math.max(0,bossBreakTime-StageModel.DT);
        if(running){
            String pose=attackAge<StageModel.MELEE_DURATION?"melee":model.guarding?"guard":!model.player.grounded?"air":Math.abs(model.player.vx)>1?"run":"idle";
            if(!pose.equals(lastPose)){poseAge=0;lastPose=pose;}else poseAge+=StageModel.DT;
        }
        if(running && !frozen)for(StageModel.Enemy e:model.enemies)enemyClocks.put(e,enemyClocks.getOrDefault(e,0f)+StageModel.DT);
        enemyClocks.keySet().retainAll(model.enemies);
        if(model.dead && !model.paused)deathAge+=StageModel.DT;
        for(java.util.Iterator<Effect> it=effects.iterator();it.hasNext();){Effect f=it.next();if(running && (f.player || !frozen))f.age+=StageModel.DT;if(f.age>.35f)it.remove();}
        for(StageModel.GameEvent e:model.events){
            audio.event(e.id);
            String clip=null,group="fx";boolean player=false;
            switch(e.id){
                case "boss_seal_break":bossBreakTime=.42f;break;
                case "sword_swing":break;
                case "parry":clip="parry";player=true;break;
                case "just_parry":clip="just_parry";player=true;break;
                case "time_stop":clip="time_stop";break;
                case "time_resume":clip="time_resume";group="fx_utilities";break;
                case "clone_spawn":case "clone_disappear":clip="clone";break;
                case "resurrect":clip="revive";break;
                case "exit_open":clip="exit";group="fx_utilities";break;
                default:break;
            }
            if(clip!=null)effects.add(new Effect(group,clip,e.x,e.y,player));
        }
    }
    private void sprite(com.badlogic.gdx.graphics.g2d.TextureRegion r,float x,float y,float w,float h,boolean flipX,boolean flipY){
        batch.draw(r.getTexture(),Math.round(x),Math.round(y),w,h,flipX?r.getU2():r.getU(),flipY?r.getV():r.getV2(),flipX?r.getU():r.getU2(),flipY?r.getV2():r.getV());
    }
    private String palette(float x,float y){return PixelAssets.isCoolRegion(model.stageIndex,x,y,model.worldWidth,model.worldHeight)?"cool":"warm";}
    private void background(boolean cool,float left,float bottom){
        boolean vertical=model.stageIndex==1;
        float[] span=PixelAssets.backgroundSpan(vertical?left:bottom,vertical?model.worldWidth:model.worldHeight,vertical?1280:720,cool);
        if(span[1]<=0)return;
        com.badlogic.gdx.graphics.g2d.TextureRegion source=art.background(cool);
        float v0=source.getV(),v1=source.getV2();
        // Crop a viewport-fixed non-seamless image, rather than repeat it up the tower.
        com.badlogic.gdx.graphics.g2d.TextureRegion crop=new com.badlogic.gdx.graphics.g2d.TextureRegion(source);
        if(vertical){
            float u0=source.getU(),u1=source.getU2();
            crop.setU(u0+(span[0]-left)/1280*(u1-u0));
            crop.setU2(u0+(span[0]+span[1]-left)/1280*(u1-u0));
        }else{
            crop.setV(v1-(span[0]+span[1]-bottom)/720*(v1-v0));
            crop.setV2(v1-(span[0]-bottom)/720*(v1-v0));
        }
        batch.setColor(cool?new Color(.29f,.35f,.43f,1):new Color(.35f,.31f,.29f,1));
        sprite(crop,vertical?span[0]:left-100,vertical?bottom:span[0],vertical?span[1]:1600,vertical?720:span[1],false,false);
    }
    private boolean targetVisible(){return PixelAssets.targetVisible(model.stageIndex,model.stopTime,model.targetRevealed,model.targetSolved,model.echoTarget!=null,model.paused);}
    /** Hollow 44px footprint and contact face; never an opaque tile over an actor. */
    private void drawEchoTarget(){
        if(model.boss!=null || !targetVisible())return;
        StageModel.Box t=model.echoTarget;float x=t.x+(t.w-44)/2,y=t.y,w=44,h=t.h,cx=x+w/2,cy=y+h/2;
        shapes.setProjectionMatrix(world.combined);shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(int pass=0;pass<2;pass++){
            float thickness=pass==0?6:2;Color c=pass==0?INK:Color.WHITE;
            line(x,y,x+w,y,thickness,c);line(x+w,y,x+w,y+h,thickness,c);line(x+w,y+h,x,y+h,thickness,c);line(x,y+h,x,y,thickness,c);
            line(cx-8,cy-8,cx+8,cy+8,thickness,c);line(cx-8,cy+8,cx+8,cy-8,thickness,c);
            for(int n=0;n<16;n++){
                double a=n*Math.PI/8,b=(n+1)*Math.PI/8;
                line(cx+(float)Math.cos(a)*34,cy+(float)Math.sin(a)*34,cx+(float)Math.cos(b)*34,cy+(float)Math.sin(b)*34,pass==0?4:1,c);
            }
        }
        float foot=model.targetGravity<0?y:y+h;
        line(x-5,foot,x+w+5,foot,3,CYAN);arrow(cx,model.targetGravity<0?y+h+16:y-16,model.targetGravity,CYAN);
        shapes.end();batch.begin();
        float labelY=model.targetGravity>0?y-36:y+h+49;
        text("MARK",x-34,labelY-1,.8f,INK);text("MARK",x-35,labelY,.8f,Color.WHITE);
        batch.end();
    }
    /** Stable world-space brackets: amber pending, cyan matched; contact face follows gravity. */
    private void drawBossTarget(){
        StageModel.Boss b=model.boss;
        if(b==null || model.echoTarget==null || b.defeated)return;
        if(!model.targetSolved && !targetVisible())return;
        StageModel.Box t=model.echoTarget;float x=t.x,y=t.y,w=t.w,h=t.h;
        Color state=(model.targetRevealed && !model.targetSolved)?AMBER:CYAN;
        shapes.setProjectionMatrix(world.combined);shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(int pass=0;pass<2;pass++){
            float thick=pass==0?7:3;Color c=pass==0?INK:state;
            line(x,y,x+w,y,thick,c);line(x+w,y,x+w,y+h,thick,c);
            line(x+w,y+h,x,y+h,thick,c);line(x,y+h,x,y,thick,c);
            float base=model.targetGravity<0?y:y+h;
            line(x-6,base,x+w+6,base,thick,c);
            arrow(x+w/2,model.targetGravity<0?y+h+17:y-17,model.targetGravity,c);
        }
        for(float stripe=x+8;stripe<x+w-6;stripe+=12)line(stripe,y+6,stripe,y+h-6,1,state);
        shapes.end();batch.begin();
        float labelY=model.targetGravity>0?y-35:y+h+49;
        String label=(model.targetRevealed && !model.targetSolved)?"J":"OK";
        float labelX=PixelAssets.targetLabelX(x,model.worldWidth);
        text(label,labelX+1,labelY-1,.8f,INK);text(label,labelX,labelY,.8f,state);
        batch.end();
    }
    private void drawDeliveryPulse(){
        if(deliveryPulse<=0)return;
        shapes.setProjectionMatrix(world.combined);shapes.begin(ShapeRenderer.ShapeType.Filled);
        float radius=28+(.8f-deliveryPulse)*100;
        for(int n=0;n<24;n++){
            double a=n*Math.PI/12,b=(n+1)*Math.PI/12;
            float x1=deliveryX+(float)Math.cos(a)*radius,y1=deliveryY+(float)Math.sin(a)*radius;
            float x2=deliveryX+(float)Math.cos(b)*radius,y2=deliveryY+(float)Math.sin(b)*radius;
            line(x1,y1,x2,y2,6,INK);line(x1,y1,x2,y2,2,CYAN);
        }
        shapes.end();
    }
    private String bossArtState(){return model.boss==null?"none":PixelAssets.bossState(model.boss.phase,model.boss.primed,model.boss.defeated,bossBreakTime);}
    private void drawWorld(){
        worldPainting=model.stageIndex==1;
        float left=world.position.x-640,bottom=world.position.y-360;
        batch.setProjectionMatrix(world.combined);batch.begin();
        // One wide stretched image per half, never repeat a non-seamless source.
        // Screen-fixed background: no parallax drift or ambient motion.
        background(false,left,bottom);background(true,left,bottom);
        batch.setColor(Color.WHITE);
        if(model.stageIndex!=1 && seam()+16>=bottom && seam()-16<=bottom+720)
            for(float x=(float)Math.floor(left/32)*32;x<left+1280;x+=32)
                sprite(art.frame("terrain_cool","seam",0),x,seam()-16,32,32,false,false);
        for(StageModel.Box p:model.platforms)terrain(p,left,bottom);
        StageModel.Box ex=model.exit;
        if(model.boss==null)sprite(art.frame("exit_"+palette(ex.x+ex.w/2,ex.y+ex.h/2),PixelAssets.doorClip(model.doorUnlocked),art.clocks.world),ex.x+(ex.w-64)/2,ex.y,64,96,false,false);
        batch.end();
        drawEchoTarget();drawBossTarget();drawDeliveryPulse();
        if(model.stageIndex==1){
            shapes.setProjectionMatrix(world.combined);shapes.begin(ShapeRenderer.ShapeType.Filled);
            rect(seam()-2,bottom,4,720,STEEL);rect(seam(),bottom,1,720,CYAN);
            shapes.end();
        }
        if(model.boss!=null){
            shapes.setProjectionMatrix(world.combined);shapes.begin(ShapeRenderer.ShapeType.Filled);
            StageModel.Boss b=model.boss;
            StageModel.Box beam=model.bossAttackBox();
            if(!b.defeated && beam!=null && (b.phase==1 || b.phase==2)){
                // Exact authoritative wall-clipped box; no projectile or wrap extension.
                rect(beam.x,beam.y,beam.w,beam.h,INK);
                if(b.phase==1){
                    rect(beam.x,beam.y,beam.w,3,AMBER);rect(beam.x,beam.y+beam.h-3,beam.w,3,AMBER);
                    for(float x=beam.x+8;x<beam.x+beam.w-8;x+=28)rect(x,b.beamY-2,12,4,AMBER);
                }else{
                    rect(beam.x,beam.y+2,beam.w,Math.max(1,beam.h-4),RED);
                    rect(beam.x,b.beamY-3,beam.w,6,Color.WHITE);
                }
            }
            // Remaining seals.
            for(int n=0;n<3;n++){
                float x=b.x+b.w/2-42+n*32;
                rect(x-3,b.y+b.h+24,26,26,INK);
                rect(x,b.y+b.h+27,20,20,n<b.seals?(b.primed?CYAN:AMBER):STEEL);
                if(n<b.seals)rect(x+7,b.y+b.h+47,6,9,b.primed?CYAN:AMBER);
                else{line(x+3,b.y+b.h+30,x+17,b.y+b.h+44,3,RED);line(x+17,b.y+b.h+30,x+3,b.y+b.h+44,3,RED);}
            }
            if(b.phase==1 && !b.defeated){
                rect(b.x-10,b.y-8,b.w+20,5,AMBER);
                rect(b.x-48,b.y+b.h+88,196,12,INK);
                rect(b.x-46,b.y+b.h+90,192*PixelAssets.bossChargeProgress(b.timer),8,b.primed?CYAN:AMBER);
            }

            if(bossBreakTime>0 && !b.defeated){
                float radius=90+(.42f-bossBreakTime)*120;
                for(int n=0;n<8;n++){
                    double a=n*Math.PI/4;
                    float cx=b.x+b.w/2,cy=b.y+b.h/2;
                    line(cx+(float)Math.cos(a)*radius,cy+(float)Math.sin(a)*radius,cx+(float)Math.cos(a)*(radius+16),cy+(float)Math.sin(a)*(radius+16),4,CYAN);
                }
            }
            shapes.end();
        }
        batch.begin();
        for(StageModel.Enemy e:model.enemies){
            float clock=enemyClocks.getOrDefault(e,0f);
            if(!e.alive){
                if(!e.clone)sprite(art.frame("bricks_"+palette(e.x+e.w/2,e.y+e.h/2),e.ranged?"corpse_diamond":"corpse_round",0),e.x,e.y,e.w,e.h,false,e.gravity>0);
                continue;
            }
            String group=(e.ranged?"ranged":"melee")+(e.clone?"_clone":"_original");
            com.badlogic.gdx.graphics.g2d.TextureRegion frame=art.enemy(group,e.phase,e.upper,Math.abs(e.vx)>1,e.stun>0,clock);
            if(e.phase>0 && e.stun<=0){
                StageModel.Box hit=model.attackBox(e);
                if(e.ranged && !e.upper){
                    // Instant full wall-clipped beam, never a moving projectile.
                    batch.setColor(e.phase==1?new Color(1,.65f,.4f,.65f):Color.WHITE);
                    sprite(art.frame("fx",e.phase==1?"ranged_warning":"ranged_hit",0),hit.x,hit.y,hit.w,hit.h,e.direction<0,e.gravity>0);
                }else if(e.phase==2){
                    if(e.upper){
                        batch.setColor(RED);
                        sprite(art.frame("fx","slash",0),hit.x,hit.y,hit.w,hit.h,e.direction<0,e.gravity>0);
                    }else{
                        // Extend the strike sprite into its hitbox.
                        
                        com.badlogic.gdx.graphics.g2d.TextureRegion tip=new com.badlogic.gdx.graphics.g2d.TextureRegion(frame,40,25,15,12);
                        sprite(tip,hit.x,hit.y+(hit.h-24)/2,hit.w,24,e.direction<0,e.gravity>0);
                    }
                }
                batch.setColor(e.upper?RED:e.phase==1?new Color(1,.78f,.48f,1):Color.WHITE);
            }
            sprite(frame,e.x+e.w/2-32,PixelAssets.spriteY(e.y,e.h,e.gravity),64,64,e.direction<0,e.gravity>0);
            batch.setColor(Color.WHITE);
            if(e.phase==1 && e.stun<=0){
                // Gravity-relative telegraph.
                float markerX=e.x+e.w/2-5,markerY=e.gravity<0?e.y+e.h+22:e.y-8;
                text(e.upper?"! NO BLOCK":"!",markerX+1,markerY-1,1.3f,INK);
                text(e.upper?"! NO BLOCK":"!",markerX,markerY,1.3f,e.upper?RED:Color.WHITE);
            }
        }
        StageModel.Boss boss=model.boss;
        if(boss!=null){
            
            batch.setColor(Color.WHITE);
            sprite(art.frame("paradox_warden",bossArtState(),art.clocks.world),boss.x+boss.w/2-96,boss.y-27,192,192,boss.direction<0,false);

        }
        StageModel.Body p=model.player;
        String clip=PixelAssets.playerPose(model.dead,model.guarding,model.parryTime,attackAge,p.grounded,p.vx,p.vy,p.gravity);
        float age=model.dead?deathAge:poseAge;
        
        if(model.invulnerable>0)batch.setColor(.75f,1,1,PixelAssets.protectionAlpha(art.clocks.player));
        if("melee".equals(clip)){
            sprite(art.swing(attackAge),PixelAssets.swingX(p.x,p.w,model.facing),PixelAssets.swingY(p.y,p.h,p.gravity),96,96,model.facing<0,p.gravity>0);
        }else{
            int frameId=PixelAssets.knightFrame(clip,age);
            sprite(art.knight(clip,age),PixelAssets.knightX(p.x,p.w,frameId,model.facing),PixelAssets.spriteY(p.y,p.h,p.gravity),64,64,model.facing<0,p.gravity>0);
        }
        batch.setColor(Color.WHITE);
        if((model.guarding || model.parryTime>0) && !model.dead && !"melee".equals(clip)){
            boolean just=model.parryTime>StageModel.PARRY_DURATION-StageModel.JUST_WINDOW;
            batch.setColor(just?Color.WHITE:AMBER);
            sprite(art.frame("fx",just?"just_parry":"parry",0),p.x+p.w/2+model.facing*23-20,p.y+p.h/2-20,40,40,model.facing<0,p.gravity>0);
            batch.setColor(Color.WHITE);
        }
        for(Effect f:effects){
            boolean defense="parry".equals(f.clip)||"just_parry".equals(f.clip);
            if(defense && f.age>.14f)continue;
            float size=defense?32:48;
            sprite(art.frame(f.group,f.clip,defense?0:f.age),f.x-size/2,f.y-size/2,size,size,false,false);
        }
        if(model.stopTime>0)sprite(art.frame("fx","time_stop",art.clocks.world),left+1180,bottom+570,64,64,false,false);
        batch.end();
        worldPainting=false;
    }
    /** Tile any model rectangle exactly, cropping edge UVs instead of changing collision geometry. */
    private void terrain(StageModel.Box p,float left,float viewBottom){
        if(!PixelAssets.visible(p.x,p.y,p.w,p.h,left,viewBottom))return;
        float firstX=p.x+Math.max(0,(float)Math.floor((left-p.x)/32))*32;
        float firstY=p.y+Math.max(0,(float)Math.floor((viewBottom-p.y)/32))*32;
        for(float y=firstY;y<p.y+p.h && y<=viewBottom+720;y+=32)for(float x=firstX;x<p.x+p.w && x<=left+1280;x+=32){
            float w=Math.min(32,p.x+p.w-x),h=Math.min(32,p.y+p.h-y);
            boolean top=y+32>=p.y+p.h,bottom=y==p.y,l=x==p.x,r=x+32>=p.x+p.w;
            String tile=top?(l?"outside_tl":r?"outside_tr":"surface_top"):bottom?(l?"outside_bl":r?"outside_br":"bottom"):l?"wall_left":r?"wall_right":"stone_fill";
            // Split a cell at the world seam, preserving its original edge UVs.
            int sy=top?0:32-(int)h;
            if(model.stageIndex==1 && x<seam() && x+w>seam()){
                terrainCell(tile,x,y,seam()-x,h,0,sy);
                terrainCell(tile,seam(),y,x+w-seam(),h,(int)(seam()-x),sy);
            }else terrainCell(tile,x,y,w,h,0,sy);
        }
    }
    private void terrainCell(String tile,float x,float y,float w,float h,int sx,int sy){
        com.badlogic.gdx.graphics.g2d.TextureRegion region=art.frame("terrain_"+palette(x+w/2,y+h/2),tile,0);
        sprite(new com.badlogic.gdx.graphics.g2d.TextureRegion(region,sx,sy,(int)w,(int)h),x,y,w,h,false,false);
    }
    private void text(String s,float x,float y,float scale,Color c){font.getData().setScale(scale);font.setColor(worldColor(c,x,y));font.draw(batch,s,x,y);}
    private void drawHud(){
        boolean overlay=!model.started||model.paused||model.won||model.dead;
        boolean pending=model.targetRevealed && !model.targetSolved;
        float guardY=model.player.gravity>0?64:632;
        shapes.setProjectionMatrix(hud.combined);shapes.begin(ShapeRenderer.ShapeType.Filled);
        rect(0,684,1280,36,INK);rect(0,682,1280,2,STEEL);
        rect(182,689,90,3,STEEL);
        if(model.woundTime>0)rect(182,689,90*model.woundTime/StageModel.WOUND_DURATION,3,RED);
        rect(540,696,160,8,STEEL);
        rect(540,696,160*(model.stopTime>0?model.stopTime/3f:1-model.stopCooldown/4f),8,model.stopTime>0?VIOLET:CYAN);
        if(model.boss==null){rect(870,696,190,4,STEEL);rect(870,696,190*PixelAssets.stageProgress(model.stageIndex,model.player.x,model.player.y,model.worldWidth,model.worldHeight),4,CYAN);}
        if(!overlay){
            rect(544,646,192,30,INK);
            if(model.combatFeedbackTime>0)rect(482,570,316,34,PANEL);
            if(model.guarding || model.parryTime>0)rect(16,guardY-20,112,28,INK);
            if(model.boundaryChain==1 && !model.player.grounded)rect(16,PixelAssets.boundaryHudY(model.player.gravity)-18,152,28,INK);
        }
        if(overlay){
            float overlayY=model.won && model.boss!=null?460:150,overlayH=580-overlayY;
            rect(220,overlayY,840,overlayH,INK);rect(220,576,840,4,model.dead?RED:CYAN);
            rect(220,overlayY,4,overlayH,STEEL);rect(1056,overlayY,4,overlayH,STEEL);
        }
        shapes.end();batch.setProjectionMatrix(hud.combined);batch.begin();
        sprite(art.frame("hud",model.woundTime>0?"wound":"heal",0),155,692,20,20,false,false);
        sprite(art.frame("hud","gravity",0),270,692,20,20,false,model.player.gravity>0);
        sprite(art.frame("hud","timestop",0),514,692,20,20,false,false);
        text(String.format(java.util.Locale.ROOT,"KNIGHT / %02d",model.stageIndex+1),24,709,1,CYAN);
        text(model.dead?"LOST":model.woundTime>0?String.format(java.util.Locale.ROOT,"WOUNDED %.1f",model.woundTime):"STABLE",182,709,1,model.woundTime>0?RED:CYAN);
        text(model.player.gravity<0?"DOWN":"UP",295,709,1,model.player.gravity<0?CYAN:VIOLET);
        text(model.stopTime>0?"FROZEN":model.stopCooldown>0?"COOLDOWN":"READY",710,709,1,model.stopTime>0?VIOLET:Color.LIGHT_GRAY);
        text(model.boss!=null?"SEALS / "+model.boss.seals:model.doorUnlocked?"EXIT":"LOCKED",950,709,1,model.boss!=null?AMBER:CYAN);
        text(audio.muted()?"M / OFF":"M / ON",1160,709,.85f,Color.LIGHT_GRAY);
        if(!overlay){
            String label=PixelAssets.hudObjective(model.stageIndex,pending,model.stopTime>0,model.boss!=null && model.boss.primed,model.doorUnlocked);
            text(label,640-label.length()*3.8f,666,1.1f,model.boss!=null && model.boss.primed?CYAN:Color.WHITE);
            if(model.combatFeedbackTime>0){
                Color color="HIT".equals(model.combatFeedback)?RED:model.combatFeedback.contains("SEALED") || model.combatFeedback.contains("BLOCK") || "PARRY".equals(model.combatFeedback)?AMBER:CYAN;
                text(model.combatFeedback,640-model.combatFeedback.length()*4.8f,594,1.4f,color);
            }
            if(model.guarding || model.parryTime>0){
                boolean just=model.parryTime>StageModel.PARRY_DURATION-StageModel.JUST_WINDOW;
                text(just?"JUST":"GUARD",24,guardY,1.2f,just?Color.WHITE:AMBER);
            }
            if(model.boundaryChain==1 && !model.player.grounded)text("WRAP 1/2",28,PixelAssets.boundaryHudY(model.player.gravity),1.1f,AMBER);
        }
        if(!model.started){
            text("KNIGHT / SLIMES",275,521,3.1f,CYAN);text(model.stageTitle,280,468,1.2f,Color.LIGHT_GRAY);
            text("A/D  MOVE       SPACE  JUMP",280,408,1.3f,Color.WHITE);
            text("J  PUSH         K  GUARD / PARRY",280,368,1.3f,AMBER);
            text("L  TIME STOP    ESC  PAUSE",280,328,1.3f,VIOLET);
            text("ENTER / START",280,225,1.6f,CYAN);
        }else if(model.paused){
            text("PAUSED",280,500,2.5f,CYAN);
            text("A/D  MOVE    SPACE  JUMP",280,430,1.2f,Color.WHITE);
            text("J  PUSH    K  PARRY    L  TIME STOP",280,390,1.2f,Color.WHITE);
            text("ESC / RESUME",280,310,1.5f,CYAN);text("R / RESTART",280,265,1.2f,AMBER);
        }else if(model.won){
            boolean finalBoss=model.boss!=null;
            text(model.hasNextStage()?"STAGE CLEAR":"VICTORY",280,finalBoss?550:500,2.5f,CYAN);
            if(model.hasNextStage())text("ENTER / NEXT STAGE",280,375,1.3f,CYAN);
            else text("N / NEW GAME",280,515,1.2f,CYAN);
            text("R / RETRY",280,finalBoss?485:305,1.2f,AMBER);
        }else if(model.dead){
            text("GAME OVER",280,500,2.7f,RED);
            text("BOUNDARY".equals(model.deathReason)?"BOUNDARY":"WOUND",280,412,1.2f,Color.WHITE);
            text("R / RETRY",280,305,1.5f,CYAN);
        }
        batch.end();
    }
    @Override public void resize(int width,int height){if(viewport!=null)viewport.update(width,height,false);}
    @Override public void dispose(){if(art!=null)art.dispose();if(audio!=null)audio.dispose();if(shapes!=null)shapes.dispose();if(batch!=null)batch.dispose();if(font!=null)font.dispose();}
}
