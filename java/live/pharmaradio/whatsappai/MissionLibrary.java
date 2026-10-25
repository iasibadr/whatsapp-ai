package live.pharmaradio.whatsappai;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class MissionLibrary {
    static final class Entry {
        final String id;
        final MissionTemplate template;
        Entry(String id, MissionTemplate template) { this.id = id; this.template = template; }
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("mission-library", Context.MODE_PRIVATE);
    }

    static JSONObject encode(MissionTemplate template) throws org.json.JSONException {
        return new JSONObject().put("title", template.title).put("objective", template.objective)
                .put("questions", template.questions).put("limits", template.limits)
                .put("handoff", template.handoff).put("report", template.report);
    }

    static MissionTemplate decode(JSONObject value) throws org.json.JSONException {
        for (String key : new String[]{"title", "objective", "questions", "limits", "handoff"}) {
            if (!(value.opt(key) instanceof String)) throw new IllegalArgumentException("Modèle invalide.");
        }
        if (!(value.opt("report") instanceof Boolean)) throw new IllegalArgumentException("Modèle invalide.");
        return new MissionTemplate(value.getString("title"), value.getString("objective"), value.getString("questions"),
                value.getString("limits"), value.getString("handoff"), value.getBoolean("report"));
    }

    static synchronized List<Entry> list(Context context) throws org.json.JSONException {
        JSONArray records = new JSONArray(prefs(context).getString("entries", "[]"));
        if (records.length() > 10) throw new IllegalArgumentException("Bibliothèque invalide.");
        List<Entry> result = new ArrayList<>();
        for (int index = 0; index < records.length(); index++) {
            JSONObject record = records.getJSONObject(index);
            String id = record.getString("id");
            if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("Modèle invalide.");
            result.add(new Entry(id, decode(record)));
        }
        return result;
    }

    static synchronized String save(Context context, MissionTemplate template) throws org.json.JSONException {
        List<Entry> entries = list(context);
        if (entries.size() >= 10) throw new IllegalArgumentException("Dix favoris maximum : supprimez un modèle avant d’en ajouter un.");
        for (Entry entry : entries) {
            if (entry.template.title.equalsIgnoreCase(template.title)) throw new IllegalArgumentException("Ce titre existe déjà. Choisissez un autre titre ou supprimez l’ancien modèle.");
        }
        String id = java.util.UUID.randomUUID().toString();
        entries.add(new Entry(id, template));
        write(context, entries);
        return id;
    }

    static synchronized MissionTemplate find(Context context, String id) throws org.json.JSONException {
        for (Entry entry : list(context)) if (entry.id.equals(id)) return entry.template;
        throw new IllegalArgumentException("Ce modèle n’est plus disponible.");
    }

    static synchronized void remove(Context context, String id) throws org.json.JSONException {
        List<Entry> entries = list(context);
        entries.removeIf(entry -> entry.id.equals(id));
        write(context, entries);
    }

    private static void write(Context context, List<Entry> entries) throws org.json.JSONException {
        JSONArray records = new JSONArray();
        for (Entry entry : entries) records.put(encode(entry.template).put("id", entry.id));
        if (!prefs(context).edit().putString("entries", records.toString()).commit()) throw new IllegalStateException("Enregistrement local impossible.");
    }
}
