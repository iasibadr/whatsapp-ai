package live.pharmaradio.whatsappai;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

public class BridgeProvider extends ContentProvider {
    static final String DESCRIPTOR = "live.pharmaradio.whatsappai.CallAudio";
    static final String AUTHORITY = "live.pharmaradio.whatsappai.bridge";
    private static volatile IBinder helper;

    static IBinder getHelper() {
        IBinder binder = helper;
        return binder != null && binder.isBinderAlive() ? binder : null;
    }

    @Override
    public boolean onCreate() { return true; }

    @Override
    public synchronized Bundle call(String method, String argument, Bundle extras) {
        int uid = Binder.getCallingUid();
        if("ai-synthetic-start".equals(method)||"ai-conversation-start".equals(method)||"ai-trial-status".equals(method)){
            if(uid!=2000)throw new SecurityException("Shell uniquement");
            if(!"ai-trial-status".equals(method)){
                if(!SetupActivity.visible)throw new IllegalStateException("Ouvrez le laboratoire avant cet essai");
                new Thread(()->new AiTrial().run(getContext(),true,"ai-conversation-start".equals(method),0,value->{}),"synthetic-ai-test").start();
            }
            Bundle result=new Bundle();result.putString("report",AiTrial.saved(getContext()));return result;
        }
        if(method!=null && method.startsWith("credentials-")) {
            if(uid!=2000)throw new SecurityException("Shell uniquement");
            Bundle result=new Bundle();
            try {
                if("credentials-public-key".equals(method))result.putString("public_key",CredentialImport.publicKey(getContext()));
                else if("credentials-import".equals(method))CredentialImport.receive(getContext(),argument);
                else if("credentials-check".equals(method))OpenAiCheck.start(getContext());
                else if(!"credentials-status".equals(method))throw new IllegalArgumentException("Methode inconnue");
                result.putBoolean("configured",KeyVault.configured(getContext()));
                result.putString("api_check",OpenAiCheck.status());
            }catch(Exception e){result.putString("error_class",e.getClass().getSimpleName());}
            return result;
        }
        if("local-socket-bench".equals(method)&&uid==2000){Bundle result=new Bundle();result.putBinder("endpoint",LabSocketBench.CONTROL);return result;}
        if (!"publish".equals(method) || uid != 2000 || extras == null) throw new SecurityException("Publication shell uniquement");
        IBinder candidate = extras.getBinder("bridge");
        if (candidate == null || !candidate.isBinderAlive()) throw new IllegalArgumentException("Binder absent");
        if (helper != candidate) {
            try {
                candidate.linkToDeath(() -> {
                    if (helper == candidate) helper = null;
                }, 0);
            } catch (Exception error) { throw new IllegalStateException("Binder arrete"); }
            helper = candidate;
        }
        Bundle result = new Bundle();
        result.putInt("app_uid", Process.myUid());
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] arguments, String order) { throw new UnsupportedOperationException(); }
    @Override
    public String getType(Uri uri) { return null; }
    @Override
    public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override
    public int delete(Uri uri, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] arguments) { throw new UnsupportedOperationException(); }
}
