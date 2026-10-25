package live.pharmaradio.whatsappai;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class LocalPairingReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results == null) return;
        CharSequence value = results.getCharSequence("pairing-code");
        if (value == null) return;
        LocalActivationService.submit(value.toString().trim());
        results.clear();
    }
}
