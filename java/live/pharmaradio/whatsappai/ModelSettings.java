package live.pharmaradio.whatsappai;

import android.content.Context;

final class ModelSettings {
    static RealtimeModel read(Context context) {
        RealtimeModel model = RealtimeModel.find(AppSettings.prefs(context).getString("voice-model", RealtimeModel.FULL.id));
        return model == null ? RealtimeModel.FULL : model;
    }

    static void save(Context context, RealtimeModel model) {
        if (model == null) throw new IllegalArgumentException("Modèle inconnu.");
        AppSettings.prefs(context).edit().putString("voice-model", model.id).apply();
    }
}
