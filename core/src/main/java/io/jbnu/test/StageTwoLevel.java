package io.jbnu.test;

/** Vertical physical geometry and local hints; target/door rules live in StageModel. */
public final class StageTwoLevel {
    private StageTwoLevel() {}
    public static void populate(StageModel m){
        m.platforms.add(new StageModel.Box(0,0,1280,40));
        m.platforms.add(new StageModel.Box(0,2540,1280,20));
        m.platforms.add(new StageModel.Box(160,120,120,20));
        m.platforms.add(new StageModel.Box(0,220,120,20));
        m.platforms.add(new StageModel.Box(160,320,120,20));
        m.platforms.add(new StageModel.Box(0,420,120,20));
        m.enemies.add(new StageModel.Enemy(10,440));
        m.platforms.add(new StageModel.Box(1000,420,280,20));
        m.platforms.add(new StageModel.Box(900,580,130,20));
        for(int n=0;n<4;n++)m.platforms.add(new StageModel.Box(n%2==0?1060:900,680+n*100,n%2==0?120:130,20));
        m.platforms.add(new StageModel.Box(1100,1020,180,20));
        m.platforms.add(new StageModel.Box(0,1000,120,20));
        for(int n=0;n<7;n++)m.platforms.add(new StageModel.Box(n%2==0?160:0,1120+n*100,120,20));
        float[][] summit={{360,1840},{500,1940},{640,2040},{780,2140},{640,2240},{540,2340}};
        for(float[] p:summit)m.platforms.add(new StageModel.Box(p[0],p[1]-20,120,20));
        m.exit.x=600;m.exit.y=2340;m.exit.w=100;m.exit.h=130;
    }
    public static String objective(float y){
        if(y<440)return "Climb the west steps. Push the original left, then follow its clone.";
        if(y<600)return "L reveals the east mark. AFTER thaw, lead its living clone there BEFORE climbing; then freeze it as a step.";
        if(y<1040)return "Climb the east terraces. Cross the right edge to the upper west landing.";
        if(y<1740)return "Land between crossings. Climb the upper west switchbacks.";
        return "Climb the upper terraces to the summit exit.";
    }
}
