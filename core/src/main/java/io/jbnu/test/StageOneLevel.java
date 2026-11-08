package io.jbnu.test;

/** Stage-one physical geometry and local hints; target/door rules live in StageModel. */
public final class StageOneLevel {
    private StageOneLevel() {}
    // x, y, width, height. Warm floor -> momentum shaft -> cold shelf -> clone stair -> brick stair.
    private static final float[][] TERRAIN={
        {0,0,220,40},{220,0,140,140},{360,0,160,240},
        {780,540,260,24},{1040,680,480,40},
        {1520,0,680,40},{1850,40,350,170},
        {2200,0,850,40},{3050,0,1150,190}
    };
    public static void populate(StageModel m){
        for(float[] p:TERRAIN)m.platforms.add(new StageModel.Box(p[0],p[1],p[2],p[3]));
        m.enemies.add(new StageModel.Enemy(1410,636,1));
        StageModel.Enemy caster=new StageModel.Enemy(2780,40);caster.ranged=true;m.enemies.add(caster);
        m.exit.x=4050;m.exit.y=190;m.exit.w=100;m.exit.h=130;
    }
    public static String objective(float x){
        if(x<520)return "Climb the steps. Walk off; keep your falling momentum.";
        if(x<1040)return "Cross the lower edge. Reach the cold shelf from its left side.";
        if(x<1520)return "L reveals the lower mark. AFTER thaw, push the ceiling original beyond the edge; follow its clone.";
        if(x<2200)return "Freeze the clone near the high step. Stand on it, then jump right.";
        if(x<3050)return "Parry the straight spell. Push its brick to the step, then jump from it.";
        return "Follow the warm ledge to the exit.";
    }
}
