import java.math.BigInteger;
import java.nio.ByteBuffer;


public class HeaderEncoderDecoder {

    private int frameNumber;
    private int frameSize;
    private int windowSize;
    private byte[] header = new byte[6];

    public HeaderEncoderDecoder(int frameNumber, int frameSize, int windowSize){
        this.frameNumber = frameNumber;
        this.frameSize = frameSize;
        this.windowSize = windowSize;
        buildHeader();
    }

    public HeaderEncoderDecoder(byte[] header){
        this.header = header;
        decodeHeader();
    }

    private void buildHeader(){
        String frameNumberHex = toHex(frameNumber);
        String frameSizeHex = toHex(frameSize);
        String windowSizeHex = toHex(windowSize);
        String hexString = frameNumberHex + frameSizeHex + windowSizeHex;

        byte[] bytes = new byte[hexString.length() / 2];
        for (int i = 0; i < hexString.length(); i += 2) {
            bytes[i / 2] = hexToByte(hexString.substring(i, i + 2));
        }

        for (int i = 0; i < bytes.length; i++){
            header[i] = bytes[i];
        }


    }

    private void decodeHeader(){
        StringBuffer hexStringBuffer = new StringBuffer();
        for (int i = 0; i < header.length; i++) {
            hexStringBuffer.append(byteToHex(header[i]));
        }
        String hexString = hexStringBuffer.toString();
        String frameNumberHex = hexString.substring(0, 4);
        String frameSizeHex = hexString.substring(4, 8);
        String windowSizeHex = hexString.substring(8, 12);
        this.frameNumber = hexToDecimal(frameNumberHex);
        this.frameSize = hexToDecimal(frameSizeHex);
        this.windowSize = hexToDecimal(windowSizeHex);

    }

    public int getFrameNumber() {
        return frameNumber;
    }

    public void setFrameNumber(int frameNumber) {
        this.frameNumber = frameNumber;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public void setFrameSize(int frameSize) {
        this.frameSize = frameSize;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public byte[] getHeader() {
        return header;
    }

    public void setHeader(byte[] header) {
        this.header = header;
    }

    private static String toHex(int decimal){
        int rem;
        String hex="";
        char hexchars[]={'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        while(decimal>0)
        {
            rem=decimal%16;
            hex=hexchars[rem]+hex;
            decimal=decimal/16;
        }
        while (hex.length() < 4){
            hex = "0" + hex;
        }
        return hex;
    }

    private static byte hexToByte(String hexString) {
        int firstDigit = toDigit(hexString.charAt(0));
        int secondDigit = toDigit(hexString.charAt(1));
        return (byte) ((firstDigit << 4) + secondDigit);
    }

    private static int toDigit(char hexChar) {
        int digit = Character.digit(hexChar, 16);
        if(digit == -1) {
            throw new IllegalArgumentException(
                    "Invalid Hexadecimal Character: "+ hexChar);
        }
        return digit;
    }

    public String byteToHex(byte num) {
        char[] hexDigits = new char[2];
        hexDigits[0] = Character.forDigit((num >> 4) & 0xF, 16);
        hexDigits[1] = Character.forDigit((num & 0xF), 16);
        return new String(hexDigits);
    }

    public static int hexToDecimal(String hex){
        String digits = "0123456789ABCDEF";
        hex = hex.toUpperCase();
        int val = 0;
        for (int i = 0; i < hex.length(); i++)
        {
            char c = hex.charAt(i);
            int d = digits.indexOf(c);
            val = 16*val + d;
        }
        return val;
    }
}
