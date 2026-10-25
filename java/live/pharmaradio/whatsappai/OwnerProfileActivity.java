package live.pharmaradio.whatsappai;

import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OwnerProfileActivity extends PrivateActivity {
    @Override
    protected void showPrivateContent() {
        AgentPersona saved = OwnerProfile.read(this);
        AgentPersona.Style[] selected = {saved.style};
        LinearLayout content = Ui.page(this, "Mon profil propriétaire", "Votre identité, votre façon de communiquer");
        LinearLayout identity = Ui.card(content, "Nom de présentation", "Ce nom sera annoncé aux correspondants et transmis à OpenAI pendant les appels. Laissez vide pour un accueil sans nom.");
        EditText name = Ui.field(identity, "Prénom, nom ou nom professionnel", false, 80);
        name.setText(saved.owner);
        name.setSaveEnabled(false);
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout tone = Ui.card(content, "Style par défaut", "Appels entrants et sortants. Les préférences par contact peuvent adapter le ton, jamais masquer l’identité IA.");
        TextView choice = Ui.text(tone, saved.style.label + " · " + saved.style.policy, 16);
        TextView preview = Ui.text(tone, "Aperçu écrit de l’accueil entrant :\n" + saved.greeting(), 17);
        Runnable refresh = () -> {
            choice.setText(selected[0].label + " · " + selected[0].policy);
            try { preview.setText("Aperçu écrit de l’accueil entrant :\n" + new AgentPersona(name.getText().toString(), selected[0]).greeting()); }
            catch (IllegalArgumentException error) { preview.setText(error.getMessage()); }
        };
        Ui.button(tone, "Choisir un style", false, () -> {
            AgentPersona.Style[] styles = AgentPersona.Style.values();
            String[] labels = new String[styles.length];
            for (int index = 0; index < styles.length; index++) labels[index] = styles[index].label;
            new AlertDialog.Builder(this).setTitle("Personnalité de l’assistant")
                    .setItems(labels, (dialog, which) -> { selected[0] = styles[which]; refresh.run(); }).show();
        });
        name.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) { refresh.run(); }
            public void afterTextChanged(android.text.Editable value) {}
        });
        TextView status = Ui.text(content, "Les modifications s’appliquent aux prochaines sessions IA après enregistrement. Le style ne change ni la voix, ni le modèle, ni les règles de sécurité.", 15);
        Ui.button(content, "Enregistrer mon profil", true, () -> {
            try {
                OwnerProfile.save(this, name.getText().toString(), selected[0]);
                status.setText("Profil enregistré pour les prochaines sessions IA.");
            } catch (IllegalArgumentException error) { status.setText(error.getMessage()); }
        });
        Ui.text(content, "Le propriétaire est indépendant du concepteur : " + getString(R.string.app_designer) + ".", 14);
    }
}
