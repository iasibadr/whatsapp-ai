package live.pharmaradio.whatsappai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;

public class OutgoingCallActivity extends Activity {
    private EditText number;
    private EditText brief;
    private EditText questions;
    private EditText limits;
    private EditText handoff;
    private android.widget.CheckBox report;
    private TextView status;
    private Button dictate;
    private Button ai;
    private MediaRecorder recorder;
    private File recording;
    private PhoneAgent transcription;
    private boolean working;
    private boolean placing;
    private volatile boolean visible;
    private MissionDossier dossier = MissionDossier.empty();
    private TextView dossierStatus;
    private LinearLayout portraitHolder;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        File[] leftovers = getCacheDir().listFiles((directory, name) -> name.startsWith("call-brief-") && name.endsWith(".m4a"));
        if (leftovers != null) for (File file : leftovers) file.delete();
        LinearLayout content = Ui.page(this, "Appel WhatsApp avec IA", "Choisissez le contact et la mission, puis confirmez dans WhatsApp");
        LinearLayout layout = Ui.card(content, "1. Qui appeler ?", "Aucun appel ne part avant votre confirmation.");
        button(layout, "Choisir un contact", () -> {
            if (working || recorder != null || placing) return;
            try { startActivityForResult(new Intent(Intent.ACTION_PICK).setType(Phone.CONTENT_TYPE), 10); }
            catch (RuntimeException error) { status.setText("Sélecteur indisponible ; saisissez le numéro."); }
        });
        number = Ui.field(layout, "Numéro de téléphone", false, 35);
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        number.setSaveEnabled(false);
        number.setText(getIntent().getStringExtra("number") == null ? "" : getIntent().getStringExtra("number"));
        portraitHolder = new LinearLayout(this);
        portraitHolder.setOrientation(LinearLayout.VERTICAL);
        layout.addView(portraitHolder);
        number.setOnFocusChangeListener((view, focused) -> { if (!focused) refreshPortrait(); });
        refreshPortrait();
        layout = Ui.card(content, "2. Que doit faire l’IA ?", "Écrivez comme vous le diriez à une personne.");
        text(layout, "Exemple : « Demande les horaires et vérifie si une livraison est possible demain. »", 15).setTextColor(Ui.MUTED);
        brief = Ui.field(layout, "Votre demande en quelques phrases", true, 1600);
        brief.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        brief.setMinLines(3);
        brief.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1600)});
        brief.setSaveEnabled(false);
        dictate = button(layout, "Dicter mes consignes (60 s maximum)", () -> {
            if (recorder == null) startDictation();
            else stopDictation();
        });
        LinearLayout advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        Ui.button(layout, "Afficher les options de la mission", false, () -> {
            boolean showing = advanced.getVisibility() != android.view.View.VISIBLE;
            advanced.setVisibility(showing ? android.view.View.VISIBLE : android.view.View.GONE);
        });
        layout.addView(advanced);
        advanced.setVisibility(android.view.View.GONE);
        text(layout, "Par défaut : aucun achat ni paiement. Un bilan est proposé uniquement avec l’accord du correspondant.", 14).setTextColor(Ui.MUTED);
        questions = Ui.field(advanced, "Questions indispensables (facultatif)", true, 900);
        text(advanced, "Ce que l’IA ne doit pas accepter", 15);
        limits = Ui.field(advanced, "Exemples : achat, paiement, promesse", true, 450);
        limits.setText("Aucun engagement, achat ou paiement.");
        handoff = Ui.field(advanced, "Quand me rendre la conversation ?", true, 450);
        report = new android.widget.CheckBox(this);
        report.setText("Proposer un bilan structuré, uniquement si le correspondant accepte sa conservation 24 h");
        report.setTextColor(Ui.INK);
        report.setChecked(true);
        advanced.addView(report);
        button(advanced, "Choisir un exemple de mission", () -> {
            if (working || recorder != null || placing) return;
            MissionTemplate[] examples = MissionTemplate.examples();
            String[] labels = new String[examples.length];
            for (int index = 0; index < examples.length; index++) labels[index] = examples[index].title;
            new AlertDialog.Builder(this).setTitle("Modèles prêts à personnaliser")
                    .setItems(labels, (dialog, which) -> confirmTemplate(examples[which])).show();
        });
        button(advanced, "Charger une mission favorite", () -> {
            if (!working && recorder == null && !placing) startActivityForResult(new Intent(this, MissionLibraryActivity.class).putExtra("picker", true), 82);
        });
        button(advanced, "Enregistrer comme favori", () -> {
            if (working || recorder != null || placing) return;
            try {
                MissionTemplate template = new MissionTemplate("Brouillon", brief.getText().toString(), questions.getText().toString(),
                        limits.getText().toString(), handoff.getText().toString(), report.isChecked());
                startActivity(new Intent(this, MissionLibraryActivity.class).putExtra("draft", MissionLibrary.encode(template).toString()));
            } catch (Exception error) { status.setText(error instanceof IllegalArgumentException ? error.getMessage() : "Préparation du favori impossible."); }
        });
        LinearLayout documents = Ui.card(content, "Dossier de mission", "Ajoutez seulement les documents utiles à cet appel.");
        documents.setVisibility(android.view.View.GONE);
        Ui.button(content, "Ajouter un document (facultatif)", false, () ->
                documents.setVisibility(documents.getVisibility() == android.view.View.VISIBLE ? android.view.View.GONE : android.view.View.VISIBLE));
        dossierStatus = text(documents, "Aucune pièce jointe à cette mission.", 15);
        button(documents, "Préparer ou relire le dossier", () -> {
            if (working || recorder != null || placing) return;
            try { startActivityForResult(new Intent(this, MissionDossierActivity.class).putExtra("dossier", DossierPayload.encode(dossier)), 83); }
            catch (Exception error) { status.setText("Dossier indisponible. Aucun document ajouté ni appel lancé."); }
        });
        text(documents, "Les pièces validées seront transmises à OpenAI avec l’appel IA confirmé, ce qui peut augmenter les tokens. Elles ne sont pas conservées dans les favoris ni les fiches d’appel.", 14);
        layout = Ui.card(content, "3. Vérifier et appeler avec l’IA", "L’IA se présentera. Informez votre correspondant avant l’appel.");
        status = text(layout, "Relisez le contact et votre demande avant de confirmer.", 16);
        ai = Ui.button(layout, "Appeler sur WhatsApp avec l’IA", true, this::confirmCall);
        button(layout, "Effacer les consignes", () -> { if (!working && recorder == null && !placing) brief.setText(""); });
        text(advanced, "Pendant un appel, répondre à un second appel risque de mettre le premier en attente. Deux conversations simultanées ne sont pas validées.", 14);
        String templateId = getIntent().getStringExtra("template-id");
        if (templateId != null) loadTemplate(templateId);
    }

    private void loadTemplate(String id) {
        try { confirmTemplate(MissionLibrary.find(this, id)); }
        catch (Exception error) { status.setText("Ce favori n’est plus disponible. Aucun appel lancé."); }
    }

    private void confirmTemplate(MissionTemplate template) {
        if (working || recorder != null || placing) return;
        new AlertDialog.Builder(this).setTitle("Charger « " + template.title + " » ?")
                .setMessage("Cela remplace l’objectif, les questions, les limites et les conditions de reprise affichés. Le numéro ne change pas. Aucun appel ne sera lancé.")
                .setNegativeButton("Annuler", null).setPositiveButton("Charger", (dialog, which) -> {
                    if (working || recorder != null || placing) return;
                    brief.setText(template.objective);
                    questions.setText(template.questions);
                    limits.setText(template.limits);
                    handoff.setText(template.handoff);
                    report.setChecked(template.report);
                    status.setText("Modèle chargé. Personnalisez-le et vérifiez le destinataire avant de confirmer un appel.");
                }).show();
    }

    private TextView text(LinearLayout layout, String value, int size) {
        return Ui.text(layout, value, size);
    }

    private Button button(LinearLayout layout, String label, Runnable action) {
        return Ui.button(layout, label, label.startsWith("Faire appeler"), action);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 83) {
            if (result == RESULT_OK && data != null) {
                try {
                    dossier = DossierPayload.decode(data.getStringExtra("dossier"));
                    dossierStatus.setText(dossierSummary(dossier));
                } catch (Exception error) { status.setText("Dossier invalide. La sélection précédente est conservée."); }
            }
            return;
        }
        if (request == 82) {
            if (result == RESULT_OK && data != null) loadTemplate(data.getStringExtra("template-id"));
            return;
        }
        if (request != 10 || result != RESULT_OK || data == null || data.getData() == null) return;
        try (Cursor cursor = getContentResolver().query(data.getData(), new String[]{Phone.NUMBER}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) { number.setText(cursor.getString(0)); refreshPortrait(); }
        } catch (RuntimeException error) {
            status.setText("Impossible de lire le contact sélectionné ; saisissez le numéro.");
        }
    }

    private void refreshPortrait() {
        if (portraitHolder == null || number == null) return;
        portraitHolder.removeAllViews();
        String selected = number.getText().toString().trim();
        if (!selected.isEmpty()) ContactPortrait.add(portraitHolder, selected, "");
    }

    private String destination() {
        String raw = number.getText().toString().trim();
        String normalized = PhoneNumberUtils.normalizeNumber(raw);
        if (!raw.matches("[+0-9 ()-]+") || !normalized.matches("\\+?[0-9]{6,15}")) {
            status.setText("Saisissez un numéro complet, sans code spécial ni extension.");
            return null;
        }
        if (PhoneNumberUtils.isEmergencyNumber(normalized)) {
            status.setText("Utilisez directement Téléphone pour un appel d’urgence, jamais l’IA.");
            return null;
        }
        return normalized;
    }

    private String dossierSummary(MissionDossier selected) {
        if (selected.documents.isEmpty()) return "Aucune pièce jointe à cette mission.";
        StringBuilder result = new StringBuilder("Pièces transmises à OpenAI avec la mission :");
        for (MissionDocument document : selected.documents) result.append("\n• ").append(document.title)
                .append(document.shareable ? " · communicable" : " · privé pour l’IA");
        return result.toString();
    }

    private boolean audioFree() {
        if (getSystemService(AudioManager.class).getMode() != AudioManager.MODE_NORMAL) {
            status.setText("Terminez l’appel ou la session vocale en cours avant de préparer un appel IA.");
            return false;
        }
        return true;
    }

    private void confirmCall() {
        if(working||recorder!=null||placing||!audioFree()||CallSessionService.running)return;
        String target=destination();if(target==null)return;
        if(checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},72);return;}
        if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){
            status.setText("WhatsApp demande l’autorisation Téléphone pour ouvrir son écran d’appel. Accordez-la puis confirmez à nouveau.");
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE},73);return;
        }
        final Intent route;final PreparedMission mission;
        try{route=WhatsAppContactRoute.resolve(this,target);mission=new PreparedMission(CallMission.build(brief.getText().toString(),questions.getText().toString(),limits.getText().toString(),handoff.getText().toString(),report.isChecked()),dossier);
            if(!KeyVault.configured(this))throw new IllegalStateException("Configurez la clé OpenAI dans WhatsApp AI.");
            if(BridgeProvider.getHelper()==null)throw new IllegalStateException("Activez le service local dans WhatsApp AI.");
        }catch(Exception e){status.setText(e.getMessage()==null?"Préparation indisponible.":e.getMessage());return;}
        new AlertDialog.Builder(this).setTitle("Confirmer la mission WhatsApp")
          .setMessage("Contact : "+target+"\n\n"+mission.brief+"\n\n"+dossierSummary(mission.dossier)+"\n\nLa voix et les consignes seront transmises à OpenAI. Les paroles du correspondant et de l’IA seront transcrites en texte dans la fiche d’appel sur ce téléphone pendant "+AppSettings.historyDays(this)+" jour(s) après l’appel. La reconnaissance peut comporter des erreurs ou des passages manquants. Informez le correspondant et obtenez son accord avant cet appel. L’IA poursuit la mission jusqu’à sa fin ou à sa demande, puis tente de raccrocher uniquement ce même appel WhatsApp. Vous pouvez arrêter l’IA à tout moment. Si WhatsApp demande de confirmer l’appel, confirmez uniquement ce contact.")
          .setNegativeButton("Annuler",null).setPositiveButton("Correspondant informé : appeler",(d,w)->{
            if(placing||!audioFree())return;placing=true;updateButtons();
            new Thread(()->{
              String nonce=null;android.os.IBinder helper=BridgeProvider.getHelper();
              try{if(helper==null)throw new IllegalStateException();nonce=CallSessionService.command(helper,16,null);
                final String token=nonce;final String payload=DossierPayload.encode(mission.dossier);
                runOnUiThread(()->{
                  try{
                    if(!visible||isFinishing()||isDestroyed())throw new IllegalStateException();
                    startForegroundService(new Intent(this,CallSessionService.class).putExtra("nonce",token).putExtra("destination",target).putExtra("brief",mission.brief).putExtra("dossier",payload));
                    new Thread(()->{
                      long deadline=android.os.SystemClock.elapsedRealtime()+7000;
                      while(!CallSessionService.readyFor(token)&&android.os.SystemClock.elapsedRealtime()<deadline)
                          android.os.SystemClock.sleep(100);
                      final boolean ready=CallSessionService.readyFor(token);
                      runOnUiThread(()->{
                        try{
                          if(!ready||!visible||isFinishing()||isDestroyed())throw new IllegalStateException();
                          startActivity(route);
                          status.setText("Appel préparé. Suivez la confirmation WhatsApp si elle apparaît.");
                        }catch(Exception error){
                          if(ready)CallSessionService.stop(this);
                          new Thread(()->{try{CallSessionService.command(helper,18,token);}catch(Exception ignored){}}).start();
                          status.setText("Appel non lancé. Aucun rappel automatique.");
                        }finally{placing=false;updateButtons();}
                      });
                    },"wait-whatsapp-service").start();
                  }catch(Exception error){
                    new Thread(()->{try{CallSessionService.command(helper,18,token);}catch(Exception ignored){}}).start();
                    status.setText("Appel non lancé. Aucun rappel automatique.");placing=false;updateButtons();
                  }
                });
              }catch(Exception error){if(nonce!=null)try{CallSessionService.command(helper,18,nonce);}catch(Exception ignored){}
                runOnUiThread(()->{placing=false;updateButtons();status.setText("Préparation refusée. Vérifiez le service local et terminez tout appel en cours.");});}
            },"prepare-whatsapp").start();
          }).show();
    }

    private void startDictation() {
        if (working || placing || !audioFree()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 11);
            return;
        }
        try {
            recording = File.createTempFile("call-brief-", ".m4a", getCacheDir());
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(24000);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setMaxDuration(60000);
            recorder.setMaxFileSize(900000);
            recorder.setOutputFile(recording.getAbsolutePath());
            recorder.setOnInfoListener((source, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) stopDictation();
            });
            recorder.prepare();
            recorder.start();
            status.setText("Enregistrement de vos consignes… Touchez Arrêter pour les transcrire. Quitter cet écran annule l’enregistrement.");
            updateButtons();
        } catch (Exception error) {
            discardRecording();
            status.setText("Microphone indisponible ; vous pouvez écrire les consignes.");
        }
    }

    private void stopDictation() {
        if (recorder == null) return;
        try { recorder.stop(); }
        catch (RuntimeException error) {
            discardRecording();
            status.setText("Dictée trop courte ou interrompue ; réessayez.");
            return;
        }
        recorder.release();
        recorder = null;
        File audio = recording;
        recording = null;
        working = true;
        updateButtons();
        status.setText("Transcription OpenAI en cours… Aucun appel n’est lancé.");
        transcription = PhoneAgent.transcribe(audio, value -> {
            if (isFinishing() || isDestroyed()) return;
            working = false;
            brief.setText(value);
            status.setText(value.length() > 1600 ? "Dictée écourtée à 1 600 caractères : vérifiez l’objectif et complétez les autres champs avant d’appeler."
                    : "Vérifiez et corrigez les consignes, puis choisissez qui appelle.");
            updateButtons();
        }, error -> {
            if (isFinishing() || isDestroyed()) return;
            working = false;
            status.setText(error);
            updateButtons();
        });
    }

    private void updateButtons() {
        if (dictate == null) return;
        dictate.setEnabled(!working && !placing);
        dictate.setText(recorder == null ? "Dicter mes consignes (60 s maximum)" : "Arrêter et transcrire");
        ai.setEnabled(!working && !placing && recorder == null);
        brief.setEnabled(!working && !placing && recorder == null);
        number.setEnabled(!working && !placing && recorder == null);
        questions.setEnabled(!working && !placing && recorder == null);
        limits.setEnabled(!working && !placing && recorder == null);
        handoff.setEnabled(!working && !placing && recorder == null);
        report.setEnabled(!working && !placing && recorder == null);
    }

    private void discardRecording() {
        if (recorder != null) { recorder.release(); recorder = null; }
        if (recording != null) { recording.delete(); recording = null; }
        updateButtons();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        status.setText(grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED
                ? "Autorisation accordée. Touchez à nouveau le bouton pour continuer."
                : "Autorisation refusée. Aucun appel ou enregistrement lancé.");
    }

    @Override
    protected void onResume() {
        super.onResume();visible=true;
        if (placing && getSystemService(AudioManager.class).getMode() == AudioManager.MODE_NORMAL) {
            CallBridgeService service = CallBridgeService.instance;
            if (service == null || service.incoming == null || service.incoming.getState() == android.telecom.Call.STATE_DISCONNECTED) {
                placing = false;
                OutgoingPlan.clear();
                updateButtons();
            }
        }
    }

    @Override
    protected void onPause() {
        visible=false;
        if (recorder != null) {
            discardRecording();
            status.setText("Dictée annulée en quittant l’écran. Aucun audio envoyé.");
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        discardRecording();
        if (transcription != null) transcription.close();
        super.onDestroy();
    }
}
