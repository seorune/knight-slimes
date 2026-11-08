package io.jbnu.test;

/** Target regressions are fixtures, deliberately separate from input-only route proof. */
public final class EchoTargetTest {
    static void check(boolean b,String s){StageModelTest.check(b,s);}
    public static void main(String[] args) throws Exception {
        StageModel m=new StageModel();
        java.lang.reflect.Field goal;
        try {goal=StageModel.class.getField("echoTarget");}
        catch(NoSuchFieldException e){throw new AssertionError("new stages must start with a public pending echo target contract",e);}
        check(goal.get(m)==null,"initial target absent");
        StageModel.Input l=new StageModel.Input();l.start=true;m.step(l);l.start=false;l.stop=true;m.step(l);
        check(goal.get(m)!=null,"accepted L selects target");
        revealAndDelivery();
        cancellationAndGates();rejections();randomAndRestart();bossCycles();upperSource();unoccupiedCandidate();hints();airborneChoice();
        System.out.println("ECHO_TARGET_OK checks="+StageModelTest.passed);
    }
    static void airborneChoice(){
        for(int stage=0;stage<3;stage++)for(int g:new int[]{-1,1}){
            if(stage<2 && g==1)continue;
            StageModel m=new StageModel();m.loadStage(stage);
            StageModel.Enemy c=new StageModel.Enemy(stage==0?1600:stage==1?1200:g<0?1200:40,stage==0?130:stage==1?530:g<0?100:576,g);c.clone=true;m.enemies.add(c);
            m.step(stop());check(stage<2?m.echoTarget==null:m.echoTarget!=null && m.targetGravity==g,"door marks require supported clones; boss marks allow nearby airborne receivers stage="+stage+" g="+g);
        }
    }
    static void hints(){
        for(int stage=0;stage<3;stage++){
            StageModel m=new StageModel();m.loadStage(stage);m.step(stop());m.stopTime=0;m.step(new StageModel.Input());
            check(stage==2?m.objective.contains("J-push") && m.objective.contains("JUST"):m.objective.contains("memorized mark"),"normal HUD explains stage-specific delivery stage "+stage);
            check(!m.objective.contains("Stand on a living clone during"),"obsolete frozen-standing boss instruction removed");
        }
        check(StageOneLevel.objective(1400).contains("L") && StageTwoLevel.objective(440).contains("L"),"fair reveal hint before irreversible climb");
    }
    static void unoccupiedCandidate(){
        java.util.Set<Float> seen=new java.util.HashSet<Float>();
        for(long seed=0;seed<48;seed++){
            StageModel m=new StageModel(seed);m.loadStage(2);
            StageModel.Enemy c=new StageModel.Enemy(1185,40);c.clone=true;m.enemies.add(c);m.step(stop());
            seen.add(m.echoTarget.x+m.echoTarget.w/2);
            check(!m.echoTarget.overlaps(new StageModel.Box(c.x+c.w/2-.001f,c.y+1,.002f,1)),"existing center excluded from new goal");
        }
        check(seen.size()>6,"boss selects continuous reachable unoccupied marks, not six legacy points");
    }
    static void upperSource(){
        StageModel m=new StageModel();m.loadStage(2);
        check(m.enemies.size()==2 && m.enemies.get(0).spawnX==15 && m.enemies.get(1).spawnX==1220 && m.enemies.get(1).gravity==1,"boss retains lower helper and supplies inverted upper original");
    }
    static void cancellationAndGates(){
        StageModel m=new StageModel();m.loadStage(2);m.player.x=20;m.player.y=84;m.player.grounded=true;
        m.step(stop());check(m.stopTime==0 && !m.targetRevealed,"original support cancels L before visible frozen state");
        check(m.echoTarget==null,"cancelled boss freeze cannot preselect a target");m.player.x=500;m.player.y=300;m.stopCooldown=0;m.step(stop());
        check(m.echoTarget!=null && m.targetRevealed,"later surviving boss freeze selects and reveals a target");
        m=new StageModel();m.loadStage(0);m.stopCooldown=1;m.step(stop());check(m.echoTarget==null && !m.targetRevealed,"cooldown-rejected L never selects or reveals");
        m.paused=true;m.stopCooldown=0;m.step(stop());check(m.echoTarget==null,"pause cannot reveal");
        for(int stage=0;stage<2;stage++){
            m=new StageModel();m.loadStage(stage);m.player.x=m.exit.x;m.player.y=m.exit.y;m.step(new StageModel.Input());
            check(!m.won && !StageModelTest.has(m,"exit_open"),"locked exit cannot clear or announce stage "+stage);
        }
    }
    static void rejections(){
        for(int kind=0;kind<6;kind++){
            StageModel m=new StageModel();m.loadStage(0);m.step(stop());StageModel.Box b=m.echoTarget;
            StageModel.Enemy c=new StageModel.Enemy(b.x-80,b.y);c.clone=true;c.stun=100;m.enemies.add(c);m.step(new StageModel.Input());
            m.stopTime=0;c.x=b.x+10;
            if(kind==0)c.clone=false;if(kind==1)c.alive=false;
            if(kind==2)c.gravity=1;if(kind==3){c.y+=20;c.vy=100;}
            if(kind==4)m.targetRevealed=false;if(kind==5)m.platforms.clear();
            m.step(new StageModel.Input());check(!m.targetSolved && !m.doorUnlocked,"invalid delivery rejected kind="+kind);
        }
        StageModel m=new StageModel();m.loadStage(2);StageModel.Enemy c=new StageModel.Enemy(1200,40);c.clone=true;m.enemies.add(c);
        m.player.x=1205;m.player.y=84;m.step(stop());check(!m.boss.primed,"old frozen-clone standing never primes boss");
        float x=c.x;StageModel.Input j=new StageModel.Input();j.melee=true;m.step(j);StageModelTest.steps(m,12);check(c.x==x && c.vx==0,"frozen J cannot push clone");
    }
    static void randomAndRestart(){
        for(int stage=0;stage<3;stage++){
            java.util.Set<String> candidates=new java.util.HashSet<String>();
            for(long seed=0;seed<48;seed++){
                StageModel m=new StageModel(seed);m.loadStage(stage);m.step(stop());
                check(m.echoTarget!=null,"seed has supported candidate");
                StageModel.Box b=m.echoTarget;candidates.add(b.x+":"+b.y);
                if(stage==2)check(b.w==64 && b.h==60,"boss marks use continuous 64x60 target bounds");
                boolean support=false;for(StageModel.Box p:m.platforms)if(b.x+b.w/2-22>=p.x && b.x+b.w/2+22<=p.x+p.w && Math.abs(m.targetGravity<0?b.y-p.y-p.h:b.y+b.h-p.y)<.01f)support=true;
                check(support,"all random goals on real support");
                m.restart();m.step(stop());check(m.echoTarget.x==b.x && m.echoTarget.y==b.y,"restart reseeds deterministic stage target");
                StageModel n=new StageModel(seed);n.loadStage(stage);n.step(stop());check(StageModelTest.sameState(m,n),"scalar PRNG included in deterministic replay");
            }
            check(stage==2?candidates.size()>6:candidates.size()==3,"seed coverage preserves three door marks and continuous boss marks stage "+stage);
        }
    }
    static void bossCycles(){
        StageModel m=new StageModel();m.loadStage(2);m.player.x=1150;m.player.y=40;
        StageModel.Enemy c=new StageModel.Enemy(1230,40);c.clone=true;c.stun=100;m.enemies.clear();m.enemies.add(c);
        for(int cycle=0;cycle<3;cycle++){
            m.stopTime=0;m.stopCooldown=0;m.step(stop());StageModel.Box b=m.echoTarget;
            check(b!=null && m.targetGravity==-1 && !m.boss.primed,"fresh target requires new same-floor placement");
            m.stopTime=0;pushIntoBossTarget(m,c);
            check(m.targetSolved && m.boss.primed,"new placement primes exactly once");
            m.boss.phase=2;m.boss.timer=.18f;m.boss.direction=1;m.boss.beamY=m.player.y+22;m.parryCooldown=0;
            StageModel.Input k=new StageModel.Input();k.parry=true;m.step(k);
            check(m.boss.seals==2-cycle && m.echoTarget==null && !m.targetSolved && !m.targetRevealed,"seal consumes goal and charge");
        }
        check(m.won && m.boss.defeated && !m.dead,"three separate target cycles defeat boss");
    }
    /** Position-only fixture: eligibility comes exclusively from a real post-mark J edge. */
    static void pushIntoBossTarget(StageModel m,StageModel.Enemy c){
        StageModel.Box b=m.echoTarget;
        c.x=b.x-48;c.y=m.targetGravity<0?40:636;c.gravity=m.targetGravity;
        c.vx=0;c.vy=0;c.grounded=true;c.stun=100;
        m.player.x=c.x-48;m.player.y=c.y;m.player.gravity=c.gravity;m.player.grounded=true;
        m.stopTime=0;m.attackCooldown=0;
        StageModel.Input j=new StageModel.Input();j.melee=true;j.move=1;j.guard=true;m.step(j);
        boolean pushed=StageModelTest.has(m,"melee_push");
        for(int n=0;n<30 && !m.targetSolved;n++){
            StageModel.Input i=new StageModel.Input();i.guard=true;m.step(i);
            pushed|=StageModelTest.has(m,"melee_push");
        }
        check(pushed && m.targetSolved && m.boss.primed,"actual thawed J impulse crosses marked target");
    }
    static StageModel.Input stop(){StageModel.Input i=new StageModel.Input();i.stop=true;return i;}
    static void revealAndDelivery(){
        for(int stage=0;stage<3;stage++){
            StageModel m=new StageModel();m.loadStage(stage);
            m.step(stop());
            check(m.targetRevealed,"actual accepted freeze reveals stage "+stage);
            StageModel.Box target=m.echoTarget;int version=m.targetVersion;
            m.step(stop());check(m.echoTarget==target && m.targetVersion==version,"pending target not rerolled by held L");
            StageModel.Enemy c=new StageModel.Enemy(target.x-70,target.y,m.targetGravity);c.clone=true;c.stun=100;m.enemies.add(c);
            m.step(new StageModel.Input());
            c.x=target.x+10;c.grounded=true;m.step(new StageModel.Input());
            check(!m.targetSolved && !m.doorUnlocked && (m.boss==null || !m.boss.primed),"freeze cannot complete placement");
            m.stopTime=0;m.step(new StageModel.Input());
            if(stage==2){check(!m.targetSolved && !m.boss.primed,"unforced post-thaw overlap cannot prime boss");pushIntoBossTarget(m,c);}
            check(m.targetSolved && (stage==2?m.boss.primed && m.boss.exposure>0:m.doorUnlocked),"thawed supported clone delivery solves stage "+stage);
            c.x=target.x-80;m.step(new StageModel.Input());check(m.targetSolved,"completion latched after clone leaves");
            m.restart();check(m.echoTarget==null && !m.targetRevealed && !m.targetSolved && !m.doorUnlocked,"restart clears target and gate");
        }
    }
}
