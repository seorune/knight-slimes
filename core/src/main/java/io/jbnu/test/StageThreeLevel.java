package io.jbnu.test;

/** Single-screen arena: open central top/bottom portals and paired side ledges. */
public final class StageThreeLevel {
    private StageThreeLevel() {}
    public static void populate(StageModel m){
        for(int x:new int[]{0,1060}){
            m.platforms.add(new StageModel.Box(x,0,220,40));
            m.platforms.add(new StageModel.Box(x,680,220,40));
        }
        m.platforms.add(new StageModel.Box(420,210,160,20));
        m.platforms.add(new StageModel.Box(700,490,160,20));
        // Two ordinary source originals: floor-left and its inverted ceiling-right partner.
        // Both retain patrol, gravity-relative upper retaliation and normal portal lifecycle.
        m.enemies.add(new StageModel.Enemy(15,40));
        m.enemies.add(new StageModel.Enemy(1220,636,1));
        m.exit.x=590;m.exit.y=300;m.exit.w=100;m.exit.h=100;
    }
}
