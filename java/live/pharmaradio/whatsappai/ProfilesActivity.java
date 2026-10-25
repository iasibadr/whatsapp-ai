package live.pharmaradio.whatsappai;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ProfilesActivity extends PrivateActivity {
    private EditText number;
    private EditText preferences;
    private TextView status;
    private String selectedNumber = "";

    @Override
    protected void showPrivateContent() {
        LinearLayout content = Ui.page(this, "Profils et langue", "Préférences locales, pas d’accès global à votre carnet");
        LinearLayout general = Ui.card(content, "Langue par défaut", AppSettings.language(this));
        Ui.text(general, "L’IA suit la langue du correspondant dès les premiers échanges, notamment la darija marocaine et le mélange darija–français. Ce choix sert à commencer ; en mode automatique, l’accueil débute en français si la personne n’a pas encore parlé. En cas de doute, l’IA demande une précision.", 14);
        Ui.button(general, "Choisir une langue", false, () -> new AlertDialog.Builder(this).setTitle("Langue souhaitée")
                .setItems(new String[]{"Automatique", "Français", "Darija marocaine", "Anglais"}, (dialog, which) -> {
                    AppSettings.prefs(this).edit().putString("language", new String[]{"Automatique", "Français", "Darija marocaine", "Anglais"}[which]).apply();
                    showPrivateContent();
                }).show());
        LinearLayout contact = Ui.card(content, "Un correspondant, ses préférences", "Langue, ton, formule d’accueil. Ne saisissez pas de secret ou de code.");
        number = Ui.field(contact, "Numéro complet", false, 35);
        number.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        Ui.button(contact, "Choisir un contact", false, () -> {
            try { startActivityForResult(new Intent(Intent.ACTION_PICK).setType(Phone.CONTENT_TYPE), 81); }
            catch (RuntimeException error) { status.setText("Sélecteur indisponible : saisissez le numéro."); }
        });
        preferences = Ui.field(contact, "Exemple : ton chaleureux, phrases courtes, français.", true, 500);
        Ui.button(contact, "Charger les préférences de ce numéro", false, this::loadProfile);
        Ui.button(contact, "Utiliser un modèle de profil", false, () -> new AlertDialog.Builder(this).setTitle("Point de départ")
                .setItems(new String[]{"Famille", "Professionnel", "Numéro inconnu"}, (dialog, which) -> preferences.setText(new String[]{
                    "Ton chaleureux et simple. Annonce clairement être l’assistant IA du propriétaire. Ne partage aucune information privée.",
                    "Ton professionnel et concis. Clarifie le motif, demande les informations utiles et ne prends aucun engagement.",
                    "Ton neutre. Demande le nom et le motif, sans confirmer d’information privée ni de disponibilité."
                }[which])).show());
        Ui.button(contact, "Enregistrer pour ce numéro", true, () -> {
            String destination = destination();
            if (destination == null) return;
            AppSettings.prefs(this).edit().putString(AppSettings.profileKey(destination), preferences.getText().toString().trim()).apply();
            status.setText("Préférences enregistrées. Elles seront appliquées aux prochains appels IA avec ce numéro exact.");
        });
        Ui.button(contact, "Effacer ce profil", false, () -> {
            String destination = destination();
            if (destination == null) return;
            AppSettings.prefs(this).edit().remove(AppSettings.profileKey(destination)).apply();
            preferences.setText(""); status.setText("Profil effacé.");
        });
        status = Ui.text(content, "Les règles de sécurité, la présentation IA et le consentement restent obligatoires pour tous les profils.", 15);
        if (!selectedNumber.isEmpty()) { number.setText(selectedNumber); loadProfile(); }
    }

    private String destination() {
        String value = android.telephony.PhoneNumberUtils.normalizeNumber(number.getText().toString());
        if (!value.matches("\\+?[0-9]{6,15}")) { status.setText("Indiquez un numéro complet, sans code spécial."); return null; }
        return value;
    }

    private void loadProfile() {
        String value = destination();
        if (value == null) return;
        preferences.setText(AppSettings.prefs(this).getString(AppSettings.profileKey(value), ""));
        status.setText("Profil chargé pour ce numéro. Les formats local et international peuvent désigner des clés différentes.");
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 81 || result != RESULT_OK || data == null || data.getData() == null) return;
        try (Cursor cursor = getContentResolver().query(data.getData(), new String[]{Phone.NUMBER}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) { selectedNumber = cursor.getString(0); number.setText(selectedNumber); loadProfile(); }
        } catch (RuntimeException error) { status.setText("Lecture du numéro impossible."); }
    }
}
