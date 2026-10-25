package live.pharmaradio.whatsappai;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import org.json.JSONObject;
import org.json.JSONArray;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class HistoryActivity extends PrivateActivity {
    private String selected;

    @Override
    protected void showPrivateContent() { render(); }

    private void render() {
        LinearLayout content = Ui.page(this, "Dialogues", "Vérifiez ce que l’IA et le correspondant se sont dit");
        if (selected == null) Ui.button(content, "Voir les messages confirmés", false, () -> startActivity(new Intent(this, MessagesActivity.class)));
        if (selected != null) Ui.button(content, "Toutes les fiches", false, () -> { selected = null; render(); });
        try {
            List<JSONObject> entries = new CallStore(this).all();
            if (selected == null) {
                android.widget.TextView section = Ui.text(content, "Activité", 27);
                section.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                Ui.text(content, entries.size() + (entries.size() > 1 ? " appels avec IA" : " appel avec IA"), 17).setTextColor(Ui.MUTED);
            }
            if (entries.isEmpty()) Ui.text(content, "Aucun dialogue pour le moment. Après votre premier appel avec l’IA, sa fiche apparaîtra ici.", 17);
            for (JSONObject entry : entries) {
                if (selected != null && !selected.equals(entry.getString("id"))) continue;
                LinearLayout card = Ui.card(content, selected == null ? "" : "Compte rendu", selected == null ? date(entry.getLong("created")) : "");
                int surface = selected != null ? Color.rgb(241, 249, 249)
                        : "FINISHED".equals(entry.optString("state")) ? Ui.LAVENDER
                        : "INTERRUPTED".equals(entry.optString("state")) ? Color.rgb(255, 206, 188) : Ui.YELLOW;
                card.setBackground(Ui.rounded(surface, 28, this));
                if (selected == null) {
                    ContactPortrait.add(card, entry.optString("number"), entry.optString("name"));
                    Ui.text(card, stateLabel(entry.optString("state")), 18).setTextColor(entry.optString("state").equals("INTERRUPTED") ? Ui.DANGER : Ui.PRIMARY);
                }
                if (selected == null) Ui.button(card, "Ouvrir la fiche", false, () -> { selected = entry.optString("id"); render(); });
                else {
                    ContactPortrait.add(card, entry.optString("number"), entry.optString("name"));
                    String currentName = ContactPortrait.lookup(this, entry.optString("number")).name;
                    showReport(card, entry.optString("id"), currentName.isEmpty() ? label(entry) : currentName);
                    actions(content, entry);
                }
            }
            if (selected == null) Ui.button(content, "Effacer les fiches…", false, () -> new AlertDialog.Builder(this).setTitle("Effacer les fiches ?")
                    .setMessage("Les messages confirmés ont leur propre boîte et ne seront pas supprimés.")
                    .setNegativeButton("Annuler", null).setPositiveButton("Effacer", (dialog, which) -> {
                        try { new CallStore(this).clear(); selected = null; render(); }
                        catch (RuntimeException error) { Ui.text(content, "Effacement impossible.", 16); }
                    }).show());
        } catch (Exception error) { Ui.text(content, "Les fiches ne peuvent pas être lues. Aucun historique n’a été remplacé par une liste vide.", 17); }
    }

    private void actions(LinearLayout content, JSONObject entry) throws Exception {
        LinearLayout card = Ui.card(content, "Actions", "");
        if (!entry.optString("number").isEmpty()) Ui.button(card, "Préparer un nouvel appel", false, () -> {
            Intent prepare = new Intent(this, OutgoingCallActivity.class).putExtra("number", entry.optString("number"));
            startActivity(prepare);
        });
        Ui.button(card, "Préparer un rappel dans mon agenda", false, () -> {
            Intent reminder = new Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.Events.TITLE, "Rappeler " + label(entry));
            try { startActivity(reminder); }
            catch (RuntimeException error) { new AlertDialog.Builder(this).setMessage("Aucun agenda compatible disponible. Aucun rappel créé.").setPositiveButton("Compris", null).show(); }
        });
        Ui.button(card, "Supprimer cette fiche…", false, () -> new AlertDialog.Builder(this).setTitle("Supprimer la fiche ?")
                .setMessage("Ses messages restent consultables jusqu’à expiration ou suppression dans Messages par contact.")
                .setNegativeButton("Annuler", null).setPositiveButton("Supprimer", (dialog, which) -> {
                    try { new CallStore(this).remove(entry.optString("id")); selected = null; render(); }
                    catch (Exception error) { Ui.text(card, "Suppression impossible.", 16); }
                }).show());
    }
    private static final class ReportItem {
        final long at;
        final int order;
        final String speaker;
        final String text;
        final boolean message;
        ReportItem(long at, int order, String speaker, String text, boolean message) {
            this.at = at; this.order = order; this.speaker = speaker; this.text = text; this.message = message;
        }
    }

    private void showReport(LinearLayout card, String callId, String contactName) {
        try {
            List<ReportItem> items = new ArrayList<>();
            JSONObject journal = TranscriptStore.read(this, callId);
            if (journal != null) {
                JSONArray turns = journal.getJSONArray("turns");
                for (int i = 0; i < turns.length(); i++) {
                    JSONObject turn = turns.getJSONObject(i);
                    boolean ai = "assistant".equals(turn.optString("role"));
                    String body = turn.optString("text").trim();
                    if (body.isEmpty()) body = ai ? "[Réponse non transcrite]" : "[Paroles non transcrites]";
                    if (ai && (turn.optBoolean("interrupted") || "partial".equals(turn.optString("status"))
                            || turn.optLong("generated_bytes") > turn.optLong("submitted_bytes")))
                        body += "\n[Réponse interrompue ou partiellement diffusée]";
                    else if (ai && turn.optLong("submitted_bytes") == 0)
                        body += "\n[Diffusion non confirmée]";
                    items.add(new ReportItem(turn.optLong("at"), i, ai ? "Assistant IA" : contactName, body, false));
                }
            }
            int order = items.size();
            for (MessageHistory.Entry message : new MessageStore(this).all())
                if (message.id.startsWith(callId + ":"))
                    items.add(new ReportItem(message.created, order++, "Message confirmé", message.message, true));
            items.sort((a, b) -> { int time = Long.compare(a.at, b.at); return time == 0 ? Integer.compare(a.order, b.order) : time; });
            if (items.isEmpty()) {
                Ui.text(card, "Aucune question, réponse ou message transcrit pour cet appel.", 16);
                return;
            }
            for (ReportItem item : items) {
                LinearLayout bubble = new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
                bubble.setBackground(Ui.rounded(item.message ? Ui.YELLOW : "Assistant IA".equals(item.speaker) ? Ui.SOFT : Ui.BACKGROUND, 18, this));
                LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2);
                spacing.topMargin = Ui.dp(this, 10);
                card.addView(bubble, spacing);
                Ui.text(bubble, item.speaker, 14).setTextColor(Ui.PRIMARY);
                Ui.text(bubble, item.text, 17);
            }
        } catch (Exception error) { Ui.text(card, "Compte rendu indisponible. Les données existantes sont conservées.", 15); }
    }

    private String label(JSONObject entry) { return entry.optString("name").isEmpty() ? entry.optString("number", "").isEmpty() ? "Numéro masqué" : entry.optString("number") : entry.optString("name"); }
    private String date(long created) { return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(created)); }
    private String stateLabel(String state) {
        switch (state) {
            case "FINISHED": return "Appel terminé";
            case "HANDOFF": return "Conversation rendue à l’utilisateur";
            case "INTERRUPTED": return "IA interrompue — consulter le motif";
            default: return "Suivi en cours ou fin non confirmée";
        }
    }
}
