package live.pharmaradio.whatsappai;
/** WebSocket-ordered current-response gate, including cancellation before item creation. */
final class ResponseGate {
    private String current="";private boolean blocked,active;
    synchronized void begin(String id){current=id;blocked=false;active=true;}
    synchronized boolean allow(String id){return !blocked&&!current.isEmpty()&&current.equals(id);}
    synchronized boolean active(){return active&&!blocked;}
    synchronized void done(String id){if(current.equals(id))active=false;}
    synchronized void interrupt(){blocked=true;active=false;}
}
