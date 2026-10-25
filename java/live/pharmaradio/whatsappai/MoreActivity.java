package live.pharmaradio.whatsappai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

public class MoreActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, "Réglages", "Personnalisez l’assistant à votre rythme");

        LinearLayout welcome = Ui.card(page, "Accueil", "Votre prénom apparaît à l’écran, sans lecture vocale.");
        Ui.button(welcome, "Changer le prénom affiché", false, () -> WelcomeProfile.edit(this, () -> {}));

        LinearLayout identity = Ui.card(page, "Votre assistant", "Choisissez comment il se présente et à qui il parle.");
        button(identity, "Profil et ton de l’IA", OwnerProfileActivity.class);
        button(identity, "Langue et préférences par contact", ProfilesActivity.class);
        button(identity, "Modèle vocal et tarifs", ModelsActivity.class);

        LinearLayout missions = Ui.card(page, "Vos missions", "Préparez des consignes réutilisables.");
        button(missions, "Missions favorites et exemples", MissionLibraryActivity.class);
        button(missions, "Partager ou importer une mission", AgentShareActivity.class);

        LinearLayout privacy = Ui.card(page, "Données et confidentialité", "Vos dialogues restent sur ce téléphone selon la durée choisie.");
        button(privacy, "Confidentialité et consommation", SettingsActivity.class);
        button(privacy, "Clé OpenAI", CredentialsActivity.class);

        LinearLayout device = Ui.card(page, "Ce téléphone", "La configuration locale se fait une seule fois.");
        button(device, "Étapes d’installation", InstallationGuideActivity.class);
        button(device, "Configuration et diagnostic", SetupActivity.class);
        Ui.text(page, "WhatsApp AI " + BuildConfig.VERSION_NAME, 14).setTextColor(Ui.MUTED);
    }

    private void button(LinearLayout parent, String label, Class<?> destination) {
        Ui.button(parent, label, false, () -> startActivity(new Intent(this, destination)));
    }
}
