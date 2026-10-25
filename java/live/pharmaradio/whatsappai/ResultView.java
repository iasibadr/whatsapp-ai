package live.pharmaradio.whatsappai;

import android.widget.LinearLayout;

final class ResultView {
    static void show(LinearLayout parent, StructuredResult result) {
        Ui.card(parent, "Informations obtenues", StructuredResult.display(result.obtained));
        Ui.card(parent, "Points non résolus", StructuredResult.display(result.unresolved));
        Ui.card(parent, "Prochaine action proposée", result.nextAction + "\nAucune action exécutée automatiquement.");
    }
}
