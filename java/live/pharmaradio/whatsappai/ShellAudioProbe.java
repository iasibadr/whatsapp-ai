package live.pharmaradio.whatsappai;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Looper;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;

public final class ShellAudioProbe {
    private static final String[] PERMISSIONS = {
        "android.permission.CALL_AUDIO_INTERCEPTION",
        "android.permission.CAPTURE_AUDIO_OUTPUT",
        "android.permission.MODIFY_AUDIO_ROUTING",
        "android.permission.MODIFY_PHONE_STATE"
    };

    public static void main(String[] arguments) {
        JSONObject result = new JSONObject();
        int exitCode = 0;
        try {
            result.put("uid", Process.myUid());
            result.put("read_only", true);
            result.put("audio_captured", false);
            result.put("audio_injected", false);
            if (Process.myUid() != 2000) {
                throw new SecurityException("Ce diagnostic doit etre lance sous le compte ADB shell.");
            }
            if (Looper.myLooper() == null) Looper.prepareMainLooper();
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("systemMain").invoke(null);
            Context systemContext = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
            Context shellContext = systemContext.createPackageContext("com.android.shell", 0);
            AudioManager audio = shellContext.getSystemService(AudioManager.class);
            JSONObject permissions = new JSONObject();
            for (String permission : PERMISSIONS) {
                permissions.put(permission, shellContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
            }
            result.put("permissions", permissions);
            result.put("audio_mode", audio.getMode());
            boolean interceptable = (Boolean) AudioManager.class.getMethod("isPstnCallAudioInterceptable").invoke(audio);
            result.put("pstn_audio_interceptable", interceptable);
            result.put("downlink_api_present", AudioManager.class.getMethod("getCallDownlinkExtractionAudioRecord", AudioFormat.class) != null);
            result.put("uplink_api_present", AudioManager.class.getMethod("getCallUplinkInjectionAudioTrack", AudioFormat.class) != null);
            JSONArray telephonyDevices = new JSONArray();
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_INPUTS | AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == AudioDeviceInfo.TYPE_TELEPHONY) {
                    JSONObject entry = new JSONObject();
                    entry.put("input", device.isSource());
                    entry.put("output", device.isSink());
                    entry.put("sample_rates", new JSONArray(device.getSampleRates()));
                    telephonyDevices.put(entry);
                }
            }
            result.put("telephony_devices", telephonyDevices);
            result.put("status", interceptable ? "capability_present_live_test_required" : "unsupported");
        } catch (Throwable error) {
            Throwable cause = error instanceof InvocationTargetException && error.getCause() != null ? error.getCause() : error;
            try {
                result.put("status", "probe_failed");
                result.put("error_type", cause.getClass().getSimpleName());
                result.put("error", cause.getMessage());
            } catch (Exception ignored) {
                System.err.println("Impossible de produire le diagnostic.");
            }
            exitCode = 1;
        }
        System.out.println(result);
        System.exit(exitCode);
    }
}
