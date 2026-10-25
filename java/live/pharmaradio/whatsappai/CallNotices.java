package live.pharmaradio.whatsappai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class CallNotices {
    static void show(Context context, int id, String title, String detail, Class<?> destination) {
        try {
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("voice-attention", "Messages et attention requise", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(context, id, new Intent(context, destination), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(id, new Notification.Builder(context, "voice-attention").setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(title).setContentText(detail).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(open).setAutoCancel(true).build());
        } catch (RuntimeException error) {
            android.util.Log.w("ReponseVoiceCall", "Notification unavailable: " + error.getClass().getSimpleName());
        }
    }
}
