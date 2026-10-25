package live.pharmaradio.whatsappai;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

public abstract class PrivateActivity extends Activity {
    private boolean unlocked;
    private boolean authenticating;
    private android.os.CancellationSignal authentication;
    protected abstract void showPrivateContent();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (unlocked) { showPrivateContent(); return; }
        if (!AppSettings.locked(this)) { unlocked = true; showPrivateContent(); return; }
        if (authenticating) return;
        KeyguardManager manager = getSystemService(KeyguardManager.class);
        if (!manager.isDeviceSecure()) {
            Ui.text(Ui.page(this, "Accès protégé", "Confidentialité"), "Configurez d’abord le verrouillage du téléphone dans les paramètres Android.", 18);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            authenticating = true;
            authentication = new android.os.CancellationSignal();
            new android.hardware.biometrics.BiometricPrompt.Builder(this).setTitle(getString(R.string.app_name))
                    .setDescription("Déverrouillez vos messages et fiches d’appel.").setDeviceCredentialAllowed(true).build()
                    .authenticate(authentication, getMainExecutor(), new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                            authenticating = false;
                            if (!isDestroyed() && !isFinishing()) { unlocked = true; showPrivateContent(); }
                        }
                        @Override
                        public void onAuthenticationError(int code, CharSequence message) { authenticating = false; finish(); }
                    });
            return;
        }
        Intent confirmation = manager.createConfirmDeviceCredentialIntent(getString(R.string.app_name), "Déverrouillez vos messages et fiches d’appel.");
        if (confirmation == null) { finish(); return; }
        authenticating = true;
        startActivityForResult(confirmation, 701);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 701) return;
        authenticating = false;
        if (result == RESULT_OK) { unlocked = true; showPrivateContent(); }
        else finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (AppSettings.locked(this) && !authenticating) {
            unlocked = false;
            Ui.page(this, "Contenu protégé", "Déverrouillage requis à la reprise");
        }
    }

    @Override
    protected void onDestroy() {
        if (authentication != null) authentication.cancel();
        super.onDestroy();
    }
}
