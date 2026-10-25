package live.pharmaradio.whatsappai;

/** Pure recipient gate. Call ownership and stable session identity remain separate guards. */
final class CallLaunchPolicy {
    private CallLaunchPolicy() {}

    static boolean accept(String expectedDestination, String observedDestination,
                          boolean outgoing, boolean active, int callCount) {
        if (!outgoing || !active || callCount != 1) return false;
        String expected = canonical(expectedDestination);
        return expected != null && expected.equals(canonical(observedDestination));
    }

    private static String canonical(String value) {
        if (value == null || value.length() > 64) return null;
        if (value.startsWith("tel:")) value = value.substring(4);
        if (!value.matches("\\+[0-9 ()-]+")) return null;
        value = value.replace(" ", "").replace("(", "").replace(")", "").replace("-", "");
        return value.matches("\\+[1-9][0-9]{6,14}") ? value : null;
    }
}
