import edu.utulsa.unet.RReceiveUDPI;
import edu.utulsa.unet.UDPSocket;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class RReceiveUDP implements RReceiveUDPI {

    private int mode;
    private long modeParameter; // represents the window size in bytes
    private String fileName;
    private long timeout;
    private int port;
    int LEN_HEADER = 6;

    public RReceiveUDP(){
        this.mode = 0;
        this.modeParameter = 256;
        this.fileName = "less_important.txt";
        this.timeout = 1000;
        this.port = 12987;
    }

    public RReceiveUDP(int mode, long modeParameter, String fileName, long timeout, int port){
        this.mode = mode;
        this.modeParameter = modeParameter;
        this.fileName = fileName;
        this.timeout = timeout;
        this.port = port;
    }

    public boolean setMode(int mode){
        this.mode = mode;
        return true;
    }
    public int getMode(){
        return mode;
    }
    public boolean setModeParameter(long n){
        this.modeParameter = n;
        return true;
    }
    public long getModeParameter(){
        return modeParameter;
    }
    public void setFilename(String fname){
        this.fileName = fname;
    }
    public String getFilename(){
        return fileName;
    }
    public boolean setLocalPort(int port){
        this.port = port;
        return true;
    }
    public int getLocalPort(){
        return port;
    }

    public boolean receiveFile(){
        try {
            UDPSocket socket = new UDPSocket(port);
            File fileToReceive = new File(fileName);
            int bufferCapacity = socket.getSendBufferSize();
            FileOutputStream fileWriter;
            //socket.setSoTimeout((int) timeout);

            //System.out.println("Receiving " + fileName + " from " + socket.getInetAddress().toString() +
                    //":" + socket.getPort() + " to " + socket.getLocalAddress().toString() + ":" +
                    //socket.getLocalPort());
            if (mode == 0){
                System.out.println("Using stop-and-wait");
                try {
                    byte[] byteBuffer = new byte[bufferCapacity];
                    fileWriter = new FileOutputStream(fileToReceive);
                    int offset = 0;
                    while (true){
                        DatagramPacket packet = new DatagramPacket(byteBuffer, bufferCapacity);
                        socket.receive(packet);
                        byte[] packetData = packet.getData();
                        byte[] header = retrieveHeader(packetData);
                        byte[] data = retrieveData(packetData);
                        HeaderEncoderDecoder headerDecoder = new HeaderEncoderDecoder(header);
                        System.out.println("Message " + Integer.toString(headerDecoder.getFrameNumber()) + " received.");
                        System.out.println(Integer.toString(headerDecoder.getFrameSize()) + " bytes of actual data from " + packet.getAddress());
                        int frameSize = headerDecoder.getFrameSize();
                        fileWriter.write(data, 0, frameSize);
                        byte[] ack =  ByteBuffer.allocate(4).putInt(headerDecoder.getFrameNumber()).array();
                        socket.send(new DatagramPacket(ack, ack.length, packet.getSocketAddress())); // send back an ack
                        offset += 1;
                   }
                } catch (FileNotFoundException e){
                    System.out.println("File not found");
                } catch (IOException e1){
                    e1.printStackTrace();
                }

            } else if (mode == 1){
                System.out.println("Using sliding window");
                // TODO
            }
        } catch (SocketException e){
            e.printStackTrace();
        }

        return true;
    }

    private byte[] retrieveHeader(byte[] headerAndData){
        byte[] header = new byte[LEN_HEADER];
        for (int i = 0; i < LEN_HEADER; i++){
            header[i] = headerAndData[i];
        }
        return header;
    }

    private byte[] retrieveData(byte[] headerAndData){
        byte[] data = new byte[headerAndData.length - LEN_HEADER];
        System.arraycopy(headerAndData, LEN_HEADER, data, 0, data.length);
        return data;
    }
}
