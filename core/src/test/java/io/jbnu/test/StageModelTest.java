package io.jbnu.test;

/** Dependency-free deterministic tests; run with javac/java, no graphics needed. */
public final class StageModelTest {
    static int passed;
    static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); passed++; }
    static StageModel empty() { StageModel m = new StageModel(); m.platforms.clear(); m.enemies.clear(); m.started=true; return m; }
    static void steps(StageModel m,int count) { for(int i=0;i<count;i++) m.step(new StageModel.Input()); }
    public static void main(String[] args) {
        if(args.length>0 && args[0].equals("boss-targets")){bossTargets();System.out.println("PASS boss targets "+passed);return;}
        if(args.length>0 && args[0].equals("boss-delivery")){bossMeleeDelivery();System.out.println("PASS boss delivery");return;}
        if(args.length>0 && args[0].equals("boss-proof")){bossStrikeProof();System.out.println("PASS boss proof");return;}
        StageModel m=empty(); m.player.y=-50; m.player.vy=-200; m.step(new StageModel.Input());
        check(m.player.gravity==1 && m.player.y>600 && Math.abs(m.player.vy-(-200-StageModel.GRAVITY*StageModel.DT))<.01f,"bottom wraps to top preserving signed velocity");
        m.player.y=721; m.player.vy=200; m.step(new StageModel.Input());
        check(m.player.gravity==-1 && m.player.y<50 && Math.abs(m.player.vy-(200+StageModel.GRAVITY*StageModel.DT))<.01f,"top wraps to bottom preserving signed velocity");
        m=empty();m.platforms.add(new StageModel.Box(0,680,400,40));m.player.x=100;m.player.y=-50;m.player.vy=-200;
        StageModel.Input moving=new StageModel.Input();moving.move=1;m.step(moving);m.step(moving);
        check(m.player.x>100 && !m.player.grounded && m.player.gravity==1 && m.player.vy<0,"boundary entry resolves ceiling without absorbing inward momentum");
        m=empty(); m.platforms.add(new StageModel.Box(0,0,400,40)); m.player.x=80;m.player.y=90;steps(m,60);
        check(m.player.grounded && Math.abs(m.player.y-40)<.01,"normal gravity landing");
        StageModel.Input in=new StageModel.Input(); in.jump=true; m.step(in);check(m.player.vy>0,"jump opposite gravity");
        m=empty();m.platforms.add(new StageModel.Box(0,680,400,40));m.player.gravity=1;m.player.y=600;steps(m,60);
        check(m.player.grounded && Math.abs(m.player.y-636)<.01,"inverted ceiling landing");
        in=new StageModel.Input();in.jump=true;m.step(in);check(m.player.vy<0,"inverted jump");
        boundarySupportRegression();carriedSideCollisionRegression();carriedCeilingCollisionRegression();
        meleeWindup();meleeClocks();heldGuardLate();heldGuardRegressions();boundaryDeath();boundaryRegressions();
        rangedContract(); rangedBeam(); rangedRules(); eventContract(); eventBehavior(); assetRegressions(); flatBrickRegressions(); aiLocks(); movingBodyRegressions(); aiObstacles(); chase(); patrol(); solidBodies(); combat(); woundRevision(); parryProtection(); momentumRevision(); lifecycle(); clocksAndRiding(); upperRevision(); redesignedMap(); level();
        bossVisibleArrival();bossEligibilityRegressions();bossMarkContracts();bossTargets();bossMeleeDelivery();bossStrikeProof();bossFeedbackClarity();
        campaignRestart();horizontalEntryRegression();
        bossLatchedExposure();stageThreeConfiguration();bossAttackClock();bossBeamRules();bossParrySeals();bossRegressions();stageThreePortals();stageTwoConfiguration();stageTwoPortals();stageTwoRegressions();stageTwoRoute();
        System.out.println("PASS "+passed+" checks");
    }
    static StageModel.Input newCampaignInput(){
        StageModel.Input n=new StageModel.Input();
        n.campaignRestart=true;
        return n;
    }
    static void campaignRestart(){
        StageModel m=new StageModel(27);m.loadStage(2);m.won=true;
        m.step(newCampaignInput());
        check(m.stageIndex==0 && m.started && !m.won,"N after final victory restarts the whole campaign at stage one");
        StageModel fresh=new StageModel(27);StageModel.Input start=new StageModel.Input();start.start=true;fresh.step(start);
        check(EchoRouteTest.stateJson(m).equals(EchoRouteTest.stateJson(fresh)),"new campaign restores exact fresh seeded stage-one state");
        check(m.events.size()==1 && has(m,"ui_confirm"),"new campaign emits only one confirmation");
        for(int stage=0;stage<3;stage++)for(int mode=0;mode<5;mode++){
            if(stage==2 && mode==4)continue;
            StageModel a=new StageModel(27),b=new StageModel(27);
            if(mode!=0){a.loadStage(stage);b.loadStage(stage);}
            else if(stage!=0)continue;
            a.paused=b.paused=mode==2;a.dead=b.dead=mode==3;a.won=b.won=mode==4;
            a.step(newCampaignInput());b.step(new StageModel.Input());
            check(EchoRouteTest.stateJson(a).equals(EchoRouteTest.stateJson(b)) && a.events.size()==b.events.size(),"N ignored outside final victory stage="+stage+" mode="+mode);
        }
        m=new StageModel(27);m.loadStage(2);m.won=true;
        StageModel.Input enter=new StageModel.Input();enter.start=true;m.step(enter);
        check(m.stageIndex==2 && m.won && m.events.isEmpty(),"final victory Enter remains inert");
        StageModel.Input retry=new StageModel.Input();retry.restart=true;m.step(retry);
        fresh.loadStage(2);
        check(has(m,"ui_confirm") && m.events.size()==1,"R retry confirms once");m.events.clear();
        check(EchoRouteTest.stateJson(m).equals(EchoRouteTest.stateJson(fresh)),"final victory R still retries only current stage");
    }
    static void bossVisibleArrival(){

        for(int g:new int[]{-1,1}){
            StageModel m=emptyThree();m.boss.timer=100;
            m.platforms.add(new StageModel.Box(0,g<0?0:680,1280,40));
            m.player.gravity=g;m.player.x=1030;m.player.y=g<0?40:636;
            m.echoTarget=new StageModel.Box(1080,g<0?40:620,64,60);m.targetGravity=g;m.targetVersion=1;m.targetRevealed=true;
            StageModel.Enemy c=new StageModel.Enemy(1090,m.player.y,g);c.clone=true;c.stun=100;m.enemies.add(c);
            m.step(new StageModel.Input());
            check(c.overlaps(m.echoTarget) && !m.boss.primed,"unstruck clone inside mark must remain uncharged "+g);
            StageModel.Input j=new StageModel.Input();j.melee=true;m.step(j);steps(m,10);
            check(has(m,"boss_exposed") || m.boss.primed,"real J against clone already inside visible mark must expose boss "+g);
            check(m.targetSolved && m.boss.seals==3,"visible arrival primes only and does not destroy a seal "+g);
        }
    }
    static void bossStrikeProof(){
        StageModel m=bossArena();m.boss.phase=0;m.boss.timer=100;
        driftByPlacement(m);
        check(!m.boss.primed,"boss target rejects unstruck AI drift or manual placement after thaw");
        m=bossArena();m.player.x=1250;m.boss.phase=0;m.boss.timer=100;
        m.echoTarget=new StageModel.Box(1170,40,64,60);m.targetGravity=-1;m.targetVersion=1;m.targetRevealed=true;
        StageModel.Enemy c=new StageModel.Enemy(1100,40);c.clone=true;m.enemies.add(c);
        boolean entered=false;StageModel.Input held=new StageModel.Input();held.guard=true;
        for(int n=0;n<800;n++){m.step(held);if(c.x+22>=1170 && c.x+22<=1234)entered=true;}
        check(entered && !m.boss.primed && !m.targetSolved,"actual unfrozen AI chase through marked zone never substitutes for post-mark J");
    }
    static void bossEligibilityRegressions(){
        for(int kind=0;kind<5;kind++){
            StageModel m=bossArena();m.boss.phase=0;m.boss.timer=100;
            m.echoTarget=new StageModel.Box(1080,40,64,60);m.targetVersion=1;m.targetGravity=-1;m.targetRevealed=true;
            StageModel.Enemy e=new StageModel.Enemy(1030,40);e.clone=kind!=0;e.alive=kind!=1;e.gravity=kind==2?1:-1;e.stun=100;m.enemies.add(e);m.player.x=980;
            StageModel.Input j=new StageModel.Input();j.melee=true;
            if(kind==3){m.echoTarget=null;m.step(j);steps(m,9);m.echoTarget=new StageModel.Box(1080,40,64,60);}
            else if(kind==4){m.stopTime=1;m.step(j);}else m.step(j);
            steps(m,30);
            check(!m.boss.primed && m.boss.seals==3,"boss excludes original corpse wrong-gravity pre-mark and frozen-J delivery kind="+kind);
        }
        StageModel upper=emptyThree();upper.platforms.add(new StageModel.Box(0,680,1280,40));upper.player.gravity=1;upper.player.x=1120;upper.player.y=636;upper.boss.timer=100;
        upper.echoTarget=new StageModel.Box(1180,620,64,60);upper.targetVersion=1;upper.targetGravity=1;upper.targetRevealed=true;
        StageModel.Enemy c=new StageModel.Enemy(1170,636,1);c.clone=true;c.stun=100;upper.enemies.add(c);
        // Start outside the box: a rightwards ordinary strike must enter its center tolerance.
        upper.echoTarget.x=1200;StageModel.Input j=new StageModel.Input();j.melee=true;upper.step(j);steps(upper,20);
        check(upper.boss.primed && upper.boss.seals==3,"inverted clone ordinary J flight completes matching ceiling target");
        StageModel m=bossArena();m.boss.phase=0;m.boss.timer=100;primeByDelivery(m);
        StageModel.Box old=m.echoTarget;int serial=m.targetVersion;
        m.paused=true;float x=m.enemies.get(0).x;steps(m,30);
        check(m.echoTarget==old && m.targetVersion==serial && m.enemies.get(0).x==x && m.boss.primed,"pause preserves marked wave and latched exposure");
        m.paused=false;m.boss.phase=2;m.boss.timer=.18f;m.boss.beamY=m.player.y+22;StageModel.Input k=new StageModel.Input();k.guard=k.parry=true;m.step(k);
        check(m.boss.seals==2 && m.echoTarget==null && !m.targetSolved && !m.targetRevealed,"one just clears target and consumes exactly one seal");
        m.step(new StageModel.Input());StageModel.Input l=new StageModel.Input();l.stop=true;m.step(l);
        check(m.echoTarget!=null && m.echoTarget!=old && m.targetVersion==serial+1 && !m.boss.primed,"next freeze creates a fresh serial and cannot inherit previous J proof");
        m.restart();check(m.echoTarget==null && m.targetVersion==0 && m.boss.seals==3 && m.enemies.size()==2,"restart restores two originals and clears all target-wave state");
        StageModel.Enemy u=m.enemies.get(1);float oldX=u.x;steps(m,10);
        check(u.alive && !u.clone && u.gravity==1 && u.x!=oldX && u.grounded,"upper inverted original retains physical ceiling patrol");
    }
    static void bossMarkContracts(){
        StageModel m=new StageModel();m.loadStage(2);StageModel.Enemy source=m.enemies.get(0);
        m.player.x=source.x+4;m.player.y=source.y+source.h;StageModel.Input l=new StageModel.Input();l.stop=true;m.step(l);
        check(m.stopTime==0 && m.echoTarget==null && !has(m,"boss_target_set"),"original-support-cancelled freeze never generates a boss mark");
        check(m.objective.contains("J") && m.objective.contains("L"),"boss objective explicitly teaches freeze then normal J then just");
    }
    static void bossTargets(){
        java.util.Set<Float> positions=new java.util.HashSet<Float>();
        for(int seed=0;seed<32;seed++){
            StageModel a=new StageModel(seed),b=new StageModel(seed);a.loadStage(2);b.loadStage(2);
            StageModel.Input l=new StageModel.Input();l.stop=true;a.step(l);b.step(l);
            check(a.echoTarget!=null && a.echoTarget.w==64 && a.echoTarget.h==60 && has(a,"boss_target_set"),"boss freeze chooses visible supported 64x60 target seed="+seed);
            check(sameState(a,b),"boss target deterministic seed="+seed);
            positions.add(a.echoTarget.x);
            StageModel.Box t=a.echoTarget;int serial=a.targetVersion;
            steps(a,365);a.step(l);
            check(a.echoTarget==t && a.targetVersion==serial && !a.boss.primed,"pending boss target survives thaw and repeated L seed="+seed);
            StageModel.Input hold=new StageModel.Input();hold.guard=true;for(int n=0;n<600;n++)a.step(hold);a.step(l);
            check(has(a,"time_stop") && !has(a,"boss_target_set") && a.echoTarget==t && a.targetVersion==serial,"accepted later L cannot reroll pending target seed="+seed);
            a.restart();b.restart();a.step(l);b.step(l);check(sameState(a,b),"restart rewinds target PRNG and full wave state seed="+seed);
        }
        check(positions.size()>12,"boss randomized mark varies within ledges rather than fixed handful of points");
        for(int g:new int[]{-1,1})for(float cx:new float[]{1090,1140,1200,1230}){
            StageModel m=new StageModel(7);m.loadStage(2);StageModel.Enemy c=new StageModel.Enemy(cx,g<0?40:636,g);c.clone=true;m.enemies.add(c);
            StageModel.Input l=new StageModel.Input();l.stop=true;m.step(l);
            check(m.echoTarget!=null && m.targetGravity==g && !m.echoTarget.overlaps(c),"target prefers living clone support and excludes initial overlap "+g+"/"+cx);
            check(m.echoTarget.x>=1060 && m.echoTarget.x+m.echoTarget.w<=1280 && Math.abs(m.echoTarget.x+32-cx-22)<=160,"target lies on same reachable ledge within normal J displacement");
        }
    }
    static void bossMeleeDelivery(){
        StageModel m=bossArena();m.boss.phase=0;m.boss.timer=100;
        m.echoTarget=new StageModel.Box(1080,40,64,60);m.targetGravity=-1;m.targetVersion=1;m.targetRevealed=true;
        StageModel.Enemy c=new StageModel.Enemy(1030,40);c.clone=true;c.stun=100;m.enemies.add(c);m.player.x=980;
        m.stopTime=.1f;StageModel.Input j=new StageModel.Input();j.melee=true;m.step(j);steps(m,13);
        check(!m.boss.primed && c.vx==0,"J consumed in frozen windup cannot mark clone for later completion");
        steps(m,40);m.step(j);
        boolean airborne=false;
        for(int n=0;n<30 && !m.boss.primed;n++){m.step(new StageModel.Input());if(m.boss.primed)airborne=!c.grounded;}
        check(m.boss.primed && airborne && m.boss.seals==3,"post-mark unfrozen ordinary J can expose boss while clone flies through zone without damage");
    }
    static void bossFeedbackClarity(){
        StageModel.Input k=new StageModel.Input();k.guard=true;k.parry=true;
        StageModel m=bossArena();m.step(k);
        check(m.boss.seals==3 && m.combatFeedback.equals("SEALED"),"keyboard just on protected boss clearly reports no seal damage");
        m=bossArena();m.boss.primed=true;m.boss.exposure=3;m.step(k);
        check(m.boss.seals==2 && m.combatFeedback.equals("SEAL BROKEN"),"keyboard just on exposed boss clearly reports seal destruction");
        m=bossArena();m.boss.primed=true;m.boss.exposure=3;m.parryCooldown=1;m.step(k);
        check(m.boss.seals==3 && m.boss.primed && m.combatFeedback.equals("BLOCKED"),"ordinary held block retains exposure but reports no damage");
        m=bossArena();m.boss.primed=true;m.boss.exposure=3;m.boss.seals=1;m.step(k);
        check(m.won && m.boss.defeated && m.combatFeedback.equals("BOSS DEFEATED"),"last keyboard just reports actual boss death");
    }
    static void horizontalEntryRegression(){
        for(int g:new int[]{-1,1})for(int direction:new int[]{-1,1}){
            StageModel m=emptyThree();m.boss.timer=100;
            for(int x:new int[]{0,1060}){m.platforms.add(new StageModel.Box(x,0,220,40));m.platforms.add(new StageModel.Box(x,680,220,40));}
            m.player.gravity=g;m.player.x=direction<0?-31:1281;m.player.y=g<0?40:636;m.player.vy=0;
            StageModel.Input i=new StageModel.Input();i.move=direction;m.step(i);
            for(StageModel.Box p:m.platforms)check(!m.player.overlaps(p),"horizontal entry cannot embed player in receiving floor/ceiling "+g+direction);
            check(m.boundaryChain==1 && !m.player.grounded && m.player.gravity==g && Math.abs(m.player.vy-g*StageModel.GRAVITY*StageModel.DT)<.001f,"X placement preserves momentum and is not a landing "+g+direction);
            m.step(new StageModel.Input());
            check(m.player.grounded && m.boundaryChain==0 && !m.dead && m.player.y==(g<0?40:636),"next physical support tick lands after X entry "+g+direction);
        }
        for(int g:new int[]{-1,1})for(int direction:new int[]{-1,1})for(boolean corpse:new boolean[]{false,true}){
            StageModel m=emptyThree();m.boss.timer=100;
            for(int x:new int[]{0,1060}){m.platforms.add(new StageModel.Box(x,0,220,40));m.platforms.add(new StageModel.Box(x,680,220,40));}
            StageModel.Enemy e=new StageModel.Enemy(300,300,g);e.alive=!corpse;e.h=corpse?22:44;e.x=direction<0?-45:1281;e.y=g<0?40:680-e.h;e.vx=direction*180;m.enemies.add(e);
            m.step(new StageModel.Input());StageModel.Enemy receiver=corpse?e:m.enemies.get(1);
            for(StageModel.Box p:m.platforms)check(!receiver.overlaps(p),"X clone/resurrection cannot embed in receiving support "+g+direction+corpse);
            check(!receiver.grounded && receiver.gravity==g && Math.abs(receiver.vy-g*StageModel.GRAVITY*StageModel.DT)<.001f,"X actor placement retains signed vertical speed "+g+direction+corpse);
            m.step(new StageModel.Input());check(receiver.grounded && receiver.y==(g<0?40:636),"X actor lands on real next support tick "+g+direction+corpse);
        }
    }
    static void stageThreeConfiguration(){
        StageModel m=new StageModel();m.loadStage(2);
        check(m.worldWidth==1280 && m.worldHeight==720 && m.wrapX && m.wrapY && !m.hasNextStage(),"stage three single screen both portals");
        check(m.boss!=null && m.boss.seals==3,"stage three creates separate three-seal boss");
    }
    static StageModel emptyThree(){StageModel m=new StageModel();m.loadStage(2);m.platforms.clear();m.enemies.clear();m.player.x=900;m.player.y=200;return m;}
    static void stageThreePortals(){
        for(boolean horizontal:new boolean[]{false,true})for(int direction:new int[]{-1,1})for(boolean corpse:new boolean[]{false,true}){
            StageModel m=emptyThree();StageModel.Enemy e=new StageModel.Enemy(300,300);m.enemies.add(e);
            e.alive=!corpse;e.h=corpse?22:44;e.x=horizontal?(direction<0?-45:1281):300;e.y=horizontal?200:(direction<0?-45:721);e.vx=100;e.vy=direction*180;
            m.step(new StageModel.Input());
            check(corpse?e.alive:m.enemies.size()==2,"both-axis enemy portal lifecycle "+horizontal+direction+corpse);
            StageModel.Enemy c=corpse?e:m.enemies.get(1);
            check(c.gravity==(horizontal?-1:direction<0?1:-1) && Math.abs(c.vx-98.5f)<.01f && Math.abs(c.vy-(direction*180-StageModel.GRAVITY*StageModel.DT))<.01f,"both-axis lifecycle preserves signed momentum");
            if(!corpse){c.x=horizontal?(direction<0?-45:1281):300;c.y=horizontal?200:(direction<0?-45:721);m.step(new StageModel.Input());check(!m.enemies.contains(c),"clone disposes on either portal axis");}
        }
        StageModel m=emptyThree();m.player.x=-31;m.player.y=-50;StageModel.Input i=new StageModel.Input();i.move=-1;m.step(i);
        check(m.boundaryChain==1 && m.player.gravity==-1 && m.player.y<0,"corner resolves X only in one tick");
        m.step(new StageModel.Input());check(m.dead && m.deathReason.equals("BOUNDARY"),"corner pending Y is second consecutive crossing");
        m=emptyThree();StageModel.Enemy e=new StageModel.Enemy(300,300);e.x=-45;e.y=-50;m.enemies.add(e);m.step(new StageModel.Input());
        check(m.enemies.size()==2 && m.enemies.get(1).gravity==-1,"enemy corner also resolves X only");
    }
    static void driftByPlacement(StageModel m){
        m.echoTarget=new StageModel.Box(1056,40,88,44);m.targetGravity=-1;m.targetRevealed=true;
        StageModel.Enemy clone=new StageModel.Enemy(1200,40);clone.clone=true;clone.stun=100;m.enemies.add(clone);
        m.step(new StageModel.Input());clone.x=1080;m.step(new StageModel.Input());
    }
    static void primeByDelivery(StageModel m){
        m.echoTarget=new StageModel.Box(1080,40,64,60);m.targetGravity=-1;m.targetRevealed=true;m.targetVersion++;
        StageModel.Enemy clone=new StageModel.Enemy(1030,40);clone.clone=true;clone.stun=100;m.enemies.add(clone);m.player.x=980;
        StageModel.Input j=new StageModel.Input();j.melee=true;m.step(j);
        for(int n=0;n<30 && !m.boss.primed;n++)m.step(new StageModel.Input());
    }
    static void bossLatchedExposure(){
        StageModel m=bossArena();m.boss.phase=0;m.boss.timer=.2f;
        primeByDelivery(m);
        check(m.boss.primed && has(m,"boss_exposed"),"supported post-thaw goal delivery earns boss charge");
        StageModel.Input held=new StageModel.Input();held.guard=true;
        int blocked=0;for(int n=0;n<1200;n++){m.step(held);if(has(m,"parry"))blocked++;}
        System.out.println("BOSS_DELAY_DIAG primed="+m.boss.primed+" exposure="+m.boss.exposure+" seals="+m.boss.seals+" heldBlocks="+blocked+" dead="+m.dead);
        check(!m.dead && blocked>=2 && m.boss.seals==3,"real long-held K blocks without reopening just or consuming seal");
        check(m.boss.primed && m.boss.exposure>0,"earned boss exposure survives human delay and normal guards until seal break");
        m.step(new StageModel.Input());
        for(int n=0;n<600 && m.boss.seals==3;n++){
            StageModel.Input tap=new StageModel.Input();tap.guard=tap.parry=m.boss.phase==1 && m.boss.timer<.04f && m.parryCooldown==0;m.step(tap);
        }
        check(m.boss.seals==2 && !m.boss.primed && m.boss.exposure==0,"release and fresh K edge consumes exactly one delayed earned charge");
    }
    static void bossAttackClock(){
        StageModel m=emptyThree();m.platforms.add(new StageModel.Box(0,0,1280,40));m.boss.timer=0;m.player.vy=100;m.step(new StageModel.Input());
        check(m.boss.phase==1 && m.boss.timer>=.79f && m.boss.direction==1,"boss starts obvious locked horizontal telegraph");
        float y=m.boss.beamY,t=m.boss.timer;m.player.y=400;m.stopTime=1;steps(m,12);
        check(m.boss.timer==t && m.boss.beamY==y,"world stop freezes boss telegraph and snapshot");
        m.stopTime=0;m.paused=true;steps(m,12);check(m.boss.timer==t,"pause freezes boss clock");m.paused=false;
        steps(m,97);check(m.boss.phase==2 && m.boss.beamY==y,"boss active keeps captured beam height");
        steps(m,24);check(m.boss.phase==3,"boss has visible recovery");
        steps(m,180);check(m.boss.phase==0,"boss recovers to idle");
    }
    static StageModel bossArena(){StageModel m=emptyThree();m.platforms.add(new StageModel.Box(0,0,1280,40));m.player.y=40;m.boss.phase=2;m.boss.timer=.18f;m.boss.beamY=62;return m;}
    static void bossBeamRules(){
        StageModel m=bossArena();m.step(new StageModel.Input());check(m.woundTime>4.9f && !m.dead,"active boss beam applies existing wound rule");
        m.invulnerable=0;steps(m,8);check(!m.dead,"same beam swing cannot wound twice");
        m=bossArena();m.stopTime=2;m.step(new StageModel.Input());check(m.woundTime>0 && m.boss.timer==.18f,"existing boss beam remains hazardous while time stopped");
        m=bossArena();m.player.x=600;m.player.y=320;m.boss.phase=0;m.boss.timer=10;m.step(new StageModel.Input());
        check(!m.won && !m.dead && m.woundTime==0 && m.player.x==600,"boss overlap harmless and exit cannot bypass seals");
        m=bossArena();m.platforms.add(new StageModel.Box(800,40,20,100));
        StageModel.Box b=m.bossAttackBox();check(b.x==690 && b.x+b.w==800,"boss beam clips walls");
    }
    static void bossParrySeals(){
        StageModel m=bossArena();m.woundTime=4;StageModel.Input k=new StageModel.Input();k.parry=true;m.step(k);
        check(!m.dead && m.woundTime==0 && m.invulnerable>=.44f && m.boss.seals==3 && m.boss.phase==3,"unprimed boss just heals and protects but cannot break seal");
        m=bossArena();m.boss.primed=true;m.boss.exposure=3;m.woundTime=4;
        StageModel.Enemy upper=new StageModel.Enemy(900,84);upper.upper=true;upper.phase=2;upper.timer=.1f;m.enemies.add(upper);m.step(k);
        check(!m.dead && m.woundTime==0 && m.boss.seals==2 && !m.boss.primed && m.boss.exposure==0,"boss just consumes one charge and protects against simultaneous upper hazard");
        check(has(m,"boss_seal_break") && has(m,"just_parry"),"boss seal event distinct from just feedback");
        m=bossArena();m.boss.primed=true;m.boss.exposure=3;m.parryCooldown=1;StageModel.Input hold=new StageModel.Input();hold.guard=true;m.step(hold);
        check(m.woundTime==0 && m.boss.seals==3 && m.boss.phase==3,"held normal guard stuns boss but never breaks seal");
        m=bossArena();m.stopTime=2;m.step(k);check(m.woundTime>0 && m.boss.seals==3,"frozen world disallows boss parry");
        m=bossArena();m.boss.primed=true;m.boss.exposure=3;m.boss.seals=1;m.woundTime=4;m.enemies.add(upper);m.step(k);
        check(m.won && !m.dead && m.boss.defeated && m.boss.seals==0 && m.woundTime==0,"third seal wins immediately before incoming damage");
        m.step(new StageModel.Input());check(m.won && !m.dead && m.events.isEmpty(),"victory is terminal and clears transient events");
        m=bossArena();m.boss.phase=0;m.boss.timer=10;StageModel.Enemy clone=new StageModel.Enemy(900,40);clone.clone=true;m.enemies.add(clone);m.player.y=84;StageModel.Input freeze=new StageModel.Input();freeze.stop=true;m.step(freeze);
        check(m.stopTime>0 && !m.boss.primed && m.boss.exposure==0 && !has(m,"boss_exposed"),"frozen living-clone standing no longer charges exposure");
        steps(m,30);check(!m.boss.primed,"camping old frozen clone does not earn exposure");
        m=bossArena();m.boss.phase=0;m.boss.timer=10;clone=new StageModel.Enemy(900,40);clone.clone=true;m.enemies.add(clone);m.player.y=87;freeze=new StageModel.Input();freeze.stop=true;m.step(freeze);
        check(!m.player.grounded && !m.boss.primed,"airborne proximity is not actual frozen clone standing");
    }
    static void bossRegressions(){
        StageModel m=new StageModel();check(m.boss==null,"stage one has no boss");m.loadStage(1);check(m.boss==null,"stage two has no boss");m.loadStage(2);
        check(m.enemies.size()==2 && m.enemies.get(0).x==15 && m.platforms.size()==6,"boss map has both physical source helpers and paired ledges without beam-covering wall");
        m.boss.seals=1;m.boss.primed=true;m.boss.exposure=2;m.boss.phase=2;m.won=true;m.dead=true;m.restart();StageModel fresh=new StageModel();fresh.loadStage(2);
        check(sameState(m,fresh),"stage-local restart fully restores boss and hazards");
        for(boolean horizontal:new boolean[]{false,true})for(int direction:new int[]{-1,1}){
            m=emptyThree();m.boss.timer=100;m.player.x=horizontal?(direction<0?-31:1281):900;m.player.y=horizontal?300:(direction<0?-45:721);m.player.gravity=horizontal?1:direction;m.player.vy=direction*200;
            StageModel.Input i=new StageModel.Input();i.move=direction;m.step(i);
            check(has(m,"boundary_cross") && m.boundaryChain==1 && !m.dead && m.player.gravity==(horizontal?1:-direction),"stage three each player portal gravity policy "+horizontal+direction);
            check(m.player.vx==direction*270 && Math.abs(m.player.vy-(direction*200+(horizontal?1:direction)*StageModel.GRAVITY*StageModel.DT))<.01f,"stage three every player portal preserves signed momentum");
        }
        for(int kind=0;kind<3;kind++){
            m=bossArena();m.boss.phase=0;m.boss.timer=10;StageModel.Enemy e=new StageModel.Enemy(900,40);e.clone=kind!=0;e.alive=kind!=1;m.enemies.add(e);m.player.y=84;
            StageModel.Input i=new StageModel.Input();i.stop=kind!=2;m.step(i);
            check(!m.boss.primed && m.boss.exposure==0,"original dead clone or unfrozen support cannot charge boss kind="+kind);
        }
        m=bossArena();m.boss.primed=true;m.boss.exposure=.005f;m.step(new StageModel.Input());check(m.boss.primed && m.boss.exposure==.005f,"latched exposure never silently expires while waiting for parry");
        m=bossArena();m.boss.primed=true;m.boss.exposure=2;m.paused=true;float y=m.player.y;steps(m,50);
        check(m.boss.exposure==2 && m.boss.timer==.18f && m.woundTime==0 && m.player.y==y,"pause freezes exposure active hazard and player");
        m=bossArena();m.boss.phase=0;m.boss.timer=100;m.player.x=700;StageModel.Input sword=new StageModel.Input();sword.melee=true;sword.move=-1;m.step(sword);steps(m,12);
        check(m.boss.x==590 && m.boss.y==300 && m.boss.seals==3 && m.enemies.isEmpty(),"boss ignores normal delayed melee and never clones");
        m=bossArena();StageModel.Input held=new StageModel.Input();held.guard=true;m.boss.phase=0;m.boss.timer=10;m.step(held);for(int n=0;n<100;n++)m.step(held);
        m.boss.phase=2;m.boss.timer=.18f;m.boss.primed=true;m.boss.exposure=3;m.step(held);
        check(m.boss.seals==3 && m.boss.phase==3 && m.woundTime==0 && m.parryTime==0,"long held guard blocks boss without reopening just window");
        for(int direction:new int[]{-1,1}){
            m=bossArena();m.boss.direction=direction;StageModel.Box b=m.bossAttackBox();check(direction<0?b.x==0:b.x+b.w==1280,"boss line clips world edge not wrap "+direction);
        }
        m=bossArena();m.woundTime=4;m.step(new StageModel.Input());check(m.dead && m.deathReason.equals("WOUND"),"second boss wound within five seconds kills without HP");
    }
    static final class RouteTwo {
        final StageModel m;final StringBuilder trace=new StringBuilder();
        int ticks,last=-1,run,crosses,spawns;boolean rode;float maxY;
        final java.util.Map<String,Integer> events=new java.util.TreeMap<String,Integer>();
        RouteTwo(StageModel model){m=model;}
        void tick(StageModel.Input i){
            int mask=i.move+1|(i.jump?4:0)|(i.melee?8:0)|(i.parry?16:0)|(i.stop?32:0)|(i.guard?64:0);
            if(mask==last)run++;else{if(run>0)trace.append(last).append('x').append(run).append(',');last=mask;run=1;}
            m.step(i);ticks++;maxY=Math.max(maxY,m.player.y);
            for(StageModel.GameEvent e:m.events){events.put(e.id,events.containsKey(e.id)?events.get(e.id)+1:1);if(e.id.equals("boundary_cross"))crosses++;if(e.id.equals("clone_spawn"))spawns++;}
            for(StageModel.Enemy e:m.enemies)if(e.clone && m.player.grounded && Math.abs(m.player.y-e.y-e.h)<.1f)rode=true;
            if(m.dead)throw new AssertionError("stage two route died tick="+ticks+" x="+m.player.x+" y="+m.player.y);
        }
        StageModel.Input input(){return new StageModel.Input();}
        void go(float x,float y){
            for(int n=0;n<500;n++){
                if(m.won || (m.player.grounded && Math.abs(m.player.y-y)<.1f && Math.abs(m.player.x-x)<4))return;
                StageModel.Input i=input();i.move=Math.abs(m.player.x-x)<2?0:m.player.x<x?1:-1;
                i.jump=m.player.grounded && m.player.y<y-1;tick(i);
            }
            throw new AssertionError("stage two target "+x+","+y+" stuck "+m.player.x+","+m.player.y+" ticks="+ticks);
        }
        String finish(){return trace.toString()+last+"x"+run;}
    }
    static void stageTwoRoute(){
        StageModel m=new StageModel();m.loadStage(1);EchoRouteTest.Run r=EchoRouteTest.two(m);EchoRouteTest.verify(r,0);
    }
    static java.util.Map<String,Integer> playTrace(StageModel m,String trace){
        java.util.Map<String,Integer> counts=new java.util.TreeMap<String,Integer>();
        for(String part:trace.split(",")){
            String[] pair=part.split("x");int mask=Integer.parseInt(pair[0]),run=Integer.parseInt(pair[1]);
            for(int n=0;n<run;n++){
                StageModel.Input i=new StageModel.Input();i.move=(mask&3)-1;i.jump=(mask&4)!=0;i.melee=(mask&8)!=0;i.parry=(mask&16)!=0;i.stop=(mask&32)!=0;i.guard=(mask&64)!=0;i.start=(mask&128)!=0;m.step(i);
                for(StageModel.GameEvent e:m.events)counts.put(e.id,counts.containsKey(e.id)?counts.get(e.id)+1:1);
            }
        }
        return counts;
    }
    static boolean sameScalars(Class<?> type,Object a,Object b){
        try{
            for(java.lang.reflect.Field f:type.getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && (f.getType().isPrimitive() || f.getType()==String.class)){
                f.setAccessible(true);if(!java.util.Objects.equals(f.get(a),f.get(b)))return false;
            }
            return true;
        }catch(IllegalAccessException ex){throw new AssertionError(ex);}
    }
    static boolean sameBody(StageModel.Body a,StageModel.Body b){return a.x==b.x && a.y==b.y && a.w==b.w && a.h==b.h && a.vx==b.vx && a.vy==b.vy && a.gravity==b.gravity && a.grounded==b.grounded;}
    static boolean sameState(StageModel a,StageModel b){
        if(!sameScalars(StageModel.class,a,b) || !sameBody(a.player,b.player) || a.enemies.size()!=b.enemies.size() || a.woundTime!=b.woundTime || a.boundaryChain!=b.boundaryChain || a.stopTime!=b.stopTime || a.stopCooldown!=b.stopCooldown || a.attackCooldown!=b.attackCooldown || a.parryCooldown!=b.parryCooldown || a.invulnerable!=b.invulnerable || a.meleeTime!=b.meleeTime || a.parryTime!=b.parryTime)return false;
        for(int n=0;n<a.enemies.size();n++){
            StageModel.Enemy x=a.enemies.get(n),y=b.enemies.get(n);
            if(!sameScalars(StageModel.Enemy.class,x,y) || a.enemies.indexOf(x.original)!=b.enemies.indexOf(y.original) || !sameBody(x,y) || x.alive!=y.alive || x.clone!=y.clone || x.phase!=y.phase || x.timer!=y.timer || x.stun!=y.stun || x.upper!=y.upper || x.direction!=y.direction)return false;
        }
        if((a.echoTarget==null)!=(b.echoTarget==null))return false;
        if(a.echoTarget!=null && !sameScalars(StageModel.Box.class,a.echoTarget,b.echoTarget))return false;
        if((a.boss==null)!=(b.boss==null))return false;
        if(a.boss!=null && (!sameScalars(StageModel.Boss.class,a.boss,b.boss) || !sameScalars(StageModel.Box.class,a.boss,b.boss)))return false;
        return a.stageIndex==b.stageIndex && a.won==b.won && a.dead==b.dead && a.objective.equals(b.objective);
    }
    static StageModel emptyTwo(){StageModel m=new StageModel();m.loadStage(1);m.platforms.clear();m.enemies.clear();m.player.x=600;m.player.y=1000;return m;}
    static void stageTwoPortals(){
        for(int direction:new int[]{-1,1}){
            StageModel m=emptyTwo();m.player.x=direction<0?-31:1281;m.player.vy=180;
            StageModel.Input i=new StageModel.Input();i.move=direction;m.step(i);
            check(has(m,"boundary_cross") && m.boundaryChain==1 && m.player.gravity==-1,"horizontal player crossing "+direction);
            check(Math.abs(m.player.y-(1000+(180-StageModel.GRAVITY*StageModel.DT)*StageModel.DT))<.01f && m.player.vx==direction*270,"horizontal portal preserves y and momentum");
            m.player.x=direction<0?-31:1281;m.invulnerable=100;i.stop=true;i.guard=true;m.step(i);
            check(m.dead && m.deathReason.equals("BOUNDARY"),"second horizontal crossing kills through stop guard invulnerability");
            m=emptyTwo();m.boundaryChain=1;m.platforms.add(new StageModel.Box(0,900,1280,40));m.player.y=950;m.player.vy=-200;steps(m,20);
            check(m.player.grounded && m.boundaryChain==0,"real stage two landing resets chain");
            for(boolean corpse:new boolean[]{false,true})for(int gravity:new int[]{-1,1}){
                m=emptyTwo();StageModel.Enemy e=new StageModel.Enemy(300,1200,gravity);m.enemies.add(e);
                e.alive=!corpse;e.h=corpse?22:44;e.x=direction<0?-45:1281;e.y=1100;e.vx=direction*180;e.vy=80;
                m.step(new StageModel.Input());StageModel.Enemy c=corpse?e:m.enemies.get(1);
                check(c.gravity==gravity && c.h==44 && c.alive && Math.abs(c.vx-direction*180*.985f)<.01f,"horizontal original lifecycle preserves gravity vx "+corpse+gravity+direction);
                check(Math.abs(c.y-(1100+(80+gravity*StageModel.GRAVITY*StageModel.DT)*StageModel.DT))<.01f,"horizontal original lifecycle preserves y vy");
                if(!corpse){check(e.x==300 && e.y==1200 && c.clone && c.original==e,"live original resets fixed spawn and creates linked clone");c.x=direction<0?-45:1281;m.step(new StageModel.Input());check(!m.enemies.contains(c),"clone horizontal crossing disposes");}
            }
        }
        StageModel m=emptyTwo();m.player.y=-5;m.player.vy=-200;m.step(new StageModel.Input());
        check(m.player.y==0 && m.player.gravity==-1 && !has(m,"boundary_cross"),"stage two bottom is bounded not portal");
        m.player.y=2550;m.player.vy=200;m.step(new StageModel.Input());check(m.player.y==2516 && !has(m,"boundary_cross"),"stage two top is bounded not portal");
        StageModel.Enemy e=new StageModel.Enemy(1200,1000);e.ranged=true;e.direction=1;
        check(m.attackBox(e).x+m.attackBox(e).w==1280,"beam stops at dynamic stage edge never wraps");
    }
    static void stageTwoRegressions(){
        for(int direction:new int[]{-1,1}){
            StageModel m=emptyTwo();m.player.x=direction<0?-29:1279;
            StageModel.Input i=new StageModel.Input();m.step(i);
            check(!has(m,"boundary_cross") && m.boundaryChain==0,"whole player body must leave horizontal edge "+direction);
            m.player.x=direction<0?-31:1281;m.player.vy=0;i.move=direction;m.step(i);
            check(has(m,"boundary_cross") && m.player.vx==270*direction,"whole player crossing retains signed horizontal speed");
            m=emptyTwo();m.platforms.add(new StageModel.Box(0,900,1280,40));
            StageModel.Enemy e=new StageModel.Enemy(direction<0?1:1235,940);e.clone=true;e.grounded=true;m.enemies.add(e);
            m.player.x=direction<0?0:1250;m.player.y=1000;steps(m,120);
            check(e.x>=0 && e.x+e.w<=1280,"AI support probe prevents walking unsupported beyond stage two edge");
            m=emptyTwo();StageModel.Enemy original=new StageModel.Enemy(300,1100);m.enemies.add(original);
            original.x=direction<0?-45:1281;m.step(new StageModel.Input());StageModel.Enemy oldClone=m.enemies.get(1);
            original.x=direction<0?-45:1281;m.step(new StageModel.Input());
            check(m.enemies.size()==2 && !m.enemies.contains(oldClone),"horizontal original has only one linked clone");
            StageModel.Enemy beam=new StageModel.Enemy(600,1100);beam.ranged=true;beam.direction=direction;
            m.platforms.add(new StageModel.Box(direction<0?400:800,1090,20,100));StageModel.Box box=m.attackBox(beam);
            check(direction<0?box.x==420:box.x+box.w==800,"stage two beam clips physical wall "+direction);
            m=emptyTwo();m.platforms.add(new StageModel.Box(0,900,1280,40));
            StageModel.Enemy wall=new StageModel.Enemy(700,940);wall.clone=true;wall.grounded=true;m.enemies.add(wall);
            m.platforms.add(new StageModel.Box(770,940,20,100));m.player.x=1000;m.player.y=940;steps(m,200);
            check(wall.x+wall.w<=770,"stage two clone chase respects physical side wall");
        }
        for(boolean clone:new boolean[]{false,true}){
            StageModel m=emptyTwo();m.player.x=600;m.player.y=1050;m.player.vy=-200;m.boundaryChain=1;m.stopTime=3;
            StageModel.Enemy e=new StageModel.Enemy(600,980);e.clone=clone;e.alive=clone;e.h=clone?44:22;m.enemies.add(e);
            for(int n=0;n<60 && !m.player.grounded;n++)m.step(new StageModel.Input());
            check(m.player.grounded && m.boundaryChain==0,"physical clone or brick support resets horizontal chain clone="+clone);
        }
        StageModel m=new StageModel();m.loadStage(1);m.won=true;m.dead=true;m.stopTime=2;m.stopCooldown=4;m.woundTime=3;m.boundaryChain=1;m.invulnerable=8;m.player.gravity=1;m.player.vx=100;m.player.vy=200;
        StageModel.Input retry=new StageModel.Input();retry.restart=true;m.step(retry);
        StageModel fresh=new StageModel();fresh.loadStage(1);
        check(sameState(m,fresh) && m.events.size()==1 && has(m,"ui_confirm"),"stage two R fully resets current stage and emits one cue");
        m.player.y=1800;m.step(new StageModel.Input());check(m.objective.contains("clone mark"),"stage two unsolved door objective retains existing echo-delivery priority");
        m.doorUnlocked=true;m.step(new StageModel.Input());check(m.objective.equals(StageTwoLevel.objective(m.player.y)),"stage two unlocked objective follows height not horizontal position");
        check(StageTwoLevel.objective(500).contains("AFTER thaw") && StageTwoLevel.objective(900).contains("right edge") && StageTwoLevel.objective(1200).contains("west"),"stage two hints distinguish clone shelf and upper portal sections");
    }
    static void stageTwoConfiguration() {
        StageModel m=new StageModel();
        check(m.stageIndex==0 && m.wrapY && !m.wrapX && !m.started,"stage one title defaults");
        m.loadStage(1);
        check(m.stageIndex==1 && m.started && m.wrapX && !m.wrapY && m.worldWidth==1280 && m.worldHeight==2560,"vertical stage configuration");
        check(m.player.y==40 && m.player.gravity==-1 && !m.won && m.hasNextStage(),"stage two fresh spawn and boss continuation");
        m.won=true;m.restart();check(m.stageIndex==1 && !m.won && m.player.y==40,"retry stays current stage");
        try {m.loadStage(3);throw new AssertionError("invalid index accepted");}catch(IllegalArgumentException expected){}
        check(m.stageIndex==1 && m.started,"invalid index does not mutate state");
        m.loadStage(0);m.won=true;StageModel.Input i=new StageModel.Input();i.start=true;m.step(i);
        check(m.stageIndex==1 && m.events.size()==1 && has(m,"ui_confirm"),"clear Enter advances with one cue");
    }
    static void meleeWindup() {
        for(int gravity:new int[]{-1,1})for(int direction:new int[]{-1,1})for(boolean corpse:new boolean[]{false,true}) {
            StageModel m=empty();m.platforms.add(new StageModel.Box(0,gravity<0?0:680,2000,40));
            m.player.x=500;m.player.y=gravity<0?40:636;m.player.gravity=gravity;m.facing=direction;
            StageModel.Enemy e=new StageModel.Enemy(500+direction*60,m.player.y,gravity);e.alive=!corpse;e.timer=10;m.enemies.add(e);
            StageModel.Input i=new StageModel.Input();i.melee=true;m.step(i);
            check(!has(m,"melee_push") && !has(m,"brick_push") && Math.abs(e.vx)<100,"accepted melee has no instant knockback "+gravity+"/"+direction+"/"+corpse);
            check(has(m,"sword_swing") && m.meleeTime==.28f && m.attackCooldown==.3f,"accepted melee starts full action and unchanged cooldown");
            int hit=0,swings=1;
            for(int n=1;n<=34;n++) {
                m.step(new StageModel.Input());boolean push=has(m,corpse?"brick_push":"melee_push");
                if(push)hit++;if(has(m,"sword_swing"))swings++;
                if(n<=8)check(!push,"no strike before .07 seconds at tick "+n);
                if(n==9)check(push && e.vx*direction>600 && e.vy*gravity<0,"downstroke strikes first fixed tick crossing .07");
            }
            check(hit==1 && swings==1 && m.meleeTime==0 && e.alive==!corpse,"single strike through full .28 action without damage");
        }
    }
    static void meleeClocks() {
        StageModel m=guardArena();StageModel.Enemy e=new StageModel.Enemy(155,40);e.timer=10;m.enemies.add(e);
        StageModel.Input attack=new StageModel.Input();attack.melee=true;m.step(attack);steps(m,4);
        StageModel.Input pause=new StageModel.Input();pause.pause=true;m.step(pause);
        float time=m.meleeTime,cool=m.attackCooldown;steps(m,100);
        check(m.meleeTime==time && m.attackCooldown==cool && !has(m,"sword_swing"),"pause freezes pending windup and clears events");
        m.step(pause);steps(m,3);check(!has(m,"melee_push"),"resumed attack still waits for remaining windup");
        m.step(new StageModel.Input());check(has(m,"melee_push"),"resume strikes once at remaining windup threshold");
        for(boolean frozenStart:new boolean[]{false,true}) {
            m=guardArena();e=new StageModel.Enemy(155,40);e.timer=10;m.enemies.add(e);
            if(frozenStart)m.stopTime=1;m.step(attack);m.stopTime=1;
            float x=e.x;steps(m,9);
            check(m.meleeTime>0 && e.x==x && !has(m,"melee_push"),"frozen strike consumes opportunity without push startFrozen="+frozenStart);
            m.stopTime=0;int hits=0;for(int n=0;n<30;n++){m.step(new StageModel.Input());if(has(m,"melee_push"))hits++;}
            check(hits==0,"consumed frozen strike never replays on thaw startFrozen="+frozenStart);
        }
        m=guardArena();e=new StageModel.Enemy(155,40);e.timer=10;m.enemies.add(e);m.step(attack);m.paused=true;
        StageModel.Input restart=new StageModel.Input();restart.restart=true;m.step(restart);
        check(m.meleeTime==0 && m.attackCooldown==0,"restart clears windup and cooldown");
        steps(m,12);check(!has(m,"melee_push") && !has(m,"sword_swing"),"restart never replays queued strike");
        for(boolean boundary:new boolean[]{false,true}) {
            m=guardArena();e=new StageModel.Enemy(155,40);e.timer=10;m.enemies.add(e);m.step(attack);
            if(boundary){m.platforms.clear();m.player.y=-50;m.boundaryChain=1;}
            else {m.woundTime=3;floorAttack(m,false);}
            m.step(new StageModel.Input());check(m.dead && has(m,"player_death") && !has(m,"melee_push"),"death interrupts queued windup boundary="+boundary);
            float remaining=m.meleeTime;steps(m,20);
            check(m.meleeTime==remaining && m.events.isEmpty(),"dead attack stays frozen without duplicate events boundary="+boundary);
            m.step(restart);check(m.meleeTime==0 && m.attackCooldown==0 && !m.dead,"death restart clears queued strike boundary="+boundary);
        }
        check(StageModel.MELEE_DURATION==.28f && StageModel.MELEE_WINDUP==.07f,"shared renderer action constants");
    }
    static StageModel.Input guard() {
        StageModel.Input i=new StageModel.Input();
        try {StageModel.Input.class.getField("guard").setBoolean(i,true);}
        catch(ReflectiveOperationException ex){throw new AssertionError("held guard input contract missing",ex);}
        return i;
    }
    static void heldGuardLate() {
        StageModel m=empty();m.platforms.add(new StageModel.Box(0,0,1800,40));m.player.x=100;m.player.y=40;
        StageModel.Input i=guard();for(int n=0;n<90;n++)m.step(i);
        StageModel.Enemy e=new StageModel.Enemy(145,40);e.phase=2;e.timer=.15f;e.direction=-1;m.enemies.add(e);m.woundTime=3;m.step(i);
        check(e.alive && e.stun>0 && m.woundTime>2.9f && m.invulnerable==0,"held guard blocks late active normally without healing");
    }
    static void boundaryDeath() {
        for(int gravity:new int[]{-1,1}) {
            StageModel m=empty();m.player.gravity=gravity;m.player.y=330;m.invulnerable=100;
            int crosses=0,deaths=0;
            for(int n=0;n<900 && !m.dead;n++){m.step(new StageModel.Input());if(has(m,"boundary_cross"))crosses++;if(has(m,"player_death"))deaths++;}
            check(m.dead && crosses==2 && deaths==1,"second actual airborne boundary kills despite invulnerability "+gravity);
        }
    }
    static StageModel guardArena() {
        StageModel m=empty();m.platforms.add(new StageModel.Box(0,0,2000,40));m.player.x=100;m.player.y=40;return m;
    }
    static StageModel.Enemy floorAttack(StageModel m,boolean upper) {
        StageModel.Enemy e=new StageModel.Enemy(145,40);e.phase=2;e.timer=.15f;e.direction=-1;e.upper=upper;m.enemies.add(e);return e;
    }
    static void heldGuardRegressions() {
        StageModel m=guardArena();StageModel.Input i=guard();i.parry=true;m.woundTime=3;
        StageModel.Enemy e=floorAttack(m,false);m.step(i);
        check(!e.alive && m.woundTime==0 && m.invulnerable==StageModel.JUST_INVULNERABILITY,"guard rising edge just heals and protects");
        m.enemies.clear();for(int n=0;n<120;n++)m.step(i);
        check(m.guarding && m.parryTime==0 && m.parryCooldown==0,"held input including repeated tap flag never rearms just");
        e=floorAttack(m,false);m.step(i);check(e.alive && e.stun>0 && has(m,"parry") && !has(m,"just_parry"),"continued hold after just success only normal blocks");
        m.enemies.clear();e=floorAttack(m,false);m.step(i);check(e.alive && e.stun>0,"holding blocks consecutive new attacks without repress");
        m.enemies.clear();m.step(new StageModel.Input());e=floorAttack(m,false);m.step(new StageModel.Input());
        check(!m.guarding && m.woundTime>0,"release ends indefinite defense");
        m.enemies.clear();m.invulnerable=0;e=floorAttack(m,false);m.step(guard());
        check(!e.alive && m.woundTime==0,"release repress after cooldown starts a new just");
        m=guardArena();m.step(guard());m.step(new StageModel.Input());e=floorAttack(m,false);m.parryTime=0;m.step(guard());
        check(e.alive && e.stun>0 && m.parryTime==0,"repress during cooldown supplies normal hold but no just");
        m.enemies.clear();for(int n=0;n<90;n++)m.step(guard());e=floorAttack(m,false);m.step(guard());
        check(e.alive && e.stun>0,"cooldown expiring during denied hold never auto just");
        m=guardArena();for(int n=0;n<90;n++)m.step(guard());i=guard();i.pause=true;m.step(i);
        float cooldown=m.parryCooldown,time=m.parryTime,y=m.player.y;for(int n=0;n<90;n++)m.step(guard());
        check(m.paused && m.parryCooldown==cooldown && m.parryTime==time && m.player.y==y,"pause freezes held guard timers and world");
        e=floorAttack(m,false);m.step(i);check(!m.paused && e.alive && e.stun>0,"resume with hold cannot create phantom just");
        m=guardArena();i=new StageModel.Input();i.pause=true;m.step(i);m.step(guard());e=floorAttack(m,false);i=guard();i.pause=true;m.step(i);
        check(e.alive && e.stun>0 && m.parryTime==0,"guard first pressed while paused resumes normal not just");
        m=guardArena();for(int n=0;n<90;n++)m.step(guard());e=floorAttack(m,true);m.step(guard());
        check(e.alive && e.stun==0 && m.woundTime>0,"held normal guard cannot block upper");
        m=guardArena();for(int n=0;n<90;n++)m.step(guard());e=floorAttack(m,false);i=guard();i.stop=true;m.step(i);
        check(e.alive && e.stun==0 && m.woundTime>0,"time stop held guard grants no new protection against existing attack");
        m=guardArena();for(int n=0;n<90;n++)m.step(guard());
        StageModel.Enemy near=new StageModel.Enemy(250,40);near.timer=10;m.enemies.add(near);
        e=new StageModel.Enemy(800,40);e.ranged=true;e.phase=2;e.timer=.15f;e.direction=-1;m.enemies.add(e);m.step(guard());
        check(e.alive && e.stun>0 && near.stun==0 && m.woundTime==0,"held guard stuns distant beam owner not nearest body");
        m=guardArena();i=guard();i.move=1;i.melee=true;e=new StageModel.Enemy(145,40);m.enemies.add(e);m.step(i);
        check(m.guarding && m.player.vx==StageModel.SPEED && e.alive && has(m,"sword_swing") && !has(m,"melee_push"),"guard allows accepted melee windup and movement unchanged");
        i.melee=false;for(int n=0;n<9;n++)m.step(i);
        check(m.guarding && e.alive && e.vx>600 && has(m,"melee_push"),"held guard permits actual delayed melee strike");
        m.paused=true;i=new StageModel.Input();i.restart=true;m.step(i);
        check(!m.guarding && m.parryTime==0 && m.parryCooldown==0,"restart clears held guard state and timers");
        m=guardArena();m.step(guard());check(m.parryTime==StageModel.PARRY_DURATION && m.parryCooldown==.42f,"guard-only rising edge starts existing exact timer and cooldown");
        i=guard();i.pause=true;m.step(i);time=m.parryTime;cooldown=m.parryCooldown;for(int n=0;n<100;n++)m.step(guard());
        check(m.parryTime==time && m.parryCooldown==cooldown,"pause preserves nonzero just timer and cooldown");
        m.step(i);check(m.parryTime<time && m.parryTime>time-2*StageModel.DT,"resume continues old just timer without refreshing");
        m=guardArena();e=floorAttack(m,true);m.step(guard());check(e.alive && m.woundTime>0 && e.stun==0,"initial held just window also cannot parry upper");
    }
    static void boundaryRegressions() {
        for(int gravity:new int[]{-1,1}) {
            StageModel m=empty();m.platforms.add(new StageModel.Box(0,gravity<0?680:0,600,40));m.player.gravity=gravity;m.player.y=gravity<0?-50:721;m.player.vy=gravity*200;m.step(new StageModel.Input());
            check(m.boundaryChain==1 && !m.dead,"first crossing is safe "+gravity);
            float entryY=m.player.y;int entryGravity=m.player.gravity;
            m.paused=true;steps(m,50);check(m.boundaryChain==1 && m.player.y==entryY,"pause neither advances nor clears chain "+gravity);
            m.paused=false;m.stopTime=3;m.step(new StageModel.Input());check(m.boundaryChain==1 && m.stopTime>0,"time stop alone does not clear airborne chain "+gravity);
            for(int n=0;n<400 && !m.player.grounded && !m.dead;n++)m.step(new StageModel.Input());
            check(!m.dead && m.player.grounded && m.boundaryChain==0,"real floor or ceiling contact resets chain "+gravity);
            m.platforms.clear();m.stopTime=0;m.player.y=entryGravity>0?721:-50;m.player.vy=entryGravity*200;m.step(new StageModel.Input());
            check(!m.dead && m.boundaryChain==1,"crossing after landing is a fresh first crossing "+gravity);
        }
        for(int gravity:new int[]{-1,1})for(boolean clone:new boolean[]{false,true}) {
            StageModel m=empty();m.player.gravity=gravity;m.player.x=105;m.player.y=gravity<0?100:570;m.boundaryChain=1;m.stopTime=3;
            StageModel.Enemy e=new StageModel.Enemy(100,gravity<0?40:636,gravity);e.clone=clone;e.alive=clone;m.enemies.add(e);
            for(int n=0;n<60 && !m.player.grounded;n++)m.step(new StageModel.Input());
            check(m.player.grounded && m.boundaryChain==0 && !m.dead,"actual clone or corpse landing resets during time stop gravity="+gravity+" clone="+clone);
        }
        StageModel m=empty();m.platforms.add(new StageModel.Box(0,680,600,40));m.player.y=-50;m.player.vy=-200;m.step(new StageModel.Input());
        check(m.boundaryChain==1 && !m.player.grounded && m.player.vy<0,"portal placement flush to ceiling is not landing");
        m.player.x=100;StageModel.Enemy attack=new StageModel.Enemy(145,636,1);attack.phase=2;attack.timer=.15f;attack.direction=-1;m.enemies.add(attack);
        m.player.y=-50;m.player.vy=-200;m.invulnerable=100;m.woundTime=3;StageModel.Input i=guard();m.step(i);
        check(m.dead && m.boundaryChain==2 && "BOUNDARY".equals(m.deathReason) && has(m,"player_death") && m.woundTime>0,"second boundary bypasses guard wound and invulnerability");
        check(m.player.gravity==1 && m.player.y==636 && m.player.vy<0 && !has(m,"just_parry") && !has(m,"stage_clear"),"fatal crossing preserves geometry momentum and skips combat victory");
        check(attack.alive && attack.stun==0 && m.woundTime>0,"fatal portal returns before overlapping just can kill or heal");
        m.step(new StageModel.Input());check(m.events.isEmpty(),"boundary death cue is emitted once only");
        i=new StageModel.Input();i.restart=true;m.step(i);check(!m.dead && m.boundaryChain==0 && m.deathReason.isEmpty() && !m.guarding,"death restart clears chain reason and guard");
        m=guardArena();m.woundTime=3;floorAttack(m,false);m.step(new StageModel.Input());check(m.dead && "WOUND".equals(m.deathReason),"combat death has WOUND reason");
        m=guardArena();m.boundaryChain=1;i=new StageModel.Input();i.jump=true;m.player.grounded=true;m.step(i);steps(m,120);
        check(!m.dead && m.boundaryChain==0,"ordinary in-bounds jump and landing never boundary kill");
        m=empty();m.player.y=-44;m.player.vy=StageModel.GRAVITY*StageModel.DT;m.step(new StageModel.Input());
        check(m.boundaryChain==0 && !m.dead,"touching boundary without whole body crossing is not counted");
        m=guardArena();m.boundaryChain=1;StageModel.Enemy e=new StageModel.Enemy(400,100);m.enemies.add(e);e.y=-50;m.step(new StageModel.Input());
        check(m.enemies.size()==2 && m.boundaryChain==0 && !m.dead,"enemy cloning leaves landed player counter unchanged");
        m=empty();m.player.y=300;m.boundaryChain=1;e=new StageModel.Enemy(400,100);e.alive=false;e.y=-50;m.enemies.add(e);m.step(new StageModel.Input());
        check(e.alive && m.boundaryChain==1 && !m.dead,"enemy resurrection never increments or clears airborne player chain");
        StageModel.Enemy c=new StageModel.Enemy(500,721);c.clone=true;m.enemies.add(c);m.step(new StageModel.Input());
        check(!m.enemies.contains(c) && m.boundaryChain==1 && !m.dead,"clone boundary disposal leaves player counter unchanged");
    }
    static void rangedContract() {
        try { check(!StageModel.Enemy.class.getField("ranged").getBoolean(new StageModel.Enemy(0,0)),"ranged defaults false"); }
        catch(ReflectiveOperationException ex){throw new AssertionError("ranged public contract missing",ex);}
    }
    static void rangedBeam() {
        StageModel m=empty();m.platforms.add(new StageModel.Box(0,0,1800,40));m.player.x=600;m.player.y=40;
        StageModel.Enemy e=new StageModel.Enemy(1000,40);e.ranged=true;e.direction=-1;e.phase=2;e.timer=.13f;m.enemies.add(e);
        check(m.attackBox(e).overlaps(m.player),"active straight line reaches distant player");
        StageModel.Input i=new StageModel.Input();i.parry=true;m.woundTime=2;m.step(i);
        check(!e.alive && m.woundTime==0,"distant just kills owning caster and heals");
        check(e.h==22 && e.y==40,"original death becomes flat brick preserving floor foot");
    }
    static void rangedRules() {
        for(int direction:new int[]{-1,1}) {
            StageModel m=empty();m.platforms.add(new StageModel.Box(0,0,2000,40));
            StageModel.Enemy e=new StageModel.Enemy(1000,40);e.ranged=true;m.enemies.add(e);m.player.x=1000+direction*500;m.player.y=40;
            m.step(new StageModel.Input());check(e.phase==1 && e.direction==direction,"ranged begins distant directional telegraph "+direction);
            m.player.x=1000-direction*500;steps(m,30);check(e.direction==direction,"telegraph locks direction "+direction);
            m.player.x=1000+direction*500;steps(m,75);check(m.woundTime>0,"real telegraph becomes distant damaging beam "+direction);
            m=empty();m.player.x=1000+direction*500;m.player.y=40;e=new StageModel.Enemy(1000,40);e.ranged=true;e.direction=direction;e.phase=2;e.timer=.15f;m.enemies.add(e);
            m.platforms.add(new StageModel.Box(direction<0?750:1250,0,30,120));
            check(!m.attackBox(e).overlaps(m.player),"wall clips ranged line "+direction);m.step(new StageModel.Input());check(m.woundTime==0,"wall prevents ranged damage "+direction);
        }
        StageModel m=empty();m.platforms.add(new StageModel.Box(0,0,2000,40));m.player.x=600;m.player.y=40;
        StageModel.Enemy other=new StageModel.Enemy(700,40);other.timer=10;m.enemies.add(other);
        StageModel.Enemy e=new StageModel.Enemy(1000,40);e.ranged=true;e.direction=-1;e.phase=2;e.timer=.15f;m.enemies.add(e);m.parryTime=.12f;m.step(new StageModel.Input());
        check(e.stun>0 && other.stun==0 && e.alive,"normal ranged parry stuns owner not nearest enemy");
        e.stun=0;e.phase=2;e.timer=.15f;m.parryCooldown=0;m.parryTime=0;StageModel.Input i=new StageModel.Input();i.parry=true;m.step(i);
        check(!e.alive && other.alive,"just ranged kills owner not intervening enemy");
        m=empty();m.player.x=600;m.player.y=40;e=new StageModel.Enemy(1000,40);e.ranged=true;e.direction=-1;e.phase=2;e.timer=.15f;m.enemies.add(e);i.stop=true;m.step(i);
        check(e.alive && e.stun==0 && m.woundTime>0,"frozen ranged remains dangerous and cannot parry");
        m=empty();e=new StageModel.Enemy(300,100);e.ranged=true;m.enemies.add(e);e.y=-60;m.step(new StageModel.Input());
        check(m.enemies.get(1).ranged,"clone inherits ranged form");
        m=empty();m.platforms.add(new StageModel.Box(0,680,2000,40));m.player.gravity=1;m.player.x=600;m.player.y=636;e=new StageModel.Enemy(1000,636,1);e.ranged=true;e.direction=-1;e.phase=2;e.timer=.15f;m.enemies.add(e);i.stop=false;m.step(i);
        check(!e.alive && e.h==22 && e.y==658,"inverted flat brick preserves ceiling anchor");
        e.y=721;m.step(new StageModel.Input());check(e.alive && e.h==44 && e.gravity==-1,"true flat brick revives full height opposite edge");
    }
    static void eventContract() {
        try { check(StageModel.class.getField("events").get(new StageModel()) instanceof java.util.List,"events public list");check(StageModel.class.getField("objective").get(new StageModel()) instanceof String,"objective public text"); }
        catch(ReflectiveOperationException ex){throw new AssertionError("asset event contract missing",ex);}
    }
    static boolean has(StageModel m,String id){for(StageModel.GameEvent e:m.events)if(e.id.equals(id))return true;return false;}
    static void eventBehavior() {
        StageModel m=new StageModel();StageModel.Input i=new StageModel.Input();i.start=true;m.step(i);check(has(m,"ui_confirm"),"title confirm emits once");m.step(new StageModel.Input());check(!has(m,"ui_confirm"),"event queue clears per tick");
        m.events.add(new StageModel.GameEvent("jump",0,0));m.dead=true;m.step(new StageModel.Input());check(m.events.isEmpty(),"dead early return clears queue");
        m.dead=false;m.paused=true;m.events.add(new StageModel.GameEvent("jump",0,0));m.step(new StageModel.Input());check(m.events.isEmpty(),"paused early return clears queue");
        m=empty();m.platforms.add(new StageModel.Box(0,0,500,40));m.player.y=40;steps(m,2);i=new StageModel.Input();i.jump=true;m.step(i);check(has(m,"jump"),"jump cue");
        boolean land=false;for(int n=0;n<140;n++){m.step(new StageModel.Input());land|=has(m,"land_light")||has(m,"land_heavy");}check(land,"landing cue");
        i=new StageModel.Input();i.stop=true;m.step(i);check(has(m,"time_stop"),"stop cue");m.stopTime=.001f;m.step(new StageModel.Input());check(has(m,"time_resume"),"natural resume cue");
        m=empty();m.player.x=100;m.player.y=100;StageModel.Enemy e=incoming(m,false);i=new StageModel.Input();i.parry=true;m.step(i);check(has(m,"just_parry")&&has(m,"slime_to_brick"),"just brick events");m.paused=true;m.step(new StageModel.Input());check(m.events.isEmpty(),"just event cannot replay while paused");
        m=empty();m.player.x=100;m.player.y=100;e=incoming(m,false);m.step(new StageModel.Input());check(has(m,"player_hit"),"hit cue");m.woundTime=.001f;m.step(new StageModel.Input());check(has(m,"wound_recover"),"recovery cue");
        e.y=-50;m.step(new StageModel.Input());check(has(m,"clone_spawn"),"clone spawn cue");e.alive=false;e.y=-50;m.step(new StageModel.Input());check(has(m,"resurrect"),"resurrection cue");
    }
    static void assetRegressions() {
        StageModel m=empty();m.doorUnlocked=true;m.player.x=2400;m.step(new StageModel.Input());check(m.objective.equals(StageOneLevel.objective(m.player.x)),"objective updates to current chamber");
        m.doorUnlocked=true;m.player.x=3900;m.player.y=190;m.step(new StageModel.Input());check(has(m,"exit_open"),"exit approach cue");m.step(new StageModel.Input());check(!has(m,"exit_open"),"exit approach cue is one-shot");
        m.dead=true;StageModel.Input i=new StageModel.Input();i.restart=true;m.step(i);check(m.objective.equals(StageOneLevel.objective(90)),"restart restores spawn objective");
        m=new StageModel();m.events.add(new StageModel.GameEvent("jump",0,0));m.step(new StageModel.Input());check(m.events.isEmpty(),"title return clears stale event");
        for(int direction:new int[]{-1,1}) {
            m=empty();m.player.x=1000+direction*400;m.player.y=200;StageModel.Enemy e=new StageModel.Enemy(1000,40);e.ranged=true;e.phase=2;e.timer=.15f;e.direction=direction;m.enemies.add(e);m.step(new StageModel.Input());check(m.woundTime==0,"horizontal beam cannot hit vertically distant player "+direction);
            m=empty();m.player.x=1000+direction*950;m.player.y=40;e=new StageModel.Enemy(1000,40);e.ranged=true;e.phase=2;e.timer=.15f;e.direction=direction;m.enemies.add(e);m.step(new StageModel.Input());check(m.woundTime==0,"straight beam finite 900 range "+direction);
        }
        m=empty();m.player.x=100;m.player.y=100;StageModel.Enemy e=incoming(m,false);m.step(new StageModel.Input());m.invulnerable=0;m.player.y=100;m.player.vy=0;incoming(m,false);m.step(new StageModel.Input());check(has(m,"player_death"),"fatal second hit cue");
        m=empty();m.player.x=600;m.player.y=40;e=new StageModel.Enemy(1000,40);e.ranged=true;e.direction=-1;e.phase=2;e.timer=.15f;e.clone=true;m.enemies.add(e);i=new StageModel.Input();i.parry=true;m.step(i);check(m.enemies.isEmpty()&&has(m,"clone_disappear")&&!has(m,"slime_to_brick"),"ranged clone just vanishes without brick");
        m=empty();m.player.x=100;m.player.y=100;e=incoming(m,false);m.parryTime=.12f;m.step(new StageModel.Input());check(has(m,"parry")&&has(m,"slime_stun"),"normal parry and owner stun cues");
    }
    static void flatBrickRegressions() {
        for(int gravity:new int[]{-1,1}) {
            StageModel m=empty();m.platforms.add(new StageModel.Box(0,gravity<0?0:680,1800,40));m.player.gravity=gravity;m.player.x=600;m.player.y=gravity<0?40:636;
            StageModel.Enemy e=new StageModel.Enemy(1000,m.player.y,gravity);e.ranged=true;e.direction=-1;e.phase=2;e.timer=.15f;m.enemies.add(e);StageModel.Input i=new StageModel.Input();i.parry=true;m.step(i);
            m.player.x=e.x+5;m.player.y=gravity<0?e.y+e.h+50:e.y-94;m.player.vy=0;steps(m,90);
            check(e.h==22 && m.player.grounded && (gravity<0?Math.abs(m.player.y-e.y-22)<.1f:Math.abs(m.player.y+44-e.y)<.1f),"real flat brick physical support "+gravity);
            check(!e.upper && m.woundTime==0,"real brick support does not trigger original upper "+gravity);
            e.y=gravity<0?-30:721;e.vy=gravity<0?-600:600;m.player.x=100;m.step(new StageModel.Input());
            check(e.alive && e.h==44 && e.gravity==-gravity,"flat brick restored live body on opposite boundary "+gravity);
            check(m.enemies.size()==1 && has(m,"resurrect"),"flat resurrection uses same original no clone "+gravity);
            m=empty();m.player.gravity=gravity;m.player.x=105;m.player.y=gravity<0?84:592;e=new StageModel.Enemy(100,gravity<0?40:636,gravity);e.ranged=true;m.enemies.add(e);i=new StageModel.Input();i.stop=true;m.step(i);
            check(e.upper && e.phase==1 && m.stopTime==0,"ranged original support also cancels stop into upper "+gravity);
        }
    }
    static void aiLocks() {
        for(boolean clone:new boolean[]{false,true}) {
            StageModel m=empty();m.player.x=1000;m.platforms.add(new StageModel.Box(0,0,1200,40));
            StageModel.Enemy e=new StageModel.Enemy(300,40);e.clone=clone;m.enemies.add(e);steps(m,3);
            float x=e.x;m.stopTime=.1f;steps(m,5);check(e.x==x,"time stop freezes autonomous motion clone="+clone);
            m.stopTime=0;e.stun=.5f;steps(m,5);check(e.x==x && e.vx==0,"stun prevents autonomous motion clone="+clone);
            e.stun=0;e.phase=1;e.timer=.4f;steps(m,5);check(e.x==x,"telegraph pauses walking clone="+clone);
        }
        StageModel m=empty();m.platforms.add(new StageModel.Box(0,0,1500,40));m.player.x=100;m.player.y=40;
        StageModel.Enemy e=new StageModel.Enemy(145,40);m.enemies.add(e);
        StageModel.Input i=new StageModel.Input();i.melee=true;m.step(i);steps(m,39);
        check(e.grounded && e.vx>300,"AI does not replace landed knockback momentum during half-second lock");
        m=empty();m.platforms.add(new StageModel.Box(0,680,1500,40));m.platforms.add(new StageModel.Box(0,0,1500,40));m.player.x=100;m.player.y=40;
        e=new StageModel.Enemy(145,40);m.enemies.add(e);m.step(i);steps(m,9);
        e.y=-50;e.vy=200;e.grounded=false;m.step(new StageModel.Input());
        StageModel.Enemy c=m.enemies.get(1);float vx=c.vx;m.step(new StageModel.Input());
        check(c.grounded && Math.abs(c.vx-vx*.985f)<.01f,"clone inherits remaining knockback lock across portal");
        float spawn=e.x;m.step(new StageModel.Input());check(e.x>spawn,"reset original clears knockback lock along with momentum");
    }
    static void movingBodyRegressions() {
        StageModel m=empty();m.player.x=105;m.player.y=84;
        StageModel.Enemy e=new StageModel.Enemy(100,40);e.alive=false;e.vy=200;m.enemies.add(e);
        m.step(new StageModel.Input());
        check(e.y>41 && m.player.y>85 && m.player.grounded,"rising corpse carries rider instead of colliding with rider");
        m=empty();m.stopTime=3;m.player.x=205;m.player.y=100;e=new StageModel.Enemy(200,100);m.enemies.add(e);
        m.step(new StageModel.Input());
        check(!m.player.overlaps(e) && m.woundTime==0 && e.x==200,"initial body overlap separates minimally without damage or pushing");
        m=empty();m.stopTime=3;m.player.x=205;m.player.y=-50;m.player.vy=-200;
        e=new StageModel.Enemy(200,650);e.alive=false;m.enemies.add(e);m.step(new StageModel.Input());
        check(!m.player.overlaps(e) && m.player.gravity==1 && m.player.vy<0,"portal destination body overlap separates without absorbing signed momentum");
        for(int gravity:new int[]{-1,1})for(int direction:new int[]{-1,1}) {
            m=empty();float y=gravity<0?40:636;
            m.platforms.add(new StageModel.Box(0,gravity<0?0:680,600,40));
            e=new StageModel.Enemy(direction>0?150:275,y,gravity);e.alive=false;e.vx=direction*620;m.enemies.add(e);
            m.player.x=direction>0?195:244;m.player.y=y;m.player.gravity=gravity;
            float x=m.player.x;m.step(new StageModel.Input());
            check(!m.player.overlaps(e) && m.player.x==x && m.woundTime==0,"moving enemy stops at idle player gravity="+gravity+" direction="+direction);
        }
        m=empty();m.platforms.add(new StageModel.Box(0,0,600,40));m.player.x=135;m.player.y=84;
        e=new StageModel.Enemy(120,40);e.alive=false;e.vx=620;m.enemies.add(e);
        StageModel.Enemy obstacle=new StageModel.Enemy(166,84);obstacle.alive=false;m.enemies.add(obstacle);
        m.step(new StageModel.Input());
        check(m.player.x+m.player.w<=obstacle.x && !m.player.overlaps(obstacle),"rider carry collides with another monster body");
        m=empty();m.platforms.add(new StageModel.Box(230,0,20,200));m.player.x=205;m.player.y=100;m.stopTime=1;
        e=new StageModel.Enemy(200,100);e.alive=false;m.enemies.add(e);m.step(new StageModel.Input());
        check(!m.player.overlaps(e) && !m.player.overlaps(m.platforms.get(0)),"overlap repair does not place player through static wall");
        m=empty();m.player.x=305;m.player.y=100;e=new StageModel.Enemy(300,100);e.x=500;e.y=-50;m.enemies.add(e);
        m.step(new StageModel.Input());
        check(!m.player.overlaps(e) && m.woundTime==0,"original respawn resolves player body overlap in same tick");
    }
    static void aiObstacles() {
        for(int gravity:new int[]{-1,1})for(boolean clone:new boolean[]{false,true})for(boolean wall:new boolean[]{false,true}) {
            StageModel m=empty();m.player.x=1000;
            m.platforms.add(new StageModel.Box(0,gravity<0?0:680,400,40));
            if(wall)m.platforms.add(new StageModel.Box(380,40,20,640));
            StageModel.Enemy e=new StageModel.Enemy(300,gravity<0?40:636,gravity);e.clone=clone;m.enemies.add(e);
            boolean stayed=true,reversed=false;float x=e.x;
            for(int n=0;n<180;n++) {m.step(new StageModel.Input());if(e.x<x)reversed=true;x=e.x;
                stayed &= Math.abs(e.y-e.spawnY)<.01f && e.x+e.w<=(wall?380:400)+.01f;}
            check(stayed,"AI respects gravity-relative ledge/wall gravity="+gravity+" clone="+clone+" wall="+wall);
            check(clone?e.vx==0:reversed,"original reverses; clone waits at obstacle");
        }
    }
    static void chase() {
        for(int gravity:new int[]{-1,1}) {
            StageModel m=empty();m.platforms.add(new StageModel.Box(0,gravity<0?0:680,1200,40));
            m.player.x=900;m.player.y=gravity<0?40:636;m.player.gravity=gravity;
            StageModel.Enemy e=new StageModel.Enemy(300,m.player.y,gravity);e.clone=true;m.enemies.add(e);
            steps(m,60);check(e.x>350 && e.grounded,"clone chases player along support "+gravity);
            m.player.x=50;float x=e.x;steps(m,60);check(e.x<x-40,"clone changes direction toward player "+gravity);
            m.player.x=e.x+e.w+40;e.timer=10;steps(m,4);
            check(e.vx==0,"clone stops within horizontal strike range "+gravity);
        }
    }
    static void patrol() {
        for(int gravity:new int[]{-1,1}) {
            StageModel m=empty();m.player.x=1000;m.platforms.add(new StageModel.Box(0,gravity<0?0:680,700,40));
            m.platforms.add(new StageModel.Box(900,0,300,40)); // Player rests while enemy patrol timer runs.
            StageModel.Enemy e=new StageModel.Enemy(300,gravity<0?40:636,gravity);m.enemies.add(e);
            steps(m,60);check(e.x>325 && e.grounded,"original patrol moves on gravity-relative support "+gravity);
            boolean reversed=false;float previous=e.x;
            for(int n=0;n<600;n++){m.step(new StageModel.Input());if(e.x<previous)reversed=true;previous=e.x;
                if(e.x<179.99f || e.x>420.01f)throw new AssertionError("patrol exceeded spawn range");}
            check(reversed && e.grounded,"original reverses within spawn plus/minus120 "+gravity);
            e.x=500;e.vx=0;steps(m,60);
            check(e.x<480,"displaced original returns toward patrol range instead of toggling in place "+gravity);
        }
    }
    static void solidBodies() {
        for(int kind=0;kind<3;kind++)for(int direction:new int[]{-1,1}) {
            StageModel m=empty();m.stopTime=3;m.player.y=100;m.player.x=direction>0?169:245;
            StageModel.Enemy e=new StageModel.Enemy(200,100);e.clone=kind==1;e.alive=kind!=2;m.enemies.add(e);
            StageModel.Input i=new StageModel.Input();i.move=direction;m.step(i);
            check(!m.player.overlaps(e) && Math.abs(e.x-200)<.001f,"solid body blocks horizontal input without pushing kind="+kind+" direction="+direction);
            check(m.woundTime==0 && !m.dead,"frozen idle body contact harmless");
        }
        StageModel m=empty();m.stopTime=3;m.player.x=205;m.player.y=55;m.player.vy=500;
        StageModel.Enemy e=new StageModel.Enemy(200,100);e.alive=false;m.enemies.add(e);m.step(new StageModel.Input());
        check(m.player.y+m.player.h<=e.y && m.player.vy==0,"solid underside blocks upward head collision");
        m=empty();m.stopTime=3;m.player.gravity=1;m.player.x=205;m.player.y=145;m.player.vy=-500;
        e=new StageModel.Enemy(200,100);e.clone=true;m.enemies.add(e);m.step(new StageModel.Input());
        check(m.player.y>=e.y+e.h && m.player.vy==0,"inverted head collision blocks downward movement");
    }
    static void boundarySupportRegression() {
        StageModel bottom=empty();bottom.platforms.add(new StageModel.Box(800,680,1100,40));
        bottom.player.x=1100;bottom.player.y=-45;bottom.player.vy=-200;
        StageModel.Enemy corpse=new StageModel.Enemy(1100,300);corpse.alive=false;bottom.enemies.add(corpse);
        bottom.step(new StageModel.Input());
        StageModel top=empty();top.player.x=1100;top.player.y=721;top.player.vy=200;
        corpse=new StageModel.Enemy(1100,300);corpse.alive=false;top.enemies.add(corpse);
        top.step(new StageModel.Input());
        check(bottom.player.gravity==1 && Math.abs(bottom.player.y-636)<.01 && !bottom.player.grounded && bottom.player.vy<0
                && top.player.gravity==-1 && Math.abs(top.player.y-1)<.01 && !top.player.grounded,
                "boundary transition must not land on distant corpse (bottom="+bottom.player.y+", top="+top.player.y+")");
    }
    static void carriedSideCollisionRegression() {
        StageModel m=empty();m.platforms.add(new StageModel.Box(1850,0,2350,40));m.platforms.add(new StageModel.Box(2320,40,100,65));
        m.player.x=2280;m.player.y=84;m.player.grounded=true;
        StageModel.Enemy corpse=new StageModel.Enemy(2245,40);corpse.alive=false;corpse.vx=500;m.enemies.add(corpse);
        steps(m,3);
        check(m.player.x+m.player.w<=2320 && Math.abs(m.player.y-84)<.01 && m.player.grounded,
                "corpse carry must stop at block side, not climb it (x="+m.player.x+", y="+m.player.y+")");
    }
    static void carriedCeilingCollisionRegression() {
        StageModel m=empty();m.platforms.add(new StageModel.Box(100,130,100,18));
        m.player.x=105;m.player.y=84;m.player.grounded=true;
        StageModel.Enemy corpse=new StageModel.Enemy(100,40);corpse.alive=false;corpse.vy=850;m.enemies.add(corpse);
        m.step(new StageModel.Input());
        check(m.player.y+m.player.h<=130 && !m.player.overlaps(m.platforms.get(0)),
                "vertical corpse carry must stop below shelf, not snap onto its top (y="+m.player.y+")");
    }
    static void redesignedMap() {
        StageModel m=new StageModel();boolean firstStep=false,secondStep=false,caster=false;
        for(StageModel.Box p:m.platforms){firstStep|=p.x==220 && p.y+p.h==140;secondStep|=p.x==360 && p.y+p.h==240;}
        for(StageModel.Enemy e:m.enemies)caster|=e.ranged;
        check(firstStep && secondStep,"new map starts with deliberate momentum stair");check(caster,"new stage has ranged lane");
        StageModel jump=empty();jump.platforms.add(new StageModel.Box(0,0,600,40));jump.player.y=40;jump.player.grounded=true;StageModel.Input i=new StageModel.Input();i.jump=true;jump.step(i);float apex=jump.player.y;
        for(int n=0;n<150;n++){jump.step(new StageModel.Input());apex=Math.max(apex,jump.player.y);}
        check(apex<190,"ordinary floor jump cannot reach either puzzle step locally");
        check(apex+44>210,"clone adds enough height for first puzzle step");
        check(apex+22>190 && apex+22<210,"flat brick enables final step but cannot replace clone locally");
        System.out.println("LOCAL_JUMP apexFromFloor="+apex+" cloneStepTop=210 brickStepTop=190");
    }
    static void level() {
        StageModel m=new StageModel();EchoRouteTest.Run r=EchoRouteTest.one(m);EchoRouteTest.verify(r,0);
    }
    static void replayRoute(String trace,int expectedTicks,float expectedX,float expectedY,java.util.Map<String,Integer> expectedEvents) {
        StageModel replay=new StageModel();StageModel.Input start=new StageModel.Input();start.start=true;replay.step(start);
        int ticks=0;java.util.Map<String,Integer> counts=new java.util.TreeMap<String,Integer>();
        for(String part:trace.split(",")){
            String[] pair=part.split("x");int mask=Integer.parseInt(pair[0]),run=Integer.parseInt(pair[1]);
            for(int n=0;n<run;n++){
                StageModel.Input i=new StageModel.Input();i.move=(mask&3)-1;i.jump=(mask&4)!=0;i.melee=(mask&8)!=0;i.parry=(mask&16)!=0;i.stop=(mask&32)!=0;replay.step(i);ticks++;
                for(StageModel.GameEvent e:replay.events)counts.put(e.id,counts.containsKey(e.id)?counts.get(e.id)+1:1);
            }
        }
        check(ticks==expectedTicks && replay.won && !replay.dead,"recorded inputs replay from untouched spawn without feedback");
        check(replay.player.x==expectedX && replay.player.y==expectedY && replay.woundTime==0,"route replay exact final position and healthy state");
        check(counts.equals(expectedEvents),"route replay exact discrete event counts");
        System.out.println("REPLAY_OK ticks="+ticks+" x="+replay.player.x+" y="+replay.player.y+" eventsMatch=true");
    }
    static void upperRevision() {
        StageModel m=empty();m.platforms.add(new StageModel.Box(0,0,600,40));
        StageModel.Enemy e=new StageModel.Enemy(100,40);m.enemies.add(e);
        m.player.x=105;m.player.y=89;m.player.vy=-600;
        StageModel.Input i=new StageModel.Input();i.stop=true;m.step(i);
        check(m.player.grounded && e.upper && e.phase==1 && m.stopTime==0,"actual landing cancels frozen original same tick");
        check(e.timer<=.061f,"upper windup is sixty milliseconds");
        i=new StageModel.Input();i.jump=true;m.step(i);steps(m,8);
        check(m.woundTime>0 && !m.dead,"immediate jump after original landing still faces fast upper");
        m=empty();m.platforms.add(new StageModel.Box(0,0,600,40));m.player.x=105;m.player.y=40;m.player.grounded=true;
        i=new StageModel.Input();i.jump=true;m.step(i);steps(m,9);
        check(m.woundTime==0 && m.player.y>40,"ordinary safe surface jump stays safe");
    }
    static void clocksAndRiding() {
        StageModel m=empty();StageModel.Enemy e=new StageModel.Enemy(145,100);m.enemies.add(e);m.player.x=100;m.player.y=100;e.phase=2;e.timer=.1f;e.direction=-1;
        StageModel.Input i=new StageModel.Input();i.stop=true;i.melee=true;i.parry=true;m.step(i);
        check(m.stopTime>0 && e.y==100 && e.timer==.1f,"stop freezes enemy physics and attack timer");
        check(e.alive && e.stun==0 && e.vx==0 && !m.dead && m.woundTime>0,"frozen attacks dangerous; no kill stun push");
        float t=m.stopTime,y=m.player.y,cool=m.stopCooldown; i=new StageModel.Input();i.pause=true;m.step(i);i=new StageModel.Input();i.move=1;i.parry=true;i.stop=true;steps(m,40);m.step(i);
        check(m.paused && m.stopTime==t && m.player.y==y && m.stopCooldown==cool,"pause freezes every timer and input");
        m=empty();m.platforms.add(new StageModel.Box(0,0,600,40));e=new StageModel.Enemy(100,40);m.enemies.add(e);m.player.x=105;m.player.y=84;i=new StageModel.Input();i.stop=true;m.step(i);
        check(m.stopTime==0 && e.upper && e.phase==1,"standing on frozen original cancels stop and telegraphs upper");
        steps(m,60);check(!m.dead && m.woundTime>0,"standing upper damages after telegraph");
        m=empty();m.platforms.add(new StageModel.Box(0,0,600,40));e=new StageModel.Enemy(100,40);e.clone=true;e.vx=90;m.enemies.add(e);m.player.x=105;m.player.y=84;float x=m.player.x;m.step(new StageModel.Input());
        check(m.player.grounded && m.player.x>x && !e.upper,"safe clone riding carries player");
        m=empty();m.platforms.add(new StageModel.Box(0,0,600,40));e=new StageModel.Enemy(100,40);e.clone=true;e.phase=2;e.timer=.15f;e.direction=1;m.enemies.add(e);m.player.x=135;m.player.y=84;m.step(new StageModel.Input());
        check(!m.dead && m.woundTime==0 && m.player.grounded,"standing on clone edge avoids horizontal attack");
        m=empty();m.platforms.add(new StageModel.Box(0,680,600,40));e=new StageModel.Enemy(100,636);e.gravity=1;m.enemies.add(e);m.player.gravity=1;m.player.x=105;m.player.y=592;m.step(new StageModel.Input());
        check(e.upper && m.player.grounded,"inverted standing uses gravity-relative face");
        m=empty();m.platforms.add(new StageModel.Box(0,0,600,40));e=new StageModel.Enemy(100,40);e.alive=false;e.vx=90;m.enemies.add(e);m.player.x=105;m.player.y=84;x=m.player.x;m.step(new StageModel.Input());
        check(m.player.grounded && m.player.x>x && !e.upper,"corpse is movable safe platform");
    }
    static float reboundDepth(float speed,boolean top) {
        StageModel m=empty();m.player.x=100;m.player.y=top?721:-50;m.player.gravity=top?1:-1;m.player.vy=top?speed:-speed;
        StageModel.Input i=new StageModel.Input();i.move=1;m.step(i);
        check(Math.abs(m.player.x-(100+StageModel.SPEED*StageModel.DT))<.01f && m.player.vx==StageModel.SPEED,"portal preserves current x and horizontal velocity");
        float entry=m.player.y,depth=0;for(int n=0;n<100 && (top?m.player.vy>0:m.player.vy<0);n++){m.step(new StageModel.Input());depth=Math.max(depth,top?m.player.y-entry:entry-m.player.y);}
        return depth;
    }
    static void momentumRevision() {
        check(reboundDepth(800,false)>reboundDepth(200,false)+150,"faster bottom drop yields higher inward rebound");
        check(reboundDepth(800,true)>reboundDepth(200,true)+150,"faster top drop yields higher inward rebound");
        for(boolean corpse:new boolean[]{false,true})for(boolean top:new boolean[]{false,true}){
            StageModel m=empty();StageModel.Enemy e=new StageModel.Enemy(300,100);m.enemies.add(e);e.alive=!corpse;
            e.x=450;e.y=top?721:-50;e.gravity=top?1:-1;e.vx=180;e.vy=top?600:-600;
            m.step(new StageModel.Input());StageModel.Enemy crossed=corpse?e:m.enemies.get(1);
            check(crossed.gravity==(top?-1:1) && Math.abs(crossed.vy-(top?600+StageModel.GRAVITY*StageModel.DT:-600-StageModel.GRAVITY*StageModel.DT))<.01f,"enemy portal preserves signed vertical velocity corpse="+corpse+" top="+top);
            check(Math.abs(crossed.x-(450+180*StageModel.DT))<.01f && Math.abs(crossed.vx-180*.985f)<.01f && !crossed.grounded,"enemy portal preserves crossing x and damped vx");
            if(!corpse)check(e.vx==0 && e.vy==0 && e.x==300,"only original spawn reset clears velocity");
        }
        for(boolean corpse:new boolean[]{false,true})for(boolean top:new boolean[]{false,true}){
            StageModel m=empty();m.platforms.add(new StageModel.Box(0,top?0:680,600,40));
            StageModel.Enemy e=new StageModel.Enemy(300,100);m.enemies.add(e);e.alive=!corpse;e.gravity=top?1:-1;e.y=top?721:-50;e.vy=top?600:-600;
            m.step(new StageModel.Input());StageModel.Enemy crossed=corpse?e:m.enemies.get(1);
            check(Math.abs(crossed.y-(top?40:636))<.01f && !crossed.grounded && crossed.vy*crossed.gravity<0,"enemy entry flush to floor/ceiling retains inward velocity");
        }
    }
    static void lifecycle() {
        StageModel m=empty();StageModel.Enemy e=new StageModel.Enemy(300,100);m.enemies.add(e);e.y=-50;m.step(new StageModel.Input());
        check(m.enemies.size()==2,"live original crossing creates clone");
        StageModel.Enemy clone=m.enemies.get(1);check(e.x==300 && e.y>90 && e.gravity==-1,"original returns to fixed spawn anchor");
        check(clone.clone && clone.gravity==1 && clone.y>600,"clone opposite edge inverted");
        e.y=-50;m.step(new StageModel.Input());check(m.enemies.size()==2 && !m.enemies.contains(clone),"one clone per original; replace living clone");
        clone=m.enemies.get(1);clone.y=721;m.step(new StageModel.Input());check(m.enemies.size()==1,"clone boundary disposal");
        e.alive=false;e.y=-50;m.step(new StageModel.Input());check(e.alive && e.gravity==1 && e.y>600 && m.enemies.size()==1,"corpse resurrection same original no clone");
        e.y=721;m.step(new StageModel.Input());check(e.y>90 && e.gravity==-1,"resurrected original still uses initial anchor");
        clone=m.enemies.get(1);e.alive=false;m.step(new StageModel.Input());check(m.enemies.contains(clone),"original death preserves clone");
        m=empty();m.player.x=100;m.player.y=100;e=new StageModel.Enemy(145,100);e.clone=true;e.phase=2;e.timer=.1f;e.direction=-1;m.enemies.add(e);
        StageModel.Input i=new StageModel.Input();i.parry=true;m.step(i);check(m.enemies.isEmpty(),"just parry clone removes without corpse");
    }
    static StageModel.Enemy incoming(StageModel m, boolean upper) {
        StageModel.Enemy e=new StageModel.Enemy(145,100);e.phase=2;e.timer=.15f;e.direction=-1;e.upper=upper;m.enemies.add(e);return e;
    }
    static void woundRevision() {
        StageModel m=empty();m.player.x=100;m.player.y=100;incoming(m,false);
        m.step(new StageModel.Input());
        m.invulnerable=0;m.player.y=100;m.player.vy=0;incoming(m,false);m.step(new StageModel.Input());
        check(m.dead,"second distinct hit within five seconds kills");
        m=empty();m.player.x=100;m.player.y=100;StageModel.Enemy e=incoming(m,false);
        m.step(new StageModel.Input());m.invulnerable=0;m.player.y=100;m.player.vy=0;m.step(new StageModel.Input());
        check(!m.dead,"one active swing cannot hit player twice");
        m.enemies.clear();m.woundTime=.02f;m.stopTime=1;steps(m,3);
        check(m.woundTime==0 && "RECOVERED".equals(m.combatFeedback),"wound recovers on player clock during stop");
        m.player.x=100;m.player.y=100;m.player.vy=0;incoming(m,false);m.invulnerable=0;m.stopTime=0;m.step(new StageModel.Input());
        check(!m.dead && m.woundTime>4.9f,"hit after recovery is first wound again");
        float wound=m.woundTime,feedback=m.combatFeedbackTime;m.paused=true;steps(m,30);
        check(m.woundTime==wound && m.combatFeedbackTime==feedback,"pause freezes wound and feedback clocks");
        m=empty();m.player.x=100;m.player.y=100;e=incoming(m,false);m.step(new StageModel.Input());
        check(Math.abs(m.woundTime-StageModel.WOUND_DURATION)<.001f && "HIT".equals(m.combatFeedback),"first hit starts full five-second wound and HIT feedback");
        m.player.x=1000;steps(m,45);e.x=145;e.y=100;e.vy=0;e.phase=1;e.timer=0;
        m.player.x=100;m.player.y=100;m.player.vy=0;m.step(new StageModel.Input());
        check(m.dead,"same enemy distinct new active swing can kill after hit protection");
        m=empty();m.player.x=100;m.player.y=100;e=incoming(m,false);m.stopTime=3;m.step(new StageModel.Input());
        steps(m,45);m.player.x=100;m.player.y=100;m.player.vy=0;m.step(new StageModel.Input());
        check(!m.dead && e.phase==2,"same frozen active hitbox cannot hit again after protection expires");
        m=empty();m.player.x=100;m.player.y=100;incoming(m,false);incoming(m,true);m.step(new StageModel.Input());
        check(!m.dead && m.woundTime==StageModel.WOUND_DURATION,"first-hit protection covers simultaneous distinct attacks");
        m=empty();m.platforms.add(new StageModel.Box(0,0,600,40));m.woundTime=StageModel.WOUND_DURATION;steps(m,599);
        check(m.woundTime>0 && m.woundTime<.02f,"wound remains just before five seconds");
        steps(m,2);check(m.woundTime==0 && !m.dead,"five seconds without another hit recovers naturally");
    }
    static void parryProtection() {
        for(int order=0;order<2;order++) {
            StageModel m=empty();m.player.x=100;m.player.y=100;m.woundTime=3;
            if(order==0)incoming(m,true);
            StageModel.Enemy target=incoming(m,false);StageModel.Enemy second=incoming(m,false);
            if(order==1)incoming(m,true);
            StageModel.Input i=new StageModel.Input();i.parry=true;m.step(i);
            check(!target.alive && !second.alive && !m.dead && m.woundTime==0,"just success heals wound and protects same tick upper regardless enemy order "+order);
            check("JUST PARRY".equals(m.combatFeedback) && Math.abs(m.invulnerable-StageModel.JUST_INVULNERABILITY)<.001f,"just success grants exact brief protection and distinct feedback");
            m.player.y=100;m.player.vy=0;incoming(m,true);m.step(new StageModel.Input());
            boolean protectedUpper=!m.dead;m.player.x=1000;steps(m,60);
            check(protectedUpper && m.invulnerable==0,"just protection covers later upper but expires naturally");
        }
        StageModel m=empty();m.player.x=100;m.player.y=100;m.woundTime=3;StageModel.Enemy e=incoming(m,false);m.parryTime=.12f;m.step(new StageModel.Input());
        check(e.alive && e.stun>0 && "PARRY".equals(m.combatFeedback) && m.invulnerable==0 && m.woundTime>2.9f,"normal parry does not heal wound or grant just protection");
    }
    static void combat() {
        StageModel m=empty();m.player.x=100;m.player.y=100;
        StageModel.Enemy e=new StageModel.Enemy(135,100);m.enemies.add(e);
        steps(m,1);check(!m.dead && m.woundTime==0,"idle contact harmless");
        StageModel.Input i=new StageModel.Input();i.melee=true;m.step(i);steps(m,9);
        check(e.alive && e.vx>0,"melee knocks back without damage after windup");
        m=empty();m.player.x=100;m.player.y=100;e=new StageModel.Enemy(145,100);m.enemies.add(e);e.phase=2;e.timer=.1f;e.direction=-1;
        i=new StageModel.Input();i.parry=true;m.step(i);check(!e.alive,"just parry incoming active strike kills original");
        m=empty();m.player.x=100;m.player.y=100;e=new StageModel.Enemy(145,100);m.enemies.add(e);e.phase=1;e.timer=.4f;e.direction=-1;
        i=new StageModel.Input();i.parry=true;m.step(i);check(e.alive && e.stun==0 && e.phase==1,"telegraph cannot be parried early");
        e.phase=2;e.timer=.1f;m.parryTime=.12f;m.step(new StageModel.Input());
        check(e.alive && e.stun>0 && e.phase==0,"normal parry only stuns incoming active strike");
        m=empty();m.player.x=100;m.player.y=100;e=new StageModel.Enemy(145,100);m.enemies.add(e);e.phase=2;e.timer=.1f;e.direction=-1;
        m.step(new StageModel.Input());check(!m.dead && m.woundTime>0,"active strike damages");
        m=empty();m.player.x=100;m.player.y=100;e=new StageModel.Enemy(145,100);m.enemies.add(e);e.phase=2;e.timer=.1f;e.direction=-1;e.upper=true;
        i=new StageModel.Input();i.parry=true;m.step(i);check(!m.dead && m.woundTime>0 && e.alive,"upper strike ignores all parries");
    }
}
