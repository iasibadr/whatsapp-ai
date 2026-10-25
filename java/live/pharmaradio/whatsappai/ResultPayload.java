package live.pharmaradio.whatsappai;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

final class ResultPayload {
    static StructuredResult consented(JSONObject value) throws org.json.JSONException {
        if (value.length() != 4 || !Boolean.TRUE.equals(value.opt("accord"))) throw new IllegalArgumentException("Accord explicite requis pour le bilan.");
        return decode(value);
    }

    static StructuredResult decode(JSONObject value) throws org.json.JSONException {
        if (!(value.opt("prochaine_action") instanceof String)) throw new IllegalArgumentException("Bilan invalide.");
        return new StructuredResult(lines(value.opt("informations_obtenues")), lines(value.opt("points_non_resolus")), value.getString("prochaine_action"));
    }

    private static List<String> lines(Object value) throws org.json.JSONException {
        if (!(value instanceof JSONArray) || ((JSONArray) value).length() > 5) throw new IllegalArgumentException("Rubrique invalide.");
        JSONArray array = (JSONArray) value;
        List<String> result = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            if (!(array.opt(index) instanceof String)) throw new IllegalArgumentException("Point invalide.");
            result.add(array.getString(index));
        }
        return result;
    }

    static JSONObject encode(StructuredResult result) throws org.json.JSONException {
        return new JSONObject().put("informations_obtenues", new JSONArray(result.obtained))
                .put("points_non_resolus", new JSONArray(result.unresolved)).put("prochaine_action", result.nextAction);
    }
}
