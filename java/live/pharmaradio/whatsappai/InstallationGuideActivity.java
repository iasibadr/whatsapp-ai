package live.pharmaradio.whatsappai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Plain-language setup guide. It never starts an audio capture or places a call. */
public class InstallationGuideActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { updateStatus(); ui.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Ui.page(this, "Installation", "Quelques étapes pour préparer les appels WhatsApp avec IA");
        LinearLayout readiness = Ui.card(page, "Ce téléphone", "L’état se met à jour automatiquement.");
        readiness.setBackground(Ui.rounded(Ui.LIME, 28, this));
        status = Ui.text(readiness, "Vérification…", 19);
        Ui.button(readiness, "Ouvrir la configuration", false,
                () -> startActivity(new Intent(this, SetupActivity.class)));

        step(page, "1. Installer les deux applications",
                "Installez WhatsApp classique et WhatsApp AI. Ouvrez WhatsApp avec votre compte habituel. WhatsApp Business n’est pas la cible de cette version.");
        step(page, "2. Ajouter votre clé OpenAI",
                "Chaque personne ajoute sa propre clé dans WhatsApp AI. Elle reste chiffrée sur son téléphone et n’est pas incluse dans l’APK.");
        Ui.button(page, "Configurer la clé OpenAI", false,
                () -> startActivity(new Intent(this, CredentialsActivity.class)));
        step(page, "3. Associer le téléphone une fois",
                "Dans Android, activez les options pour les développeurs et le débogage sans fil. Revenez dans Configuration → Configurer cet appareil. Saisissez le code d’association uniquement dans la notification sur le téléphone. L’association déjà enregistrée n’est pas à refaire.");
        step(page, "4. Attendre « Prêt »",
                "Ouvrez WhatsApp AI hors appel et attendez que l’accueil indique « Prêt ». Le relais se réactive localement si nécessaire. Si « À vérifier » reste affiché, ouvrez Configuration ; vérifiez le débogage sans fil et les notifications.");
        step(page, "5. Lancer l’appel avec l’IA",
                "Touchez « Préparer un appel IA » dans WhatsApp AI, choisissez le contact et écrivez la mission. Confirmez après avoir informé le correspondant. WhatsApp peut demander une dernière confirmation de l’appel.");
        LinearLayout warning = Ui.card(page, "Si vous entendez directement le contact",
                "Un appel lancé depuis WhatsApp seul n’active pas l’IA. Si l’IA s’arrête pendant l’appel, votre micro peut être entendu. Revenez dans WhatsApp AI pour lire l’état, puis reprenez ou terminez l’appel dans WhatsApp. L’application ne reprend pas automatiquement un appel déjà engagé.");
        warning.setBackground(Ui.rounded(Ui.YELLOW, 28, this));
        Ui.button(page, "Préparer un appel IA", true,
                () -> startActivity(new Intent(this, OutgoingCallActivity.class)));
        Ui.text(page, "La voix de présentation dans l’appel reste celle de l’assistant IA. Cet écran d’installation ne lit aucun audio.", 14)
                .setTextColor(Ui.MUTED);
    }

    private void step(LinearLayout page, String title, String explanation) {
        Ui.card(page, title, explanation);
    }

    @Override protected void onResume() { super.onResume(); ui.post(refresh); }
    @Override protected void onPause() { ui.removeCallbacks(refresh); super.onPause(); }

    private void updateStatus() {
        if (status == null) return;
        boolean key = KeyVault.configured(this);
        boolean paired = LocalActivationService.paired(this);
        boolean helper = BridgeProvider.getHelper() != null;
        String now = !key ? "Clé OpenAI à configurer"
                : !paired ? "Association locale à terminer"
                : helper ? "Prêt pour préparer un appel IA"
                : LocalActivationService.running() ? "Activation locale en cours…"
                : "Relais à vérifier hors appel";
        String previous = "";
        try {
            JSONObject last = new JSONObject(AiTrial.saved(this));
            if (last.optString("test").startsWith("consented_whatsapp_ai") && "failed".equals(last.optString("state")))
                previous = last.optLong("pcm_started_wall_ms") > 0
                        ? "\nDernier appel IA : le transport s’est interrompu pendant la conversation."
                        : "\nDernier appel IA : préparation interrompue.";
        } catch (Exception ignored) { }
        status.setText(now + previous);
    }
}
