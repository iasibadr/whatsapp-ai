package live.pharmaradio.whatsappai;

final class CallMission {
    static String build(String objective, String questions, String limits, String handoff, boolean report) {
        if (objective == null || objective.trim().isEmpty()) throw new IllegalArgumentException("Indiquez d’abord l’objectif de l’appel.");
        String mission = "Objectif : " + objective.trim()
                + "\nQuestions indispensables : " + (questions.trim().isEmpty() ? "Clarifier uniquement le sujet demandé." : questions.trim())
                + "\nLimites fixées par le propriétaire : " + (limits.trim().isEmpty() ? "Aucun engagement, achat ou paiement." : limits.trim())
                + "\nRendre la main si : " + (handoff.trim().isEmpty() ? "Le correspondant le demande ou une décision engage le propriétaire." : handoff.trim());
        if (report) mission += "\nProposer un bilan structuré : informations obtenues, points non résolus et prochaine action proposée, jamais exécutée. Reformuler les trois rubriques, annoncer leur conservation locale de 24 heures, attendre l’accord explicite du correspondant, puis utiliser noter_bilan. En cas de refus ou de silence, ne pas enregistrer de bilan.";
        if (mission.length() > 4000) throw new IllegalArgumentException("La mission est trop longue : raccourcissez les consignes.");
        return mission;
    }
}
