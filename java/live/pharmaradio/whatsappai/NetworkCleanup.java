package live.pharmaradio.whatsappai;

final class NetworkCleanup {
    static void start(Runnable cleanup) {
        new Thread(cleanup, "voice-network-cleanup").start();
    }
}
