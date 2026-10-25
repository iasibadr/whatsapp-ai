package live.pharmaradio.whatsappai;

/** In-memory authorization only. Caller supplies monotonic elapsedRealtime milliseconds.
 * observe() returns true exactly once when a verified call can begin network/audio preparation.
 * The Android coordinator must stop resources on FAILED/STOPPING; this class never owns audio.
 */
final class CallStatePolicy {
    enum State { IDLE, WAITING, CONNECTING, ACTIVE, STOPPING, FAILED }
    private State state = State.IDLE;
    private String destination, callId;
    private long armedAt, lastTime;
    private static final java.util.concurrent.atomic.AtomicLong NEXT = new java.util.concurrent.atomic.AtomicLong();
    private long generation;
    synchronized State state() { return state; }
    synchronized long generation() { return generation; }

    synchronized boolean arm(String destination, long now) {
        if (state != State.IDLE || now < 0 ||
                !CallLaunchPolicy.accept(destination, destination, true, true, 1)) return false;
        this.destination = destination;
        generation = NEXT.incrementAndGet();
        armedAt = lastTime = now;
        callId = null;
        state = State.WAITING;
        return true;
    }

    synchronized boolean observe(long attempt, String id, String observed, boolean outgoing,
                                 boolean active, int calls, long now) {
        if (attempt != generation) return false;
        if (state == State.IDLE || state == State.STOPPING || state == State.FAILED) return false;
        if (now < lastTime) return fail();
        lastTime = now;
        if (state == State.WAITING && now - armedAt >= 120000) return fail();
        if (calls == 0) {
            if (callId != null || state != State.WAITING) stop();
            return false;
        }
        if (id == null || !id.matches("[A-Za-z0-9@._-]{1,96}") ||
                !CallLaunchPolicy.accept(destination, observed, outgoing, true, calls)) return fail();
        if (callId != null && !callId.equals(id)) return fail();
        callId = id;
        if (!active) {
            if (state != State.WAITING) stop();
            return false;
        }
        if (state == State.WAITING) { state = State.CONNECTING; return true; }
        return false;
    }

    synchronized boolean connected(long attempt, String id) {
        if (attempt != generation || state != State.CONNECTING || callId == null || !callId.equals(id)) return false;
        state = State.ACTIVE;
        return true;
    }
    synchronized void stop() {
        if (state != State.IDLE && state != State.FAILED) state = State.STOPPING;
    }
    synchronized void stopped(long attempt) {
        if (attempt != generation) return;
        if (state != State.STOPPING && state != State.FAILED) return;
        destination = callId = null;
        state = State.IDLE;
    }
    private boolean fail() {
        destination = callId = null;
        state = State.FAILED;
        return false;
    }
}
