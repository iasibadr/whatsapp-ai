package live.pharmaradio.whatsappai;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class CredentialsActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView status; private EditText input;
    private final Runnable refresh=new Runnable(){public void run(){
        status.setText((KeyVault.configured(CredentialsActivity.this)?"Clé enregistrée et chiffrée sur ce téléphone.":"Aucune clé enregistrée.")
            +"\n\n"+OpenAiCheck.status());ui.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout page=Ui.page(this,"Clé OpenAI","Votre clé reste privée sur ce téléphone");
        LinearLayout panel=Ui.card(page,"Votre accès vocal","Chaque utilisateur enregistre sa propre clé. Elle est chiffrée sur ce téléphone et absente de l’APK partagée.");
        input=Ui.field(panel,"Nouvelle clé (facultatif)",false,1000);input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSaveEnabled(false);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        button(panel,"Enregistrer la clé saisie",()->{
            try{KeyVault.save(this,input.getText().toString());Toast.makeText(this,"Clé enregistrée",Toast.LENGTH_SHORT).show();}
            catch(Exception e){Toast.makeText(this,"Enregistrement refusé",Toast.LENGTH_SHORT).show();}
            finally{input.setText("");}
        });
        button(panel,"Vérifier l’accès vocal OpenAI — sans audio",()->OpenAiCheck.start(this));
        button(panel,"Effacer la clé de cette application",()->new AlertDialog.Builder(this).setTitle("Effacer la clé WhatsApp AI ?")
            .setNegativeButton("Annuler",null).setPositiveButton("Effacer",(d,w)->{
                try{KeyVault.clear(this);}catch(Exception e){Toast.makeText(this,"Effacement impossible",Toast.LENGTH_SHORT).show();}
            }).show());
        status=Ui.text(Ui.card(page,"État de la clé","Le contrôle réseau ne transmet aucun audio."),"Vérification…",15);
        status.setTextColor(Ui.MUTED);
    }
    private void button(LinearLayout p,String text,Runnable action){Ui.button(p,text,text.startsWith("Enregistrer"),action);}
    @Override public void onResume(){super.onResume();ui.post(refresh);}
    @Override public void onPause(){ui.removeCallbacks(refresh);input.setText("");super.onPause();}
}
