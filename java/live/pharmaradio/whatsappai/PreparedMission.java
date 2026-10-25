package live.pharmaradio.whatsappai;

final class PreparedMission {
    final String brief;
    final MissionDossier dossier;

    PreparedMission(String brief, MissionDossier dossier) {
        if (brief == null || brief.trim().isEmpty() || brief.length() > 4000 || dossier == null) {
            throw new IllegalArgumentException("Mission ou dossier invalide.");
        }
        this.brief = brief.trim();
        this.dossier = dossier;
    }
}
