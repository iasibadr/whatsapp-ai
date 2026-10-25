package live.pharmaradio.whatsappai;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class CallStore {
    private static final Object LOCK = new Object();
    private final Context context;
    private final SharedPreferences preferences;
    CallStore(Context context) { this.context=context.getApplicationContext();preferences = context.getSharedPreferences("call-history", Context.MODE_PRIVATE); }

    List<JSONObject> all() throws Exception {
        synchronized (LOCK) {
            TranscriptStore.prune(context);
            JSONArray saved = new JSONArray(preferences.getString("entries", "[]"));
            List<JSONObject> entries = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int index = 0; index < saved.length(); index++) {
                JSONObject entry = saved.getJSONObject(index);
                if (entry.getLong("expires") > now) entries.add(entry);
            }
            entries.sort((first, second) -> Long.compare(second.optLong("created"), first.optLong("created")));
            save(entries);
            return entries;
        }
    }

    void put(JSONObject entry) throws Exception {
        synchronized (LOCK) {
            List<JSONObject> entries = all();
            entries.removeIf(previous -> previous.optString("id").equals(entry.optString("id")));
            entries.add(0, entry);
            entries.sort((first, second) -> Long.compare(second.optLong("created"), first.optLong("created")));
            if (entries.size() > 200) entries = new ArrayList<>(entries.subList(0, 200));
            save(entries);
        }
    }

    void remove(String id) throws Exception {
        synchronized (LOCK) {
            List<JSONObject> entries = all();
            entries.removeIf(entry -> entry.optString("id").equals(id));
            save(entries);
            TranscriptStore.remove(context,id);
        }
    }

    void clear() {
        synchronized (LOCK) {
            if (!preferences.edit().remove("entries").commit()) throw new IllegalStateException("Effacement impossible");
            TranscriptStore.clear(context);
        }
    }

    private void save(List<JSONObject> entries) {
        JSONArray saved = new JSONArray();
        for (JSONObject entry : entries) saved.put(entry);
        String encoded = saved.toString();
        if (!encoded.equals(preferences.getString("entries", "[]")) && !preferences.edit().putString("entries", encoded).commit()) {
            throw new IllegalStateException("Stockage des fiches indisponible");
        }
    }
}
