package live.pharmaradio.whatsappai;

import android.os.Build;
import android.telecom.Call;
import android.telecom.TelecomManager;

final class CallIdentity {
    final String name;
    final String number;
    private CallIdentity(String name, String number) { this.name = name; this.number = number; }
    static CallIdentity read(Call call) {
        String number = "";
        String name = "";
        if (call == null || call.getDetails() == null) return new CallIdentity(name, number);
        Call.Details details = call.getDetails();
        if (details.getHandlePresentation() == TelecomManager.PRESENTATION_ALLOWED) {
            android.net.Uri handle = details.getHandle();
            if (handle != null && "tel".equals(handle.getScheme())) {
                number = android.telephony.PhoneNumberUtils.normalizeNumber(handle.getSchemeSpecificPart());
                if (!number.matches("\\+?[0-9]{1,30}")) number = "";
            }
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    String contact = details.getContactDisplayName();
                    if (contact != null) name = contact.substring(0, Math.min(150, contact.length()));
                } catch (SecurityException ignored) { }
            }
        }
        return new CallIdentity(name, number);
    }
}
