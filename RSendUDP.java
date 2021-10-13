import edu.utulsa.unet.RSendUDPI;
import edu.utulsa.unet.UDPSocket;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RSendUDP implements RSendUDPI {

    private int mode;
    private long modeParameter; // represents the window size in bytes
    private String fileName;
    private long timeout;
    private int port;
    private InetSocketAddress receiver;
    private int LEN_HEADER = 10;

    public RSendUDP(){
        // setting defaults
        this.mode = 0;
        this.modeParameter = 256;
        this.fileName = "important.txt";
        this.timeout = 1000;
        this.port = 12987;
        this.receiver = new InetSocketAddress("localhost", port);
    }

    public RSendUDP(int mode, int modeParameter, String fileName, long timeout, int port, InetSocketAddress receiver){
        this.mode = mode;
        this.modeParameter = modeParameter;
        this.fileName = fileName;
        this.timeout = timeout;
        this.port = port;
        this.receiver = receiver;
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
    public boolean setTimeout(long timeout){
        this.timeout = timeout;
        return true;
    }
    public long getTimeout(){
        return timeout;
    }
    public boolean setLocalPort(int port){
        this.port = port;
        return true;
    }
    public int getLocalPort() {
        return port;
    }
    public boolean setReceiver(InetSocketAddress receiver){
        this.receiver = receiver;
        return true;
    }

    public InetSocketAddress getReceiver(){
        return receiver;
    }
    public boolean sendFile(){
        try {
            UDPSocket socket = new UDPSocket(port);
            File fileToSend = new File(fileName);
            int bufferCapacity = socket.getSendBufferSize() - LEN_HEADER; // subtract size of header
            socket.setSoTimeout((int) timeout);

            System.out.println("Sending " + fileName + " from " + socket.getLocalAddress().toString() +
            ":" + socket.getLocalPort() + " to " + receiver.getAddress().toString() + ":" +
            Integer.toString(receiver.getPort()) + " with " + fileToSend.length() + " bytes");

            if (mode == 0){
                System.out.println("Using stop-and-wait");

                try {
                    int offset = 0;
                    int flip_bit = 0;
                    int msgcount = 1;
                    int ending = 0;
                    int frameSize = (int) Math.min(bufferCapacity, fileToSend.length());
                    Path path = Paths.get(fileName);
                    byte[] all_file_data = Files.readAllBytes(path);
                    long start_time = System.nanoTime();
                    while (offset < all_file_data.length){
                        byte[] byteBuffer = new byte[bufferCapacity];
                        // Calculate where loop ends
                        int maximum_loop = (int) Math.min(msgcount * (frameSize), fileToSend.length());
                        // If maximum_loop = file length, the frameSize changes for the last header
                        if (maximum_loop == fileToSend.length()){ // This is the last iteration of the data sending
                            frameSize = (int) fileToSend.length() - offset;
                            ending = 1;
                        }
                        HeaderEncoderDecoder headerEncoder = new HeaderEncoderDecoder(msgcount, frameSize, 1, flip_bit, ending); // creates header
                        byte data[] = combine_byte_arrays(headerEncoder.getHeader(), byteBuffer);
                        for (int i = offset; i < maximum_loop; i++){
                            if (offset >= frameSize){
                                data[i - offset + LEN_HEADER] = all_file_data[i];
                            } else {
                                data[i + LEN_HEADER] = all_file_data[i];
                            }
                        }
                        DatagramPacket packet = new DatagramPacket(data, data.length, receiver);
                        socket.send(packet);
                        System.out.println("Message " + msgcount + " sent with " + Integer.toString((int)
                                frameSize) +
                                " bytes of actual data");
                        validateAck(socket, msgcount, packet, flip_bit);
                        offset += frameSize;
                        msgcount++;
                        flip_bit = (flip_bit == 0 ? 1 : 0); // Change the flip bit
                    }
                    long end_time = System.nanoTime();
                    System.out.println("Successfully transferred " + fileName + " (" + fileToSend.length() +
                            " bytes) in " + (end_time - start_time) / 1000000000 + " seconds");
                } catch (FileNotFoundException e){
                    System.out.println("File not found");
                } catch (IOException e2){
                    e2.printStackTrace();
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

    private byte[] combine_byte_arrays(byte[] b1, byte[] b2){
        byte[] allByteArray = new byte[b1.length + b2.length];
        ByteBuffer buff = ByteBuffer.wrap(allByteArray);
        buff.put(b1);
        buff.put(b2);
        return buff.array();
    }

    private void validateAck(UDPSocket socket, int frameNumber, DatagramPacket packet, int flip_bit) {
        byte[] ackBuffer = new byte[4];
        try {
            DatagramPacket ack = new DatagramPacket(ackBuffer, 4);
            socket.receive(ack);
            int acknowledgedFrameNumber = ByteBuffer.wrap(ack.getData()).getInt();
            if (acknowledgedFrameNumber == frameNumber) {
                System.out.println("Message " + frameNumber + " acknowledged.");
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Message " + frameNumber + " not acknowledged. Resending Message.");
            resendPacket(socket, packet, frameNumber, flip_bit);
        } catch (IOException e1){
            e1.printStackTrace();
        }
    }

    private void resendPacket(UDPSocket socket, DatagramPacket packet, int frameNumber, int flip_bit){
        try {
            socket.send(packet);
            validateAck(socket, frameNumber, packet, flip_bit);
        } catch (SocketTimeoutException e){
            e.printStackTrace();
        } catch (IOException e1){
            e1.printStackTrace();
        }

    }

}
