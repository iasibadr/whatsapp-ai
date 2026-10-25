package live.pharmaradio.whatsappai;

final class SetupLinks {
    static final String OPENAI_KEYS = "https://platform.openai.com/api-keys";
    static final String OPENAI_PRICING = "https://developers.openai.com/api/docs/pricing";
    static final String OPENAI_BILLING = "https://platform.openai.com/settings/organization/billing";

    static void open(android.app.Activity activity, String link) {
        if (!link.equals(OPENAI_KEYS) && !link.equals(OPENAI_PRICING) && !link.equals(OPENAI_BILLING)) {
            throw new IllegalArgumentException("Lien non autorisé");
        }
        try { activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))); }
        catch (android.content.ActivityNotFoundException error) {
            android.widget.Toast.makeText(activity, "Aucun navigateur disponible.", android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
