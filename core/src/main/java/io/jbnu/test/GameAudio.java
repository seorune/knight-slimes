package io.jbnu.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import java.util.HashMap;
import java.util.Map;

/** One-shot event audio; called once after each simulation tick, never from drawing. */
public final class GameAudio implements Disposable {
    public static final String IDS="boundary_cross brick_push clone_disappear clone_spawn exit_open jump just_parry land_heavy land_light melee_push parry player_death player_hit resurrect slime_melee slime_stun slime_to_brick slime_upper slime_windup spell_charge spell_line_hit stage_clear step_stone_01 step_stone_02 sword_swing time_resume time_stop ui_confirm ui_pause wound_recover";
    private final Map<String,Sound> sounds=new HashMap<>();
    private boolean muted;
    private int consumed,played,missing;
    public GameAudio(boolean muted){
        this.muted=muted;
        for(String id:IDS.split(" ")){
            try{sounds.put(id,Gdx.audio.newSound(Gdx.files.internal("audio/pixel_knight/"+id+".wav")));}
            catch(RuntimeException error){missing++;Gdx.app.error("GameAudio","Cannot decode "+id,error);}
        }
    }
    public void event(String id){consumed++;Sound s=sounds.get(id);if(s!=null && !muted){s.play(.32f);played++;}}
    public void toggle(){muted=!muted;if(muted)for(Sound s:sounds.values())s.stop();}
    public boolean muted(){return muted;}
    public int soundCount(){return sounds.size();}
    public int missingCount(){return missing;}
    public String stats(){return "sounds="+soundCount()+" missing="+missing+" consumed="+consumed+" played="+played;}
    @Override public void dispose(){for(Sound s:sounds.values())s.dispose();sounds.clear();}
}
