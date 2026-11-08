package io.jbnu.test;

/** Route recording uses observations and ordinary inputs only; replay uses fixed RLE. */
public final class StageThreeRouteTest {
    static final class Run {
        final StageModel m;final StringBuilder trace=new StringBuilder();
        final java.util.Map<String,Integer> events=new java.util.TreeMap<String,Integer>();
        int ticks,last=-1,run,charges,seals,guardHold,waves,side=1;
        float px(){return side>0?m.player.x:1250-m.player.x;}
        float ex(StageModel.Enemy e){return side>0?e.x:1236-e.x;}
        float ground(){return side>0?40:636;}
        void goLocal(float x){go(side>0?x:1250-x,ground());}
        Run(StageModel m){this.m=m;}
        void tick(StageModel.Input i){
            int mask=i.move+1|(i.jump?4:0)|(i.melee?8:0)|(i.parry?16:0)|(i.stop?32:0)|(i.guard?64:0);
            if(mask==last)run++;else{if(run>0)trace.append(last).append('x').append(run).append(',');last=mask;run=1;}
            m.step(i);ticks++;
            for(StageModel.GameEvent e:m.events){events.put(e.id,events.containsKey(e.id)?events.get(e.id)+1:1);if(e.id.equals("boss_exposed"))charges++;if(e.id.equals("boss_seal_break"))seals++;}
            if(m.dead)throw new AssertionError("stage3 died "+state());
        }
        String state(){return "tick="+ticks+" player="+m.player.x+","+m.player.y+" boss="+m.boss.phase+"/"+m.boss.timer+" seals="+m.boss.seals+" stop="+m.stopTime+" wound="+m.woundTime+" enemies="+m.enemies.size();}
        void defend(StageModel.Input i){
            i.guard=true;
            if(m.boss.phase==1 && m.boss.timer<.14f && m.boss.timer>=.04f && m.parryCooldown==0)i.guard=false;
            if(guardHold==0 && m.stopTime==0 && m.boss.phase==1 && m.boss.timer<.04f && m.parryCooldown==0)guardHold=8;
            i.parry=guardHold==8;if(guardHold>0){i.guard=true;guardHold--;}
        }
        void waitTicks(int count){for(int n=0;n<count;n++){StageModel.Input i=new StageModel.Input();defend(i);tick(i);}}
        StageModel.Enemy liveClone(){for(StageModel.Enemy e:m.enemies)if(e.alive && e.clone && e.gravity==-side)return e;return null;}
        void go(float x,float y){
            for(int n=0;n<650;n++){
                if(m.won || (m.player.grounded && Math.abs(m.player.y-y)<.2f && Math.abs(m.player.x-x)<3))return;
                StageModel.Input i=new StageModel.Input();i.move=Math.abs(m.player.x-x)<1.1?0:m.player.x<x?1:-1;i.jump=m.player.grounded && (m.player.gravity<0?m.player.y<y-1:m.player.y>y+1);
                if(m.player.grounded)for(StageModel.Enemy e:m.enemies)if(e.clone && Math.abs(e.y-m.player.y)<4 && Math.abs(e.x-m.player.x)<95 && (e.x-m.player.x)*i.move>0)i.jump=true;
                defend(i);tick(i);
            }
            throw new AssertionError("go "+x+","+y+" stuck "+state());
        }
        void spawnAndFollow(){
            StageModel.Enemy source=m.enemies.get(side>0?0:1);
            if(waves>0){
                for(int n=0;n<200 && px()>500;n++){StageModel.Input i=new StageModel.Input();i.move=side;defend(i);tick(i);}
                waitTicks(3);
                for(int n=0;n<180;n++){
                    if(m.player.grounded && px()>ex(source)+source.w+1 && Math.abs(m.player.y-ground())<1)break;
                    StageModel.Input i=new StageModel.Input();i.move=px()<ex(source)+60?side:0;i.jump=m.player.grounded && px()<ex(source)+60;defend(i);tick(i);
                }
            }
            int before=events.containsKey("clone_spawn")?events.get("clone_spawn"):0;
            for(int n=0;n<180;n++){
                if((events.containsKey("clone_spawn")?events.get("clone_spawn"):0)>before)break;
                StageModel.Input i=new StageModel.Input();i.move=-side;i.melee=true;defend(i);tick(i);
            }
            if(liveClone()==null)throw new AssertionError("no J-created clone "+state());
            for(int n=0;n<180 && px()<500;n++){StageModel.Input i=new StageModel.Input();i.move=-side;i.jump=m.player.grounded;defend(i);tick(i);}
            goLocal(1250);waves++;
        }
        void deliver(){
            int before=m.boss.seals;
            while(m.stopCooldown>0 || m.stopTime>0)waitTicks(1);
            StageModel.Input l=new StageModel.Input();l.stop=true;defend(l);tick(l);
            StageModel.Enemy c=liveClone();if(c==null || m.echoTarget==null)throw new AssertionError("mark missing "+state());
            float center=side>0?m.echoTarget.x+32:1280-m.echoTarget.x-32;int direction=center<ex(c)+22?-1:1;
            float x=direction<0?ex(c)+54:ex(c)-40;goLocal(x);
            while(m.stopTime>0)waitTicks(1);
            for(int n=0;n<200 && !m.boss.primed;n++){
                StageModel.Input j=new StageModel.Input();j.move=direction*side;j.melee=m.attackCooldown==0;defend(j);tick(j);
            }
            if(!m.boss.primed)throw new AssertionError("post-thaw J delivery failed "+state());
            System.out.println("DELIVERY "+state());goLocal(1230);
            for(int n=0;n<1000 && m.boss.seals==before;n++)waitTicks(1);
            if(m.boss.seals==before)throw new AssertionError("no fresh keyboard just "+state());
        }
        String finish(){return trace.toString()+last+"x"+run;}
    }
    static Run record(StageModel m){
        Run r=new Run(m);for(int wave=0;wave<3;wave++){r.spawnAndFollow();r.deliver();}
        StageModelTest.check(m.won && !m.dead && r.charges==3 && r.seals==3,"three actual thawed J deliveries and fresh keyboard just edges defeat boss");
        System.out.println("STAGE3_ROUTE "+r.state()+" events="+r.events+" RLE="+r.finish());return r;
    }
    static final String TRACE="72x22,68x1,64x49,65x50,97x1,64x10,68x1,64x17,65x331,72x1,64x14,66x29,65x59,1x12,81x1,65x4,66x23,65x3,70x1,66x78,65x21,72x48,68x1,64x47,65x88,1x12,81x1,65x37,97x1,68x1,64x58,65x300,74x1,66x32,70x1,66x21,65x244,1x12,81x1,65x4,66x22,65x3,70x1,66x49,65x41,66x1,65x2,66x1,65x3,66x1,65x1,72x26,68x1,64x49,65x50,97x1,64x4,68x1,64x59,65x295,74x1,66x32,70x1,66x25,2x1,1x11,81x1,65x336,1x12,81x1,65x4";

    static void legalHorizontalFloorEntry(){
        StageModel m=new StageModel();m.loadStage(2);Run r=new Run(m);r.spawnAndFollow();
        for(int n=0;n<40 && m.player.x>500;n++){StageModel.Input i=new StageModel.Input();i.move=1;r.defend(i);r.tick(i);}
        for(StageModel.Box p:m.platforms)StageModelTest.check(!m.player.overlaps(p),"input-only floor X portal does not embed player");
        StageModelTest.check(!m.dead && m.boundaryChain==1 && !m.player.grounded,"floor X portal retains pending real landing");
        r.waitTicks(1);StageModelTest.check(!m.dead && m.player.grounded && m.boundaryChain==0,"receiving floor resets chain only on next real collision");
        r.waitTicks(180);StageModelTest.check(!m.dead,"input-only floor X crossing never causes unintended Y portal death");
    }
    static Run recordUpper(StageModel m){
        Run r=new Run(m);
        for(int n=0;n<220 && m.boundaryChain==0;n++){StageModel.Input i=new StageModel.Input();i.move=-1;i.jump=m.player.grounded;r.defend(i);r.tick(i);}
        r.go(1100,40);
        for(int n=0;n<300 && m.player.gravity<0;n++){StageModel.Input i=new StageModel.Input();i.move=m.player.x>1030?-1:0;r.defend(i);r.tick(i);}
        r.side=-1;r.go(1090,636);
        for(int wave=0;wave<3;wave++){r.spawnAndFollow();r.deliver();}
        StageModelTest.check(m.won && !m.dead && r.charges==3 && r.seals==3,"upper original and three inverted ordinary J deliveries legitimately defeat boss");
        System.out.println("STAGE3_UPPER_ROUTE "+r.state()+" events="+r.events+" RLE="+r.finish());return r;
    }
    public static void main(String[] args){
        if(args.length>0 && args[0].equals("upper")){
            for(int seed=0;seed<12;seed++){
                StageModel a=new StageModel(seed);a.loadStage(2);Run recorded=recordUpper(a);
                StageModel b=new StageModel(seed);b.loadStage(2);Run blind=replay(b,recorded.finish());
                StageModelTest.check(EchoRouteTest.stateJson(a).equals(EchoRouteTest.stateJson(b)) && recorded.events.equals(blind.events),"upper random-seed route exact state and events seed="+seed);
                System.out.println("BOSS_UPPER_SEED_ROUTE_OK seed="+seed+" ticks="+recorded.ticks);
            }
            System.out.println("PASS twelve upper routes "+StageModelTest.passed);return;
        }
        if(args.length>0 && args[0].equals("seeds")){
            for(int seed=0;seed<12;seed++){
                StageModel a=new StageModel(seed);a.loadStage(2);Run recorded=record(a);
                StageModel b=new StageModel(seed);b.loadStage(2);Run blind=replay(b,recorded.finish());
                StageModelTest.check(EchoRouteTest.stateJson(a).equals(EchoRouteTest.stateJson(b)) && recorded.events.equals(blind.events),"random-seed real-input boss route exact replay seed="+seed);
                System.out.println("BOSS_SEED_ROUTE_OK seed="+seed+" ticks="+recorded.ticks);
            }
            System.out.println("PASS twelve seed boss routes "+StageModelTest.passed);return;
        }
        legalHorizontalFloorEntry();
        StageModel m=new StageModel();m.loadStage(2);Run recording=record(m);String trace=recording.finish();
        StageModelTest.check(trace.equals(TRACE),"observational recording exactly retains committed fixed keyboard RLE");
        StageModel fresh=new StageModel();fresh.loadStage(2);Run replay=replay(fresh,trace);
        StageModelTest.check(StageModelTest.sameState(m,fresh) && EchoRouteTest.stateJson(m).equals(EchoRouteTest.stateJson(fresh)),"boss replay exact public/private state including target and push stamps");
        StageModelTest.check(recording.events.equals(replay.events),"recording and blind replay exact events");
        if(args.length>0 && args[0].equals("record"))return;
        StageModel campaign=new StageModel();EchoRouteTest.Run one=EchoRouteTest.one(campaign);EchoRouteTest.verify(one,0);
        StageModel.Input enter=new StageModel.Input();enter.start=true;campaign.step(enter);
        EchoRouteTest.Run two=EchoRouteTest.two(campaign);EchoRouteTest.verify(two,0);campaign.step(enter);
        StageModelTest.check(campaign.stageIndex==2 && !campaign.won && campaign.boss.seals==3,"real cleared stages Enter initializes boss stage");
        Run full=replay(campaign,trace);
        StageModelTest.check(EchoRouteTest.stateJson(fresh).equals(EchoRouteTest.stateJson(campaign)) && full.events.equals(replay.events),"continuous three-stage campaign matches fresh boss private state and events");
        StageModel blind=new StageModel();StageModelTest.playTrace(blind,one.finish());blind.step(enter);StageModelTest.playTrace(blind,two.finish());blind.step(enter);StageModelTest.playTrace(blind,trace);
        StageModelTest.check(EchoRouteTest.stateJson(campaign).equals(EchoRouteTest.stateJson(blind)),"full input-only campaign blind replay exact state JSON");
        campaign.step(enter);StageModelTest.check(campaign.stageIndex==2 && campaign.won && campaign.events.isEmpty(),"final clear Enter cannot advance campaign");
        StageModel expectedStart=new StageModel();expectedStart.step(enter);
        campaign.step(StageModelTest.newCampaignInput());
        StageModelTest.check(campaign.stageIndex==0 && campaign.started && !campaign.won && !campaign.dead,"N after actual three-stage clear returns to playable stage one");
        StageModelTest.check(EchoRouteTest.stateJson(campaign).equals(EchoRouteTest.stateJson(expectedStart)),"real campaign N resets every public/private scalar and seeded stage-one state");
        StageModelTest.check(campaign.events.size()==1 && StageModelTest.has(campaign,"ui_confirm"),"real campaign N discards victory events and confirms once");
        StageModel.Input move=new StageModel.Input();move.move=1;campaign.step(move);expectedStart.step(move);
        StageModelTest.check(EchoRouteTest.stateJson(campaign).equals(EchoRouteTest.stateJson(expectedStart)) && campaign.player.x>90,"new campaign immediately responds exactly like fresh stage one");
        System.out.println("CAMPAIGN_RESTART_OK stage=1 exactFreshState=true confirmOnce=true playable=true");
        System.out.println("PASS "+StageModelTest.passed+" stage-three route/campaign checks");
        System.out.println("STAGE3_REPLAY_OK exactState=true eventsMatch=true charges=3 seals=3 CAMPAIGN_OK stage1Ticks="+one.ticks+" stage2Ticks="+two.ticks+" stage3Ticks="+recording.ticks);
    }
    static Run replay(StageModel m,String trace){
        Run r=new Run(m);
        for(String part:trace.split(",")){String[] pair=part.split("x");int mask=Integer.parseInt(pair[0]),count=Integer.parseInt(pair[1]);
            for(int n=0;n<count;n++){StageModel.Input i=new StageModel.Input();i.move=(mask&3)-1;i.jump=(mask&4)!=0;i.melee=(mask&8)!=0;i.parry=(mask&16)!=0;i.stop=(mask&32)!=0;i.guard=(mask&64)!=0;r.tick(i);}}
        StageModelTest.check(m.won && !m.dead && m.boss.defeated && r.charges==3 && r.seals==3,"blind boss input replay wins with three real melee charges");return r;
    }

}
