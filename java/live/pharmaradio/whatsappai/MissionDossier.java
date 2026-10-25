package live.pharmaradio.whatsappai;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

final class MissionDossier {
    final List<MissionDocument> documents;

    MissionDossier(List<MissionDocument> documents) {
        if (documents == null || documents.size() > 5) throw new IllegalArgumentException("Cinq pièces maximum par mission.");
        int length = 0;
        for (MissionDocument document : documents) {
            if (document == null) throw new IllegalArgumentException("Pièce invalide.");
            length += document.title.length() + document.text.length();
        }
        if (length > 12000) throw new IllegalArgumentException("Dossier limité à 12 000 caractères au total, titres compris.");
        this.documents = Collections.unmodifiableList(new ArrayList<>(documents));
    }

    static MissionDossier empty() { return new MissionDossier(Collections.emptyList()); }

    MissionDossier add(MissionDocument document) {
        List<MissionDocument> updated = new ArrayList<>(documents);
        updated.add(document);
        return new MissionDossier(updated);
    }

    MissionDossier remove(int index) {
        List<MissionDocument> updated = new ArrayList<>(documents);
        updated.remove(index);
        return new MissionDossier(updated);
    }
}
