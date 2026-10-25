package live.pharmaradio.whatsappai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;

final class WelcomeProfile {
    static String firstName(Context context) {
        String saved = AppSettings.prefs(context).getString("welcome-name", "").trim();
        if (saved.isEmpty()) saved = OwnerProfile.read(context).owner.trim();
        return saved.isEmpty() ? "" : saved.split("\\s+")[0];
    }

    static boolean needsIntroduction(Context context) {
        return firstName(context).isEmpty() && !AppSettings.prefs(context).getBoolean("welcome-name-asked", false);
    }

    static void edit(Activity activity, Runnable updated) {
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setTextSize(18);
        name.setHint("Votre prénom");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        name.setText(AppSettings.prefs(activity).getString("welcome-name", ""));
        int padding = Ui.dp(activity, 20);
        name.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(activity)
                .setTitle("Comment dois-je vous appeler ?")
                .setMessage("Ce prénom personnalise seulement l’accueil sur ce téléphone. Vous pourrez le changer dans Réglages.")
                .setView(name)
                .setNegativeButton("Plus tard", (dialog, which) -> {
                    AppSettings.prefs(activity).edit().putBoolean("welcome-name-asked", true).apply();
                    updated.run();
                })
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String value = name.getText().toString().trim().replaceAll("\\s+", " ");
                    AppSettings.prefs(activity).edit().putString("welcome-name", value)
                            .putBoolean("welcome-name-asked", true).apply();
                    updated.run();
                })
                .setOnCancelListener(dialog -> {
                    AppSettings.prefs(activity).edit().putBoolean("welcome-name-asked", true).apply();
                    updated.run();
                })
                .show();
    }
}
