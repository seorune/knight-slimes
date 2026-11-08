package io.jbnu.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.HashMap;
import java.util.Map;

/** Manifest-driven nearest-filtered art. Cached regions are never flipped/mutated. */
public final class PixelAssets implements Disposable {
    private final Map<String,Texture> textures=new HashMap<>();
    private final Map<String,TextureRegion[]> frames=new HashMap<>();
    private final Map<String,JsonValue> clips=new HashMap<>();
    public final Clocks clocks=new Clocks();
    public static final class Clocks {
        public float player,world;
        public void advance(float dt,boolean running,boolean frozen){if(running){player+=dt;if(!frozen)world+=dt;}}
    }
    public static int frameIndex(float time,float duration,int count,boolean loop){
        int index=Math.max(0,(int)(time/duration));return loop?index%count:Math.min(index,count-1);
    }
    public static String playerPose(boolean dead,boolean guarding,float parryTime,float attackAge,boolean grounded,float vx,float vy,int gravity){
        return dead?"death":attackAge<.28f?"melee":guarding || parryTime>0?"guard":!grounded?(vy*gravity<0?"jump":"fall"):Math.abs(vx)>1?"run":"idle";
    }
    public static float protectionAlpha(float age){return 1f;}
    /** Pixel-snapped camera center; worlds smaller than the viewport stay centered. */
    public static float follow(float center,float size,float viewport){
        if(size<=viewport)return size/2f;
        return Math.max(viewport/2f,Math.min(size-viewport/2f,Math.round(center)));
    }
    public static boolean coolRegion(float y,float worldHeight){return y>=worldHeight/2f;}
    public static boolean isCoolRegion(int stage,float x,float y,float width,float height){return stage==1?x>=width/2f:coolRegion(y,height);}
    /** Intersect either palette half with the visible interval along its split axis. */
    public static float[] backgroundSpan(float start,float worldSize,float viewportSize,boolean cool){
        float lo=cool?Math.max(start,worldSize/2f):start;
        float hi=cool?start+viewportSize:Math.min(start+viewportSize,worldSize/2f);
        return new float[]{lo,Math.max(0,hi-lo)};
    }
    public static float stageProgress(int stage,float x,float y,float width,float height){
        return Math.max(0,Math.min(1,stage==0?x/width:y/height));
    }
    /** Visible warm/cool interval, cropped from a single viewport-fixed background. */
    public static float[] backgroundSpan(float bottom,float height,boolean cool){
        float start=cool?Math.max(bottom,height/2f):bottom;
        float end=cool?bottom+720:Math.min(bottom+720,height/2f);
        return new float[]{start,Math.max(0,end-start)};
    }
    public static boolean visible(float x,float y,float w,float h,float left,float bottom){
        return x+w>=left && x<=left+1280 && y+h>=bottom && y<=bottom+720;
    }
    /** Calm semantic poses; attack starts on a strike, never a fictitious windup. */
    public static int knightFrame(String pose,float age){
        switch(pose){
            case "guard":return 12;
            case "run":return 2+frameIndex(age,.18f,2,true);
            case "jump":return 6;
            case "fall":return 7;
            case "hit":return 13;
            case "death":return age<.14f?14:15;
            default:return 0;
        }
    }
    /** Real arm/blade character poses; no rotating slash overlay. */
    public static int swingFrame(float age){
        if(Float.isNaN(age) || age<0)return 0;
        return Math.min(7,(int)(age/.035f));
    }
    public static float swingX(float x,float width,int facing){return x+width/2-(facing<0?54:42);}
    public static float swingY(float y,float height,int gravity){return gravity<0?y-25:y+height-71;}
    public TextureRegion swing(float age){return frames.get("knight_swing")[swingFrame(age)];}
    // Body axes inspected in the actual nearest-upscaled sheet, not weapon-inclusive bounds.
    private static final int[] KNIGHT_ROOT={26,27,26,25,26,29,29,29,26,24,31,26,27,26,29,29};
    public static float knightRoot(int frame){return KNIGHT_ROOT[frame];}
    public static float knightX(float x,float width,int frame,int facing){return x+width/2-(facing<0?64-knightRoot(frame):knightRoot(frame));}
    public static int enemyFrame(int phase,boolean upper,boolean moving,float age){
        if(phase==1)return 4;
        if(phase==2)return upper?9:7;
        return moving?2+frameIndex(age,.22f,2,true):0;
    }
    public TextureRegion enemy(String group,int phase,boolean upper,boolean moving,boolean stunned,float age){return frames.get(group)[stunned?10:enemyFrame(phase,upper,moving,age)];}
    /** Defeat and one-shot break outrank exposure; earned exposure keeps its open core. */
    public static String bossState(int phase,boolean primed,boolean defeated,float breakTime){
        return defeated?"defeated":breakTime>0?"break":primed?"exposed":phase==1?"charge":phase==2?"attack":"sealed";
    }
    public static String hudObjective(int stage,boolean pending,boolean frozen,boolean primed,boolean doorOpen){
        if(stage==2){
            if(primed)return frozen?"WAIT":"K / PARRY";
            if(pending)return frozen?"WAIT":"J / PUSH";
            return "L / MARK";
        }
        if(doorOpen)return "EXIT";
        if(pending)return frozen?"MARK":"MOVE ECHO";
        return "L / MARK";
    }
    public static float bossChargeProgress(float timer){return Math.max(0,Math.min(1,1-timer/.8f));}
    /** Pause does not reveal a hidden target or hide a currently frozen one. */
    public static boolean targetVisible(int stage,float stopTime,boolean revealed,boolean solved,boolean hasTarget,boolean paused){
        return (stage==2 || stopTime>0) && revealed && !solved && hasTarget;
    }
    public static boolean targetVisible(float stopTime,boolean revealed,boolean solved,boolean hasTarget,boolean paused){
        return stopTime>0 && revealed && !solved && hasTarget;
    }
    /** Leave the entire inverted echo receiver [0,220] clear of objective panels. */
    public static float objectiveHudX(boolean boss,boolean targetVisible,int gravity){return boss && targetVisible && gravity>0?244:16;}
    public static String doorClip(boolean unlocked){return unlocked?"open":"closed";}
    /** A 154px target caption remains inside the world, including edge receivers. */
    public static float targetLabelX(float x,float worldWidth){return Math.max(8,Math.min(worldWidth-162,x-28));}
    /** Runtime objective hints distinguish a pending delivery from a consumed exposure. */
    public static String bossHint(boolean primed,boolean frozen,int phase,float timer,boolean targetActive,boolean hasTarget){
        if(frozen && hasTarget)return "TARGET MARKED / WAIT FOR THAW";
        if(primed)return phase==2 || (phase==1 && timer<=.08f)?"EXPOSED / TAP K NOW":"EXPOSED / TAP K ON HIT";
        if(targetActive && hasTarget)return "PUSH CLONE TO MARK / J";
        return "SEALED / L TO MARK CLONE TARGET";
    }
    /** Legacy signature retained for callers; runtime uses the target-aware overload. */
    public static String bossHint(boolean primed,boolean frozen,int phase,float timer){
        if(primed && frozen)return "ARMED / WAIT FOR THAW";
        if(!primed)return "L / REVEAL MARK, THEN THAW / GUIDE ECHO";
        return phase==2 || (phase==1 && timer<=.08f)?"EXPOSED / TAP K NOW":"EXPOSED / TAP K ON HIT";
    }
    /** Explain actual combat feedback without treating a defensive block as boss damage. */
    public static String bossFeedbackHint(String feedback){
        if(feedback.contains("SEALED"))return "NO SEAL DAMAGE / REVEAL THEN DELIVER LIVING ECHO";
        if(feedback.contains("BLOCK") || feedback.equals("PARRY"))return "BLOCK ONLY / TAP K ON HIT TO BREAK A SEAL";
        if(feedback.contains("SEAL") && feedback.contains("BROKEN"))return "ONE SEAL REMOVED / L TO REVEAL NEXT MARK";
        if(feedback.contains("DEFEAT"))return "ALL SEALS BROKEN";
        return feedback.equals("JUST PARRY")?"COUNTER / WOUND CLEARED / INVULNERABLE":"";
    }
    /** Legacy semantic indices retained for callers, not the dedicated boss runtime. */
    public static int bossFrame(int phase,boolean defeated){return defeated?11:phase==1?4:phase==2?7:0;}
    public TextureRegion boss(int frame){return frames.get("ranged_original")[frame];}
    public TextureRegion knight(String pose,float age){return frames.get("knight")[knightFrame(pose,age)];}
    /** Opaque baseline is row 54 inclusive (9 transparent pixels beneath feet). */
    public static float spriteY(float y,float height,int gravity){return gravity<0?y-9:y+height-55;}
    /** Keep floating information off the gravity-side walking surface. */
    public static float floatingHudShift(int gravity){return gravity>0?-500f:0f;}
    public static float floatingHudShift(int gravity,int stage,float cameraY){return stage==1 && cameraY>360?-500f:floatingHudShift(gravity);}
    public static float boundaryHudY(int gravity){return gravity>0?102:530;}
    public PixelAssets(){
        load("art/pixel_knight/manifest.json",true);
        load("art/terrain_knight/manifest.json",false);
        load("art/knight_swing/manifest.json",false);
        load("art/paradox_warden/manifest.json",false);
        texture("art/pixel_knight/background/warm_lower.png");
        texture("art/pixel_knight/background/cold_upper.png");
    }
    private Texture texture(String path){
        Texture t=textures.get(path);if(t==null){t=new Texture(Gdx.files.internal(path),false);t.setFilter(Texture.TextureFilter.Nearest,Texture.TextureFilter.Nearest);textures.put(path,t);}return t;
    }
    private void load(String path,boolean actors){
        JsonValue groups=new JsonReader().parse(Gdx.files.internal(path)).get("groups");
        for(JsonValue g=groups.child;g!=null;g=g.next){
            if(actors && g.name.startsWith("tiles_"))continue;
            Texture sheet=texture(g.getString("sheet").replaceFirst("^assets/",""));
            TextureRegion[] regions=new TextureRegion[g.getInt("frame_count")];
            for(JsonValue f=g.get("frames").child;f!=null;f=f.next){int[] r=f.get("rect").asIntArray();regions[f.getInt("index")]=new TextureRegion(sheet,r[0],r[1],r[2],r[3]);}
            frames.put(g.name,regions);clips.put(g.name,g.get("clips"));
        }
    }
    public TextureRegion frame(String group,String clip,float time){
        JsonValue c=clips.get(group).get(clip);int[] ids=c.get("frames").asIntArray();
        return frames.get(group)[ids[frameIndex(time,c.getFloat("frame_duration_seconds"),ids.length,c.getBoolean("loop"))]];
    }
    /** Drive a non-looping action clip by the authoritative model phase progress. */
    public TextureRegion phase(String group,String clip,float progress){
        int[] ids=clips.get(group).get(clip).get("frames").asIntArray();
        return frames.get(group)[ids[Math.min(ids.length-1,Math.max(0,(int)(progress*ids.length)))]];
    }
    public TextureRegion background(boolean cool){return new TextureRegion(texture("art/pixel_knight/background/"+(cool?"cold_upper":"warm_lower")+".png"));}
    public int textureCount(){return textures.size();}
    @Override public void dispose(){for(Texture t:textures.values())t.dispose();textures.clear();}
}
