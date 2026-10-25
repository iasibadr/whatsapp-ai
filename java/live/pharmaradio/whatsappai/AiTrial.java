package live.pharmaradio.whatsappai;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;

/** Synthetic network test or one consented real call, stopped by the call guard or user. */
final class AiTrial {
    private String launchToken;
    private PreparedMission mission;
    private String destination;
    AiTrial(){}
    AiTrial(String token,PreparedMission mission,String destination){this.launchToken=token;this.mission=mission;this.destination=destination;}
    private static final AtomicBoolean active=new AtomicBoolean();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private volatile RealtimeAudio realtime; private volatile PcmSocket wire;
    boolean steer(String text){RealtimeAudio r=realtime;return !cancelled.get()&&r!=null&&r.steer(text);}
    void cancel(){cancelled.set(true);RealtimeAudio r=realtime;if(r!=null)r.close();PcmSocket w=wire;if(w!=null)w.close();}
    private void check() throws Exception {if(cancelled.get())throw new IOException("Cancelled");realtime.check();}
    void run(Context c,boolean synthetic,PcmTransportTest.Listener listener){
        run(c,synthetic,false,0,listener);
    }
    void run(Context c,boolean synthetic,boolean continuous,int delaySeconds,PcmTransportTest.Listener listener){
        if(!active.compareAndSet(false,true)){listener.result("Essai IA déjà en cours");return;}
        c.getSharedPreferences("ai-trial",Context.MODE_PRIVATE).edit().putString("report","{\"state\":\"running\"}").commit();
        ScheduledExecutorService timer=continuous&&!synthetic?null:Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> watchdog=timer==null?null:timer.schedule(this::cancel,(continuous?640:65)+delaySeconds,TimeUnit.SECONDS);
        JSONObject report=new JSONObject();IBinder binder=null;Thread receiver=null;
        SyntheticConversation syntheticBench=null;
        AtomicBoolean finished=new AtomicBoolean(),listening=new AtomicBoolean();Object inputLock=new Object();
        AtomicReference<String> receiverError=new AtomicReference<>("");
        AtomicLong received=new AtomicLong();long played=0,greetingBytes=0;
        String stage="prepare";
        try{
            report.put("test",synthetic?(continuous?"synthetic_ai_conversation":"synthetic_ai_audio"):(continuous?"consented_whatsapp_ai_continuous":"consented_whatsapp_ai_one_turn")).put("audio_saved",false).put("human_audio_verified",false);
            report.put("scheduled_wall_ms",System.currentTimeMillis()).put("delay_seconds",delaySeconds);
            String preparedToken=null;
            if(continuous&&!synthetic){
                binder=BridgeProvider.getHelper();if(binder==null)throw new IOException("Relais absent");
                JSONObject s=new JSONObject(PcmTransportTest.command(binder,1));
                if(!"0.22-pcm-transport".equals(s.optString("version"))||s.optInt("helper_uid")!=2000)throw new IOException("Relais incompatible");
                preparedToken=launchToken==null?PcmTransportTest.command(binder,15):launchToken;
            }
            if(delaySeconds>0){
                if(delaySeconds!=20)throw new IOException("Invalid delay");
                listener.result("Départ dans20secondes. Restez sur cet écran. Vous pouvez déconnecter ADB.");
                long deadline=SystemClock.elapsedRealtime()+20000;
                while(SystemClock.elapsedRealtime()<deadline){if(cancelled.get()||!SetupActivity.visible)throw new IOException("Preparation cancelled");Thread.sleep(100);}
            }
            realtime=new RealtimeAudio(synthetic,continuous); if(mission!=null)realtime.mission(c,mission,destination);check();report.put("network_started_wall_ms",System.currentTimeMillis());
            stage="network_connect";listener.result("IA : connexion sécurisée…");realtime.connect(c);check();
            if(synthetic&&continuous){
                syntheticBench=new SyntheticConversation(realtime);report.put("synthetic",syntheticBench.run(c)).put("state","passed");
            }else if(synthetic){
                byte[] phrase;
                try(InputStream in=c.getAssets().open("ai-challenge.pcm")){phrase=in.readNBytes(480001);}
                try{
                    if(phrase.length==0||phrase.length>480000||(phrase.length&1)!=0)throw new IOException("Invalid synthetic asset");
                    long start=SystemClock.elapsedRealtime();
                    for(int offset=0;offset<phrase.length;offset+=960){
                        check();byte[] frame=Arrays.copyOfRange(phrase,offset,Math.min(offset+960,phrase.length));
                        try{realtime.append(frame);}finally{Arrays.fill(frame,(byte)0);}
                        long wait=start+(offset+960)*1000L/48000-SystemClock.elapsedRealtime();if(wait>0)Thread.sleep(wait);
                    }
                }finally{Arrays.fill(phrase,(byte)0);}
                realtime.answer();realtime.await(()->realtime.completed.get()==1,18000);
                boolean correct=realtime.syntheticAnswerCorrect();
                report.put("synthetic_answer_correct",correct).put("audio_response_received",realtime.outputBytes.get()>0);
                if(!correct||realtime.outputBytes.get()==0)throw new IOException("Synthetic answer not validated");
                report.put("state","passed");
            }else{
                binder=BridgeProvider.getHelper();if(binder==null)throw new IOException("Relais absent");
                JSONObject helperStatus=new JSONObject(PcmTransportTest.command(binder,1));
                if(!"0.22-pcm-transport".equals(helperStatus.optString("version"))||helperStatus.optInt("helper_uid")!=2000)throw new IOException("Relais incompatible");
                stage="greeting";if(continuous)realtime.beginTranscript();realtime.greeting();check();greetingBytes=realtime.output.size();
                stage="pcm_start";
                Parcel req=Parcel.obtain(),reply=Parcel.obtain();
                try{req.writeInterfaceToken(BridgeProvider.DESCRIPTOR);req.writeString(continuous?"whatsapp-ai-session-consented":"whatsapp-pcm-thirty-seconds-consented");
                    if(continuous)req.writeString(preparedToken);
                    if(!binder.transact(continuous?14:9,req,reply,0))throw new IOException("Transport incompatible");
                    reply.readException();ParcelFileDescriptor fd=reply.readTypedObject(ParcelFileDescriptor.CREATOR);
                    if(fd==null)throw new IOException("Canal absent");wire=new PcmSocket(fd);
                }finally{req.recycle();reply.recycle();}
                long init=SystemClock.elapsedRealtime();
                while(true){check();String state=new JSONObject(PcmTransportTest.command(binder,10)).optString("state");if("running".equals(state))break;
                    if(!"starting".equals(state)||SystemClock.elapsedRealtime()-init>7000)throw new IOException("PCM initialization refused");Thread.sleep(50);}
                PcmSocket channel=wire;
                receiver=new Thread(()->{
                    try{while(!finished.get()&&!cancelled.get()){
                        byte[] frame=channel.read(1500);
                        try{received.addAndGet(frame.length);synchronized(inputLock){if(listening.get())realtime.append(frame);}}
                        finally{Arrays.fill(frame,(byte)0);}
                    }}catch(Exception e){if(!finished.get()&&!cancelled.get())receiverError.compareAndSet("",e.getClass().getSimpleName());}
                },"ai-call-receiver");receiver.start();
                long begin=SystemClock.elapsedRealtime(),next=begin,lastGuard=0;
                stage="pcm_stream";report.put("pcm_started_wall_ms",System.currentTimeMillis());
                if(continuous)listening.set(true);
                long listenAt=begin+500+((greetingBytes+959)/960)*20+500,answerAt=listenAt+6500;
                boolean listeningStarted=false,answerRequested=false;long drainedAt=0;byte[] frame=new byte[960];
                listener.result(continuous?"IA : conversation en cours. Vous pouvez interrompre la réponse ou arrêter l’IA.":"IA : présentation, puis écoute de votre question pendant 6,5 secondes…");
                try{
                    while(continuous||SystemClock.elapsedRealtime()-begin<28500){
                        check();if(!receiverError.get().isEmpty())throw new IOException("PCM receive stopped");
                        long now=SystemClock.elapsedRealtime();
                        if(now-lastGuard>=500){
                            if(!"running".equals(new JSONObject(PcmTransportTest.command(binder,10)).optString("state")))throw new IOException("Call guard stopped PCM");lastGuard=now;
                        }
                        if(realtime.stopRequested){
                            synchronized(inputLock){listening.set(false);}
                            if(realtime.output.size()==0){if(drainedAt==0)drainedAt=now;if(now-drainedAt>=700)break;}
                            else drainedAt=0;
                        }
                        if(now<next){Thread.sleep(Math.min(20,next-now));continue;}
                        if(now-next>100)throw new IOException("PCM pacing late");
                        if(!continuous&&now>=listenAt&&!listeningStarted){synchronized(inputLock){listening.set(true);}listeningStarted=true;listener.result("IA : écoute du correspondant…");}
                        if(!continuous&&now>=answerAt&&!answerRequested){synchronized(inputLock){listening.set(false);}realtime.answer();answerRequested=true;listener.result("IA : réponse en cours…");}
                        Arrays.fill(frame,(byte)0);
                        if(now>=begin+500){ConversationAudio.Drained drained=realtime.output.drainTagged(frame);played+=drained.bytes;realtime.noteSubmitted(drained.id,drained.bytes);}
                        channel.write(frame,frame.length,500);next+=20;
                        if(!continuous&&answerRequested&&realtime.completed.get()>=2&&realtime.output.size()==0){
                            if(drainedAt==0)drainedAt=now;
                            if(now-drainedAt>=700)break;
                        }
                    }
                }finally{Arrays.fill(frame,(byte)0);synchronized(inputLock){listening.set(false);}}
                if(!continuous&&(realtime.completed.get()<2||realtime.output.size()!=0||played<=greetingBytes||drainedAt==0||SystemClock.elapsedRealtime()-drainedAt<700))throw new IOException("Answer incomplete before deadline");
                if(continuous){
                    String action=realtime.terminalAction;
                    report.put("assistant_terminal_action",action);
                    if("conflict".equals(action))throw new IOException("Conflicting call-end tools");
                    if("hangup".equals(action)&&launchToken!=null){
                        stage="verified_hangup";
                        JSONObject end=finishWhitelistedCall(binder,preparedToken,channel);
                        report.put("hangup",end).put("state",end.optBoolean("no_current_call_observed")?"call_ended":"hangup_unconfirmed");
                    }else if("hangup".equals(action)){
                        report.put("state","manual_diagnostic_no_auto_hangup");
                    }else report.put("state","handoff".equals(action)?"handoff_to_owner":"ended_without_terminal_action");
                }else report.put("state","transport_completed");
            }
        }catch(Exception e){try{report.put("state",cancelled.get()?"cancelled":"failed").put("error_class",e.getClass().getSimpleName()).put("failure_stage",stage);}catch(Exception ignored){}}
        finally{
            finished.set(true);synchronized(inputLock){listening.set(false);}
            if(binder!=null)try{PcmTransportTest.command(binder,11);}catch(Exception ignored){}
            if(wire!=null)wire.close();if(receiver!=null)try{receiver.join(2000);}catch(InterruptedException ignored){}
            try{
                if(syntheticBench!=null)report.put("synthetic",syntheticBench.evidence());
                if(realtime!=null)report.put("api_input_pcm_bytes",realtime.inputBytes.get()).put("api_output_pcm_bytes",realtime.outputBytes.get()).put("responses_completed",realtime.completed.get()).put("api_error",realtime.failure.get()).put("audio_sent_to_ai",realtime.inputBytes.get()>0).put("assistant_requested_end",realtime.stopRequested).put("assistant_terminal_action",realtime.terminalAction);
                if(realtime!=null)report.put("responses_limited_by_token_budget",realtime.limitedResponses.get());
                if(realtime!=null)report.put("speech_turns",realtime.speechTurns.get()).put("interruptions",realtime.interruptions.get()).put("truncations_confirmed",realtime.truncationsConfirmed.get()).put("pending_audio_bytes_at_stop",realtime.output.size()).put("response_generating_at_stop",realtime.responseGenerating());
                report.put("call_received_pcm_bytes",received.get()).put("call_ai_pcm_bytes_submitted",played).put("greeting_pcm_bytes",greetingBytes);
                if(binder!=null){
                    String helper="{}";for(int i=0;i<40;i++){helper=PcmTransportTest.command(binder,10);String state=new JSONObject(helper).optString("state");if(!"running".equals(state)&&!"starting".equals(state))break;Thread.sleep(100);}
                    JSONObject h=new JSONObject(helper);report.put("helper",h);
                    String outcome=report.optString("state");
                    if(("transport_completed".equals(outcome)||"handoff_to_owner".equals(outcome)||"ended_without_terminal_action".equals(outcome)
                            ||"manual_diagnostic_no_auto_hangup".equals(outcome))
                            &&(!"stopped".equals(h.optString("state"))||!h.optString("error").isEmpty()))report.put("state","failed");
                }
            }catch(Exception e){try{report.put("state","failed");}catch(Exception ignored){}}
                if(realtime!=null){
                    try{realtime.finishTranscript(report.optString("state","unknown"));
                        report.put("transcript_turns",realtime.transcriptTurns()).put("transcript_gaps",realtime.transcriptGaps());}
                    catch(Exception storageFailure){try{report.put("transcript_storage_error",storageFailure.getClass().getSimpleName()).put("state","failed");}catch(Exception ignored){}}
                    realtime.saveHistory(report.optString("state","unknown"));realtime.close();
                }
            if(watchdog!=null)watchdog.cancel(false);if(timer!=null)timer.shutdownNow();
            try{report.put("finished_wall_ms",System.currentTimeMillis());}catch(Exception ignored){}
            String result=report.toString();c.getSharedPreferences("ai-trial",Context.MODE_PRIVATE).edit().putString("report",result).commit();active.set(false);listener.result(result);
        }
    }
    private static JSONObject finishWhitelistedCall(IBinder binder,String token,PcmSocket channel) throws Exception {
        if(binder==null||token==null||token.isEmpty())throw new SecurityException("Mission sans appel identifié");
        FutureTask<JSONObject> requestTask=new FutureTask<>(()->{
            Parcel request=Parcel.obtain(),reply=Parcel.obtain();
            try{
                request.writeInterfaceToken(BridgeProvider.DESCRIPTOR);request.writeString(token);
                if(!binder.transact(20,request,reply,0))throw new IOException("Raccrochage indisponible");
                reply.readException();
                String result=reply.readString();
                if(result==null)throw new IOException("Réponse de raccrochage absente");
                JSONObject evidence=new JSONObject(result);
                if(!"com.whatsapp".equals(evidence.optString("target")))throw new IOException("Cible de raccrochage invalide");
                return evidence;
            }finally{request.recycle();reply.recycle();}
        });
        new Thread(requestTask,"whatsapp-verified-hangup").start();
        byte[] silence=new byte[960];
        long until=SystemClock.elapsedRealtime()+18000,next=SystemClock.elapsedRealtime();
        while(!requestTask.isDone()&&SystemClock.elapsedRealtime()<until){
            try{channel.write(silence,silence.length,500);}catch(Exception closed){break;}
            next=Math.max(next+20,SystemClock.elapsedRealtime()+20);long delay=next-SystemClock.elapsedRealtime();if(delay>0)Thread.sleep(Math.min(delay,20));
        }
        return requestTask.get(Math.max(1,until-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS);
    }
    static String saved(Context c){return c.getSharedPreferences("ai-trial",Context.MODE_PRIVATE).getString("report","{}");}
}
