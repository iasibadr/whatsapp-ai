package live.pharmaradio.whatsappai;

import org.json.JSONObject;

final class AgentSpecialization {
    private static final String APPLICATION = "live.pharmaradio.whatsappai";

    static String encode(MissionTemplate template) throws Exception {
        return new JSONObject().put("format", "agent-apk-specialization").put("schema", 1)
                .put("application", APPLICATION).put("template", MissionLibrary.encode(AgentSharePolicy.validate(template))).toString(2);
    }

    static MissionTemplate decode(byte[] bytes) throws Exception {
        if (bytes.length == 0 || bytes.length > AgentSharePolicy.MAX_BYTES) throw new IllegalArgumentException("Fichier vide ou trop volumineux.");
        String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        try (android.util.JsonReader reader = new android.util.JsonReader(new java.io.StringReader(text))) {
            reader.setLenient(false);
            validateObject(reader, 0);
            if (reader.peek() != android.util.JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Contenu supplémentaire refusé.");
        } catch (java.io.IOException error) { throw new IllegalArgumentException("Document JSON invalide."); }
        JSONObject envelope = new JSONObject(text);
        exactKeys(envelope, "format", "schema", "application", "template");
        if (!"agent-apk-specialization".equals(envelope.opt("format"))
                || !(envelope.opt("schema") instanceof Integer) || envelope.getInt("schema") != 1
                || !APPLICATION.equals(envelope.opt("application")) || !(envelope.opt("template") instanceof JSONObject)) {
            throw new IllegalArgumentException("Format ou application incompatible.");
        }
        JSONObject template = envelope.getJSONObject("template");
        exactKeys(template, "title", "objective", "questions", "limits", "handoff", "report");
        return AgentSharePolicy.validate(MissionLibrary.decode(template));
    }

    private static void exactKeys(JSONObject value, String... expected) {
        if (value.length() != expected.length) throw new IllegalArgumentException("Champs non autorisés.");
        for (String key : expected) if (!value.has(key)) throw new IllegalArgumentException("Champ manquant.");
    }

    private static void validateObject(android.util.JsonReader reader, int depth) throws java.io.IOException {
        if (depth > 1) throw new IllegalArgumentException("Structure trop profonde.");
        java.util.Set<String> keys = new java.util.HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            if (!keys.add(reader.nextName()) || keys.size() > 6) throw new IllegalArgumentException("Champ répété ou non autorisé.");
            switch (reader.peek()) {
                case BEGIN_OBJECT: validateObject(reader, depth + 1); break;
                case STRING: case NUMBER: reader.nextString(); break;
                case BOOLEAN: reader.nextBoolean(); break;
                default: throw new IllegalArgumentException("Valeur non autorisée.");
            }
        }
        reader.endObject();
    }
}
