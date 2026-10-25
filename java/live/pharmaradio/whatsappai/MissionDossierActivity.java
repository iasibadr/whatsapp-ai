package live.pharmaradio.whatsappai;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.ScrollView;

public class MissionDossierActivity extends PrivateActivity {
    private MissionDossier dossier = MissionDossier.empty();
    private boolean importing;
    private boolean displayAllowed;
    private boolean needsRefresh;
    private String imported;
    private String notice = "";
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String encoded = getIntent().getStringExtra("dossier");
        if (encoded != null) {
            try { dossier = DossierPayload.decode(encoded); }
            catch (Exception error) { notice = "Dossier illisible : annulez pour conserver le dossier de la préparation."; }
        }
    }

    @Override
    protected void showPrivateContent() {
        displayAllowed = true;
        needsRefresh = false;
        LinearLayout content = Ui.page(this, "Dossier de cette mission", "Seulement les pièces que vous choisissez et validez");
        Ui.text(content, "Le texte des pièces validées sera envoyé à OpenAI seulement lors de la préparation de l’appel IA confirmé. Les pièces privées ne doivent pas être divulguées au correspondant. Ne joignez ni mot de passe ni code. Cette séparation est une consigne à l’IA, pas une garantie contre toute erreur : minimisez les données sensibles.", 15);
        Ui.text(content, "Dossier temporaire, non enregistré dans les favoris ou les fiches : 5 pièces, 6 000 caractères par pièce, 12 000 au total. Les originaux ne sont ni modifiés ni supprimés.", 15);
        Ui.text(content, notice, 16);
        Ui.button(content, "Ajouter une information", true, () -> { if (!importing) edit(-1, null, ""); });
        Ui.button(content, importing ? "Lecture du document en cours…" : "Importer un document sélectionné", false, () -> {
            if (importing) return;
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "text/markdown", "text/csv", "application/pdf"});
            try { startActivityForResult(pick, 91); }
            catch (RuntimeException error) { notice = "Sélecteur indisponible. Vous pouvez copier le texte manuellement."; showPrivateContent(); }
        });
        Ui.text(content, "TXT/MD/CSV en UTF-8 ; PDF texte sous Android 15 ou plus. Fichier limité à 2 Mo et PDF à 10 pages. Relisez et corrigez l’extraction ; les tableaux ou mises en page complexes peuvent être mal restitués. Aucun OCR ou envoi du fichier original à OpenAI.", 14);
        if (imported != null) Ui.button(content, "Relire le texte importé avant ajout", true, () -> edit(-1, null, imported));
        for (int index = 0; index < dossier.documents.size(); index++) {
            int selected = index;
            MissionDocument document = dossier.documents.get(index);
            LinearLayout card = Ui.card(content, document.title, document.shareable ? "Communicable au correspondant, dans les limites de la mission" : "Privé pour l’IA : ne pas citer au correspondant");
            Ui.text(card, document.text.length() + " caractères", 14);
            Ui.button(card, "Relire ou modifier cette pièce", false, () -> edit(selected, document, document.text));
            Ui.button(card, "Retirer cette pièce", false, () -> { dossier = dossier.remove(selected); showPrivateContent(); });
        }
        Ui.button(content, "Utiliser ce dossier pour la mission · " + dossier.documents.size() + " pièce(s)", true, () -> {
            if (importing) return;
            try {
                setResult(RESULT_OK, new Intent().putExtra("dossier", DossierPayload.encode(dossier)));
                finish();
            } catch (Exception error) { notice = "Validation impossible. Le dossier de préparation n’est pas remplacé."; showPrivateContent(); }
        });
        Ui.text(content, "Retour annule vos modifications à ce dossier. Aucun appel, lecture du carnet ou recherche d’autres fichiers n’est déclenché.", 14);
    }

    private void edit(int index, MissionDocument previous, String initialText) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        EditText title = Ui.field(form, "Titre de la pièce", false, 60);
        if (previous != null) title.setText(previous.title);
        EditText text = Ui.field(form, "Informations utiles à cette mission", true, 6000);
        text.setMaxLines(8);
        text.setText(initialText);
        CheckBox share = new CheckBox(this);
        share.setText("L’IA peut communiquer ces informations au correspondant si nécessaire");
        share.setTextColor(Ui.INK);
        share.setChecked(previous != null && previous.shareable);
        form.addView(share);
        TextView feedback = Ui.text(form, "Sans cette autorisation, la pièce reste privée pour l’IA. Son texte sera néanmoins traité par OpenAI si vous confirmez l’appel.", 14);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(previous == null ? "Ajouter une pièce" : "Modifier la pièce")
                .setView(scroll).setNegativeButton("Annuler", null).setPositiveButton("Valider la pièce", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                MissionDocument document = new MissionDocument(title.getText().toString(), text.getText().toString(), share.isChecked());
                java.util.List<MissionDocument> updated = new java.util.ArrayList<>(dossier.documents);
                if (index < 0) updated.add(document); else updated.set(index, document);
                dossier = new MissionDossier(updated);
                if (index < 0) imported = null;
                notice = "Pièce validée dans le dossier temporaire. Aucun envoi à OpenAI à cette étape.";
                dialog.dismiss();
                showPrivateContent();
            } catch (IllegalArgumentException error) { feedback.setText(error.getMessage()); }
        }));
        dialog.show();
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 91 || result != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        importing = true;
        notice = "Lecture de la pièce sélectionnée, sans appel ni envoi à OpenAI…";
        worker.execute(() -> {
            String text = null;
            String message;
            try { text = DossierImporter.read(this, uri); message = "Texte extrait : relisez-le avant de l’ajouter au dossier."; }
            catch (Exception error) { message = error instanceof IllegalArgumentException ? error.getMessage() : "Document illisible, protégé ou encodage non pris en charge. Rien n’a été ajouté."; }
            String extracted = text;
            String outcome = message;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                importing = false;
                imported = extracted;
                notice = outcome;
                needsRefresh = true;
                if (displayAllowed && hasWindowFocus() && !getSystemService(android.app.KeyguardManager.class).isDeviceLocked()) showPrivateContent();
            });
        });
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && displayAllowed && needsRefresh && !isFinishing() && !isDestroyed()
                && !getSystemService(android.app.KeyguardManager.class).isDeviceLocked()) showPrivateContent();
    }

    @Override
    protected void onStop() { displayAllowed = false; super.onStop(); }

    @Override
    protected void onDestroy() { worker.shutdownNow(); imported = null; dossier = MissionDossier.empty(); super.onDestroy(); }
}
