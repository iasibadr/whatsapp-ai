package live.pharmaradio.whatsappai;

import java.util.*;
import java.util.regex.*;

/** Pixel format observed on-device. Unknown, concurrent or changing calls fail closed. */
final class TelecomCallPolicy {
    private static final String COMPONENT = "com.whatsapp/com.whatsapp";
    private static final String[] KEYS = {"id","tId","state","tpac","cmgr","handle","vidst","cap","prop","voip"};
    private static final Pattern KEY = Pattern.compile("(?:^|[, \\t])([A-Za-z][A-Za-z0-9_]*)=");
    private static final Pattern ACCOUNT = Pattern.compile("ComponentInfo\\{com\\.whatsapp/com\\.whatsapp\\},[ \\t]*[^,\\r\\n]{1,256},[ \\t]*UserHandle\\{0\\}");

    private static final class Current {
        int count;
        boolean bounded, parsed, account, voip, manager;
        String id="", state="unrecognized";
        boolean allowed() { return parsed && count==1 && account && voip && manager && "ACTIVE".equals(state); }
    }
    private static int uniqueHeader(String dump, String regex) {
        Matcher m=Pattern.compile(regex,Pattern.MULTILINE).matcher(dump);
        if (!m.find()) return -1;
        int start=m.start();
        return m.find() ? -1 : start;
    }
    private static Current current(String dump) {
        Current c=new Current();
        if (dump==null || dump.length()>2000000) return c;
        int root=uniqueHeader(dump,"^CallsManager:[ \\t]*\\r?$");
        int start=uniqueHeader(dump,"^  mCalls:[ \\t]*\\r?$");
        int end=uniqueHeader(dump,"^  mCallAudioManager:[ \\t]*\\r?$");
        if (root<0 || start<=root || end<=start) return c;
        int newline=dump.indexOf('\n',start);
        if (newline<0 || newline>=end) return c;
        c.bounded=true;
        String record="";
        for(String line:dump.substring(newline+1,end).split("\\r?\\n")) {
            if (!line.trim().isEmpty()) { c.count++; record=line.trim(); }
        }
        if (c.count!=1 || !record.startsWith("[Call id=")) return c;
        // Pixel API 37 appends VoIP status after the Call object's closing bracket.
        if (!record.matches(".*[]], voip=(true|false)")) return c;
        record=record.replaceFirst("[]], voip=(true|false)$", ", voip=$1");
        Matcher m=KEY.matcher(record);
        List<Integer> starts=new ArrayList<>(), ends=new ArrayList<>();
        int i=0;
        while(m.find()) {
            if (i>=KEYS.length || !KEYS[i++].equals(m.group(1))) return c;
            starts.add(m.start()); ends.add(m.end());
        }
        if (i!=KEYS.length) return c;
        String[] values=new String[KEYS.length];
        for(i=0;i<values.length;i++) {
            values[i]=record.substring(ends.get(i),i+1<values.length?starts.get(i+1):record.length()).trim();
            if (values[i].endsWith(",")) values[i]=values[i].substring(0,values[i].length()-1).trim();
        }
        if (!values[0].matches("[A-Za-z0-9@._-]{1,96}")) return c;
        c.id=values[0];
        if (values[2].matches("ACTIVE|DIALING|RINGING|HOLDING|DISCONNECTED|CONNECTING|NEW|SELECT_PHONE_ACCOUNT")) c.state=values[2];
        c.account=ACCOUNT.matcher(values[3]).matches();
        c.manager="null".equals(values[4]);
        c.voip="true".equals(values[9]);
        c.parsed=true;
        return c;
    }
    static boolean observeLaunch(LaunchAttempt attempt,String before,String after,long now) {
        Current a=current(before),b=current(after);
        if(!a.bounded||!b.bounded){attempt.cancel("unbounded_snapshots");return false;}
        if(a.count==0&&b.count==1){
            attempt.observe(0,"","",false,now);
            return attempt.observe(b.count,b.id,b.state,b.parsed&&b.account&&b.voip&&b.manager,now);
        }
        if(a.count!=b.count||!a.id.equals(b.id)) {
            attempt.cancel("unstable_snapshots");return false;
        }
        attempt.observe(a.count,a.id,a.state,a.parsed&&a.account&&a.voip&&a.manager,now);
        return attempt.observe(b.count,b.id,b.state,b.parsed&&b.account&&b.voip&&b.manager,now);
    }
    static boolean noCalls(String dump) { Current c=current(dump); return c.bounded && c.count==0; }
    static String sessionKey(String before,String after) {
        Current a=current(before),b=current(after);
        return a.allowed() && b.allowed() && a.id.equals(b.id) ? a.id : "";
    }
    static boolean permits(String audio,String before,String after,int uid) {
        Current a=current(before), b=current(after);
        return uid>=10000 && uid<100000 && a.allowed() && b.allowed() && a.id.equals(b.id)
            && (WhatsAppAudioPolicy.permits(audio,uid) || WhatsAppAudioPolicy.permitsTelecomOwner(audio));
    }
    static Map<String,Object> diagnostics(String audio,String before,String after,int uid) {
        Current a=current(before), b=current(after);
        Map<String,Object> out=new LinkedHashMap<>(WhatsAppAudioPolicy.diagnostics(audio,uid));
        out.put("direct_owner_allows",out.get("strict_policy_allows"));
        out.put("telecom_audio_owner_matches",WhatsAppAudioPolicy.permitsTelecomOwner(audio));
        out.put("current_call_count",b.count);
        out.put("current_call_format_recognized",b.parsed);
        out.put("current_call_state",b.state);
        out.put("current_call_account",b.account?COMPONENT:"unrecognized_or_other");
        out.put("current_call_voip",b.voip);
        out.put("current_call_manager_null",b.manager);
        out.put("same_call_before_after",a.allowed() && b.allowed() && a.id.equals(b.id));
        out.put("strict_policy_allows",permits(audio,before,after,uid));
        out.put("raw_dump_included",false);
        return out;
    }
}
