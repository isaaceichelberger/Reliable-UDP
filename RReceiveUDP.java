import edu.utulsa.unet.RReceiveUDPI;
import edu.utulsa.unet.UDPSocket;

import java.io.*;
import java.net.DatagramPacket;import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.HashSet;

public class RReceiveUDP implements RReceiveUDPI {

    private int mode;
    private long modeParameter; // represents the window size in bytes
    private String fileName;
    private long timeout;
    private int port;
    private int LEN_HEADER = 10;

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

            //System.out.println("Receiving " + fileName + " from " + socket.getInetAddress().toString() +
                    //":" + socket.getPort() + " to " + socket.getLocalAddress().toString() + ":" +
                    //socket.getLocalPort());

            if (mode == 0){
                System.out.println("Using stop-and-wait");
                boolean receiving = false;
                try {
                    fileWriter = new FileOutputStream(fileToReceive);
                    fileWriter.flush();
                    while (true){
                        byte[] byteBuffer = new byte[bufferCapacity];
                        DatagramPacket packet = new DatagramPacket(byteBuffer, bufferCapacity);
                        socket.receive(packet);
                        if (checkExtraneousPacket(packet)){
                            continue; // skip
                        }
                        receiving = true;
                        HashSet<Integer> acknowledgedFrames = new HashSet<>();
                        int previous_flip_bit = 1; // initialize to 1 bc we will start at 0
                        boolean first_entry = true;
                        long start_time = System.nanoTime();
                        while (receiving){
                            // We need to only receive in this loop on the second entry and more, otherwise we
                            // needlessly require retransmission
                            if (!first_entry) {
                                socket.receive(packet);
                            }
                            byte[] packetData = packet.getData();
                            byte[] header = retrieveHeader(packetData);
                            byte[] data = retrieveData(packetData);
                            HeaderEncoderDecoder headerDecoder = new HeaderEncoderDecoder(header);
                            System.out.println("Message " + Integer.toString(headerDecoder.getFrameNumber()) + " received.");
                            System.out.println(Integer.toString(headerDecoder.getFrameSize()) + " bytes of actual data from " + packet.getAddress());
                            // Check if the message was a retransmitted message
                            if (headerDecoder.getFlipBit() == previous_flip_bit){
                                //System.out.println("Message was retransmitted");
                                // we do not want to write this if we have already acknowledged it
                                if (acknowledgedFrames.contains(headerDecoder.getFrameNumber())){
                                    // acknowledge the frame, in case our ack was dropped
                                    byte[] ack =  ByteBuffer.allocate(4).putInt(headerDecoder.getFrameNumber()).array();
                                    socket.send(new DatagramPacket(ack, ack.length, packet.getSocketAddress())); // send back an ack
                                    continue;
                                }
                            }
                            int frameSize = headerDecoder.getFrameSize();
                            fileWriter.write(data, 0, frameSize);

                            // Send Ack
                            byte[] ack =  ByteBuffer.allocate(4).putInt(headerDecoder.getFrameNumber()).array();
                            socket.send(new DatagramPacket(ack, ack.length, packet.getSocketAddress())); // send back an ack

                            // Set up for next frame to be received
                            acknowledgedFrames.add(headerDecoder.getFrameNumber()); // add to acknowledged frames so we don't rewrite
                            previous_flip_bit = headerDecoder.getFlipBit();
                            first_entry = false;

                            // If ending frame
                            if (headerDecoder.getEnding() == 1){
                                long end_time = System.nanoTime();
                                System.out.println("Successfully received " + fileName + " ("
                                        + fileToReceive.length() + " bytes) in " + (end_time - start_time) / 1000000000 + " seconds");
                                receiving = false;
                                fileWriter.flush(); //TODO ASK PAPA IF FILE NEEDS TO BE CLEARED OR WHAT BC LIKE THIS KINDA ISSUE?
                            }
                        }
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

    private boolean checkExtraneousPacket(DatagramPacket packet){
        byte[] packetData = packet.getData();
        byte[] header = retrieveHeader(packetData);
        HeaderEncoderDecoder headerDecoder = new HeaderEncoderDecoder(header);
        if (headerDecoder.getFrameNumber() != 1){
            return true; // extraneous
        } else {
            return false;
        }
    }
}
