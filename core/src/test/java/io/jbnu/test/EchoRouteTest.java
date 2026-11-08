package io.jbnu.test;
import java.util.*;

/** Observation-driven ordinary keyboard routes; fresh blind RLE and continuous campaign replay. No state writes. */
public final class EchoRouteTest {
    static final class Run {
        final StageModel m;final StringBuilder trace=new StringBuilder();
        final Map<String,Integer> events=new TreeMap<String,Integer>();
        final Set<String> goals=new TreeSet<String>();
        int last=-1,run,ticks,guardHold;
        String recordedTrace;
        Run(StageModel m){this.m=m;}
        String state(){String s="stage="+m.stageIndex+" tick="+ticks+" p="+m.player.x+","+m.player.y+" g="+m.player.gravity+" stop="+m.stopTime+" solved="+m.targetSolved+" target="+(m.echoTarget==null?"null":m.echoTarget.x+","+m.echoTarget.y);for(StageModel.Enemy e:m.enemies)s+=" e="+e.x+","+e.y+"/"+e.clone+"/"+e.phase;return s;}
        void tick(StageModel.Input i){
            int mask=(i.move+1)|(i.jump?4:0)|(i.melee?8:0)|(i.parry?16:0)|(i.stop?32:0)|(i.guard?64:0)|(i.start?128:0);
            if(mask==last)run++;else{if(run>0)trace.append(last).append('x').append(run).append(',');last=mask;run=1;}
            m.step(i);ticks++;
            for(StageModel.GameEvent e:m.events){events.put(e.id,events.containsKey(e.id)?events.get(e.id)+1:1);if(e.id.equals("clone_disappear"))System.out.println("CLONE_LOST "+state()+" event="+e.x+","+e.y);}
            if(m.targetSolved && m.echoTarget!=null)goals.add(m.echoTarget.x+":"+m.echoTarget.y);
            if(m.dead)throw new AssertionError("ordinary-input route death "+state());
        }
        String finish(){return recordedTrace!=null?recordedTrace:trace.toString()+last+"x"+run;}
        StageModel.Input input(){return new StageModel.Input();}
        void defend(StageModel.Input i){
            if(m.boss==null){i.guard=true;return;}
            i.guard=true;
            if(m.boss.phase==1 && m.boss.timer<.14f && m.boss.timer>=.04f && m.parryCooldown==0)i.guard=false;
            if(guardHold==0 && m.stopTime==0 && m.boss.phase==1 && m.boss.timer<.04f && m.parryCooldown==0)guardHold=8;
            i.parry=guardHold==8;if(guardHold>0){i.guard=true;guardHold--;}
        }
        void waitTicks(int count){for(int n=0;n<count;n++){StageModel.Input i=input();defend(i);tick(i);}}
        void go(float x,float y){
            for(int n=0;n<700;n++){
                if(m.won || (m.player.grounded && Math.abs(m.player.y-y)<.1f && Math.abs(m.player.x-x)<3))return;
                StageModel.Input i=input();i.move=Math.abs(m.player.x-x)<1.1f?0:m.player.x<x?1:-1;
                i.jump=m.player.grounded && (m.player.gravity<0?m.player.y<y-1:m.player.y>y+1);
                if(m.player.grounded)for(StageModel.Enemy e:m.enemies)if(e.clone && Math.abs(e.y-m.player.y)<4 && Math.abs(e.x-m.player.x)<95 && (e.x-m.player.x)*i.move>0)i.jump=true;
                defend(i);tick(i);
            }
            throw new AssertionError("go "+x+","+y+" stuck "+state());
        }
        StageModel.Enemy liveClone(){for(StageModel.Enemy e:m.enemies)if(e.alive && e.clone)return e;return null;}
        void deliver(){
            if(m.echoTarget==null){StageModel.Input l=input();l.stop=true;defend(l);tick(l);}
            if(m.echoTarget==null)throw new AssertionError("no reachable target "+state());
            StageModel.Box b=m.echoTarget;float center=b.x+b.w/2;
            StageModel.Enemy c=liveClone();if(c==null)throw new AssertionError("no live clone "+state());
            int direction=center<c.x+c.w/2?-1:1;
            float goal=center+(direction<0?-135:105);
            float min=m.stageIndex==0?1520:m.stageIndex==1?971:m.targetGravity<0?1031:-29;
            float max=m.stageIndex==0?1818:m.stageIndex==1?1279:m.targetGravity<0?1279:219;
            goal=Math.max(min,Math.min(max,goal));
            for(int n=0;n<2200 && !m.targetSolved;n++){
                StageModel.Input i=input();i.move=Math.abs(m.player.x-goal)<1.2?0:m.player.x<goal?1:-1;
                // Jump across solid clone when changing sides; normal J is not required for chasing.
                i.jump=m.player.grounded && Math.abs(m.player.x-c.x)<90 && ((direction<0 && m.player.x>c.x)||(direction>0 && m.player.x<c.x));
                defend(i);tick(i);
            }
            if(!m.targetSolved)throw new AssertionError("delivery stuck lure="+goal+" "+state());
        }
    }
    static Run one(StageModel m){
        Run r=new Run(m);StageModel.Input enter=r.input();enter.start=true;r.tick(enter);
        int phase=0;boolean delivered=false;
        for(int n=0;n<8000 && !m.won;n++){
            StageModel.Enemy source=m.enemies.get(0),caster=m.enemies.get(1),c=r.liveClone();StageModel.Input i=r.input();
            if(phase==0){i.move=1;i.jump=m.player.grounded && ((m.player.x>=180 && m.player.x<220)||(m.player.x>=325 && m.player.x<360));if(m.player.gravity==1 && Math.abs(source.y-m.player.y)<50 && source.x-m.player.x<90 && source.x>m.player.x){if(m.echoTarget==null){i.stop=true;i.move=0;}else if(m.stopTime>0)i.move=0;else{i.melee=true;phase=1;}}}
            else if(phase==1){i.move=m.player.x<1815?1:0;if(m.player.gravity==-1 && m.player.grounded && m.player.x>=1815 && c!=null){r.deliver();delivered=true;phase=2;continue;}}
            else if(phase==2){i.move=m.player.x<1815?1:0;if(c!=null && c.grounded && c.x>1700 && m.stopCooldown==0){i.stop=true;i.jump=true;i.move=-1;phase=3;}}
            else if(phase==3){if(c!=null){i.move=m.player.x>c.x+8?-1:0;if(m.player.grounded && Math.abs(m.player.y-c.y-c.h)<.1f){i.jump=true;i.move=1;phase=4;}}}
            else if(phase==4){i.move=1;if(m.player.x>2300 && m.player.grounded)phase=5;}
            else if(phase==5){if(caster.phase==1 && caster.timer<.035f)i.parry=true;if(!caster.alive)phase=6;}
            else if(phase==6){i.move=1;if(caster.x-m.player.x<90 && caster.x>=m.player.x && m.attackCooldown==0)i.melee=true;if(caster.x>=3005 && caster.grounded){phase=7;i.melee=false;i.jump=m.player.grounded;}}
            else if(phase==7){i.move=m.player.x<caster.x+4?1:0;if(m.player.grounded && Math.abs(m.player.y-caster.y-caster.h)<.1f){i.jump=true;i.move=1;phase=8;}}
            else i.move=1;
            r.tick(i);
        }
        StageModelTest.check(m.won && delivered && m.doorUnlocked,"ordinary stage one random target and exit "+r.state());return r;
    }
    static Run two(StageModel m){
        Run r=new Run(m);r.go(200,140);r.go(65,240);r.go(200,340);r.go(80,440);
        StageModel.Input reveal=r.input();reveal.stop=true;r.tick(reveal);
        while(m.stopTime>0)r.waitTicks(1);
        for(int n=0;n<160 && r.liveClone()==null;n++){StageModel.Input i=r.input();i.move=-1;i.melee=true;i.guard=true;r.tick(i);}
        for(int n=0;n<180 && m.boundaryChain==0;n++){StageModel.Input i=r.input();i.move=-1;i.jump=m.player.grounded;i.guard=true;r.tick(i);}
        r.deliver();
        // Lure clone near the shelf before freezing it as an ordinary physical step.
        if(m.player.y<599){
            r.go(1005,440);
            for(int n=0;n<900 && (r.liveClone().x>1090 || m.stopCooldown>0);n++)r.waitTicks(1);
            StageModel.Input l=r.input();l.stop=true;r.tick(l);StageModel.Enemy c=r.liveClone();r.go(c.x+6,484);
        }
        r.go(985,600);r.go(1100,700);r.go(985,800);r.go(1100,900);r.go(985,1000);r.go(1200,1040);
        for(int n=0;n<90 && m.boundaryChain==0;n++){StageModel.Input i=r.input();i.move=1;r.tick(i);}
        r.go(60,1020);for(int n=0;n<7;n++)r.go(n%2==0?200:65,1140+n*100);
        r.go(400,1840);r.go(540,1940);r.go(680,2040);r.go(820,2140);r.go(680,2240);r.go(600,2340);
        StageModelTest.check(m.won && m.doorUnlocked,"ordinary stage two random target and summit");return r;
    }
    static Run three(StageModel m){return three(m,false);}
    static Run three(StageModel m,boolean upper){return three(m,upper,0);}
    static Run three(StageModel m,boolean upper,long seed){
        // Record on the actual campaign model; never replay onto its already-won state.
        StageThreeRouteTest.Run recorded=upper?StageThreeRouteTest.recordUpper(m):StageThreeRouteTest.record(m);
        StageModel fresh=new StageModel(seed);fresh.loadStage(2);Run blind=new Run(fresh);
        for(String part:recorded.finish().split(",")){
            String[] pair=part.split("x");int mask=Integer.parseInt(pair[0]),count=Integer.parseInt(pair[1]);
            for(int n=0;n<count;n++){
                StageModel.Input i=blind.input();i.move=(mask&3)-1;i.jump=(mask&4)!=0;i.melee=(mask&8)!=0;
                i.parry=(mask&16)!=0;i.stop=(mask&32)!=0;i.guard=(mask&64)!=0;blind.tick(i);
            }
        }
        StageModelTest.check(stateJson(m).equals(stateJson(fresh)) && recorded.events.equals(blind.events),"new boss route imports exact input trace, goals and events");
        Run result=new Run(m);result.recordedTrace=recorded.finish();result.ticks=recorded.ticks;
        result.events.putAll(recorded.events);result.goals.addAll(blind.goals);
        StageModelTest.check(result.events.get("boss_exposed")==3 && result.events.get("boss_seal_break")==3 && m.won && !m.dead,"three post-mark thawed J deliveries and fresh just parries");
        return result;
    }
    static void verify(Run r,long seed){
        StageModel fresh=new StageModel(seed);if(r.m.stageIndex!=0)fresh.loadStage(r.m.stageIndex);
        Map<String,Integer> events=StageModelTest.playTrace(fresh,r.finish());
        StageModelTest.check(StageModelTest.sameState(r.m,fresh) && stateJson(r.m).equals(stateJson(fresh)) && events.equals(r.events),"blind fixed input replay matches full public/private state/events stage "+r.m.stageIndex);
        System.out.println("ECHO_ROUTE stage="+r.m.stageIndex+" seed="+seed+" ticks="+r.ticks+" goals="+r.goals+" RLE="+r.finish());
    }
    static void scalars(Map<String,String> out,String name,Object value){
        if(value==null){out.put(name,"null");return;}
        try{for(Class<?> type=value.getClass();type!=null && type!=Object.class;type=type.getSuperclass())for(java.lang.reflect.Field f:type.getDeclaredFields()){
            if(java.lang.reflect.Modifier.isStatic(f.getModifiers()))continue;
            if(f.getType().isPrimitive() || f.getType()==String.class){f.setAccessible(true);out.put(name+"."+f.getName(),String.valueOf(f.get(value)));}
        }}catch(IllegalAccessException e){throw new AssertionError(e);}
    }
    static String stateJson(StageModel m){
        Map<String,String> out=new TreeMap<String,String>();scalars(out,"model",m);scalars(out,"player",m.player);scalars(out,"boss",m.boss);scalars(out,"target",m.echoTarget);scalars(out,"exit",m.exit);
        out.put("enemies.size",String.valueOf(m.enemies.size()));out.put("platforms.size",String.valueOf(m.platforms.size()));out.put("events.size",String.valueOf(m.events.size()));
        for(int n=0;n<m.enemies.size();n++){scalars(out,"enemy"+n,m.enemies.get(n));out.put("enemy"+n+".original",String.valueOf(m.enemies.indexOf(m.enemies.get(n).original)));}
        for(int n=0;n<m.platforms.size();n++)scalars(out,"platform"+n,m.platforms.get(n));
        for(int n=0;n<m.events.size();n++)scalars(out,"event"+n,m.events.get(n));
        StringBuilder json=new StringBuilder("{");for(Map.Entry<String,String> e:out.entrySet()){if(json.length()>1)json.append(',');json.append('"').append(e.getKey()).append("\":\"").append(e.getValue().replace("\\","\\\\").replace("\"","\\\"")).append('"');}return json.append('}').toString();
    }
    static Run[] campaign(long seed,boolean upper){
        StageModel m=new StageModel(seed);Run a=one(m);verify(a,seed);
        StageModel.Input enter=new StageModel.Input();enter.start=true;m.step(enter);
        StageModelTest.check(m.stageIndex==1 && !m.won && StageModelTest.has(m,"ui_confirm"),"real clear Enter opens stage two");
        Run b=two(m);verify(b,seed);m.step(enter);
        StageModelTest.check(m.stageIndex==2 && m.boss.seals==3 && StageModelTest.has(m,"ui_confirm"),"real clear Enter opens three-seal arena");
        Run c=three(m,upper,seed);verify(c,seed);
        StageModel blind=new StageModel(seed);StageModelTest.playTrace(blind,a.finish());blind.step(enter);StageModelTest.playTrace(blind,b.finish());blind.step(enter);StageModelTest.playTrace(blind,c.finish());
        StageModelTest.check(stateJson(m).equals(stateJson(blind)),"complete real Enter campaign full deterministic JSON blind replay");
        m.step(enter);StageModelTest.check(m.won && m.stageIndex==2 && m.events.isEmpty(),"last seal retains final campaign priority");
        System.out.println("ECHO_CAMPAIGN_OK seed="+seed+" upper="+upper+" exactStateJSON=true eventsMatch=true");return new Run[]{a,b,c};
    }
    static void coverage(){
        List<Set<String>> goals=Arrays.<Set<String>>asList(new TreeSet<String>(),new TreeSet<String>(),new TreeSet<String>());
        int campaigns=0;
        for(long seed=0;seed<12;seed++)for(boolean upper:new boolean[]{false,true}){
            Run[] routes=campaign(seed,upper);campaigns++;
            for(int stage=0;stage<3;stage++)goals.get(stage).addAll(routes[stage].goals);
        }
        StageModelTest.check(goals.get(0).size()==3 && goals.get(1).size()==3 && goals.get(2).size()>6,"ordinary campaigns cover all three door marks and more than six continuous boss marks");
        int privateScalars=0;for(Class<?> t:new Class<?>[]{StageModel.class,StageModel.Enemy.class,StageModel.Boss.class})for(java.lang.reflect.Field f:t.getDeclaredFields())if(java.lang.reflect.Modifier.isPrivate(f.getModifiers()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers()) && (f.getType().isPrimitive() || f.getType()==String.class))privateScalars++;
        System.out.println("ECHO_CANDIDATE_COVERAGE_OK campaigns="+campaigns+" stage1=3/3 stage2=3/3 bossContinuousMarks="+goals.get(2).size()+" privateScalarFields="+privateScalars+" goals="+goals);
    }
    public static void main(String[] args){
        if(args.length==0){coverage();return;}
        long seed=Long.parseLong(args[0]);
        if(args.length>1){StageModel upper=new StageModel(seed);upper.loadStage(2);Run u=three(upper,true,seed);verify(u,seed);return;}
        campaign(seed,false);
    }
}
