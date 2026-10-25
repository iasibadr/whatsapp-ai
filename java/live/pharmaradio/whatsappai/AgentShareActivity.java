package live.pharmaradio.whatsappai;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class AgentShareActivity extends PrivateActivity {
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private MissionTemplate imported;
    private String notice = "";
    private boolean busy;
    private boolean visible;

    @Override protected void showPrivateContent() {
        visible = true;
        LinearLayout content = Ui.page(this, "Agent APK", "Partager un outil et son savoir-faire, pas ses données privées");
        Ui.text(content, "WhatsApp AI est une application Android indépendante utilisant l’API OpenAI. Ce n’est pas une application officielle OpenAI.", 16);
        Ui.text(content, "Choisissez Quick Share dans le menu de partage pour envoyer à un téléphone à proximité, s’il est disponible. Le destinataire accepte, installe avec les validations Android, puis configure sa propre clé et ses autorisations. Le simple rapprochement ne déclenche aucune installation.", 15);
        LinearLayout application = Ui.card(content, "Partager l’application", "WhatsApp AI · version " + BuildConfig.VERSION_NAME);
        Ui.text(application, "L’APK contient le programme et les consignes génériques, jamais votre stockage privé : clé API, identité d’association, profil, contacts, messages et historique ne sont pas copiés.", 15);
        Ui.button(application, "Partager l’APK seule…", true, () -> confirmExport(null)).setEnabled(!busy);
        LinearLayout templates = Ui.card(content, "Partager une spécialisation", "APK + un modèle de mission relu par vous");
        Ui.text(templates, "Aucun favori personnel n’est exporté automatiquement. Choisissez un exemple ou rédigez un modèle sans nom, numéro, référence privée ni secret. Le modèle ne remplace pas les règles de sécurité de l’assistant.", 15);
        Ui.button(templates, "Créer un modèle à partager…", true, () -> editTemplate(null)).setEnabled(!busy);
        for (MissionTemplate template : MissionTemplate.examples()) {
            Ui.button(templates, template.title + "…", false, () -> editTemplate(template)).setEnabled(!busy);
        }
        LinearLayout receive = Ui.card(content, "Recevoir un modèle", "Après installation de WhatsApp AI, choisissez le fichier JSON reçu");
        Ui.text(receive, "Le fichier n’est pas un programme et son auteur n’est pas authentifié. Relisez toutes les consignes. L’import ajoute seulement un favori ; il ne configure aucun compte, ne donne aucune permission et ne lance aucun appel.", 15);
        Ui.button(receive, "Choisir un modèle reçu…", true, () -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(picker, 6510); }
            catch (android.content.ActivityNotFoundException error) { notice = "Sélecteur de fichiers indisponible."; showPrivateContent(); }
        }).setEnabled(!busy);
        if (imported != null) {
            MissionTemplate candidate = imported;
            LinearLayout review = Ui.card(content, "Modèle reçu · à vérifier", AgentSharePolicy.preview(candidate));
            Ui.button(review, "Enregistrer dans mes favoris…", true, () -> new AlertDialog.Builder(this)
                    .setTitle("Ajouter ce modèle ?").setMessage("Les consignes ont été relues. Aucun favori existant ne sera remplacé. Aucun appel ne sera passé.")
                    .setNegativeButton("Annuler", null).setPositiveButton("Enregistrer", (dialog, which) -> {
                        try {
                            MissionLibrary.save(this, candidate);
                            imported = null;
                            notice = "Modèle ajouté aux favoris. Aucun appel passé.";
                        } catch (Exception error) { notice = error instanceof IllegalArgumentException ? error.getMessage() : "Enregistrement impossible."; }
                        showPrivateContent();
                    }).show());
            Ui.button(review, "Refuser ce modèle", false, () -> { imported = null; showPrivateContent(); });
        }
        Ui.text(content, busy ? "Préparation en cours…" : notice, 15);
    }

    private void editTemplate(MissionTemplate example) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        EditText title = Ui.field(form, "Titre du modèle", false, 60);
        EditText objective = Ui.field(form, "Objectif générique", true, 1600);
        EditText questions = Ui.field(form, "Questions", true, 900);
        EditText limits = Ui.field(form, "Limites", true, 450);
        EditText handoff = Ui.field(form, "Quand rendre la main", true, 450);
        CheckBox report = new CheckBox(this);
        report.setText("Proposer un bilan avec le consentement du correspondant");
        form.addView(report);
        if (example != null) {
            title.setText(example.title); objective.setText(example.objective); questions.setText(example.questions);
            limits.setText(example.limits); handoff.setText(example.handoff); report.setChecked(example.report);
        }
        android.widget.TextView feedback = Ui.text(form, "Ces champs seront transmis intégralement. Retirez toutes les données personnelles.", 15);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Spécialisation à partager")
                .setView(scroll).setNegativeButton("Annuler", null).setPositiveButton("Voir l’aperçu", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                MissionTemplate candidate = AgentSharePolicy.validate(new MissionTemplate(title.getText().toString(), objective.getText().toString(),
                        questions.getText().toString(), limits.getText().toString(), handoff.getText().toString(), report.isChecked()));
                dialog.dismiss();
                confirmExport(candidate);
            } catch (IllegalArgumentException error) { feedback.setText(error.getMessage()); }
        }));
        dialog.show();
    }

    private void confirmExport(MissionTemplate template) {
        if (BuildConfig.DEBUG) {
            new AlertDialog.Builder(this).setTitle("Version de développement")
                    .setMessage("Pour partager une application sans outils de test, utilisez la version de diffusion signée.")
                    .setPositiveButton("Compris", null).show();
            return;
        }
        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        Ui.text(preview, "Fichiers : WhatsApp AI " + BuildConfig.VERSION_NAME + ".apk" + (template == null ? "" : " + agent-apk-modele.json")
                + "\nAucune clé, donnée du profil, conversation ou autorisation n’est jointe automatiquement.", 16);
        if (template != null) Ui.text(preview, AgentSharePolicy.preview(template), 16);
        CheckBox consent = new CheckBox(this);
        consent.setText(template == null ? "Je souhaite partager cette application." : "J’ai relu le modèle et retiré toute donnée personnelle ou secrète.");
        preview.addView(consent);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(preview);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Vérifier avant de partager")
                .setView(scroll).setNegativeButton("Annuler", null).setPositiveButton("Choisir le destinataire", null).create();
        dialog.setOnShowListener(ignored -> {
            android.widget.Button send = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            send.setEnabled(false);
            consent.setOnCheckedChangeListener((button, checked) -> send.setEnabled(checked));
            send.setOnClickListener(view -> { dialog.dismiss(); share(template); });
        });
        dialog.show();
    }

    private void share(MissionTemplate template) {
        if (busy) return;
        busy = true;
        showPrivateContent();
        worker.execute(() -> {
            try {
                if (getApplicationInfo().splitSourceDirs != null && getApplicationInfo().splitSourceDirs.length != 0) {
                    throw new IllegalArgumentException("Cette installation utilise plusieurs APK. Partagez la version de diffusion complète.");
                }
                AgentShareProvider.cleanup(this);
                ArrayList<Uri> files = new ArrayList<>();
                File apk = new File(AgentShareProvider.directory(this), java.util.UUID.randomUUID() + ".apk");
                File temporary = new File(apk.getPath() + ".pending");
                try {
                    java.nio.file.Files.copy(new File(getApplicationInfo().sourceDir).toPath(), temporary.toPath());
                    java.nio.file.Files.move(temporary.toPath(), apk.toPath());
                } finally { temporary.delete(); }
                files.add(uri(apk));
                if (template != null) {
                    File model = new File(AgentShareProvider.directory(this), java.util.UUID.randomUUID() + ".json");
                    java.nio.file.Files.write(model.toPath(), AgentSpecialization.encode(template).getBytes(StandardCharsets.UTF_8));
                    files.add(uri(model));
                }
                runOnUiThread(() -> {
                    busy = false;
                    if (isDestroyed() || isFinishing() || !visible) return;
                    Intent send = new Intent(files.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
                    send.setType(files.size() == 1 ? "application/vnd.android.package-archive" : "*/*");
                    if (files.size() == 1) send.putExtra(Intent.EXTRA_STREAM, files.get(0));
                    else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                    ClipData clip = ClipData.newUri(getContentResolver(), "Agent APK", files.get(0));
                    for (int index = 1; index < files.size(); index++) clip.addItem(new ClipData.Item(files.get(index)));
                    send.setClipData(clip);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    notice = "Menu de partage ouvert. Cela ne confirme ni réception ni installation. Les fichiers temporaires sont accessibles pendant une heure au maximum.";
                    try { startActivity(Intent.createChooser(send, "Partager cet Agent APK")); }
                    catch (android.content.ActivityNotFoundException error) { notice = "Aucune application de partage disponible."; showPrivateContent(); }
                });
            } catch (Exception error) {
                runOnUiThread(() -> { busy = false; notice = "Partage impossible. Aucun fichier privé n’a été exporté."; if (visible) showPrivateContent(); });
            }
        });
    }

    private Uri uri(File file) { return new Uri.Builder().scheme("content").authority(AgentShareProvider.AUTHORITY).appendPath(file.getName()).build(); }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 6510 || result != RESULT_OK || data == null || data.getData() == null || busy) return;
        Uri selected = data.getData();
        if (!"content".equals(selected.getScheme())) { notice = "Choisissez un document local reçu."; return; }
        busy = true;
        worker.execute(() -> {
            MissionTemplate candidate = null;
            try (java.io.InputStream input = getContentResolver().openInputStream(selected)) {
                if (input == null) throw new java.io.IOException();
                candidate = AgentSpecialization.decode(input.readNBytes(AgentSharePolicy.MAX_BYTES + 1));
            } catch (Exception error) { }
            MissionTemplate parsed = candidate;
            runOnUiThread(() -> {
                busy = false;
                if (isDestroyed() || isFinishing()) return;
                imported = parsed;
                notice = parsed == null ? "Modèle refusé : fichier invalide, incompatible ou contenant un secret détecté. Aucun favori modifié."
                        : "Modèle chargé pour lecture uniquement. Il n’est pas encore enregistré.";
                if (visible) showPrivateContent();
            });
        });
    }

    @Override protected void onPause() { visible = false; super.onPause(); }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
