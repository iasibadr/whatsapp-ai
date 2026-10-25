package live.pharmaradio.whatsappai;

final class SetupPolicy {
    static final int VERSION = 1;

    static boolean canFinish(boolean key, boolean helper, boolean diagnostic) {
        return key && helper && diagnostic;
    }

    static String activationStatus(boolean helper, boolean shizuku, boolean permission) {
        if (helper) return "Service audio déjà démarré";
        if (!shizuku) return "Démarrez Shizuku pour activer le service audio";
        if (!permission) return "Autorisez WhatsApp AI dans Shizuku";
        return "Prêt à activer le service audio";
    }
}
