package live.pharmaradio.whatsappai;

final class LocalActivationPolicy {
    enum Stage { KEY, DEVELOPER, WIRELESS, PAIR, ACTIVATE, VERIFY, READY }

    static Stage next(boolean key, boolean paired, boolean activatedLocally, boolean helper, boolean fresh, boolean developer, boolean wireless) {
        if (!key) return Stage.KEY;
        if (paired && activatedLocally && helper) return fresh ? Stage.READY : Stage.VERIFY;
        if (!developer) return Stage.DEVELOPER;
        if (!wireless) return Stage.WIRELESS;
        return paired ? Stage.ACTIVATE : Stage.PAIR;
    }

    static boolean freshPort(int port, long seen, long now) {
        return port >= 1024 && port <= 65535 && seen >= 0 && now >= seen && now - seen < 60000;
    }

    static boolean pairingCode(String code) { return code != null && code.matches("[0-9]{6}"); }

    static String activationCommand(String installedApk) {
        if (installedApk == null || !installedApk.matches("/data/app/[A-Za-z0-9_~=/+.-]+/base\\.apk")
                || installedApk.contains("/../") || installedApk.contains("/./")) throw new IllegalArgumentException("APK installée invalide");
        return "CLASSPATH='" + installedApk + "' /system/bin/app_process / "
                + "live.pharmaradio.whatsappai.ShellAudioBridge --activate-only";
    }
}
