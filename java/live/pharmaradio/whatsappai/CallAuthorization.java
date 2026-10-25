package live.pharmaradio.whatsappai;

final class CallAuthorization {
    private String number;
    private PreparedMission mission;
    private long expires;

    synchronized void arm(String destination, String instructions, long now) {
        arm(destination, instructions, MissionDossier.empty(), now);
    }

    synchronized void arm(String destination, String instructions, MissionDossier dossier, long now) {
        clear();
        if (destination == null || !destination.matches("\\+?[0-9]{6,15}") || instructions == null
                || instructions.trim().isEmpty() || instructions.length() > 4000) {
            throw new IllegalArgumentException("Destination ou mission invalide");
        }
        number = destination;
        mission = new PreparedMission(instructions, dossier);
        expires = now + 90000;
    }

    synchronized String consume(String destination, long now) {
        PreparedMission prepared = consumePrepared(destination, now);
        return prepared == null ? null : prepared.brief;
    }

    synchronized PreparedMission consumePrepared(String destination, long now) {
        expire(now);
        if (number == null || !number.equals(destination)) return null;
        PreparedMission result = mission;
        clear();
        return result;
    }

    synchronized void expire(long now) {
        if (now >= expires) clear();
    }

    synchronized void clear() {
        number = null;
        mission = null;
        expires = 0;
    }
}
