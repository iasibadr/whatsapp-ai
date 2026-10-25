package live.pharmaradio.whatsappai;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessagesActivity extends PrivateActivity {
    private LinearLayout layout;
    private MessageStore store;
    private String selectedContact;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store = new MessageStore(this);
    }

    @Override
    protected void showPrivateContent() {
        layout = Ui.page(this, "Messages par contact", "Les messages que vos correspondants ont confirmés");
        refresh();
    }

    private void refresh() {
        layout.removeAllViews();
        button("‹ Retour", this::finish);
        text("Vos messages confirmés sont conservés sur ce téléphone pendant 24 heures. Aucun enregistrement audio n’est conservé.", 16);
        button("Actualiser", this::refresh);
        if (selectedContact != null) button("Tous les correspondants", () -> { selectedContact = null; refresh(); });
        try {
            List<MessageHistory.Entry> entries = store.all();
            if (selectedContact == null) showContacts(entries);
            else showMessages(entries);
        } catch (Exception error) {
            text("Historique illisible ou stockage indisponible. Les données n’ont pas été remplacées par une boîte vide.", 17);
        }
        button("Effacer tous les messages…", () -> new AlertDialog.Builder(this).setTitle("Tout effacer ?")
                .setMessage("Supprimer les messages enregistrés sur ce téléphone ? Cette action ne supprime pas les données déjà traitées par OpenAI.")
                .setNegativeButton("Annuler", null).setPositiveButton("Effacer", (dialog, which) -> {
                    try {
                        store.clear();
                        CallBridgeService.lastMessage = "";
                        selectedContact = null;
                        refresh();
                    } catch (Exception error) { text("Effacement impossible.", 17); }
                }).show());
        button("Fermer", this::finish);
    }

    private void showContacts(List<MessageHistory.Entry> entries) {
        Map<String, List<MessageHistory.Entry>> grouped = new LinkedHashMap<>();
        for (MessageHistory.Entry entry : entries) grouped.computeIfAbsent(entry.contactKey, key -> new ArrayList<>()).add(entry);
        if (grouped.isEmpty()) {
            text("Aucun message enregistré. Un message apparaît ici lorsque le correspondant confirme qu’il souhaite le conserver.", 18);
            return;
        }
        for (List<MessageHistory.Entry> group : grouped.values()) {
            MessageHistory.Entry latest = group.get(0);
            button(label(latest) + "\n" + group.size() + " message(s) · " + date(latest.created), () -> {
                selectedContact = latest.contactKey;
                refresh();
            });
        }
    }

    private void showMessages(List<MessageHistory.Entry> entries) {
        boolean found = false;
        for (MessageHistory.Entry entry : entries) {
            if (!selectedContact.equals(entry.contactKey)) continue;
            found = true;
            text(label(entry), 22);
            text(date(entry.created) + " · " + (entry.direction.equals("outgoing") ? "Appel sortant" : "Appel entrant"), 15);
            if (entry.result == null) text(entry.message, 19);
            else ResultView.show(layout, entry.result);
            text("Expire le " + date(entry.created + MessageHistory.RETENTION_MS), 14);
            button("Supprimer ce message…", () -> new AlertDialog.Builder(this).setTitle("Supprimer ce message ?")
                    .setNegativeButton("Annuler", null).setPositiveButton("Supprimer", (dialog, which) -> {
                        try { store.remove(entry.id); refresh(); }
                        catch (Exception error) { text("Suppression impossible.", 17); }
                    }).show());
        }
        if (!found) text("Aucun message conservé pour ce correspondant : les messages ont été effacés ou ont expiré.", 18);
    }

    private String label(MessageHistory.Entry entry) {
        if (!entry.name.isEmpty()) return entry.name + (entry.number.isEmpty() ? "" : "\n" + entry.number);
        return entry.number.isEmpty() ? "Numéro masqué · " + date(entry.created) : entry.number;
    }

    private String date(long timestamp) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(timestamp));
    }

    private void text(String value, int size) {
        Ui.text(layout, value, size);
    }

    private void button(String label, Runnable action) {
        Ui.button(layout, label, false, action);
    }
}
