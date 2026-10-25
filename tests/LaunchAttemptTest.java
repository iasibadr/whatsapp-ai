package live.pharmaradio.whatsappai;
public final class LaunchAttemptTest {
    static int n;
    static void check(boolean v){n++;if(!v)throw new AssertionError("check "+n);}
    static LaunchAttempt armed(){LaunchAttempt a=new LaunchAttempt();check(a.arm(true,100));return a;}
    public static void main(String[] args){
        LaunchAttempt a=new LaunchAttempt();check(!a.arm(false,100));
        a=armed();check(!a.arm(true,101));
        check(!a.observe(0,"","",false,101));
        check(!a.observe(1,"A","DIALING",true,102));
        check(a.observe(1,"A","ACTIVE",true,103));check("A".equals(a.consume()));check(a.consume()==null);
        for(String state:new String[]{"ACTIVE","RINGING","HOLDING","CONNECTING","unknown"}){
            a=armed();check(!a.observe(1,"A",state,true,101));check(a.failed());
        }
        a=armed();a.observe(1,"A","DIALING",true,101);check(!a.observe(1,"B","ACTIVE",true,102));check(a.failed());
        a=armed();check(!a.observe(2,"A","DIALING",true,101));check(a.failed());
        a=armed();check(!a.observe(1,"A","DIALING",false,101));check(a.failed());
        a=armed();check(!a.observe(1,"A","DIALING",true,120100));check(a.failed());
        a=armed();a.observe(1,"A","DIALING",true,101);check(!a.observe(0,"","",false,102));check(a.failed());
        a=armed();a.cancel();check(!a.observe(1,"A","DIALING",true,101));check(a.failed());
        System.out.println("PASS "+n+" launch-attempt checks");
    }
}
