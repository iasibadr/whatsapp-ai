package live.pharmaradio.whatsappai;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Delayed phone-only activation and synthetic bench; never places a call. */
final class AutonomyBench {
    interface Listener {void update(String report);}
    private static final AtomicBoolean live=new AtomicBoolean(),preparing=new AtomicBoolean(),cancelled=new AtomicBoolean();
    static boolean running(){return live.get();}
    static void cancelPreparation(){if(preparing.get())cancelled.set(true);}
    static String saved(Context c){return c.getSharedPreferences("local-bench-report",0).getString("report","Aucun banc local enregistré.");}
    static String rpc(IBinder binder,int code)throws Exception{
        Parcel req=Parcel.obtain(),reply=Parcel.obtain();
        try{req.writeInterfaceToken(BridgeProvider.DESCRIPTOR);if(code==12)req.writeString("local-synthetic-no-call");
            if(!binder.transact(code,req,reply,0))throw new IOException("Local bench unavailable");reply.readException();return code==12?"":reply.readString();
        }finally{req.recycle();reply.recycle();}
    }
    static void start(Activity activity,Listener listener){
        if(!live.compareAndSet(false,true))return;
        cancelled.set(false);preparing.set(true);
        Context context=activity.getApplicationContext();
        Handler ui=new Handler(Looper.getMainLooper());
        activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(()->{
            JSONObject report=new JSONObject();IBinder helper=null;String phase="delay";
            try{
                if(!LocalActivationService.paired(context))throw new IOException("Association locale absente");
                if(BridgeProvider.getHelper()!=null)throw new IOException("Arrêtez le relais avant ce test de démarrage autonome");
                long scheduled=SystemClock.elapsedRealtime();
                report.put("state","scheduled").put("scheduled_elapsed_ms",scheduled).put("scheduled_wall_ms",System.currentTimeMillis())
                    .put("delay_seconds",20).put("external_adb_independence","requires_external_observation");
                listener.update("Départ dans20secondes. Restez sur cet écran. Aucun appel ne sera lancé.");
                while(SystemClock.elapsedRealtime()-scheduled<20000){if(cancelled.get())throw new IOException("Préparation annulée");SystemClock.sleep(100);}
                if(context.getSystemService(AudioManager.class).getMode()!=0)throw new IOException("Audio occupé");
                phase="local_activation";report.put("activation_started_wall_ms",System.currentTimeMillis());
                ui.post(()->{if(!cancelled.get())LocalActivationService.start(context,false);});
                long deadline=SystemClock.elapsedRealtime()+45000;
                while((helper=BridgeProvider.getHelper())==null){if(cancelled.get()||SystemClock.elapsedRealtime()>=deadline)throw new IOException("Activation non confirmée");SystemClock.sleep(100);}
                JSONObject status=new JSONObject(rpc(helper,1));
                if(!"0.22-pcm-transport".equals(status.optString("version"))||status.optInt("helper_uid")!=2000)throw new IOException("Relais incompatible");
                if(cancelled.get())throw new IOException("Préparation annulée");
                report.put("activation_confirmed_wall_ms",System.currentTimeMillis()).put("helper_version",status.getString("version"));
                phase="synthetic_audio";preparing.set(false); // Peer activity now owns the visible stop control.
                rpc(helper,12);deadline=SystemClock.elapsedRealtime()+25000;
                JSONObject result;
                do{result=new JSONObject(rpc(helper,13));if(!"starting".equals(result.optString("state")))break;
                    if(SystemClock.elapsedRealtime()>=deadline)throw new IOException("Banc local expiré");SystemClock.sleep(200);
                }while(true);
                report.put("state",result.optString("state")).put("bench",result);
            }catch(Exception e){try{report.put("state","failed").put("error",e.getClass().getSimpleName()+": "+e.getMessage());}catch(Exception ignored){}}
            finally{
                preparing.set(false);
                try{report.put("phase",phase).put("finished_wall_ms",System.currentTimeMillis()).put("audio_saved",false).put("whatsapp_verified",false);
                    String value=report.toString(2);boolean stored=context.getSharedPreferences("local-bench-report",0).edit().putString("report",value).commit();
                    live.set(false);listener.update(stored?value:"Rapport non enregistré : "+value);
                }catch(Exception e){live.set(false);listener.update("Rapport local indisponible");}
                ui.post(()->activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            }
        },"phone-only-bench-control").start();
    }
}
