package live.pharmaradio.whatsappai;

import android.app.*;
import android.content.*;
import android.os.*;

/** Owns one explicit outgoing attempt. Never sticky and never restored after process death. */
public final class CallSessionService extends Service {
    static volatile String status="Aucune mission en cours.";
    static volatile boolean running;
    static volatile String currentDestination;
    static volatile boolean selfTestCompleted;
    private static volatile String readyNonce;
    static boolean readyFor(String token){return running && token!=null && token.equals(readyNonce);}
    private static volatile CallSessionService instance;
    static boolean steer(String text){CallSessionService s=instance;return s!=null&&s.trial!=null&&s.trial.steer(text);}
    @Override public void onCreate(){super.onCreate();instance=this;}
    private volatile boolean cancelled;
    private volatile AiTrial trial;
    private Thread worker;
    private PowerManager.WakeLock wake;
    private String nonce;
    private IBinder bridge;
    static String command(IBinder binder,int code,String argument) throws Exception {
        Parcel req=Parcel.obtain(),res=Parcel.obtain();
        try {req.writeInterfaceToken(BridgeProvider.DESCRIPTOR);if(argument!=null)req.writeString(argument);
            if(!binder.transact(code,req,res,0))throw new IllegalStateException("Relais incompatible");
            res.readException();return res.readString();
        }finally{req.recycle();res.recycle();}
    }
    static void stop(Context c){c.stopService(new Intent(c,CallSessionService.class));}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||"stop".equals(intent.getAction())){cancel();return START_NOT_STICKY;}
        if(running){return START_NOT_STICKY;}
        running=true;cancelled=false;currentDestination=intent.getStringExtra("destination");
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("whatsapp-ai-call","Mission WhatsApp",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CallSessionService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,CallMenuActivity.class),PendingIntent.FLAG_IMMUTABLE);
        startForeground(8101,new Notification.Builder(this,"whatsapp-ai-call").setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle("WhatsApp AI — mission autorisée").setContentText("Attente du décrochage · arrêt manuel possible")
            .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Arrêter l’IA",stop).build()).build());
        if("selftest".equals(intent.getAction())){
            selfTestCompleted=true;status="Service d’appel démarré sans appel.";
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                running=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
            },1500);
            return START_NOT_STICKY;
        }
        nonce=intent.getStringExtra("nonce");bridge=BridgeProvider.getHelper();readyNonce=nonce;
        worker=new Thread(()->{
            try {
                if(nonce==null||bridge==null)throw new IllegalStateException();
                PreparedMission mission=new PreparedMission(intent.getStringExtra("brief"),DossierPayload.decode(intent.getStringExtra("dossier")));
                String destination=intent.getStringExtra("destination");
                wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"WhatsAppAI:mission");wake.acquire();
                status="Appel WhatsApp préparé. Confirmez l’appel dans WhatsApp si demandé.";
                long deadline=SystemClock.elapsedRealtime()+120000;
                while(!cancelled&&SystemClock.elapsedRealtime()<deadline){
                    String result=command(bridge,17,nonce);
                    if("failed".equals(result))throw new IllegalStateException("Appel non conforme");
                    if(!"waiting".equals(result)){
                        if(cancelled)break;
                        trial=new AiTrial(result,mission,destination);
                        if(cancelled){trial.cancel();break;}
                        trial.run(this,false,true,0,value->status=value);
                        org.json.JSONObject trialReport=new org.json.JSONObject(AiTrial.saved(this));
                        String outcome=trialReport.optString("state");
                        if(trialReport.optInt("transcript_turns")>0)
                            CallNotices.show(this,4408,"WhatsApp AI — dialogue à relire",
                                    trialReport.optInt("transcript_gaps")>0
                                            ? "La transcription comporte des passages incertains. Ouvrez la fiche d’appel."
                                            : "Ouvrez la fiche d’appel pour contrôler la mission.",HistoryActivity.class);
                        status="call_ended".equals(outcome)
                                ? "Mission terminée et fin de l’appel WhatsApp confirmée."
                                : "handoff_to_owner".equals(outcome)
                                ? "L’IA vous rend la conversation. L’appel WhatsApp reste ouvert et votre micro peut être entendu."
                                : "Session IA terminée. Raccrochage non confirmé ; vérifiez WhatsApp. Votre micro peut être entendu.";
                        if(!"call_ended".equals(outcome))
                            CallNotices.show(this,4409,"Assistant IA arrêté",
                                    "Vérifiez l’appel WhatsApp : votre micro peut être entendu.",CallMenuActivity.class);
                        return;
                    }
                    Thread.sleep(250);
                }
                status=cancelled?"IA arrêtée. Reprenez dans WhatsApp.":"Attente expirée. Aucun rappel automatique.";
            }catch(Exception e){
                String phase="inconnu";
                if(bridge!=null)try{phase=new org.json.JSONObject(command(bridge,19,null)).optString("phase","inconnu");}catch(Exception ignored){}
                status="IA arrêtée : contrôle d’appel "+phase+". Reprenez dans WhatsApp.";
                CallNotices.show(this,4409,"Assistant IA arrêté",
                        "Vérifiez l’appel WhatsApp : votre micro peut être entendu.",CallMenuActivity.class);
            }
            finally{
                if(nonce!=null&&bridge!=null)try{command(bridge,18,nonce);}catch(Exception ignored){}
                if(wake!=null&&wake.isHeld())wake.release();
                readyNonce=null;running=false;currentDestination=null;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
            }
        },"whatsapp-ai-mission");worker.start();
        return START_NOT_STICKY;
    }
    private void cancel(){cancelled=true;if(trial!=null)trial.cancel();if(worker==null){running=false;stopSelf();}}
    @Override public void onDestroy(){readyNonce=null;currentDestination=null;cancel();if(instance==this)instance=null;super.onDestroy();}
}
