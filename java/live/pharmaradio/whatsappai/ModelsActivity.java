package live.pharmaradio.whatsappai;

import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ModelsActivity extends PrivateActivity {
    @Override
    protected void showPrivateContent() {
        RealtimeModel selected = ModelSettings.read(this);
        LinearLayout content = Ui.page(this, "Modèle et tarifs OpenAI", "Choisir le modèle des prochaines sessions IA");
        Ui.card(content, "Modèle sélectionné", selected.id + " · " + selected.label);
        Ui.text(content, "OpenAI facture les tokens, pas un forfait par minute d’appel. Ces équivalents concernent l’audio neuf seul : le texte et l’historique réutilisé s’ajoutent. Ce n’est ni un devis ni un plafond.", 15);
        for (RealtimeModel model : RealtimeModel.values()) {
            LinearLayout card = Ui.card(content, model.label + " · " + model.id, model.description);
            Ui.text(card, model.minutePrices(), 17);
            Ui.button(card, selected == model ? "Sélectionné" : "Choisir " + model.label.toLowerCase(java.util.Locale.FRANCE), selected != model, () -> {
                if (ModelSettings.read(this) == model) return;
                new AlertDialog.Builder(this).setTitle("Choisir " + model.id + " ?")
                        .setMessage("Le choix concerne les prochaines sessions IA, entrantes et sortantes. Une session déjà préparée ou en cours garde son modèle. Aucun appel ni test API n’est déclenché. Si votre projet OpenAI refuse ce modèle, aucun remplacement automatique ne sera effectué.")
                        .setNegativeButton("Annuler", null).setPositiveButton("Confirmer", (dialog, which) -> {
                            ModelSettings.save(this, model);
                            showPrivateContent();
                        }).show();
            });
            Ui.button(card, "Voir les tarifs en tokens · " + model.label, false, () -> new AlertDialog.Builder(this)
                    .setTitle(model.id).setMessage(model.tokenPrices() + "\n\nTarifs relevés le " + RealtimeModel.PRICE_DATE + ", susceptibles d’évoluer. La facturation OpenAI fait foi.")
                    .setPositiveButton("Fermer", null).show());
        }
        Ui.text(content, "Hypothèse de conversion : environ 600 tokens audio par minute reçue et 1 200 par minute produite. L’exemple 30 s / 30 s ne représente pas le coût total d’une minute de conversation. Hors dictée préalable, taxes et opérateur. L’abonnement ChatGPT n’inclut pas cette API.", 15);
        Ui.text(content, "Tarifs vérifiés le " + RealtimeModel.PRICE_DATE + ". Ils ne se mettent pas à jour automatiquement. Le coût des appels est estimé à partir des relevés API et du modèle réellement demandé, pas à partir de leur durée seule.", 15);
        Ui.button(content, "Consulter les tarifs officiels OpenAI", false, () -> SetupLinks.open(this, SetupLinks.OPENAI_PRICING));
        TextView status = Ui.text(content, "Après un changement de modèle, vérifiez son accès avec votre clé. La qualité et l’audio réel se vérifient ensuite avec un correspondant informé.", 15);
        Ui.button(content, "Vérifier le modèle sélectionné sans appel", true, () -> {
            if (Readiness.checking()) { status.setText("Un diagnostic est déjà en cours."); return; }
            RealtimeModel checked = ModelSettings.read(this);
            Readiness.begin();
            status.setText("Vérification de " + checked.id + "… Aucun appel ni microphone.");
            PhoneAgent.diagnose(message -> {
                Readiness.result(message);
                if (!isDestroyed() && !isFinishing()) status.setText(checked.id + "\n" + message);
            });
        });
    }
}
