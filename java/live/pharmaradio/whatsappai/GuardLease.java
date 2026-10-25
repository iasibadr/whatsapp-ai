package live.pharmaradio.whatsappai;
/** Private call identity never leaves the helper; a failure permanently closes this lease. */
final class GuardLease {
    private String identity;
    private long until;
    private boolean closed;
    synchronized boolean renew(String key,long now) {
        return renew(key,now,now);
    }
    synchronized boolean renew(String key,long sampledAt,long completedAt) {
        if(closed || key==null || key.isEmpty() || completedAt>=sampledAt+2000
                || identity!=null && (!identity.equals(key) || completedAt>=until)) { closed=true; return false; }
        identity=key; until=sampledAt+2000; return true;
    }
    synchronized boolean valid(long now) {
        if(identity!=null && now>=until) closed=true;
        return !closed && identity!=null;
    }
    synchronized void close() { closed=true; }
}
