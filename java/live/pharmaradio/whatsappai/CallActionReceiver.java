package live.pharmaradio.whatsappai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CallActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        CallBridgeService service = CallBridgeService.instance;
        if (service == null) return;
        if ("human".equals(intent.getAction())) service.chooseHuman();
        if ("ai".equals(intent.getAction())) service.chooseAI();
        if ("hangup".equals(intent.getAction())) service.hangUp();
    }
}
