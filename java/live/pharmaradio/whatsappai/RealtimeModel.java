package live.pharmaradio.whatsappai;

import java.util.Locale;

enum RealtimeModel {
    FULL("gpt-realtime-2.1", "Complet", "Le modèle utilisé jusqu’ici.", 4.0, 0.4, 24.0, 32.0, 0.4, 64.0),
    MINI("gpt-realtime-2.1-mini", "Économique", "Version Mini, moins coûteuse. Comparez la qualité sur vos propres missions.", 0.6, 0.06, 2.4, 10.0, 0.3, 20.0);

    static final String PRICE_DATE = "18/10/2026";
    final String id;
    final String label;
    final String description;
    final double textInput;
    final double cachedText;
    final double textOutput;
    final double audioInput;
    final double cachedAudio;
    final double audioOutput;

    RealtimeModel(String id, String label, String description, double textInput, double cachedText, double textOutput,
            double audioInput, double cachedAudio, double audioOutput) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.textInput = textInput;
        this.cachedText = cachedText;
        this.textOutput = textOutput;
        this.audioInput = audioInput;
        this.cachedAudio = cachedAudio;
        this.audioOutput = audioOutput;
    }

    static RealtimeModel find(String id) {
        for (RealtimeModel model : values()) if (model.id.equals(id)) return model;
        return null;
    }

    double inputMinute() { return 600.0 * audioInput / 1000000.0; }
    double outputMinute() { return 1200.0 * audioOutput / 1000000.0; }
    double balancedMinute() { return (inputMinute() + outputMinute()) / 2.0; }

    String minutePrices() {
        return String.format(Locale.FRANCE, "Audio neuf seul :\n• 1 min de parole reçue : ≈ %.4f USD\n• 1 min de parole IA produite : ≈ %.4f USD\n• Exemple 30 s reçues + 30 s IA : ≈ %.4f USD", inputMinute(), outputMinute(), balancedMinute());
    }

    String tokenPrices() {
        return String.format(Locale.FRANCE, "Tarifs USD / million de tokens :\nAudio entrée %.2f · cache %.2f · sortie %.2f\nTexte entrée %.2f · cache %.2f · sortie %.2f",
                audioInput, cachedAudio, audioOutput, textInput, cachedText, textOutput);
    }
}
