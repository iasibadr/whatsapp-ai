package live.pharmaradio.whatsappai;

final class CallOutcome {
    enum State { PREPARING, ACTIVE, FINISHED, HANDOFF, INTERRUPTED }
    private State state = State.PREPARING;
    private String reason = "Préparation de l’IA";
    synchronized void active() { if (!terminal()) { state = State.ACTIVE; reason = "Conversation IA en cours"; } }
    synchronized void finish(String message, boolean failure, boolean handoff) {
        if (terminal()) return;
        state = failure ? State.INTERRUPTED : handoff ? State.HANDOFF : State.FINISHED;
        reason = message;
    }
    synchronized boolean terminal() { return state != State.PREPARING && state != State.ACTIVE; }
    synchronized String state() { return state.name(); }
    synchronized String reason() { return reason; }
}
