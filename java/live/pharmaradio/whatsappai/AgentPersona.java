package live.pharmaradio.whatsappai;

final class AgentPersona {
    enum Style {
        OFFICIEL("Officiel", "Vouvoiement, ton formel, phrases précises et sobres.", "Bonjour, je suis l’assistant IA%s. Quel est l’objet de votre appel ?"),
        AMICAL("Amical", "Ton cordial et détendu, sans familiarité imposée. Suis le registre du correspondant.", "Bonjour, je suis l’assistant IA%s. Comment puis-je vous aider ?"),
        FAMILIAL("Familial", "Ton chaleureux et attentionné, mots simples. Ne prétends pas connaître le correspondant ou partager un lien familial.", "Bonjour, je suis l’assistant IA%s. Je vous écoute, dites-moi ce qui vous amène."),
        PROFESSIONNEL("Professionnel", "Accueil efficace, clarifie le motif puis reformule les informations utiles, sans engagement.", "Bonjour, je suis l’assistant IA%s. En quoi puis-je vous être utile ?"),
        CONCIERGE("Concierge", "Ton serviable et organisé. Clarifie les besoins, sans réservation définitive ni promesse de disponibilité.", "Bonjour, je suis l’assistant IA%s. Quelle est votre demande ?"),
        CONCIS("Concis", "Une phrase courte par tour lorsque possible, une question à la fois. Aucun bavardage.", "Bonjour, assistant IA%s. Quel est votre message ?");

        final String label;
        final String policy;
        final String greeting;

        Style(String label, String policy, String greeting) {
            this.label = label;
            this.policy = policy;
            this.greeting = greeting;
        }

        static Style read(String value) {
            try { return valueOf(value); }
            catch (IllegalArgumentException | NullPointerException error) { return AMICAL; }
        }
    }

    final String owner;
    final Style style;

    AgentPersona(String owner, Style style) {
        this.owner = validateName(owner);
        this.style = style == null ? Style.AMICAL : style;
    }

    static String validateName(String name) {
        if (name == null || name.length() > 80 || name.codePoints().anyMatch(value -> Character.isISOControl(value)
                || Character.getType(value) == Character.FORMAT)) {
            throw new IllegalArgumentException("Utilisez un nom de présentation de 80 caractères maximum, sans caractères de contrôle.");
        }
        return name.trim();
    }

    String greeting() { return String.format(java.util.Locale.ROOT, style.greeting, owner.isEmpty() ? "" : " de " + owner); }

}
