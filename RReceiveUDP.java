import edu.utulsa.unet.RReceiveUDPI;
import edu.utulsa.unet.UDPSocket;

import java.io.*;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
            if (mode == 0){
                System.out.println("Using stop-and-wait");
                boolean receiving = false;
                try {
                    fileWriter = new FileOutputStream(fileToReceive);
                    fileWriter.flush();
                        byte[] byteBuffer = new byte[bufferCapacity];
                        DatagramPacket packet = new DatagramPacket(byteBuffer, bufferCapacity);
                        socket.receive(packet);
                        if (checkExtraneousPacket(packet)){
                            //continue; // skip
                        }
                        receiving = true;
                        HashSet<Integer> acknowledgedFrames = new HashSet<>();
                        int previous_flip_bit = 1; // initialize to 1 bc we will start at 0
                        boolean first_entry = true;
                        System.out.println("Receiving " + fileName + " from " + packet.getAddress().toString() +
                        ":" + packet.getPort() + " to " + socket.getLocalAddress().toString() + ":" +
                        socket.getLocalPort());
                        long start_time = System.nanoTime();
                        while (receiving){
                            // We need to only receive in this loop on the second entry and more, otherwise we
                            // needlessly require retransmission
                            if (!first_entry) {
                                socket.receive(packet);
                            }
                            byte[] packetData = packet.getData();
                            byte[] header = retrieveHeader(packetData);
                            byte[] data = retrieveData(packetData);HeaderEncoderDecoder headerDecoder = new HeaderEncoderDecoder(header);

                            // Check if the message was a retransmitted message
                            if (headerDecoder.getFlipBit() == previous_flip_bit){
                                System.out.println("Message was retransmitted");
                                // we do not want to write this if we have already acknowledged it
                                if (acknowledgedFrames.contains(headerDecoder.getFrameNumber())){
                                    // acknowledge the frame, in case our ack was dropped
                                    byte[] ack =  ByteBuffer.allocate(4).putInt(headerDecoder.getFrameNumber()).array();
                                    socket.send(new DatagramPacket(ack, ack.length, packet.getSocketAddress())); // send back an ack
                                    continue;
                                }
                            } else { // we only want to print receiving if this is the first time it was received
                                System.out.println("Message " + Integer.toString(headerDecoder.getFrameNumber()) + " received.");
                                System.out.println(Integer.toString(headerDecoder.getFrameSize()) + " bytes of actual data from " + packet.getAddress());
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
                                fileWriter.flush();
                            }
                        }
                } catch (FileNotFoundException e){
                    System.out.println("File not found");
                } catch (IOException e1){
                    e1.printStackTrace();
                }

            } else if (mode == 1){
                System.out.println("Using sliding window");
                boolean receiving = false;
                byte[] rws = new byte[(int) modeParameter];
                try {
                    fileWriter = new FileOutputStream(fileToReceive);
                    fileWriter.flush();
                        byte[] byteBuffer = new byte[bufferCapacity];
                        DatagramPacket packet = new DatagramPacket(byteBuffer, bufferCapacity);
                        socket.receive(packet);
                        int lastFrameReceived = 0; // never will receive 0
                        int rws_data_length = 0;
                        int lastBufferedFrame = 0;
                        boolean allFramesBuffered = false;
                        int outstanding_frames = (int) modeParameter / socket.getSendBufferSize();
                        int ending_frame_number = 0;
                        boolean buffering = false;
                        int largestAcceptableFrame = lastFrameReceived + outstanding_frames;
                        System.out.println("Receiving " + fileName + " from " + packet.getAddress().toString() +
                            ":" + packet.getPort() + " to " + socket.getLocalAddress().toString() + ":" +
                            socket.getLocalPort());
                        if (checkExtraneousPacket(packet)){
                            // discard
                        }
                        receiving = true;
                        HashSet<Integer> acknowledgedFrames = new HashSet<>();
                        HashSet<Integer> bufferedFrames = new HashSet<>();
                        boolean first_entry = true;
                        boolean endingFlag = false;
                        boolean noPacket = false;
                        boolean doNothing = false;
                        int endFrameSize = 0;
                        int lastFrameWritten = 0;
                        long start_time = System.nanoTime();
                        while (receiving){
                            // We need to only receive in this loop on the second entry and more, otherwise we
                            // needlessly require retransmission
                            try {
                                if (!first_entry && !doNothing) {
                                    socket.receive(packet);
                                }
                            } catch (SocketTimeoutException e){
                                receiving = false;
                                noPacket = true; // we are done
                                doNothing = true;
                            }
                            byte[] packetData = packet.getData();
                            byte[] header = retrieveHeader(packetData);
                            byte[] data = retrieveData(packetData);

                            HeaderEncoderDecoder headerDecoder = new HeaderEncoderDecoder(header);
                            int frameNumber = headerDecoder.getFrameNumber();
                            int frameSize = headerDecoder.getFrameSize();
                            if (!bufferedFrames.contains(frameNumber)){
                                System.out.println("Message " + Integer.toString(frameNumber) + " received.");
                                System.out.println(Integer.toString(frameSize) + " bytes of actual data from " + packet.getAddress());
                            }
                            if (!bufferedFrames.contains(frameNumber) && !doNothing){
                                // We have frames to buffer
                                buffering = true;
                                bufferedFrames.add(frameNumber);
                                lastBufferedFrame = Math.max(lastBufferedFrame, frameNumber);
                                if (headerDecoder.getEnding() == 1){
                                    // if we are buffering the last frame before others are received, we have to go at full length
                                    int offset;
                                    if (lastFrameReceived == 0){
                                        offset = (frameNumber - 1) * data.length;
                                    } else {
                                        offset = (frameNumber - lastFrameReceived - 1) * data.length;
                                    }
                                    System.arraycopy(data, 0, rws, offset, frameSize);
                                    int temp = rws_data_length;
                                    // data length for writing is the max of the two, since frames can be out of order
                                    rws_data_length = Math.max(temp, offset + frameSize);
                                    endingFlag = true;
                                    endFrameSize = frameSize;
                                    ending_frame_number = frameNumber;
                                    // if this frame is acknowledged as well, we can end
                                    if (acknowledgedFrames.contains(frameNumber)){
                                        buffering = false;
                                    }
                                    lastBufferedFrame = Math.max(lastBufferedFrame, frameNumber);
                                } else if (!doNothing){
                                    int offset;
                                    if (lastFrameReceived == 0){
                                        offset = (frameNumber - 1) * frameSize;
                                    } else {
                                        offset = (frameNumber - lastFrameReceived - 1) * frameSize;
                                    }
                                    System.arraycopy(data, 0, rws, offset, frameSize);
                                    int temp = rws_data_length;
                                    // data length for writing is the max of the two, since frames can be out of order
                                    rws_data_length = Math.max(temp, offset + frameSize);
                                    lastBufferedFrame = Math.max(lastBufferedFrame, frameNumber);
                                }
                            }
                            // Only trigger this when we receive a duplicate frame but the ack was missing
                            if (acknowledgedFrames.contains(frameNumber) && !doNothing){
                                byte[] ack =  ByteBuffer.allocate(4).putInt(lastFrameReceived).array();
                                socket.send(new DatagramPacket(ack, ack.length, packet.getSocketAddress())); // send back an ack
                            }
                            // if we have the previous frame and it's not a duplicate
                            if ((acknowledgedFrames.contains(frameNumber - 1) || frameNumber == 1)){
                                acknowledgedFrames.add(frameNumber); // add to acknowledged frames so we don't rewrite
                                lastFrameReceived = frameNumber;
                                for (int i = frameNumber; i <= lastBufferedFrame; i++) {
                                    if (!bufferedFrames.contains(i)) {
                                        break;
                                    } else {
                                        acknowledgedFrames.add(i);
                                        lastFrameReceived = i;
                                    }
                                }
                                largestAcceptableFrame += lastFrameReceived;
                                if (frameNumber != lastFrameReceived) { // Don't print this acknowledgement every time
                                    System.out.println("Acknowledging packet " + lastFrameReceived);
                                }
                                // Send Ack if we can acknowledge
                                byte[] ack =  ByteBuffer.allocate(4).putInt(lastFrameReceived).array();
                                socket.send(new DatagramPacket(ack, ack.length, packet.getSocketAddress())); // send back an ack
                                allFramesBuffered = true;
                            }
                            // Set up for next frame to be received
                            first_entry = false;
                            if ((allFramesBuffered || rws_data_length == modeParameter) && !doNothing){
                                // if this is the case, we can write to the file
                                int shiftLen;
                                if (endingFlag && lastFrameReceived == ending_frame_number){
                                    shiftLen = (lastFrameReceived - lastFrameWritten - 1) * data.length + endFrameSize;
                                } else {
                                    shiftLen = (lastFrameReceived - lastFrameWritten) * data.length;
                                }
                                lastFrameWritten = lastFrameReceived;
                                fileWriter.write(rws, 0, shiftLen);
                                rws_data_length = 0;
                                byte[] temp = rws;
                                rws = new byte[(int) modeParameter];
                                System.arraycopy(temp, shiftLen, rws, 0, temp.length - shiftLen);
                                allFramesBuffered = false;
                                buffering = false;
                            }


                            if ((endingFlag && !buffering && lastFrameReceived == ending_frame_number) && !doNothing){
                                try {
                                    while (true) {
                                        socket.setSoTimeout((int) timeout);
                                        socket.receive(packet);
                                        packetData = packet.getData();
                                        header = retrieveHeader(packetData);
                                        byte[] ack = ByteBuffer.allocate(4).putInt(lastFrameReceived).array();
                                        socket.send(new DatagramPacket(ack, ack.length, packet.getSocketAddress())); // send back an ack
                                    }
                                } catch (SocketTimeoutException e){
                                    // Sender is done sending
                                    noPacket = true;
                                    first_entry = true;
                                    doNothing = true;
                                }

                            }
                            // If ending frame
                            if (endingFlag && !buffering && lastFrameReceived == ending_frame_number && noPacket){
                                long end_time = System.nanoTime();
                                System.out.println("Successfully received " + fileName + " ("
                                        + fileToReceive.length() + " bytes) in " + (end_time - start_time) / 1000000000 + " seconds");
                                receiving = false;
                                fileWriter.flush();
                            }
                        }
                } catch (FileNotFoundException e){
                    System.out.println("File not found");
                } catch (IOException e1){
                    e1.printStackTrace();
                }
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
