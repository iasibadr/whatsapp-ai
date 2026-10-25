package live.pharmaradio.whatsappai;

import android.content.Context;
import android.media.*;
import org.json.*;

/** Numeric metadata only. Never serialize configuration/device objects or their names. */
final class RouteDiagnostics {
    static JSONObject device(AudioDeviceInfo d) throws JSONException {
        return new JSONObject().put("present",d!=null)
            .put("id",d==null?-1:d.getId()).put("type",d==null?-1:d.getType());
    }
    static JSONObject recordings(Context context, AudioManager audio) {
        JSONObject result=new JSONObject(); JSONArray matches=new JSONArray();
        try {
            int uid=context.getPackageManager().getApplicationInfo("com.whatsapp",0).uid;
            for(AudioRecordingConfiguration c:audio.getActiveRecordingConfigurations()) {
                int client=(Integer)AudioRecordingConfiguration.class.getMethod("getClientUid").invoke(c);
                if(client!=uid) continue;
                if(matches.length()>=8) { result.put("truncated",true); break; }
                matches.put(new JSONObject().put("uid",client)
                    .put("source",c.getClientAudioSource()).put("session",c.getClientAudioSessionId())
                    .put("silenced",c.isClientSilenced()).put("device",device(c.getAudioDevice()))
                    .put("client_rate",c.getClientFormat().getSampleRate())
                    .put("device_rate",c.getFormat().getSampleRate()));
            }
            result.put("available",true).put("whatsapp_inputs",matches);
        } catch(Exception e) { try { result.put("available",false).put("error_type",e.getClass().getSimpleName()); } catch(JSONException ignored) {} }
        return result;
    }
    static JSONObject snapshot(Context context,AudioManager audio,AudioTrack track,AudioRecord record) {
        JSONObject result=new JSONObject();
        try {
            result.put("whatsapp",recordings(context,audio));
            if(track!=null) {
                AudioDeviceInfo output=track.getRoutedDevice();
                int uid=context.getPackageManager().getApplicationInfo("com.whatsapp",0).uid;
                JSONArray links=new JSONArray();
                for(AudioRecordingConfiguration c:audio.getActiveRecordingConfigurations()) {
                    int client=(Integer)AudioRecordingConfiguration.class.getMethod("getClientUid").invoke(c);
                    if(client!=uid || links.length()>=8) continue;
                    AudioDeviceInfo input=c.getAudioDevice();
                    boolean comparable=output!=null && input!=null
                        && output.getType()==AudioDeviceInfo.TYPE_REMOTE_SUBMIX
                        && input.getType()==AudioDeviceInfo.TYPE_REMOTE_SUBMIX
                        && !output.getAddress().isEmpty() && !input.getAddress().isEmpty();
                    links.put(new JSONObject().put("session",c.getClientAudioSessionId())
                        .put("address_comparison_available",comparable)
                        .put("same_submix_address",comparable && output.getAddress().equals(input.getAddress())));
                }
                result.put("injection_links",links);
                AudioTimestamp stamp=new AudioTimestamp(); boolean has=track.getTimestamp(stamp);
                result.put("track_device",device(track.getRoutedDevice()))
                    .put("track_session",track.getAudioSessionId())
                    .put("play_state",track.getPlayState())
                    .put("playback_head_frames",Integer.toUnsignedLong(track.getPlaybackHeadPosition()))
                    .put("underruns",track.getUnderrunCount())
                    .put("buffer_frames",track.getBufferSizeInFrames())
                    .put("start_threshold_frames",track.getStartThresholdInFrames())
                    .put("timestamp_available",has);
                if(has) result.put("timestamp_frames",stamp.framePosition);
            }
            if(record!=null) result.put("capture_device",device(record.getRoutedDevice()));
        } catch(Exception e) { try { result.put("error_type",e.getClass().getSimpleName()); } catch(JSONException ignored) {} }
        return result;
    }
}
