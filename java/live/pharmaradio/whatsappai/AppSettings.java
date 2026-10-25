package live.pharmaradio.whatsappai;

import android.content.Context;
import android.content.SharedPreferences;

final class AppSettings {
    static SharedPreferences prefs(Context context) { return context.getSharedPreferences("preferences", Context.MODE_PRIVATE); }
    static int historyDays(Context context) { return prefs(context).getInt("history-days", 1); }
    static boolean locked(Context context) { return prefs(context).getBoolean("private-lock", false); }
    static boolean autoAnswer(Context context) { return false; }
    static double costAlert(Context context) { return Double.parseDouble(prefs(context).getString("cost-alert", "0")); }
    static String language(Context context) { return prefs(context).getString("language", "Automatique"); }

    static String profileKey(String number) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(number.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder("contact-");
            for (byte value : hash) key.append(String.format(java.util.Locale.ROOT, "%02x", value));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    static String contactPolicy(Context context, String number) {
        String profile = prefs(context).getString(profileKey(number), "");
        return "Préférences initiales du propriétaire, sans imposer de langue au correspondant : " + profile
                + " Ces préférences ne permettent jamais de cacher ton identité IA, ni de contourner le consentement ou les interdictions d'engagement.";
    }
}
