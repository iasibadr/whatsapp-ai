package live.pharmaradio.whatsappai;

import android.content.Context;
import android.os.SystemClock;
import android.util.Base64;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import okhttp3.*;
import org.json.*;

/** A bounded single-turn experiment. Never logs transcripts, keys or server payloads. */
final class RealtimeAudio implements AutoCloseable {
    final ConversationAudio output=new ConversationAudio();
    final AtomicLong inputBytes=new AtomicLong(),outputBytes=new AtomicLong();
    final AtomicInteger completed=new AtomicInteger();
    final AtomicInteger created=new AtomicInteger(),completedGeneration=new AtomicInteger();
    final AtomicInteger limitedResponses=new AtomicInteger();
    final AtomicInteger speechTurns=new AtomicInteger(),interruptions=new AtomicInteger(),truncationsConfirmed=new AtomicInteger(),cancelledResponses=new AtomicInteger();
    final AtomicInteger inputTranscriptions=new AtomicInteger(),outputTranscriptions=new AtomicInteger();
    final AtomicReference<String> failure=new AtomicReference<>("");
    private final AtomicBoolean closed=new AtomicBoolean(),ready=new AtomicBoolean();
    private final StringBuilder syntheticTranscript=new StringBuilder();
    private final boolean synthetic;
    private final boolean continuous;
    private String currentResponse="";
    private boolean costAlerted;
    private JSONObject missionConfig;
    private Context missionContext;
    private TranscriptStore transcript;
    private String destination,missionBrief;
    private final String localId=java.util.UUID.randomUUID().toString();
    private final long createdAt=System.currentTimeMillis();
    private final ApiUsage usage=new ApiUsage();
    private final LocalTools localTools=new LocalTools();
    private final java.util.Set<String> completedTools=new java.util.HashSet<>();
    volatile boolean stopRequested;
    volatile String terminalAction="none";
    void mission(Context c,PreparedMission mission,String number)throws Exception{
        missionContext=c.getApplicationContext();destination=number;missionBrief=mission.brief;
        missionConfig=new DirectOpenAi(c,http,ModelSettings.read(c),mission.dossier).session(new JSONObject().put("direction","outgoing").put("brief",mission.brief));
        missionConfig.put("instructions",missionConfig.getString("instructions")+"\n"+AppSettings.contactPolicy(c,number)
            +"\nLe destinataire est celui choisi par le propriétaire. Cet appel est transcrit en texte sur le téléphone du propriétaire pour contrôler la mission. Vérifie que le correspondant accepte de poursuivre avec cette transcription ; s'il refuse, cesse la mission et utilise demander_reprise. Continue à écouter et à répondre jusqu'à ce que la mission soit accomplie ou que le correspondant demande la fin. Une première réponse ne suffit pas si le message ou les questions restent ouverts. Après un bref récapitulatif et des adieux, appelle terminer_discussion avec motif=demande_correspondant ou mission_accomplie. Le téléphone ne raccroche que si le même appel WhatsApp est encore confirmé. Pour rendre la main au propriétaire, utilise demander_reprise. Ne prétends pas avoir raccroché avant confirmation.");
    }
    synchronized void beginTranscript() throws Exception {
        if(!synthetic&&continuous&&missionContext!=null&&transcript==null)
            transcript=new TranscriptStore(missionContext,localId);
    }
    void noteSubmitted(String itemId,int bytes)throws Exception{
        TranscriptStore journal=transcript;if(journal!=null)journal.submitted(itemId,bytes);
    }
    void finishTranscript(String state)throws Exception{
        TranscriptStore journal=transcript;if(journal==null)return;
        long deadline=SystemClock.elapsedRealtime()+2000;
        while(journal.pendingInput()>0&&SystemClock.elapsedRealtime()<deadline&&!closed.get())Thread.sleep(50);
        journal.finish(state);
    }
    int transcriptTurns(){TranscriptStore journal=transcript;return journal==null?0:journal.turnCount();}
    int transcriptGaps(){TranscriptStore journal=transcript;return journal==null?0:journal.gaps();}
    private void recordUsage(JSONObject response) {
        JSONObject report = response.optJSONObject("usage");
        long input = report == null ? -1 : report.optLong("input_tokens", -1);
        long output = report == null ? -1 : report.optLong("output_tokens", -1);
        Double cost = null;
        RealtimeModel pricing = RealtimeModel.find(missionConfig==null?"gpt-realtime-2.1":missionConfig.optString("model"));
        if (report != null && pricing != null) {
            JSONObject incoming = report.optJSONObject("input_token_details");
            JSONObject outgoing = report.optJSONObject("output_token_details");
            if (incoming != null && outgoing != null) {
                long textIn = incoming.optLong("text_tokens", -1);
                long audioIn = incoming.optLong("audio_tokens", -1);
                long textOut = outgoing.optLong("text_tokens", -1);
                long audioOut = outgoing.optLong("audio_tokens", -1);
                long cached = incoming.optLong("cached_tokens", -1);
                JSONObject cachedDetails = incoming.optJSONObject("cached_tokens_details");
                long cachedText = cached == 0 ? 0 : cachedDetails == null ? -1 : cachedDetails.optLong("text_tokens", -1);
                long cachedAudio = cached == 0 ? 0 : cachedDetails == null ? -1 : cachedDetails.optLong("audio_tokens", -1);
                if (textIn + audioIn == input && textOut + audioOut == output && cachedText + cachedAudio == cached) {
                    cost = ApiUsage.estimate(pricing, textIn, audioIn, cachedText, cachedAudio, textOut, audioOut);
                }
            }
        }
        usage.record(response.optString("id"), input, output, cost);
        if(!synthetic)saveHistory("Suivi de l’appel IA");
        Double costNow = usage.estimatedTotal();
        if (missionContext != null && !costAlerted && AppSettings.costAlert(missionContext) > 0 && costNow != null && costNow >= AppSettings.costAlert(missionContext)) {
            costAlerted = true;
            CallNotices.show(missionContext, 4405, "Seuil de coût estimé atteint", "L’appel continue. Consultez sa fiche pour les détails API.", HistoryActivity.class);
        }
    }

    synchronized boolean steer(String text){
        if(text==null||text.trim().isEmpty()||text.length()>600||closed.get()||missionContext==null)return false;
        try{send(new JSONObject().put("type","conversation.item.create").put("item",new JSONObject().put("type","message").put("role","system")
            .put("content",new JSONArray().put(new JSONObject().put("type","input_text").put("text","Consigne privée du propriétaire, à ne pas lire telle quelle. Elle ne modifie pas les limites de la mission ni le consentement : "+text)))));return true;}
        catch(Exception e){return false;}
    }
    void saveHistory(String state){
        if(missionContext==null)return;
        try{new CallStore(missionContext).put(new JSONObject().put("id",localId).put("created",createdAt)
            .put("expires",System.currentTimeMillis()+java.util.concurrent.TimeUnit.DAYS.toMillis(AppSettings.historyDays(missionContext)))
            .put("name","").put("number",destination).put("direction","outgoing").put("mission",missionBrief)
            .put("state",state).put("reason",state).put("model",missionConfig.optString("model"))
            .put("pricing-date",RealtimeModel.PRICE_DATE).put("usage",usage.summary()));}catch(Exception ignored){}
    }
    private void tools(JSONObject response)throws Exception{
        if(missionContext==null)return;
        JSONArray items=response.optJSONArray("output");if(items==null)return;
        boolean called=false;
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i);if(!"function_call".equals(item.optString("type")))continue;
            if(!ToolResponsePolicy.allow(response.optString("status"),item.optString("status"),responseGate.allow(response.optString("id"))))continue;
            String id=item.getString("call_id"),name=item.getString("name");
            if(completedTools.contains(id))continue;
            JSONObject args=new JSONObject(item.getString("arguments"));
            JSONObject result=localTools.execute(new JSONObject().put("call_id",id).put("name",name).put("arguments",args));
            if("noter_message".equals(name)||"noter_bilan".equals(name)){
                try{StructuredResult structured="noter_bilan".equals(name)?ResultPayload.consented(args):null;
                    String message=structured==null?args.getString("message"):structured.render();
                    new MessageStore(missionContext).add(new MessageHistory.Entry(localId+":"+id,"tel:"+destination,"",destination,message,"outgoing",System.currentTimeMillis(),structured));
                    result.put("copie_locale",true).put("consultable_heures",24);
                }catch(Exception e){result.put("ok",false).put("copie_locale",false);}
            }
            send(new JSONObject().put("type","conversation.item.create").put("item",new JSONObject().put("type","function_call_output").put("call_id",id).put("output",result.toString())));
            completedTools.add(id);called=true;
            if("demander_reprise".equals(name)||"terminer_discussion".equals(name)){
                String next="demander_reprise".equals(name)?"handoff":"hangup";
                terminalAction="none".equals(terminalAction)?next:terminalAction.equals(next)?next:"conflict";
                stopRequested=true;
            }
        }
        if(called&&!stopRequested)send(new JSONObject().put("type","response.create"));
    }

    private final ResponseGate responseGate=new ResponseGate();
    private volatile WebSocket socket;
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS)
        .readTimeout(10,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    RealtimeAudio(boolean synthetic){this(synthetic,false);}
    RealtimeAudio(boolean synthetic,boolean continuous){this.synthetic=synthetic;this.continuous=continuous;}
    void connect(Context context) throws Exception {
        JSONObject config=new JSONObject().put("type","realtime").put("model",missionConfig==null?"gpt-realtime-2.1":missionConfig.getString("model"));
        JSONObject payload=new JSONObject().put("expires_after",new JSONObject().put("anchor","created_at").put("seconds",60)).put("session",config);
        Request tokenRequest=new Request.Builder().url("https://api.openai.com/v1/realtime/client_secrets")
            .header("Authorization","Bearer "+KeyVault.load(context))
            .post(RequestBody.create(MediaType.get("application/json"),payload.toString())).build();
        String ephemeral;
        try(Response r=http.newCall(tokenRequest).execute()){
            if(!r.isSuccessful()||r.body()==null)throw new IOException("Token HTTP "+r.code());
            byte[] raw=r.body().byteStream().readNBytes(65537);
            try{if(raw.length>65536)throw new IOException("Token size");ephemeral=new JSONObject(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).getString("value");}
            finally{Arrays.fill(raw,(byte)0);}
        }
        if(closed.get())throw new IOException("Cancelled");
        Request request=new Request.Builder().url("wss://api.openai.com/v1/realtime?model="+config.getString("model")).header("Authorization","Bearer "+ephemeral).build();
        synchronized(this){
        if(closed.get())throw new IOException("Cancelled");
        socket=http.newBuilder().readTimeout(0,TimeUnit.SECONDS).pingInterval(10,TimeUnit.SECONDS).build().newWebSocket(request,new WebSocketListener(){
            @Override public void onOpen(WebSocket ws,Response response){
                try{
                    JSONObject format=new JSONObject().put("type","audio/pcm").put("rate",24000);
                    JSONObject input=new JSONObject().put("format",format)
                        .put("turn_detection",continuous?new JSONObject().put("type","server_vad").put("threshold",0.6).put("prefix_padding_ms",300).put("silence_duration_ms",800).put("create_response",true).put("interrupt_response",true):JSONObject.NULL);
                    if(continuous&&missionConfig!=null)input.put("transcription",new JSONObject().put("model","gpt-4o-transcribe"));
                    JSONObject session=new JSONObject().put("type","realtime").put("model","gpt-realtime-2.1")
                        .put("instructions","Tu es un assistant vocal IA pour un essai consenti. Réponds en français en une ou deux phrases courtes. Tu ne disposes d'aucun outil ni accès personnel. Ne prétends jamais que le projet est terminé. Réponds seulement à la petite question entendue. Si tu n'as pas compris, dis-le.")
                        .put("output_modalities",new JSONArray().put("audio")).put("max_output_tokens","inf")
                        .put("audio",new JSONObject().put("input",input)
                            .put("output",new JSONObject().put("format",format).put("voice","marin")));
                    if(missionConfig!=null){session.put("model",missionConfig.getString("model")).put("instructions",missionConfig.getString("instructions")).put("tools",missionConfig.getJSONArray("tools"));}
                    if(!ws.send(new JSONObject().put("type","session.update").put("session",session).toString()))throw new IOException();
                }catch(Exception e){fail("session_configuration");}
            }
            @Override public void onMessage(WebSocket ws,String text){
                synchronized(RealtimeAudio.this){
                if(closed.get())return;
                try{
                    if(text.length()>2_000_000)throw new IOException();
                    JSONObject event=new JSONObject(text);String type=event.optString("type");
                    if("session.updated".equals(type))ready.set(true);
                    else if("error".equals(type))fail("server_error_"+safeCode(event.optJSONObject("error"),"code"));
                    else if("response.created".equals(type)){
                        currentResponse=event.getJSONObject("response").getString("id");
                        responseGate.begin(currentResponse);created.incrementAndGet();usage.started(currentResponse);
                        if(synthetic)synchronized(syntheticTranscript){syntheticTranscript.setLength(0);}
                    }else if("response.output_item.added".equals(type)){
                        JSONObject item=event.getJSONObject("item");if(responseGate.allow(event.optString("response_id"))&&"message".equals(item.optString("type"))){output.begin(item.getString("id"));if(transcript!=null)transcript.outputAdded(item.getString("id"));}
                    }else if("input_audio_buffer.speech_started".equals(type)&&continuous){
                        speechTurns.incrementAndGet();if(transcript!=null)transcript.speechStarted(event.optString("item_id"));
                        boolean generating=responseGate.active();ConversationAudio.Cut cut=output.interrupt();
                        if(generating||cut!=null){responseGate.interrupt();interruptions.incrementAndGet();}
                        if(cut!=null){if(transcript!=null)transcript.interrupted(cut.id);send(new JSONObject().put("type","conversation.item.truncate").put("item_id",cut.id).put("content_index",0).put("audio_end_ms",cut.audioEndMs));}
                    }else if("input_audio_buffer.committed".equals(type)){
                        if(transcript!=null)transcript.inputCommitted(event.optString("item_id"));
                    }else if("conversation.item.input_audio_transcription.completed".equals(type)){
                        inputTranscriptions.incrementAndGet();
                        if(transcript!=null)transcript.inputDone(event.optString("item_id"),event.optString("transcript"));
                    }else if("conversation.item.input_audio_transcription.failed".equals(type)){
                        if(transcript!=null)transcript.inputFailed(event.optString("item_id"));
                    }else if("conversation.item.truncated".equals(type)){
                        truncationsConfirmed.incrementAndGet();if(transcript!=null)transcript.interrupted(event.optString("item_id"));
                    }
                    else if("response.output_audio.delta".equals(type)){
                        byte[] bytes=Base64.decode(event.getString("delta"),Base64.DEFAULT);
                        try{if(responseGate.allow(event.optString("response_id"))&&output.add(event.getString("item_id"),bytes)){
                            outputBytes.addAndGet(bytes.length);if(transcript!=null)transcript.generated(event.getString("item_id"),bytes.length);
                        }}finally{Arrays.fill(bytes,(byte)0);}
                    }else if("response.output_audio_transcript.delta".equals(type)&&responseGate.allow(event.optString("response_id"))){
                        if(synthetic)synchronized(syntheticTranscript){if(syntheticTranscript.length()<4096)syntheticTranscript.append(event.optString("delta"));}
                        if(transcript!=null)transcript.outputDelta(event.optString("item_id"),event.optString("delta"));
                    }else if("response.output_audio_transcript.done".equals(type)){
                        outputTranscriptions.incrementAndGet();
                        if(transcript!=null)transcript.outputDone(event.optString("item_id"),event.optString("transcript"));
                    }else if("response.done".equals(type)){
                        JSONObject response=event.getJSONObject("response");
                        recordUsage(response);
                        tools(response);
                        if(response.optString("id").equals(currentResponse))output.done();
                        responseGate.done(response.optString("id"));
                        ResponseOutcome outcome=ResponseOutcome.classify(response.optString("status"),safeCode(response.optJSONObject("status_details"),"reason"),continuous);
                        if(outcome==ResponseOutcome.COMPLETE){completed.incrementAndGet();if(responseGate.allow(response.optString("id")))completedGeneration.set(created.get());}
                        else if(outcome==ResponseOutcome.CANCELLED)cancelledResponses.incrementAndGet();
                        else if(outcome==ResponseOutcome.LIMITED)limitedResponses.incrementAndGet();
                        else fail("response_"+safeCode(response,"status")+"_"+safeCode(response.optJSONObject("status_details"),"reason"));
                    }
                }catch(Exception e){fail("invalid_server_event");}
                }
            }
            @Override public void onFailure(WebSocket ws,Throwable error,Response response){if(!closed.get())fail("websocket_failure_"+error.getClass().getSimpleName()+(response==null?"":"_http_"+response.code()));}
            @Override public void onClosed(WebSocket ws,int code,String reason){if(!closed.get())fail("websocket_closed");}
        });
        }
        await(()->ready.get(),12000);
    }
    private void fail(String code){failure.compareAndSet("",code);}
    private static String safeCode(JSONObject o,String key){String value=o==null?"unknown":o.optString(key,"unknown");return value.matches("[A-Za-z0-9_]{1,80}")?value:"unknown";}
    interface Condition{boolean done();}
    void await(Condition condition,long timeout) throws Exception {
        long end=SystemClock.elapsedRealtime()+timeout;
        while(!condition.done()){check();if(SystemClock.elapsedRealtime()>end)throw new IOException("Realtime deadline");Thread.sleep(20);}check();
    }
    void check() throws IOException {if(closed.get()||!failure.get().isEmpty())throw new IOException(closed.get()?"Cancelled":failure.get());}
    boolean responseGenerating(){return responseGate.active();}
    private void send(JSONObject event) throws Exception {check();WebSocket ws=socket;if(ws==null||ws.queueSize()>262144||!ws.send(event.toString()))throw new IOException("Realtime send refused");}
    void append(byte[] bytes) throws Exception {
        if(bytes.length==0)return;
        send(new JSONObject().put("type","input_audio_buffer.append").put("audio",Base64.encodeToString(bytes,Base64.NO_WRAP)));inputBytes.addAndGet(bytes.length);
    }
    void answer() throws Exception {send(new JSONObject().put("type","input_audio_buffer.commit"));send(new JSONObject().put("type","response.create"));}
    void syntheticOutputLimit(int limit)throws Exception{
        if(!synthetic||!continuous||(limit!=32&&limit!=300))throw new IllegalArgumentException("Synthetic limit only");
        ready.set(false);send(new JSONObject().put("type","session.update").put("session",new JSONObject().put("type","realtime").put("max_output_tokens",limit)));
        await(()->ready.get(),12000);
    }
    void greeting() throws Exception {
        String[] requests={
            "Dis seulement : Bonjour, je suis un assistant IA.",
            "Dis seulement : Bonjour, je suis une IA."
        };
        for(String request:requests){
            int beforeComplete=completed.get(),beforeLimited=limitedResponses.get();
            send(new JSONObject().put("type","conversation.item.create").put("item",new JSONObject().put("type","message").put("role","user")
                .put("content",new JSONArray().put(new JSONObject().put("type","input_text").put("text",request)))));
            send(new JSONObject().put("type","response.create"));
            await(()->completed.get()>beforeComplete||limitedResponses.get()>beforeLimited,25000);
            if(completed.get()>beforeComplete&&output.size()>0&&output.size()<=576000)return;
            ConversationAudio.Cut cut=output.interrupt();
            if(cut!=null)send(new JSONObject().put("type","conversation.item.truncate").put("item_id",cut.id)
                    .put("content_index",0).put("audio_end_ms",0));
        }
        throw new IOException("Greeting not complete or too long");
    }
    boolean syntheticAnswerCorrect(){synchronized(syntheticTranscript){String t=syntheticTranscript.toString().toLowerCase(java.util.Locale.ROOT).replaceAll("[\\p{Punct}\\s]","");return t.equals("douze")||t.equals("12");}}
    boolean syntheticAnswerEquals(String word,String number){synchronized(syntheticTranscript){String t=syntheticTranscript.toString().toLowerCase(java.util.Locale.ROOT).replaceAll("[\\p{Punct}\\s]","");return t.equals(word)||t.equals(number);}}
    String syntheticText(){if(!synthetic)return "";synchronized(syntheticTranscript){return syntheticTranscript.substring(0,Math.min(320,syntheticTranscript.length()));}}
    @Override public synchronized void close(){
        if(!closed.compareAndSet(false,true))return;
        WebSocket ws=socket;if(ws!=null)ws.cancel();http.dispatcher().cancelAll();http.connectionPool().evictAll();http.dispatcher().executorService().shutdownNow();
        output.close();synchronized(syntheticTranscript){syntheticTranscript.setLength(0);}
    }
}
