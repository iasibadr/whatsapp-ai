package live.pharmaradio.whatsappai;

final class Readiness {
    private static long checked;
    private static String detail = "Connexion à vérifier";
    private static boolean ready;
    private static boolean checking;
    private static String checkedModel = "";
    static synchronized void begin() {
        checking = true;
        checkedModel = ModelSettings.read(AutonomousApp.context()).id;
    }
    static synchronized void result(String message) {
        checking = false;
        ready = message.startsWith("Diagnostic réussi");
        detail = message;
        checked = android.os.SystemClock.elapsedRealtime();
    }
    static synchronized boolean checking() { return checking; }
    static synchronized boolean fresh() {
        return ready && checkedModel.equals(ModelSettings.read(AutonomousApp.context()).id)
                && BridgeProvider.getHelper() != null && android.os.SystemClock.elapsedRealtime() - checked < 120000;
    }
    static synchronized String detail() { return detail; }
}
