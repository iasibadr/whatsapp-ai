package live.pharmaradio.whatsappai;

final class MissionTemplate {
    final String title;
    final String objective;
    final String questions;
    final String limits;
    final String handoff;
    final boolean report;

    MissionTemplate(String title, String objective, String questions, String limits, String handoff, boolean report) {
        if (title == null || title.trim().isEmpty() || title.length() > 60
                || title.codePoints().anyMatch(value -> Character.isISOControl(value) || Character.getType(value) == Character.FORMAT)) {
            throw new IllegalArgumentException("Donnez un titre de 1 à 60 caractères, sans caractères de contrôle.");
        }
        this.title = title.trim();
        this.objective = field(objective, 1600);
        this.questions = field(questions, 900);
        this.limits = field(limits, 450);
        this.handoff = field(handoff, 450);
        this.report = report;
        CallMission.build(this.objective, this.questions, this.limits, this.handoff, report);
    }

    private static String field(String text, int maximum) {
        if (text == null || text.length() > maximum || text.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Un champ du modèle est vide ou trop long.");
        }
        return text.trim();
    }

    static MissionTemplate[] examples() {
        String limits = "Aucun engagement, achat, paiement ni réservation définitive.";
        String handoff = "Le correspondant demande une personne ou une décision est nécessaire.";
        return new MissionTemplate[]{
            new MissionTemplate("Horaires et accès", "Demander les horaires et les modalités d’accès.", "Quels jours et horaires ? Faut-il prendre rendez-vous ?", limits, handoff, true),
            new MissionTemplate("Renseignement et devis", "Demander des informations et un tarif indicatif, sans commander.", "Quel est le prix indicatif et que comprend-il ? Quels délais ?", limits, handoff, true),
            new MissionTemplate("Disponibilités de rendez-vous", "Demander des créneaux possibles, sans confirmer de réservation.", "Quels créneaux sont disponibles ? Quelles démarches pour confirmer ensuite ?", limits, handoff, true),
            new MissionTemplate("Suivi de livraison", "Demander un point sur une livraison. Je préciserai la référence utile avant d’appeler.", "Quel est le statut et le délai estimé ?", limits + " Ne donner ni code d’accès ni donnée bancaire.", handoff, true),
            new MissionTemplate("Prendre des nouvelles", "Prendre des nouvelles du correspondant et lui demander s’il souhaite laisser un message.", "Comment allez-vous ? Souhaitez-vous transmettre un message ?", limits + " Ne prétendre ni être une personne ni avoir un lien familial.", handoff, true)
        };
    }
}
