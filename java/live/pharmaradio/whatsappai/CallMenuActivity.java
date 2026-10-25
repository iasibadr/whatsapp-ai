package live.pharmaradio.whatsappai;
import android.app.Activity; import android.os.Bundle; import android.os.Handler; import android.widget.*;
public class CallMenuActivity extends Activity {
 private TextView status; private final Handler handler=new Handler();
 private final Runnable refresh=new Runnable(){public void run(){status.setText(CallSessionService.status);handler.postDelayed(this,500);}};
 public void onCreate(Bundle b){super.onCreate(b);LinearLayout p=Ui.page(this,"Conversation WhatsApp avec IA","Vous gardez la main");
 if(CallSessionService.currentDestination!=null)ContactPortrait.add(p,CallSessionService.currentDestination,"");
 status=Ui.text(p,CallSessionService.status,18);
 Ui.button(p,"Arrêter l’IA et reprendre",true,()->CallSessionService.stop(this));
 EditText instructions=Ui.field(p,"Consigne privée à l’IA (600 caractères)",true,600);
 Ui.button(p,"Transmettre la consigne",false,()->{if(getSystemService(android.app.KeyguardManager.class).isKeyguardLocked()){status.setText("Déverrouillez le téléphone.");return;}if(CallSessionService.steer(instructions.getText().toString())){instructions.setText("");}else status.setText("Aucune session IA disponible.");});
 Ui.text(p,"L’arrêt de l’IA ne raccroche pas WhatsApp. Pour terminer l’appel, utilisez le bouton rouge dans WhatsApp.",16);
 Ui.button(p,"Ouvrir WhatsApp",false,()->{android.content.Intent i=getPackageManager().getLaunchIntentForPackage("com.whatsapp");if(i!=null)startActivity(i);});}
 protected void onResume(){super.onResume();handler.post(refresh);} protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
