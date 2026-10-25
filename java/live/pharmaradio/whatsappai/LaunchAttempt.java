package live.pharmaradio.whatsappai;
/** One-use launch correlation. Never claims to independently verify a remote number. */
final class LaunchAttempt {
    private boolean armed, failed, ready, consumed;
    private String id;
    private long start,last;
    private String reason="none",lastState="none";
    private int observations;
    synchronized boolean arm(boolean noCalls,long now){
        if(armed||failed||consumed||!noCalls||now<0)return false;
        armed=true;start=last=now;return true;
    }
    synchronized boolean observe(int count,String key,String state,boolean whatsapp,long now){
        if(!armed||failed||consumed)return false;
        observations++;lastState=state!=null&&state.matches("ACTIVE|DIALING|RINGING|HOLDING|DISCONNECTED|CONNECTING|NEW|SELECT_PHONE_ACCOUNT")?state:"unknown";
        if(now<last||now-start>=120000)return reject("timeout_or_clock");last=now;
        if(count==0){if(id!=null)return reject("call_disappeared");return false;}
        if(count!=1||!whatsapp||key==null||!key.matches("[A-Za-z0-9@._-]{1,96}"))return reject("shape_or_owner");
        if(id==null){if(!"DIALING".equals(state))return reject("first_state_"+lastState);id=key;return false;}
        if(!id.equals(key))return reject("call_changed");
        if("DIALING".equals(state)&&!ready)return false;
        if(!"ACTIVE".equals(state))return reject("later_state_"+lastState);
        ready=true;return true;
    }
    synchronized String consume(){if(!armed||!ready||failed||consumed)return null;consumed=true;return id;}
    synchronized boolean failed(){return failed;}
    synchronized String reason(){return reason;}
    synchronized String lastState(){return lastState;}
    synchronized int observations(){return observations;}
    synchronized boolean sawDialing(){return id!=null||ready||consumed;}
    synchronized void cancel(){reject("cancelled");}
    synchronized void cancel(String why){reject(why);}
    private boolean reject(String why){reason=why;failed=true;armed=false;ready=false;id=null;return false;}
}
