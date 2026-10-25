package live.pharmaradio.whatsappai;
final class ToolResponsePolicy {
 static boolean allow(String responseStatus,String itemStatus,boolean current){
  return current&&"completed".equals(responseStatus)&&"completed".equals(itemStatus);
 }
}
