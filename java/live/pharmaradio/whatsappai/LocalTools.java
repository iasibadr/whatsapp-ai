package live.pharmaradio.whatsappai;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

final class LocalTools {
    private final Map<String, String> requests = new HashMap<>();
    private final Map<String, JSONObject> results = new HashMap<>();

    synchronized JSONObject execute(JSONObject request) throws Exception {
        String id = request.getString("call_id");
        String name = request.getString("name");
        JSONObject arguments = request.getJSONObject("arguments");
        String message = "";
        if (name.equals("noter_message")) {
            if (arguments.length() != 2 || !Boolean.TRUE.equals(arguments.opt("accord")) || !(arguments.opt("message") instanceof String)) {
                throw new IllegalArgumentException("Accord explicite requis");
            }
            message = arguments.getString("message").trim();
            if (message.isEmpty() || message.length() > 1500) throw new IllegalArgumentException("Message invalide");
        } else if (name.equals("noter_bilan")) {
            message = ResultPayload.encode(ResultPayload.consented(arguments)).toString();
        } else if (name.equals("terminer_discussion")) {
            String reason = arguments.optString("motif", "");
            if (arguments.length() != 1 || !(reason.equals("demande_correspondant") || reason.equals("mission_accomplie")))
                throw new IllegalArgumentException("Motif de clôture invalide");
            message = reason;
        } else if (!name.equals("demander_reprise") || arguments.length() != 0) {
            throw new IllegalArgumentException("Outil ou arguments invalides");
        }
        if (id.isEmpty() || id.length() > 200) throw new IllegalArgumentException("Identifiant invalide");
        String fingerprint = name + "\n" + message;
        if (requests.containsKey(id)) {
            if (!requests.get(id).equals(fingerprint)) throw new IllegalArgumentException("Identifiant réutilisé");
            return results.get(id);
        }
        if (requests.size() >= 100) throw new IllegalArgumentException("Limite outils atteinte");
        JSONObject result = new JSONObject().put("ok", true).put("envoye", false);
        if (name.equals("noter_message") || name.equals("noter_bilan")) result.put("copie_locale", false);
        else if (name.equals("demander_reprise")) result.put("reprise_locale_demandee", true).put("transfert_effectue", false);
        else result.put("raccrochage_demande", true).put("raccrochage_effectue", false);
        if (name.equals("noter_bilan")) result.put("action_executee", false);
        requests.put(id, fingerprint);
        results.put(id, result);
        return result;
    }
}
