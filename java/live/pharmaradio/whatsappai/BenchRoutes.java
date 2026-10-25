package live.pharmaradio.whatsappai;
final class BenchRoutes {
    static boolean matches(int a,String ah,int b,String bh) {
        return a==25 && b==25 && ah!=null && ah.matches("[a-f0-9]{64}") && ah.equals(bh);
    }
}
