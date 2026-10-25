package live.pharmaradio.whatsappai;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class MessageStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;

    MessageStore(Context context) {
        preferences = context.getSharedPreferences("confirmed-messages", Context.MODE_PRIVATE);
    }

    List<MessageHistory.Entry> all() throws JSONException {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            List<MessageHistory.Entry> entries = load(now).all(now);
            save(entries);
            return entries;
        }
    }

    void add(MessageHistory.Entry entry) throws JSONException {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            MessageHistory history = load(now);
            history.add(entry, now);
            save(history.all(now));
        }
    }

    void remove(String id) throws JSONException {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            MessageHistory history = load(now);
            history.remove(id);
            save(history.all(now));
        }
    }

    void clear() {
        synchronized (LOCK) {
            if (!preferences.edit().remove("entries").commit()) throw new IllegalStateException("Effacement impossible");
        }
    }

    private MessageHistory load(long now) throws JSONException {
        JSONArray saved = new JSONArray(preferences.getString("entries", "[]"));
        List<MessageHistory.Entry> entries = new ArrayList<>();
        for (int index = 0; index < saved.length(); index++) {
            JSONObject item = saved.getJSONObject(index);
            entries.add(new MessageHistory.Entry(item.getString("id"), item.getString("contact"), item.getString("name"),
                    item.getString("number"), item.getString("message"), item.getString("direction"), item.getLong("created"),
                    item.has("result") ? ResultPayload.decode(item.getJSONObject("result")) : null));
        }
        return new MessageHistory(entries, now);
    }

    private void save(List<MessageHistory.Entry> entries) throws JSONException {
        JSONArray saved = new JSONArray();
        for (MessageHistory.Entry entry : entries) {
            JSONObject item = new JSONObject().put("id", entry.id).put("contact", entry.contactKey).put("name", entry.name)
                    .put("number", entry.number).put("message", entry.message).put("direction", entry.direction).put("created", entry.created);
            if (entry.result != null) item.put("result", ResultPayload.encode(entry.result));
            saved.put(item);
        }
        String encoded = saved.toString();
        if (encoded.equals(preferences.getString("entries", "[]"))) return;
        if (!preferences.edit().putString("entries", encoded).commit()) throw new IllegalStateException("Enregistrement impossible");
    }
}
