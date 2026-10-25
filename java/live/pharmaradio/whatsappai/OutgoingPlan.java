package live.pharmaradio.whatsappai;

import android.net.Uri;
import android.os.SystemClock;
import android.telephony.PhoneNumberUtils;

final class OutgoingPlan {
    private static final CallAuthorization authorization = new CallAuthorization();

    static synchronized void arm(String destination, String instructions) {
        arm(destination, instructions, MissionDossier.empty());
    }

    static synchronized void arm(String destination, String instructions, MissionDossier dossier) {
        authorization.arm(PhoneNumberUtils.normalizeNumber(destination), instructions, dossier, SystemClock.elapsedRealtime());
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> authorization.expire(SystemClock.elapsedRealtime()), 90000);
    }

    static synchronized PreparedMission consume(Uri handle) {
        if (handle == null || !"tel".equals(handle.getScheme())) return null;
        return authorization.consumePrepared(PhoneNumberUtils.normalizeNumber(handle.getSchemeSpecificPart()), SystemClock.elapsedRealtime());
    }

    static synchronized void clear() {
        authorization.clear();
    }
}
