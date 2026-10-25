package live.pharmaradio.whatsappai;
public class LaunchSnapshotTest {
 static String dump(String state){return "CallsManager:\n  mCalls:\n    [Call id=TC@A, tId=synthetic, state="+state+", tpac=ComponentInfo{com.whatsapp/com.whatsapp}, synthetic, UserHandle{0}, cmgr=null, handle=tel:***, vidst=A, cap=[], prop=[]], voip=true\n  mCallAudioManager:\n";}
 static String empty(){return "CallsManager:\n  mCalls:\n  mCallAudioManager:\n";}
 public static void main(String[] args){
  LaunchAttempt a=new LaunchAttempt();a.arm(true,100);
  if(!TelecomCallPolicy.observeLaunch(a,dump("DIALING"),dump("ACTIVE"),101))throw new AssertionError("valid answer transition lost");
  a=new LaunchAttempt();a.arm(true,100);
  if(TelecomCallPolicy.observeLaunch(a,dump("ACTIVE"),dump("ACTIVE"),101)||!a.failed())throw new AssertionError("preexisting call accepted");
  a=new LaunchAttempt();a.arm(true,100);
  if(TelecomCallPolicy.observeLaunch(a,dump("DIALING"),dump("ACTIVE").replace("TC@A","TC@B"),101)||!a.failed())throw new AssertionError("different call accepted");
  a=new LaunchAttempt();a.arm(true,100);
  if(TelecomCallPolicy.observeLaunch(a,empty(),dump("DIALING"),101)||a.failed()||!a.sawDialing())throw new AssertionError("fresh dialing transition lost");
  if(!TelecomCallPolicy.observeLaunch(a,dump("DIALING"),dump("ACTIVE"),102))throw new AssertionError("fresh dialing did not reach active");
  a=new LaunchAttempt();a.arm(true,100);
  if(TelecomCallPolicy.observeLaunch(a,empty(),dump("ACTIVE"),101)||!a.failed())throw new AssertionError("direct active accepted");
  System.out.println("PASS 6 launch snapshot checks");
 }
}
