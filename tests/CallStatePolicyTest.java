package live.pharmaradio.whatsappai;

public final class CallStatePolicyTest {
    private static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("session check " + checks); }
    static CallStatePolicy armed() {
        CallStatePolicy p = new CallStatePolicy();
        check(p.arm("+12025550123", 100));
        return p;
    }
    public static void main(String[] args) {
        CallStatePolicy p = armed();
        check(!p.arm("+12025550124", 101));
        check(!p.observe(p.generation(), "call-a", "+12025550123", true, false, 1, 102));
        check(p.observe(p.generation(), "call-a", "+12025550123", true, true, 1, 103));
        check(p.state() == CallStatePolicy.State.CONNECTING);
        check(p.connected(p.generation(), "call-a"));
        check(p.state() == CallStatePolicy.State.ACTIVE);
        check(!p.observe(p.generation(), "call-a", "+12025550123", true, true, 1, 104)); // no double start
        check(!p.observe(p.generation(), "call-b", "+12025550123", true, true, 1, 105));
        check(p.state() == CallStatePolicy.State.FAILED);
        check(!p.connected(p.generation(), "call-a"));
        p = armed();
        check(!p.observe(p.generation(), "call-a", "+12025550124", true, true, 1, 101));
        check(p.state() == CallStatePolicy.State.FAILED);
        p = armed();
        check(!p.observe(p.generation(), "call-a", "+12025550123", false, true, 1, 101));
        check(p.state() == CallStatePolicy.State.FAILED);
        p = armed();
        check(!p.observe(p.generation(), "call-a", "+12025550123", true, true, 2, 101));
        check(p.state() == CallStatePolicy.State.FAILED);
        p = armed();
        check(!p.observe(p.generation(), "call-a", "+12025550123", true, true, 1, 120100));
        check(p.state() == CallStatePolicy.State.FAILED);
        p = armed(); p.stop();
        check(p.state() == CallStatePolicy.State.STOPPING);
        check(!p.observe(p.generation(), "call-a", "+12025550123", true, true, 1, 102));
        check(!p.arm("+12025550123", 103));
        p.stopped(p.generation());
        check(p.state() == CallStatePolicy.State.IDLE);
        check(p.arm("+12025550123", 104));
        p = new CallStatePolicy(); // process recreation never restores authorization
        check(!p.observe(p.generation(), "call-a", "+12025550123", true, true, 1, 101));
        check(p.state() == CallStatePolicy.State.IDLE);
        check(!p.arm("2025550123", 101));
        p = armed();
        check(p.observe(p.generation(), "call-a", "+12025550123", true, true, 1, 101));
        check(p.connected(p.generation(), "call-a"));
        check(!p.observe(p.generation(), "", "", true, false, 0, 102));
        check(p.state() == CallStatePolicy.State.STOPPING);
        p = armed();
        long old = p.generation();
        check(p.observe(old, "same-call", "+12025550123", true, true, 1, 101));
        p.stop(); p.stopped(old);
        check(p.arm("+12025550123", 102));
        long fresh = p.generation();
        check(fresh != old);
        check(p.observe(fresh, "same-call", "+12025550123", true, true, 1, 103));
        check(!p.connected(old, "same-call"));
        check(!p.observe(old, "other-call", "+12025550124", true, true, 1, 104));
        check(p.state() == CallStatePolicy.State.CONNECTING);
        p.stop(); p.stopped(old);
        check(p.state() == CallStatePolicy.State.STOPPING);
        p.stopped(fresh);
        check(p.state() == CallStatePolicy.State.IDLE);
        System.out.println("PASS " + checks + " session lifecycle checks (synthetic only)");
    }
}
