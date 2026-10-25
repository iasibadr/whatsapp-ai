package live.pharmaradio.whatsappai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class StructuredResult {
    final List<String> obtained;
    final List<String> unresolved;
    final String nextAction;

    StructuredResult(List<String> obtained, List<String> unresolved, String nextAction) {
        this.obtained = lines(obtained);
        this.unresolved = lines(unresolved);
        if (nextAction == null || nextAction.trim().isEmpty() || nextAction.length() > 300 || nextAction.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Prochaine action requise, 300 caractères maximum. Indiquer Aucune si nécessaire.");
        }
        this.nextAction = nextAction.trim();
        if (render().length() > 1500) throw new IllegalArgumentException("Bilan trop long : 1 500 caractères maximum avec les rubriques.");
    }

    private static List<String> lines(List<String> values) {
        if (values == null || values.size() > 5) throw new IllegalArgumentException("Cinq points maximum par rubrique.");
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty() || value.length() > 240 || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Point de bilan invalide.");
            result.add(value.trim());
        }
        return Collections.unmodifiableList(result);
    }

    static String display(List<String> values) { return values.isEmpty() ? "Non renseigné." : "• " + String.join("\n• ", values); }

    String render() {
        return "BILAN STRUCTURÉ\nInformations obtenues\n" + display(obtained)
                + "\nPoints non résolus\n" + display(unresolved) + "\nProchaine action proposée (non exécutée)\n" + nextAction;
    }
}
