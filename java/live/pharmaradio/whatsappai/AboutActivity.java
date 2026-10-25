package live.pharmaradio.whatsappai;

public class AboutActivity extends android.app.Activity {
    @Override
    public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.LinearLayout content = Ui.page(this, "À propos", getString(R.string.app_name));
        android.widget.LinearLayout card = Ui.card(content, getString(R.string.app_name), "Votre assistant d’appels");
        Ui.text(card, getString(R.string.designer_credit, getString(R.string.app_designer)), 21);
        Ui.text(card, getString(R.string.app_version, BuildConfig.VERSION_NAME), 18);
        Ui.button(content, "Licences des composants", false, () -> {
            try (java.io.InputStream stream = getAssets().open("local_activation_licenses.txt");
                    java.io.InputStream additional = getAssets().open("local_activation_additional_licenses.txt")) {
                android.widget.ScrollView scroll = new android.widget.ScrollView(this);
                android.widget.TextView text = new android.widget.TextView(this);
                text.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
                text.setText(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        + "\n\n" + new String(additional.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                scroll.addView(text);
                new android.app.AlertDialog.Builder(this).setTitle("Licences des composants").setView(scroll).setPositiveButton("Fermer", null).show();
            } catch (java.io.IOException error) {
                android.widget.Toast.makeText(this, "Licences indisponibles.", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }
}
