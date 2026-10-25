package live.pharmaradio.whatsappai;

public final class CallLaunchPolicyTest {
    private static int count;
    private static void check(boolean value) {
        count++;
        if (!value) throw new AssertionError("recipient guard check " + count);
    }
    public static void main(String[] args) {
        String expected = "+12025550123";
        check(CallLaunchPolicy.accept(expected, "tel:+12025550123", true, true, 1));
        check(CallLaunchPolicy.accept("+1 (202) 555-0123", "+12025550123", true, true, 1));
        check(!CallLaunchPolicy.accept(expected, "+12025550124", true, true, 1));
        for (String bad : new String[]{null, "", "tel:***", "tel:null", "2025550123", "0012025550123",
                "sip:+12025550123", "+12025550123;ext=1", "+12025550123,7", "+12025550123\n",
                "+12025550123@attacker", "+00000000", "+1", "+1202555012345678", "+١٢٠٢٥٥٥٠١٢٣"}) {
            check(!CallLaunchPolicy.accept(expected, bad, true, true, 1));
            check(!CallLaunchPolicy.accept(bad, expected, true, true, 1));
        }
        check(!CallLaunchPolicy.accept(expected, expected, false, true, 1));
        check(!CallLaunchPolicy.accept(expected, expected, true, false, 1));
        for (int calls : new int[]{-1, 0, 2, 3}) check(!CallLaunchPolicy.accept(expected, expected, true, true, calls));
        check(!CallLaunchPolicy.accept("+442025550123", expected, true, true, 1));
        System.out.println("PASS " + count + " recipient identity checks (synthetic only)");
    }
}
