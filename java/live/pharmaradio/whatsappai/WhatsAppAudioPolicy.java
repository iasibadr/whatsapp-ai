package live.pharmaradio.whatsappai;

/** Fail closed: only the current mode owner, never history or the owner stack. */
final class WhatsAppAudioPolicy {
    /** Only typed allowlisted fields leave the phone's full in-memory dumpsys output. */
    static java.util.Map<String, Object> diagnostics(String dump, int uid) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        String text = dump == null ? "" : dump;
        int start = text.indexOf("## Audio mode:");
        int owner = start < 0 ? -1 : text.indexOf("- Mode owner:", start);
        int end = owner < 0 ? -1 : text.indexOf("- Mode owner stack:", owner);
        boolean bounded = start >= 0 && owner >= start && end > owner;
        result.put("expected_whatsapp_uid", uid);
        result.put("audio_section_found", start >= 0);
        result.put("owner_header_found", owner >= 0);
        result.put("owner_end_found", end >= 0);
        result.put("owner_block_bounded", bounded);
        String header = bounded ? text.substring(start, owner) : "";
        String block = bounded ? text.substring(owner, end) : "";
        result.put("requested_mode", field(header, "Requested mode", "MODE_[A-Z_]{1,48}"));
        result.put("actual_mode", field(header, "Actual mode", "MODE_[A-Z_]{1,48}"));
        result.put("owner_mode", field(block, "Mode", "MODE_[A-Z_]{1,48}"));
        result.put("owner_package", field(block, "Package", "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*){1,15}"));
        result.put("owner_uid", field(block, "Uid", "[0-9]{1,9}"));
        result.put("owner_pid", field(block, "Pid", "[0-9]{1,9}"));
        result.put("owner_active", field(block, "Active", "true|false"));
        result.put("owner_privileged", field(block, "Privileged", "true|false"));
        result.put("owner_none", bounded && block.matches("(?s).*\\bNone\\b.*"));
        result.put("strict_policy_allows", permits(dump, uid));
        result.put("actual_mode_matches", line(header, "Actual mode", "MODE_IN_COMMUNICATION"));
        result.put("owner_mode_matches", line(block, "Mode", "MODE_IN_COMMUNICATION"));
        result.put("owner_package_matches", line(block, "Package", "com.whatsapp"));
        result.put("owner_uid_matches", line(block, "Uid", Integer.toString(uid)));
        result.put("owner_active_matches", line(block, "Active", "true"));
        result.put("raw_dump_included", false);
        return result;
    }
    private static String field(String text, String name, String allowed) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^[ \\t]*- "
            + java.util.regex.Pattern.quote(name) + "[ \\t]*(?::|=)[ \\t]*([^\\r\\n]*)\\r?$").matcher(text);
        if (!m.find()) return "missing_or_unrecognized";
        String value = m.group(1).trim();
        if (m.find()) return "ambiguous";
        return value.matches(allowed) ? value : "missing_or_unrecognized";
    }
    static boolean permits(String dump, int uid) {
        if (dump == null || uid < 10000) return false;
        return ownerMatches(dump,"com.whatsapp",uid,false);
    }
    static boolean permitsTelecomOwner(String dump) {
        return ownerMatches(dump,"com.android.server.telecom",1000,true);
    }
    private static boolean ownerMatches(String dump,String pkg,int uid,boolean privileged) {
        if (dump==null) return false;
        int start = dump.indexOf("## Audio mode:");
        if (start < 0 || dump.indexOf("## Audio mode:",start+1)>=0) return false;
        int owner = dump.indexOf("- Mode owner:", start);
        int end = dump.indexOf("- Mode owner stack:", owner);
        if (owner < 0 || end < owner) return false;
        String header = dump.substring(start, owner);
        String block = dump.substring(owner, end);
        return line(header, "Actual mode", "MODE_IN_COMMUNICATION")
            && line(block, "Mode", "MODE_IN_COMMUNICATION")
            && line(block, "Package", pkg)
            && line(block, "Uid", Integer.toString(uid))
            && line(block, "Active", "true")
            && (!privileged || line(block,"Privileged","true"));
    }
    private static boolean line(String text, String key, String value) {
        return value.equals(field(text,key,java.util.regex.Pattern.quote(value)));
    }
}
