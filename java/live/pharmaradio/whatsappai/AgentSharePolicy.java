package live.pharmaradio.whatsappai;

final class AgentSharePolicy {
    static final int MAX_BYTES = 16384;
    static final long LIFETIME_MS = 3600000;

    static boolean filename(String name) {
        return name != null && name.matches("[a-f0-9-]{36}\\.(apk|json)");
    }

    static boolean fresh(long modified, long now) {
        return modified > 0 && now >= modified && now - modified < LIFETIME_MS;
    }

    static MissionTemplate validate(MissionTemplate template) {
        for (String field : new String[]{template.title, template.objective, template.questions, template.limits, template.handoff}) {
            if (field.codePoints().anyMatch(value -> Character.getType(value) == Character.FORMAT
                    || Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t')
                    || java.util.regex.Pattern.compile("sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}|-----BEGIN .*PRIVATE KEY-----").matcher(field).find()) {
                throw new IllegalArgumentException("Retirez les secrets et les caractères masqués du modèle.");
            }
        }
        return template;
    }

    static String preview(MissionTemplate template) {
        return "Titre : " + template.title + "\n\nObjectif : " + template.objective + "\n\nQuestions : " + template.questions
                + "\n\nLimites : " + template.limits + "\n\nReprise humaine : " + template.handoff
                + "\n\nProposer un bilan avec consentement : " + (template.report ? "oui" : "non");
    }
}
