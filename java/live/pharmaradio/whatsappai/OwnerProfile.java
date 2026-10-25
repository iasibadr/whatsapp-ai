package live.pharmaradio.whatsappai;

import android.content.Context;

final class OwnerProfile {
    static AgentPersona read(Context context) {
        String name = AppSettings.prefs(context).getString("owner-name", "");
        try { return new AgentPersona(name, AgentPersona.Style.read(AppSettings.prefs(context).getString("owner-style", "AMICAL"))); }
        catch (IllegalArgumentException error) { return new AgentPersona("", AgentPersona.Style.AMICAL); }
    }

    static void save(Context context, String name, AgentPersona.Style style) {
        AgentPersona persona = new AgentPersona(name, style);
        AppSettings.prefs(context).edit().putString("owner-name", persona.owner).putString("owner-style", persona.style.name()).apply();
    }
}
