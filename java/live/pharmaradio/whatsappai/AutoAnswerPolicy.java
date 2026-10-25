package live.pharmaradio.whatsappai;

final class AutoAnswerPolicy {
    static boolean allowed(boolean enabled, boolean ringing, boolean exclusive, boolean visibleNumber, boolean emergency) {
        return enabled && ringing && exclusive && visibleNumber && !emergency;
    }
}
