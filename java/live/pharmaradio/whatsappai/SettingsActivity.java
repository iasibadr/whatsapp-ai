package live.pharmaradio.whatsappai;

import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.Switch;

public class SettingsActivity extends PrivateActivity {
    @Override
    protected void showPrivateContent() { render(); }

    private void render() {
        LinearLayout content = Ui.page(this, "Confidentialité et coût", "Vos choix pour les prochains appels");
        LinearLayout privacy = Ui.card(content, "Confidentialité", "Les messages confirmés restent consultables 24 h, selon l’annonce faite au correspondant.");
        Switch lock = new Switch(this);
        lock.setText("Protéger les messages et l’historique par biométrie ou code du téléphone");
        lock.setTextColor(Ui.INK);
        lock.setTextSize(16);
        lock.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        lock.setChecked(AppSettings.locked(this));
        privacy.addView(lock);
        lock.setOnCheckedChangeListener((button, enabled) -> {
            if (enabled && !getSystemService(android.app.KeyguardManager.class).isDeviceSecure()) {
                lock.setChecked(false);
                new AlertDialog.Builder(this).setMessage("Configurez d’abord un code ou un verrouillage sécurisé dans Android.").setPositiveButton("Compris", null).show();
            } else AppSettings.prefs(this).edit().putBoolean("private-lock", enabled).apply();
        });
        Ui.button(privacy, "Conservation des fiches : " + AppSettings.historyDays(this) + " jour(s)", false, () -> {
            String[] labels = {"24 heures", "7 jours", "30 jours"};
            int[] days = {1, 7, 30};
            new AlertDialog.Builder(this).setTitle("Nouvelles fiches d’appel")
                    .setItems(labels, (dialog, which) -> { AppSettings.prefs(this).edit().putInt("history-days", days[which]).apply(); render(); }).show();
        });
        Ui.text(privacy, "Ce réglage concerne les nouvelles fiches, leurs dialogues transcrits et vos missions, pas une prolongation des messages déjà consentis. Aucun audio n’est enregistré par l’APK.", 14);
        LinearLayout spending = Ui.card(content, "Consommation API", "Une alerte ne coupe jamais la conversation.");
        Ui.button(spending, "Modèle : " + ModelSettings.read(this).id + " · tarifs", true,
                () -> startActivity(new android.content.Intent(this, ModelsActivity.class)));
        Ui.button(spending, "Seuil par appel : " + (AppSettings.costAlert(this) == 0 ? "désactivé" : AppSettings.costAlert(this) + " USD estimés"), false, () -> {
            String[] labels = {"Pas d’alerte", "0,10 USD", "0,50 USD", "1,00 USD"};
            String[] values = {"0", "0.1", "0.5", "1"};
            new AlertDialog.Builder(this).setTitle("Prévenir à partir de…").setItems(labels, (dialog, which) -> {
                AppSettings.prefs(this).edit().putString("cost-alert", values[which]).apply(); render();
            }).show();
        });
        Ui.text(spending, "Le montant reste une estimation Realtime, hors dictée, taxes et opérateur. Le modèle actuel n’est pas remplacé automatiquement par un modèle économique.", 14);
        Ui.button(content, "Clé OpenAI chiffrée", false, () -> startActivity(new android.content.Intent(this, CredentialsActivity.class)));
        Ui.button(content, "Langue et préférences par contact", false, () -> startActivity(new android.content.Intent(this, ProfilesActivity.class)));
    }
}
