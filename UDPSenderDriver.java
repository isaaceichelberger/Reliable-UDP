import java.net.InetSocketAddress;

public class UDPSenderDriver {

    public static void main(String[] args){

        RSendUDP sender = new RSendUDP();
        sender.setMode(0);
        //sender.setModeParameter(512);
        sender.setTimeout(10000);
        sender.setFilename("important.txt");
        sender.setLocalPort(23456);
        sender.setReceiver(new InetSocketAddress("localhost", 32456));
        sender.sendFile();
    }
}
