package io.jbnu.test;

import java.util.ArrayList;
import java.util.List;

/** Pure deterministic simulation. Coordinates are world units, y increases upward. */
public final class StageModel {
    public static final float DT=1f/120f, HEIGHT=720, WIDTH=4200, SPEED=270, JUMP=650, GRAVITY=1550;
    public static class Box {
        public float x,y,w,h;
        public Box(float x,float y,float w,float h){this.x=x;this.y=y;this.w=w;this.h=h;}
        public boolean overlaps(Box b){return x<b.x+b.w && x+w>b.x && y<b.y+b.h && y+h>b.y;}
    }
    public static class Body extends Box {
        public float vx,vy; public int gravity=-1; public boolean grounded;
        public Body(float x,float y,float w,float h){super(x,y,w,h);}
    }
    public static class Enemy extends Body {
        public boolean alive=true,upper,clone,ranged; public Enemy original; public final float spawnX,spawnY;
        public int phase,direction=1; public float timer,stun;
        private boolean hitPlayer;
        private boolean targetOutside;
        private int bossPushSerial=-1;
        private float upperRecovery,knockback;
        private int patrolDirection=1;
        public final int spawnGravity;
        public Enemy(float x,float y){this(x,y,-1);}
        public Enemy(float x,float y,int gravity){super(x,y,44,44);spawnX=x;spawnY=y;this.gravity=gravity;spawnGravity=gravity;}
    }
    public static class Boss extends Box {
        public int seals=3,phase,direction=1;
        public float timer=1.2f,beamY,exposure;
        public boolean primed,defeated;
        private boolean hitPlayer;
        public Boss(){super(590,300,100,100);}
    }
    public Boss boss;
    public String objective="Cross the lower boundary.";
    public static final class GameEvent {
        public final String id;public final float x,y;
        public GameEvent(String id,float x,float y){this.id=id;this.x=x;this.y=y;}
    }
    public final List<GameEvent> events=new ArrayList<GameEvent>();
    private void cue(String id,Box b){events.add(new GameEvent(id,b.x+b.w/2,b.y+b.h/2));}
    public boolean dead;
    public int boundaryChain;
    public String deathReason="";
    public float woundTime,combatFeedbackTime;
    public String combatFeedback="";
    public static final float WOUND_DURATION=5f, JUST_INVULNERABILITY=.45f, HIT_INVULNERABILITY=.35f, UPPER_WINDUP=.06f;
    public static class Input { public int move; public boolean jump,melee,parry,guard,stop,pause,restart,start,campaignRestart; }
    public final Body player=new Body(90,50,30,44);
    public final List<Box> platforms=new ArrayList<Box>();
    public final List<Enemy> enemies=new ArrayList<Enemy>();
    public boolean started,paused,won,guarding;
    private boolean guardWasHeld;
    public float stopTime,stopCooldown;
    public final Box exit=new Box(4050,240,100,130);
    public int stageIndex;
    public float worldWidth=WIDTH,worldHeight=HEIGHT;
    public boolean wrapX=false,wrapY=true;
    public String stageTitle="STAGE 1 - Boundary Foundry";
    public Box echoTarget;
    public boolean targetRevealed,targetSolved,doorUnlocked;
    public int targetGravity=-1,targetVersion;
    private final long initialSeed;
    private long targetRandom;
    private float previousTargetX=-1000,previousTargetY=-1000;
    public StageModel(){this(0L);}
    public StageModel(long seed){initialSeed=seed;targetRandom=seed;buildLevel();}
    public boolean hasNextStage(){return stageIndex<2;}
    public void loadStage(int index){
        if(index<0 || index>2)throw new IllegalArgumentException("Unknown stage: "+index);
        stageIndex=index;restart();
    }
    private String currentObjective(){
        if(boss!=null){
            if(boss.primed)return "EXPOSED: JUST parry the boss beam to break one seal. Each seal needs a fresh placement.";
            return targetRevealed?"After thaw: J-push a living clone into the visible target, then JUST parry. One new target per seal.":"L: freeze to mark a target. After thaw J-push a living clone there, then JUST parry the boss beam.";
        }
        if(!doorUnlocked && (targetRevealed || (stageIndex==0?player.x>=1520:player.y>=420)))
            return targetRevealed?"After thaw, lead a living clone onto the memorized mark BEFORE climbing; the door stays unlocked.":"Before climbing: L reveals a clone mark. Memorize it, thaw, then lead a living clone there to unlock the door.";
        return stageIndex==0?StageOneLevel.objective(player.x):StageTwoLevel.objective(player.y);
    }
    private void buildLevel(){
        platforms.clear();enemies.clear();boss=stageIndex==2?new Boss():null;
        echoTarget=null;targetRevealed=false;targetSolved=false;doorUnlocked=false;targetGravity=-1;targetVersion=0;
        previousTargetX=previousTargetY=-1000;targetRandom=initialSeed+stageIndex;
        worldWidth=stageIndex==0?WIDTH:1280;worldHeight=stageIndex==1?2560:HEIGHT;
        wrapX=stageIndex!=0;wrapY=stageIndex!=1;
        stageTitle=stageIndex==0?"STAGE 1 - Boundary Foundry":stageIndex==1?"STAGE 2 - Lateral Ascent":"STAGE 3 - Paradox Warden";
        if(stageIndex==0)StageOneLevel.populate(this);else if(stageIndex==1)StageTwoLevel.populate(this);else StageThreeLevel.populate(this);
        objective=currentObjective();
    }
    public void restart(){
        buildLevel();events.clear();player.x=90;player.y=stageIndex==0?50:40;player.vx=0;player.vy=0;player.gravity=-1;player.grounded=false;
        objective=currentObjective();exitAnnounced=false;
        guarding=false;guardWasHeld=false;meleePending=false;
        boundaryChain=0;deathReason="";
        dead=false;woundTime=0;combatFeedbackTime=0;combatFeedback="";started=true;paused=false;won=false;facing=1;stopTime=0;stopCooldown=0;parryTime=0;meleeTime=0;invulnerable=0;attackCooldown=0;parryCooldown=0;
    }
    public int facing=1;
    public float parryTime, meleeTime, invulnerable, attackCooldown,parryCooldown;
    public static final float STOP_DURATION=3f, STOP_COOLDOWN=4f, PARRY_DURATION=.28f, JUST_WINDOW=.08f;
    public static final float MELEE_DURATION=.28f, MELEE_WINDUP=.07f;
    private boolean struck,exitAnnounced,meleePending;
    public void step(Input in){
        events.clear();
        if(in.campaignRestart && won && !hasNextStage()){loadStage(0);cue("ui_confirm",player);return;}
        if(in.start && won && hasNextStage()){loadStage(stageIndex+1);cue("ui_confirm",player);return;}
        if(in.restart && (paused || won || dead)){restart();cue("ui_confirm",player);return;}
        if(!started){if(in.start){started=true;cue("ui_confirm",player);}return;}
        boolean resuming=paused && in.pause;
        if(in.pause){paused=!paused;cue("ui_pause",player);}
        if(paused){guardWasHeld=in.guard;return;}
        if(won || dead){meleePending=false;return;}
        if(resuming)guardWasHeld=in.guard;
        boolean guardPress=in.guard && !guardWasHeld;
        guardWasHeld=in.guard;guarding=in.guard;
        combatFeedbackTime=Math.max(0,combatFeedbackTime-DT);
        if(combatFeedbackTime==0)combatFeedback="";
        if(woundTime>0){woundTime=Math.max(0,woundTime-DT);if(woundTime==0){feedback("RECOVERED");cue("wound_recover",player);}}
        boolean acceptedStop=in.stop && stopTime==0 && stopCooldown==0;
        if(acceptedStop){stopTime=STOP_DURATION;cue("time_stop",player);if(boss==null && echoTarget==null && !targetSolved)selectEchoTarget();}
        separatePlayer();
        triggerOriginalSupport();
        boolean frozen=stopTime>0;
        if(frozen){stopTime=Math.max(0,stopTime-DT);if(stopTime==0){stopCooldown=STOP_COOLDOWN;cue("time_resume",player);}}
        else stopCooldown=Math.max(0,stopCooldown-DT);
        player.vx=in.move*SPEED;if(in.move!=0)facing=in.move;
        parryTime=Math.max(0,parryTime-DT);meleeTime=Math.max(0,meleeTime-DT);
        attackCooldown=Math.max(0,attackCooldown-DT);parryCooldown=Math.max(0,parryCooldown-DT);invulnerable=Math.max(0,invulnerable-DT);
        if((guardPress || (!in.guard && in.parry)) && parryCooldown==0){parryTime=PARRY_DURATION;parryCooldown=.42f;}
        struck=false;
        if(meleePending && meleeTime<=MELEE_DURATION-MELEE_WINDUP){meleePending=false;struck=!frozen;}
        if(in.melee && attackCooldown==0){meleeTime=MELEE_DURATION;attackCooldown=.3f;meleePending=true;cue("sword_swing",player);}
        if(in.jump && player.grounded){player.vy=-player.gravity*JUMP;player.grounded=false;cue("jump",player);}
        Enemy ride=null;
        if(!in.jump)for(Enemy e:enemies)if(standing(e)){ride=e;break;}
        for(Enemy e:enemies){
            float oldX=e.x,oldY=e.y;
            if(!frozen){
                if(e.stun>0)e.stun-=DT;
                e.upperRecovery=Math.max(0,e.upperRecovery-DT);
                e.knockback=Math.max(0,e.knockback-DT);
                if(struck && Math.abs(e.x-player.x)<96 && Math.abs(e.y-player.y)<70 && (e.x+e.w/2-player.x-player.w/2)*facing>=-24){
                    e.vx=facing*620;e.vy=-e.gravity*180;e.knockback=.5f;
                    if(boss!=null && echoTarget!=null && targetRevealed && !targetSolved && e.alive && e.clone)e.bossPushSerial=targetVersion;
                    cue(e.alive?"melee_push":"brick_push",e);
                }
                updateMovement(e);
                physics(e);e.vx*=.985f;
                if(e.alive && e.stun<=0)updateAttack(e);
            }
            if(ride==e){moveX(player,e.x-oldX);moveY(player,e.y-oldY);}
        }
        boolean wasGrounded=player.grounded;float landingSpeed=Math.abs(player.vy);
        boolean wrapped=physics(player);
        if(!wasGrounded && player.grounded)cue(landingSpeed>500?"land_heavy":"land_light",player);
        if(wrapped){
            cue("boundary_cross",player);
            if(++boundaryChain>=2){dead=true;meleePending=false;deathReason="BOUNDARY";cue("player_death",player);return;}
        }else if(player.grounded && hasContactSupport())boundaryChain=0;
        // Portal placement is not a sweep through intervening enemy bodies.
        // Landing is authoritative immediately, including during time stop.
        if(!wrapped && triggerOriginalSupport())frozen=false;
        // Reveal only when a frozen state survives BOTH original-support cancellation checks.
        if(frozen && stopTime>0 && boss!=null && echoTarget==null && !targetSolved)selectEchoTarget();
        if(frozen && stopTime>0 && echoTarget!=null)targetRevealed=true;
        updateEchoTarget(frozen);
        if(!frozen)updateBoss();
        // Resolve every eligible incoming parry before any damage, independent of list order.
        boolean parried=false,just=parryTime>PARRY_DURATION-JUST_WINDOW;
        String bossFeedback=null;
        if(!frozen && (guarding || parryTime>0))for(Enemy e:enemies){
            if(!e.alive || e.stun>0 || e.phase!=2 || e.upper || !attackBox(e).overlaps(player))continue;
            if(just){woundTime=0;e.alive=false;if(!e.clone){if(e.gravity>0)e.y+=e.h-22;e.h=22;cue("slime_to_brick",e);}e.phase=0;e.vx=facing*100;invulnerable=Math.max(invulnerable,JUST_INVULNERABILITY);}
            else {e.stun=1.1f;e.phase=0;cue("slime_stun",e);}
            parried=true;
        }
        if(!frozen && (guarding || parryTime>0) && boss!=null && !boss.defeated && boss.phase==2 && bossAttackBox().overlaps(player)){
            boss.phase=3;boss.timer=1.2f;boss.hitPlayer=true;parried=true;
            bossFeedback=just?"SEALED":"BLOCKED";
            if(just){
                woundTime=0;invulnerable=Math.max(invulnerable,JUST_INVULNERABILITY);
                if(boss.primed && boss.exposure>0){
                    boss.seals--;boss.primed=false;boss.exposure=0;bossFeedback="SEAL BROKEN";cue("boss_seal_break",boss);
                    if(echoTarget!=null){previousTargetX=echoTarget.x+echoTarget.w/2;previousTargetY=echoTarget.y;}
                    echoTarget=null;targetRevealed=false;targetSolved=false;
                    if(boss.seals==0){boss.defeated=true;won=true;bossFeedback="BOSS DEFEATED";cue("boss_defeat",boss);cue("stage_clear",player);}
                }
            }
        }
        if(parried){parryTime=0;feedback(bossFeedback!=null?bossFeedback:just?"JUST PARRY":"PARRY");cue(just?"just_parry":"parry",player);}
        if(won){meleePending=false;return;}
        for(Enemy e:enemies){
            if(!e.alive || e.stun>0 || e.phase!=2 || e.hitPlayer || !attackBox(e).overlaps(player))continue;
            if(invulnerable<=0){e.hitPlayer=true;if(woundTime>0){dead=true;deathReason="WOUND";cue("player_death",player);}else woundTime=WOUND_DURATION;invulnerable=HIT_INVULNERABILITY;feedback("HIT");cue("player_hit",player);player.vy=-player.gravity*260;}
        }
        if(boss!=null && !boss.defeated && boss.phase==2 && !boss.hitPlayer && bossAttackBox().overlaps(player) && invulnerable<=0){
            boss.hitPlayer=true;if(woundTime>0){dead=true;deathReason="WOUND";cue("player_death",player);}else woundTime=WOUND_DURATION;
            invulnerable=HIT_INVULNERABILITY;feedback("HIT");cue("player_hit",player);player.vy=-player.gravity*260;
        }
        if(dead){meleePending=false;return;}
        if(!frozen){handleEnemyBoundaries();separatePlayer();}
        objective=currentObjective();
        if(doorUnlocked && !exitAnnounced && (stageIndex==0?player.x>exit.x-550:player.y>exit.y-400)){exitAnnounced=true;cue("exit_open",exit);}
        if(!dead && boss==null && doorUnlocked && player.overlaps(exit)){won=true;cue("stage_clear",player);}
    }
    /** Fixed supported destinations, never free-falling coordinates. */
    private void selectEchoTarget(){
        if(boss!=null){selectBossTarget();return;}
        float[] centers=stageIndex==0?new float[]{1660,1730,1790}:stageIndex==1?new float[]{1050,1120,1200}:new float[]{1100,1160,1220,60,120,180};
        List<Box> choices=new ArrayList<Box>();
        for(float center:centers){
            int g=stageIndex==2 && center<300?1:-1;
            float y=stageIndex==1?440:g>0?636:40;
            // One clone-width center tolerance keeps edge marks reachable by ordinary chase.
            Box b=new Box(center-44,y,88,44);
            boolean anyClone=false,reachable=false,occupied=false;
            for(Enemy e:enemies)if(e.alive && e.clone){
                anyClone=true;
                if(e.gravity==g && Math.abs(e.y-y)<4 && e.x>= (stageIndex==0?1520:stageIndex==1?1000:g>0?0:1060) && e.x+e.w<= (stageIndex==0?1850:stageIndex==1?1280:g>0?220:1280))reachable=true;
                if(e.gravity==g && Math.abs(e.y-y)<4 && Math.abs(e.x+e.w/2-center)<=b.w/2)occupied=true;
            }
            if(anyClone && !reachable)continue;
            // Without a clone both boss source originals can create their paired receiver.
            if(occupied || (Math.abs(previousTargetX-center)<1 && Math.abs(previousTargetY-y)<1))continue;
            choices.add(b);
        }
        if(choices.isEmpty())return; // Retry on a later accepted freeze after a source becomes available.
        targetRandom+=0x9E3779B97F4A7C15L;
        long z=targetRandom;z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;z^=z>>>31;
        echoTarget=choices.get((int)((z&Long.MAX_VALUE)%choices.size()));
        for(Enemy e:enemies)e.targetOutside=false;
        targetGravity=stageIndex==2 && echoTarget.x<300?1:-1;targetVersion++;
    }
    /** Boss marks use the existing echo API, but require a new marked-wave melee impulse. */
    private void selectBossTarget(){
        List<Box> choices=new ArrayList<Box>();List<Integer> gravities=new ArrayList<Integer>();
        boolean hasClone=false;for(Enemy e:enemies)if(e.alive && e.clone)hasClone=true;
        for(int g:new int[]{-1,1})for(int left:new int[]{0,1060}){
            float y=g<0?40:620;boolean supported=false,source=false;
            for(Box p:platforms)if(p.x<=left && p.x+p.w>=left+220 && Math.abs((g<0?p.y+p.h:p.y)-(g<0?40:680))<.1f)supported=true;
            if(!supported)continue;
            if(!hasClone)for(Enemy e:enemies)if(e.alive && !e.clone && e.gravity==g && (left==0?e.x>=1060:e.x+e.w<=220) && Math.abs(e.y-(g<0?40:636))<4)source=true;
            for(int center=left+32;center<=left+188;center++){
                Box b=new Box(center-32,y,64,60);boolean reachable=source,occupied=false;
                for(Enemy e:enemies)if(e.alive && e.clone){
                    if(b.overlaps(e))occupied=true;
                    if(e.gravity==g && e.x+e.w>left && e.x<left+220 && Math.abs(e.y-(g<0?40:636))<70 && Math.abs(e.x+e.w/2-center)<=160)reachable=true;
                }
                if(!reachable || occupied || (Math.abs(center-previousTargetX)<2 && Math.abs(y-previousTargetY)<2))continue;
                choices.add(b);gravities.add(g);
            }
        }
        if(choices.isEmpty())return;
        targetRandom+=0x9E3779B97F4A7C15L;
        long z=targetRandom;z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;z^=z>>>31;
        int pick=(int)((z&Long.MAX_VALUE)%choices.size());echoTarget=choices.get(pick);targetGravity=gravities.get(pick);targetVersion++;
        for(Enemy e:enemies){e.targetOutside=false;e.bossPushSerial=-1;}
        feedback("TARGET SET");cue("boss_target_set",echoTarget);
    }
    private boolean cloneAtTarget(Enemy e){
        return e.gravity==targetGravity && e.x+e.w/2>=echoTarget.x && e.x+e.w/2<=echoTarget.x+echoTarget.w && Math.abs(e.y-echoTarget.y)<=2;
    }
    private boolean bossCloneAtTarget(Enemy e){
        return e.gravity==targetGravity && e.overlaps(echoTarget) && e.x+e.w/2>=echoTarget.x && e.x+e.w/2<=echoTarget.x+echoTarget.w;
    }
    private void updateEchoTarget(boolean frozen){
        if(echoTarget==null || !targetRevealed || targetSolved)return;
        if(boss!=null){
            if(frozen)return;
            for(Enemy e:enemies)if(e.alive && e.clone && e.bossPushSerial==targetVersion && e.knockback>0 && bossCloneAtTarget(e)){
                targetSolved=true;boss.primed=true;boss.exposure=3f;feedback("EXPOSED");cue("boss_exposed",boss);return;
            }
            return;
        }
        for(Enemy e:enemies){
            if(!e.alive || !e.clone)continue;
            if(!cloneAtTarget(e)){e.targetOutside=true;continue;}
            if(frozen || !e.targetOutside || !e.grounded || Math.abs(e.vy)>.01f)continue;
            boolean support=false;
            for(Box p:platforms)if(e.x+e.w>p.x && e.x<p.x+p.w && Math.abs(e.gravity<0?e.y-p.y-p.h:e.y+e.h-p.y)<.1f)support=true;
            if(!support)continue;
            targetSolved=true;
            doorUnlocked=true;
            return;
        }
    }
    private void updateBoss(){
        if(boss==null || boss.defeated)return;
        boss.timer-=DT;
        if(boss.timer>0)return;
        if(boss.phase==0){boss.phase=1;boss.timer=.8f;boss.beamY=player.y+player.h/2;boss.direction=player.x+player.w/2<boss.x+boss.w/2?-1:1;cue("spell_charge",boss);}
        else if(boss.phase==1){boss.phase=2;boss.timer=.18f;boss.hitPlayer=false;cue("spell_line_hit",boss);}
        else if(boss.phase==2){boss.phase=3;boss.timer=1.2f;}
        else {boss.phase=0;boss.timer=.7f;}
    }
    private void feedback(String value){combatFeedback=value;combatFeedbackTime=.65f;}
    /** Only physical gravity-facing contact, never portal placement alone. */
    private boolean hasContactSupport(){
        if(player.vy*player.gravity<0)return false;
        for(Box p:solids(player))if(player.x+player.w>p.x && player.x<p.x+p.w
                && Math.abs(player.gravity<0?player.y-p.y-p.h:player.y+player.h-p.y)<.01f)return true;
        return false;
    }
    private boolean triggerOriginalSupport(){
        boolean supported=false;
        for(Enemy e:enemies)if(e.alive && !e.clone && standing(e)){
            supported=true;
            if(!e.upper && e.upperRecovery==0){e.upper=true;e.phase=1;e.timer=UPPER_WINDUP;e.stun=0;cue("slime_windup",e);}
        }
        if(supported && stopTime>0){stopTime=0;stopCooldown=STOP_COOLDOWN;cue("time_resume",player);}
        return supported;
    }
    private boolean standing(Enemy e){
        if(player.x+player.w<=e.x || player.x>=e.x+e.w)return false;
        return player.gravity<0?Math.abs(player.y-e.y-e.h)<4 && player.vy<=0:Math.abs(player.y+player.h-e.y)<4 && player.vy>=0;
    }
    private void updateMovement(Enemy e){
        if(!e.alive || !e.grounded || e.knockback>0)return;
        e.vx=0;
        if(e.stun>0 || e.phase!=0)return;
        int direction=e.clone?(player.x+player.w/2<e.x+e.w/2?-1:1):e.patrolDirection;
        float gap=direction>0?player.x-e.x-e.w:e.x-player.x-player.w;
        if(e.clone && gap<=50)return;
        float speed=e.clone?110:70;
        float next=e.x+direction*speed*DT;
        if(!e.clone && ((direction<0 && next<e.spawnX-120) || (direction>0 && next>e.spawnX+120))){e.patrolDirection=-direction;return;}
        boolean supported=false,blocked=!wrapX && (next<0 || next+e.w>worldWidth);
        float foot=e.gravity<0?e.y:e.y+e.h,probe=direction>0?next+e.w:next;
        Box destination=new Box(next,e.y,e.w,e.h);
        for(Box p:platforms){
            if(probe>=p.x && probe<=p.x+p.w && Math.abs(foot-(e.gravity<0?p.y+p.h:p.y))<.1f)supported=true;
            if(destination.overlaps(p))blocked=true;
        }
        if(!supported || blocked){if(!e.clone)e.patrolDirection=-direction;return;}
        e.vx=direction*speed;
    }
    private void updateAttack(Enemy e){
        e.timer-=DT;
        if(e.phase==0 && e.timer<=0 && Math.abs(e.x-player.x)<(e.ranged?900:170) && Math.abs(e.y-player.y)<(e.ranged?35:80)){e.phase=1;e.timer=e.ranged?.8f:.58f;e.direction=player.x<e.x?-1:1;e.upper=false;cue(e.ranged?"spell_charge":"slime_windup",e);}
        else if(e.phase==1 && e.timer<=0){e.phase=2;e.timer=.18f;e.hitPlayer=false;cue(e.upper?"slime_upper":e.ranged?"spell_line_hit":"slime_melee",e);}
        else if(e.phase==2 && e.timer<=0){e.phase=0;e.timer=1.2f;if(e.upper)e.upperRecovery=1.2f;e.upper=false;}
    }
    private void handleEnemyBoundaries(){
        for(Enemy e:new ArrayList<Enemy>(enemies)){
            if(e.clone && !e.alive){enemies.remove(e);cue("clone_disappear",e);continue;}
            if(wrapX && (e.x+e.w<0 || e.x>worldWidth)){
                if(e.clone){enemies.remove(e);cue("clone_disappear",e);continue;}
                boolean left=e.x<0;
                if(!e.alive){
                    e.alive=true;e.h=44;e.x=left?worldWidth-e.w-1:1;resolveHorizontalEntry(e);
                    e.phase=0;e.timer=.9f;e.stun=0;e.upper=false;e.upperRecovery=0;e.hitPlayer=false;
                    cue("resurrect",e);
                }else{
                    for(Enemy c:new ArrayList<Enemy>(enemies))if(c.clone && c.original==e){enemies.remove(c);cue("clone_disappear",c);}
                    Enemy c=new Enemy(left?worldWidth-45:1,e.y,e.gravity);
                    c.clone=true;c.original=e;c.ranged=e.ranged;c.vx=e.vx;c.vy=e.vy;c.knockback=e.knockback;c.timer=.9f;
                    resolveHorizontalEntry(c);enemies.add(c);cue("clone_spawn",c);
                    e.x=e.spawnX;e.y=e.spawnY;e.gravity=e.spawnGravity;e.vx=0;e.vy=0;e.knockback=0;
                    e.phase=0;e.timer=.9f;e.upper=false;e.stun=0;e.upperRecovery=0;e.hitPlayer=false;
                }
                continue;
            }
            if(!wrapY || (e.y+e.h>=0 && e.y<=worldHeight))continue;
            if(e.clone){enemies.remove(e);cue("clone_disappear",e);continue;}
            boolean bottom=e.y<0;int g=bottom?1:-1;float entry=bottom?worldHeight-e.h-1:1;
            if(!e.alive){e.alive=true;e.h=44;e.y=bottom?worldHeight-e.h-1:1;e.gravity=g;e.phase=0;e.timer=.9f;e.stun=0;e.upper=false;e.upperRecovery=0;e.hitPlayer=false;resolveEntry(e);cue("resurrect",e);}
            else {
                for(Enemy c:new ArrayList<Enemy>(enemies))if(c.clone && c.original==e){enemies.remove(c);cue("clone_disappear",c);}
                Enemy c=new Enemy(e.x,entry);c.clone=true;c.ranged=e.ranged;c.original=e;c.gravity=g;c.vx=e.vx;c.vy=e.vy;c.knockback=e.knockback;c.timer=.9f;resolveEntry(c);enemies.add(c);cue("clone_spawn",c);
                e.x=e.spawnX;e.y=e.spawnY;e.gravity=e.spawnGravity;e.vx=0;e.vy=0;e.knockback=0;e.phase=0;e.timer=.9f;e.upper=false;e.stun=0;e.upperRecovery=0;e.hitPlayer=false;
            }
        }
    }
    /** Locked instantaneous horizontal line; it never wraps or tracks during charge. */
    public Box bossAttackBox(){
        if(boss==null)return new Box(0,0,0,0);
        float start=boss.direction<0?boss.x:boss.x+boss.w,end=boss.direction<0?0:worldWidth,y=boss.beamY-6;
        for(Box p:platforms)if(p.y<y+12 && p.y+p.h>y){
            if(boss.direction>0 && p.x>=start)end=Math.min(end,p.x);
            if(boss.direction<0 && p.x+p.w<=start)end=Math.max(end,p.x+p.w);
        }
        return new Box(Math.min(start,end),y,Math.abs(end-start),12);
    }
    public Box attackBox(Enemy e){
        if(e.upper)return new Box(e.x-24,e.y-55,92,154);
        if(!e.ranged)return new Box(e.direction<0?e.x-70:e.x+e.w,e.y+5,70,e.h-10);
        float start=e.direction<0?e.x:e.x+e.w, end=Math.max(0,Math.min(worldWidth,start+e.direction*900));
        float y=e.y+e.h/2-5;
        for(Box p:platforms)if(p.y<y+10 && p.y+p.h>y){
            if(e.direction>0 && p.x>=start)end=Math.min(end,p.x);
            if(e.direction<0 && p.x+p.w<=start)end=Math.max(end,p.x+p.w);
        }
        return new Box(Math.min(start,end),y,Math.abs(end-start),10);
    }
    /** Placement repair only: never pushes enemies or treats a portal as a world sweep. */
    private void separatePlayer(){
        float best=Float.POSITIVE_INFINITY,bestX=player.x,bestY=player.y;
        List<Box> obstacles=solids(player);
        for(Enemy e:enemies)if(player.overlaps(e)){
            float[][] candidates={{e.x-player.w,player.y},{e.x+e.w,player.y},{player.x,e.y-player.h},{player.x,e.y+e.h}};
            for(float[] c:candidates){
                Box target=new Box(c[0],c[1],player.w,player.h);
                boolean valid=(wrapX || (target.x>=0 && target.x+target.w<=worldWidth)) && target.y>=0 && target.y+target.h<=worldHeight;
                for(Box obstacle:obstacles)if(target.overlaps(obstacle))valid=false;
                float distance=Math.abs(c[0]-player.x)+Math.abs(c[1]-player.y);
                if(valid && distance<best){best=distance;bestX=c[0];bestY=c[1];}
            }
        }
        player.x=bestX;player.y=bestY;
    }
    private List<Box> solids(Body b){
        List<Box> result=new ArrayList<Box>(platforms);
        if(b==player)result.addAll(enemies);else if(!standing((Enemy)b))result.add(player);
        return result;
    }
    private void moveX(Body b,float dx){
        float oldX=b.x;b.x+=dx;
        for(Box p:solids(b))if(b.overlaps(p)){
            if(dx>0 && oldX+b.w<=p.x+.001f)b.x=p.x-b.w;
            else if(dx<0 && oldX>=p.x+p.w-.001f)b.x=p.x+p.w;
        }
        if(!wrapX)b.x=Math.max(0,Math.min(worldWidth-b.w,b.x));
    }
    private void moveY(Body b,float dy){
        float oldY=b.y;b.y+=dy;
        for(Box p:solids(b))if(b.overlaps(p)){
            // Only the face crossed on this axis can stop vertical movement.
            if(dy<0 && oldY>=p.y+p.h){b.y=p.y+p.h;b.grounded=b.gravity<0;b.vy=0;}
            else if(dy>0 && oldY+b.h<=p.y){b.y=p.y-b.h;b.grounded=b.gravity>0;b.vy=0;}
        }
    }
    private boolean physics(Body b){
        moveX(b,b.vx*DT);
        b.vy=Math.max(-850,Math.min(850,b.vy+b.gravity*GRAVITY*DT));
        b.grounded=false;moveY(b,b.vy*DT);
        if(!wrapY){
            if(b.y<0){b.y=0;b.vy=0;b.grounded=b.gravity<0;}
            if(b.y+b.h>worldHeight){b.y=worldHeight-b.h;b.vy=0;b.grounded=b.gravity>0;}
        }
        if(b instanceof Enemy)return false;
        if(wrapX && (b.x+b.w<0 || b.x>worldWidth)){
            b.x=b.x<0?worldWidth-b.w-1:1;resolveHorizontalEntry(b);return true;
        }
        if(!wrapY)return false;
        if(b.y+b.h<0){b.y=worldHeight-b.h-1;b.gravity=1;resolveEntry(b);return true;}
        else if(b.y>worldHeight){b.y=1;b.gravity=-1;resolveEntry(b);return true;}
        return false;
    }
    /** X portal placement must not leave a body below its destination support face.
     * Preserve velocity and defer grounded/counter reset to the next real collision. */
    private void resolveHorizontalEntry(Body b){
        b.grounded=false;
        for(Box p:platforms)if(b.overlaps(p))b.y=b.gravity>0?p.y-b.h:p.y+p.h;
    }
    private void resolveEntry(Body b){
        b.grounded=false;
        for(Box p:platforms)if(b.overlaps(p)){
            b.y=b.gravity>0?p.y-b.h:p.y+p.h;
            // Re-entry is placement, not a collision: inward motion must retain its energy.
            b.grounded=b.vy*b.gravity>=0;
            if(b.grounded)b.vy=0;
        }
        if(b==player)separatePlayer();
    }
}
