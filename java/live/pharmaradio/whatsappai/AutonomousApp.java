package live.pharmaradio.whatsappai;

public class AutonomousApp extends android.app.Application {
    private static AutonomousApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        java.io.File[] leftovers = getCacheDir().listFiles((directory, name) -> name.startsWith("mission-document-") && name.endsWith(".pdf"));
        if (leftovers != null) for (java.io.File file : leftovers) file.delete();
    }

    static android.content.Context context() {
        if (instance == null) throw new IllegalStateException("Application indisponible");
        return instance;
    }
}
