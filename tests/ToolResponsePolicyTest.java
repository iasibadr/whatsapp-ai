package live.pharmaradio.whatsappai;
public final class ToolResponsePolicyTest {
 public static void main(String[] args){int n=0;
  for(String response:new String[]{"completed","cancelled","failed","incomplete",""})
   for(String item:new String[]{"completed","in_progress",""})
    for(boolean current:new boolean[]{false,true}){
     boolean expected=current&&response.equals("completed")&&item.equals("completed");
     if(ToolResponsePolicy.allow(response,item,current)!=expected)throw new AssertionError();n++;
    }
  System.out.println("PASS "+n+" tool response checks");
 }
}
