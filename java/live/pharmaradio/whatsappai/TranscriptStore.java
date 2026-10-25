package live.pharmaradio.whatsappai;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Private, crash-safe text journal for one consented call. Never stores PCM. */
final class TranscriptStore {
    private static final String PREFIX="transcript-";
    private static final String SUFFIX=".json";
    private final AtomicFile file;
    private final JSONObject document;
    private final JSONArray turns;
    private int sequence;

    TranscriptStore(Context context,String callId) throws Exception {
        File target=target(context,callId);
        File folder=target.getParentFile();
        if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("Dossier de transcription indisponible");
        file=new AtomicFile(target);
        long created=System.currentTimeMillis();
        turns=new JSONArray();
        document=new JSONObject().put("schema",1).put("call_id",callId).put("created",created)
                .put("retention_days",AppSettings.historyDays(context)).put("expires",Long.MAX_VALUE)
                .put("state","running").put("turns",turns);
        persist();
    }

    private static File target(Context context,String id) {
        if(id==null||!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("Identifiant d'appel invalide");
        return new File(new File(context.getFilesDir(),"transcripts"),PREFIX+id+SUFFIX);
    }

    private JSONObject find(String id) {
        for(int i=0;i<turns.length();i++){
            JSONObject turn=turns.optJSONObject(i);
            if(turn!=null&&id.equals(turn.optString("item_id")))return turn;
        }
        return null;
    }
    private JSONObject add(String id,String role) throws Exception {
        if(id==null||id.isEmpty())id="pending-"+(sequence+1);
        JSONObject old=find(id);if(old!=null)return old;
        JSONObject turn=new JSONObject().put("order",++sequence).put("item_id",id).put("role",role)
                .put("at",System.currentTimeMillis()).put("text","").put("status","pending")
                .put("generated_bytes",0).put("submitted_bytes",0).put("interrupted",false);
        turns.put(turn);persist();return turn;
    }
    synchronized void speechStarted(String id)throws Exception{add(id,"correspondent");}
    synchronized void inputCommitted(String id)throws Exception{
        if(id==null||id.isEmpty())return;
        if(find(id)!=null)return;
        for(int i=0;i<turns.length();i++){
            JSONObject turn=turns.getJSONObject(i);
            if("correspondent".equals(turn.optString("role"))&&turn.optString("item_id").startsWith("pending-")
                    &&"pending".equals(turn.optString("status"))){turn.put("item_id",id);persist();return;}
        }
        add(id,"correspondent");
    }
    synchronized void inputDone(String id,String text)throws Exception{
        if(id==null||id.isEmpty())return;
        JSONObject turn=add(id,"correspondent");
        turn.put("text",text==null?"":text.trim()).put("status",text==null||text.trim().isEmpty()?"missing":"transcribed");persist();
    }
    synchronized void inputFailed(String id)throws Exception{
        if(id==null||id.isEmpty())return;
        add(id,"correspondent").put("status","failed");persist();
    }
    synchronized void outputAdded(String id)throws Exception{if(id!=null&&!id.isEmpty())add(id,"assistant");}
    synchronized void outputDelta(String id,String delta)throws Exception{
        if(id==null||id.isEmpty()||delta==null||delta.isEmpty())return;
        JSONObject turn=add(id,"assistant");turn.put("text",turn.optString("text")+delta).put("status","partial");
    }
    synchronized void outputDone(String id,String text)throws Exception{
        if(id==null||id.isEmpty())return;
        add(id,"assistant").put("text",text==null?"":text.trim()).put("status",text==null||text.trim().isEmpty()?"missing":"transcribed");persist();
    }
    synchronized void generated(String id,int bytes)throws Exception{
        if(bytes<=0||id==null||id.isEmpty())return;
        JSONObject turn=add(id,"assistant");turn.put("generated_bytes",turn.optLong("generated_bytes")+bytes);
    }
    synchronized void submitted(String id,int bytes)throws Exception{
        if(bytes<=0||id==null||id.isEmpty())return;
        JSONObject turn=find(id);if(turn==null)return;
        turn.put("submitted_bytes",turn.optLong("submitted_bytes")+bytes);
    }
    synchronized void interrupted(String id)throws Exception{
        JSONObject turn=id==null?null:find(id);if(turn==null)return;
        turn.put("interrupted",true);persist();
    }
    synchronized void finish(String state)throws Exception{
        long finished=System.currentTimeMillis();
        document.put("state",state==null?"unknown":state).put("finished",finished)
                .put("expires",finished+java.util.concurrent.TimeUnit.DAYS.toMillis(document.optInt("retention_days",1)));
        for(int i=0;i<turns.length();i++){
            JSONObject turn=turns.getJSONObject(i);
            if("pending".equals(turn.optString("status"))||"partial".equals(turn.optString("status")))
                turn.put("status",turn.optString("text").isEmpty()?"missing":"partial");
        }
        persist();
    }
    synchronized int turnCount(){return turns.length();}
    synchronized int pendingInput(){
        int count=0;
        for(int i=0;i<turns.length();i++){
            JSONObject turn=turns.optJSONObject(i);
            if(turn!=null&&"correspondent".equals(turn.optString("role"))&&"pending".equals(turn.optString("status")))count++;
        }
        return count;
    }
    synchronized int gaps(){
        int count=0;
        for(int i=0;i<turns.length();i++){
            JSONObject turn=turns.optJSONObject(i);if(turn==null)continue;
            if("missing".equals(turn.optString("status"))||"failed".equals(turn.optString("status"))
                    ||"partial".equals(turn.optString("status"))||turn.optBoolean("interrupted")
                    ||("assistant".equals(turn.optString("role"))&&turn.optLong("generated_bytes")>turn.optLong("submitted_bytes")))count++;
        }
        return count;
    }
    private void persist()throws Exception{
        byte[] bytes=document.toString().getBytes(StandardCharsets.UTF_8);
        FileOutputStream out=null;
        try{out=file.startWrite();out.write(bytes);file.finishWrite(out);}
        catch(Exception error){if(out!=null)file.failWrite(out);throw error;}
    }

    static JSONObject read(Context context,String id)throws Exception{
        AtomicFile source=new AtomicFile(target(context,id));
        if(!source.getBaseFile().isFile())return null;
        JSONObject result=new JSONObject(new String(source.readFully(),StandardCharsets.UTF_8));
        if(!id.equals(result.optString("call_id")))throw new java.io.IOException("Transcription incohérente");
        if(result.optLong("expires")<=System.currentTimeMillis()){source.delete();return null;}
        return result;
    }
    static void remove(Context context,String id){new AtomicFile(target(context,id)).delete();}
    static void clear(Context context){
        File dir=new File(context.getFilesDir(),"transcripts");File[] files=dir.listFiles();if(files==null)return;
        for(File candidate:files)if(candidate.getName().startsWith(PREFIX)&&candidate.getName().endsWith(SUFFIX))new AtomicFile(candidate).delete();
    }
    static void prune(Context context){
        File dir=new File(context.getFilesDir(),"transcripts");File[] files=dir.listFiles();if(files==null)return;
        for(File candidate:files)if(candidate.getName().startsWith(PREFIX)&&candidate.getName().endsWith(SUFFIX)){
            try{AtomicFile source=new AtomicFile(candidate);JSONObject body=new JSONObject(new String(source.readFully(),StandardCharsets.UTF_8));
                if(body.optLong("expires",Long.MAX_VALUE)<=System.currentTimeMillis())source.delete();}
            catch(Exception ignored){} // Preserve a damaged file for diagnosis rather than deleting personal text.
        }
    }
    static boolean selfTest(Context context)throws Exception{
        String id=java.util.UUID.randomUUID().toString();
        try{
            TranscriptStore journal=new TranscriptStore(context,id);
            journal.speechStarted("sample-input");journal.inputCommitted("sample-input");journal.inputDone("sample-input","Question de contrôle.");
            journal.outputAdded("sample-output");journal.generated("sample-output",960);
            journal.outputDelta("sample-output","Réponse de contrôle.");journal.outputDone("sample-output","Réponse de contrôle.");
            journal.submitted("sample-output",960);journal.finish("selftest");
            JSONObject restored=read(context,id);if(restored==null)return false;
            JSONArray entries=restored.getJSONArray("turns");
            return entries.length()==2&&restored.getLong("expires")>restored.getLong("finished")
                &&"correspondent".equals(entries.getJSONObject(0).getString("role"))
                &&"Question de contrôle.".equals(entries.getJSONObject(0).getString("text"))
                &&"assistant".equals(entries.getJSONObject(1).getString("role"))
                &&"Réponse de contrôle.".equals(entries.getJSONObject(1).getString("text"))
                &&entries.getJSONObject(1).getLong("submitted_bytes")==960;
        }finally{remove(context,id);}
    }
}
