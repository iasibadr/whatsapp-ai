package live.pharmaradio.whatsappai;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class ApiUsage {
    private final Set<String> responses = new HashSet<>();
    private final Set<String> pending = new HashSet<>();
    private long inputTokens;
    private long outputTokens;
    private double estimatedUsd;
    private boolean complete = true;

    synchronized void started(String responseId) {
        if (responseId == null || responseId.isEmpty()) complete = false;
        else pending.add(responseId);
    }

    synchronized void record(String responseId, long input, long output, Double cost) {
        if (responseId == null || responseId.isEmpty()) { complete = false; return; }
        pending.remove(responseId);
        if (!responses.add(responseId)) return;
        if (input < 0 || output < 0) { complete = false; return; }
        inputTokens += input;
        outputTokens += output;
        if (cost == null || !Double.isFinite(cost) || cost < 0) complete = false;
        else estimatedUsd += cost;
    }

    static Double estimate(RealtimeModel model, long textInput, long audioInput, long cachedText, long cachedAudio, long textOutput, long audioOutput) {
        if (model == null || textInput < 0 || audioInput < 0 || cachedText < 0 || cachedAudio < 0 || textOutput < 0 || audioOutput < 0
                || cachedText > textInput || cachedAudio > audioInput) return null;
        return ((textInput - cachedText) * model.textInput + (audioInput - cachedAudio) * model.audioInput
                + cachedText * model.cachedText + cachedAudio * model.cachedAudio
                + textOutput * model.textOutput + audioOutput * model.audioOutput) / 1000000.0;
    }

    synchronized String summary() {
        if (responses.isEmpty()) return "Consommation : aucun relevé API reçu. Cela ne prouve pas un coût nul.";
        return "Relevés de réponse : " + responses.size() + "\nTokens cumulés : entrée " + inputTokens + ", sortie " + outputTokens
                + (complete && pending.isEmpty() ? String.format(Locale.FRANCE, "\nCoût Realtime estimé : %.6f USD", estimatedUsd)
                        : "\nRelevé partiel : coût total non calculable.")
                + "\nTarifs de référence : " + RealtimeModel.PRICE_DATE
                + ". Hors dictée préalable, taxes et frais opérateur. La facturation OpenAI fait foi.";
    }

    synchronized Double estimatedTotal() {
        return !responses.isEmpty() && complete && pending.isEmpty() ? estimatedUsd : null;
    }
}
