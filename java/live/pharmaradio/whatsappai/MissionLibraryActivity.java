package live.pharmaradio.whatsappai;

import android.app.AlertDialog;
import android.content.Intent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MissionLibraryActivity extends PrivateActivity {
    private TextView status;
    private boolean savedDraft;

    @Override
    protected void showPrivateContent() {
        LinearLayout content = Ui.page(this, "Mes missions favorites", "Réutiliser des consignes, jamais une autorisation d’appel");
        Ui.text(content, "Les favoris restent sur ce téléphone jusqu’à leur suppression. Ils ne contiennent aucun destinataire automatiquement ; évitez les secrets et les informations sensibles. Charger un modèle ne compose jamais un numéro.", 15);
        status = Ui.text(content, "Dix favoris personnels maximum. Relisez les consignes avant chaque appel.", 15);
        String draft = getIntent().getStringExtra("draft");
        if (draft != null && !savedDraft) {
            try {
                MissionTemplate template = MissionLibrary.decode(new org.json.JSONObject(draft));
                LinearLayout save = Ui.card(content, "Enregistrer cette mission", template.objective);
                EditText title = Ui.field(save, "Titre du favori", false, 60);
                Ui.button(save, "Enregistrer le favori", true, () -> {
                    try {
                        MissionTemplate named = new MissionTemplate(title.getText().toString(), template.objective,
                                template.questions, template.limits, template.handoff, template.report);
                        MissionLibrary.save(this, named);
                        savedDraft = true;
                        showPrivateContent();
                        status.setText("Favori enregistré. Aucun appel lancé.");
                    } catch (Exception error) { status.setText(error instanceof IllegalArgumentException ? error.getMessage() : "Enregistrement impossible. Aucun favori existant remplacé."); }
                });
            } catch (Exception error) { status.setText("La mission ne peut pas être enregistrée. Revenez à la préparation."); }
        }
        try {
            java.util.List<MissionLibrary.Entry> entries = MissionLibrary.list(this);
            if (entries.isEmpty()) Ui.card(content, "Aucun favori pour le moment", "Préparez une mission puis choisissez Enregistrer comme favori.");
            for (MissionLibrary.Entry entry : entries) {
                LinearLayout card = Ui.card(content, entry.template.title, entry.template.objective);
                Ui.button(card, "Utiliser ce modèle", true, () -> choose(entry));
                Ui.button(card, "Supprimer ce favori…", false, () -> new AlertDialog.Builder(this).setTitle("Supprimer le favori ?")
                        .setMessage(entry.template.title + "\nCette suppression ne touche ni les messages ni les fiches d’appel.")
                        .setNegativeButton("Annuler", null).setPositiveButton("Supprimer", (dialog, which) -> {
                            try { MissionLibrary.remove(this, entry.id); showPrivateContent(); }
                            catch (Exception error) { status.setText("Suppression impossible."); }
                        }).show());
            }
        } catch (Exception error) { status.setText("Bibliothèque illisible : aucun favori n’a été effacé ni remplacé."); }
    }

    private void choose(MissionLibrary.Entry entry) {
        if (getIntent().getBooleanExtra("picker", false)) {
            setResult(RESULT_OK, new Intent().putExtra("template-id", entry.id));
        } else startActivity(new Intent(this, OutgoingCallActivity.class).putExtra("template-id", entry.id));
        finish();
    }
}
