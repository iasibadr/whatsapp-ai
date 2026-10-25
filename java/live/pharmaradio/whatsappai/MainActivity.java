package live.pharmaradio.whatsappai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView greeting;
    private TextView readiness;
    private TextView primary;
    private LinearLayout activeCall;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            showStatus();
            ui.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.root(this);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 24), Ui.dp(this, 19), Ui.dp(this, 24), Ui.dp(this, 8));
        root.addView(top);
        TextView brand = new TextView(this);
        brand.setText("WhatsApp AI");
        brand.setTextSize(22);
        brand.setTextColor(Ui.INK);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        top.addView(brand, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView settings = new TextView(this);
        settings.setText("···");
        settings.setTextSize(26);
        settings.setGravity(Gravity.CENTER);
        settings.setTextColor(Ui.INK);
        settings.setBackground(Ui.rounded(Color.WHITE, 100, this));
        settings.setContentDescription("Ouvrir les réglages");
        settings.setOnClickListener(view -> open(MoreActivity.class));
        top.addView(settings, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 24), Ui.dp(this, 30), Ui.dp(this, 24), Ui.dp(this, 28));
        scroll.addView(page);

        greeting = Ui.text(page, "Bonjour.", 48);
        greeting.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        greeting.setLineSpacing(0, 0.93f);
        TextView invitation = Ui.text(page, "L’IA peut appeler un contact sur WhatsApp et accomplir votre mission.", 19);
        invitation.setTextColor(Ui.MUTED);
        LinearLayout.LayoutParams invitationSpace = (LinearLayout.LayoutParams) invitation.getLayoutParams();
        invitationSpace.topMargin = Ui.dp(this, 8);
        invitation.setLayoutParams(invitationSpace);

        LinearLayout facts = new LinearLayout(this);
        facts.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams factsSpace = new LinearLayout.LayoutParams(-1, -2);
        factsSpace.topMargin = Ui.dp(this, 27);
        page.addView(facts, factsSpace);
        fact(facts, "⌕", "Contact", "À choisir");
        fact(facts, "✎", "Mission", "À décrire");
        readiness = fact(facts, "✦", "Assistant", "Connexion…");

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsSpace = new LinearLayout.LayoutParams(-1, Ui.dp(this, 236));
        actionsSpace.topMargin = Ui.dp(this, 34);
        page.addView(actions, actionsSpace);
        primary = tile(actions, 1.55f, Ui.rounded(Ui.LIME, 28, this), "↗", "Préparer\nun appel IA", () -> {
            if (BridgeProvider.getHelper() == null) open(SetupActivity.class);
            else open(OutgoingCallActivity.class);
        }, "Préparer un appel WhatsApp avec IA");
        tile(actions, 1f, Ui.rounded(Ui.YELLOW, 28, this), "▤", "Dialogues", () -> open(HistoryActivity.class),
                "Lire les dialogues et résultats");
        tile(actions, 1f, Ui.stripes(this), "?", "Installation", () -> open(InstallationGuideActivity.class),
                "Voir les étapes d’installation et l’état de ce téléphone");

        activeCall = new LinearLayout(this);
        activeCall.setOrientation(LinearLayout.VERTICAL);
        page.addView(activeCall);
        Ui.button(activeCall, "Reprendre les commandes de l’appel", false, () -> open(CallMenuActivity.class));
        TextView assurance = Ui.text(page, "Vous choisissez le contact et confirmez avant l’appel.", 15);
        assurance.setTextColor(Ui.MUTED);
        LinearLayout.LayoutParams assuranceSpace = (LinearLayout.LayoutParams) assurance.getLayoutParams();
        assuranceSpace.topMargin = Ui.dp(this, 22);
        assurance.setLayoutParams(assuranceSpace);

        Ui.navigation(root, "Accueil", tab -> {
            if ("Appeler".equals(tab)) open(OutgoingCallActivity.class);
            else if ("Dialogues".equals(tab)) open(HistoryActivity.class);
            else if ("Réglages".equals(tab)) open(MoreActivity.class);
        });
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 0)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 71);
    }

    @Override protected void onResume() {
        super.onResume();
        updateGreeting();
        activateLocalRelay();
        ui.post(refresh);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(refresh);
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 71) activateLocalRelay();
    }

    private void activateLocalRelay() {
        if (!KeyVault.configured(this) || !LocalActivationService.paired(this)) return;
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 0) return;
        if (getSystemService(AudioManager.class).getMode() != AudioManager.MODE_NORMAL) return;
        if (BridgeProvider.getHelper() != null || LocalActivationService.running()) return;
        LocalActivationService.start(this, false);
    }

    private void updateGreeting() {
        String name = WelcomeProfile.firstName(this);
        String message = name.isEmpty() ? "Bonjour." : "Bonjour,\n" + name + ".";
        SpannableString styled = new SpannableString(message);
        if (!name.isEmpty()) styled.setSpan(new ForegroundColorSpan(Color.rgb(111, 129, 134)), 9, message.length(), 0);
        greeting.setText(styled);
    }

    private void showStatus() {
        if (readiness == null) return;
        activeCall.setVisibility(CallSessionService.running ? View.VISIBLE : View.GONE);
        if (!KeyVault.configured(this) || !LocalActivationService.paired(this)) {
            readiness.setText("À configurer");
            primary.setText("Configurer\nl’assistant");
        } else if (BridgeProvider.getHelper() != null) {
            readiness.setText("Prêt");
            primary.setText("Préparer\nun appel IA");
        } else if (LocalActivationService.running()) {
            readiness.setText("Connexion…");
            primary.setText("Vérifier\nle téléphone");
        } else {
            readiness.setText("À vérifier");
            primary.setText("Vérifier\nle téléphone");
        }
    }

    private void open(Class<?> target) { startActivity(new Intent(this, target)); }

    private TextView fact(LinearLayout parent, String symbol, String label, String initialValue) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        parent.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextSize(19);
        icon.setTextColor(Ui.INK);
        icon.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable circle = Ui.rounded(Color.TRANSPARENT, 100, this);
        circle.setStroke(Ui.dp(this, 1), 0x668CABB0);
        icon.setBackground(circle);
        column.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        TextView caption = Ui.text(column, label, 13);
        caption.setTextColor(Ui.MUTED);
        TextView value = Ui.text(column, initialValue, 15);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return value;
    }

    private TextView tile(LinearLayout parent, float weight, android.graphics.drawable.Drawable background,
                          String symbol, String label, Runnable action, String description) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.TOP);
        panel.setPadding(Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 15));
        panel.setBackground(background);
        panel.setContentDescription(description);
        panel.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams placement = new LinearLayout.LayoutParams(0, -1, weight);
        placement.rightMargin = Ui.dp(this, 5);
        parent.addView(panel, placement);
        TextView icon = new TextView(this);
        icon.setText(symbol);
        icon.setTextSize(19);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(Ui.INK);
        android.graphics.drawable.GradientDrawable circle = Ui.rounded(0x33FFFFFF, 100, this);
        circle.setStroke(Ui.dp(this, 1), 0x55738786);
        icon.setBackground(circle);
        panel.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        panel.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        TextView title = Ui.text(panel, label, 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setMinHeight(Ui.dp(this, 56));
        return title;
    }
}
