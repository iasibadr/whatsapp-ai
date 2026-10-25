package live.pharmaradio.whatsappai;

final class MissionDocument {
    final String title;
    final String text;
    final boolean shareable;

    MissionDocument(String title, String text, boolean shareable) {
        if (title == null || title.trim().isEmpty() || title.length() > 60
                || title.codePoints().anyMatch(value -> Character.isISOControl(value) || Character.getType(value) == Character.FORMAT)) {
            throw new IllegalArgumentException("Titre requis, 60 caractères maximum, sans caractères de contrôle.");
        }
        if (text == null || text.trim().isEmpty() || text.length() > 6000 || text.codePoints().anyMatch(value ->
                Character.isISOControl(value) && value != '\n' && value != '\r' && value != '\t')) {
            throw new IllegalArgumentException("Texte lisible requis : 6 000 caractères maximum par pièce.");
        }
        this.title = title.trim();
        this.text = text.trim();
        this.shareable = shareable;
    }
}
