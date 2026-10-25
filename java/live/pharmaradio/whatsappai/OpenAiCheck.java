package live.pharmaradio.whatsappai;

import android.content.Context;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Authentication check only: no microphone, PCM session, or model response. */
final class OpenAiCheck {
    private static final AtomicBoolean running=new AtomicBoolean();
    private static volatile String result="{\"state\":\"not_started\"}";
    private static int generation;
    static synchronized void invalidate(){generation++;result="{\"state\":\"not_started\"}";}
    static String status(){return result;}
    static void start(Context context){
        if(!running.compareAndSet(false,true))return;
        Context c=context.getApplicationContext(); result="{\"state\":\"running\"}";
        new Thread(()->{
            JSONObject report=new JSONObject(); HttpsURLConnection connection=null;
            java.util.concurrent.ScheduledExecutorService timer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            java.util.concurrent.ScheduledFuture<?> watchdog=null;
            AtomicBoolean expired=new AtomicBoolean();
            int revision;
            synchronized(OpenAiCheck.class){revision=generation;}
            try {
                report.put("test","realtime_client_secret_only").put("audio_sent",false).put("model_response_requested",false);
                connection=(HttpsURLConnection)new URL("https://api.openai.com/v1/realtime/client_secrets").openConnection();
                connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(10000);connection.setReadTimeout(15000);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                synchronized(KeyVault.class){
                    synchronized(OpenAiCheck.class){revision=generation;}
                    connection.setRequestProperty("Authorization","Bearer "+KeyVault.load(c));
                }
                final HttpsURLConnection pending=connection;
                watchdog=timer.schedule(()->{expired.set(true);pending.disconnect();},30,java.util.concurrent.TimeUnit.SECONDS);
                connection.setRequestProperty("Content-Type","application/json");
                byte[] payload=new JSONObject().put("expires_after",new JSONObject().put("anchor","created_at").put("seconds",60))
                    .put("session",new JSONObject().put("type","realtime").put("model","gpt-realtime-2.1")).toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try(java.io.OutputStream out=connection.getOutputStream()){out.write(payload);}
                int http=connection.getResponseCode();report.put("http_status",http);
                if(http<200||http>=300)throw new IllegalStateException("HTTP");
                byte[] response;
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[4096];int size;
                    while((size=in.read(buffer))!=-1){if(expired.get())throw new java.io.IOException("Deadline");if(out.size()+size>65536)throw new IllegalStateException("Size");out.write(buffer,0,size);}
                    response=out.toByteArray();java.util.Arrays.fill(buffer,(byte)0);
                }
                try {
                    JSONObject token=new JSONObject(new String(response,StandardCharsets.UTF_8));
                    if(expired.get()||token.optString("value").isEmpty()||token.optLong("expires_at")<=System.currentTimeMillis()/1000)throw new IllegalStateException("Token");
                    report.put("ephemeral_credential_received",true).put("state","passed").put("model","gpt-realtime-2.1");
                }finally{java.util.Arrays.fill(response,(byte)0);}
            }catch(Exception e){try{report.put("state","failed").put("error_class",e.getClass().getSimpleName());}catch(Exception ignored){}}
            finally{
                if(watchdog!=null)watchdog.cancel(false);timer.shutdownNow();
                if(connection!=null)connection.disconnect();
                synchronized(OpenAiCheck.class){if(revision==generation)result=report.toString();}
                running.set(false);
            }
        },"openai-auth-check").start();
    }
}
