import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TFTPPacket {
    private short block;
    private byte[] data;
    private int DataLength;
    private int tftpHeader = 4;
    public final int BUFSIZE = 516;
    public final short OP_DAT = 3;
    public  final short OP_ACK = 4;
    public final short OP_ERR = 5;

    public TFTPPacket(short block,byte[] data,int DataLength) {
        this.block = block;
        this.data=data;
        this.DataLength = DataLength;
    }
    public TFTPPacket(byte[] data,int DataLength) {
        this.data=data;
        this.DataLength = DataLength;
    }

    public TFTPPacket() {
        ;
    }

    /**
     * Create a data packet to be sent and returns it.
     */
    public DatagramPacket dataPacket(){
        ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.putShort(OP_DAT);
        buffer.putShort(block);
        buffer.put(data);
        return new DatagramPacket(buffer.array(),0, tftpHeader+ DataLength);
    }
    /**
     returns the ack number from the incoming datagram
     */
    public short getAckNumber (DatagramPacket ack){
        ByteBuffer buffer = ByteBuffer.wrap(ack.getData());
        short opcode = buffer.getShort();
        //if an error message is received instead of ACK then we return -2 so the server can send an error message.
        if (opcode == OP_ERR) {
            System.out.println("Client sent an error message!! with error code"+buffer.getShort());
            return -2;
        }
        return buffer.getShort();
    }


    /**
     * Creates and ack message to send to the client using the block number that was received
     */
    public DatagramPacket ackPacket (short blockNumber){
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFSIZE);
        byteBuffer.putShort(OP_ACK);
        byteBuffer.putShort(blockNumber);
        return new DatagramPacket(byteBuffer.array(), tftpHeader);
    }

    /**
     * Create an error packet to be sent and returns it.
     */
    public DatagramPacket errorPacket(short ErrorCode, String ErrorMsg){
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFSIZE);
        byteBuffer.putShort(OP_ERR);
        byteBuffer.putShort(ErrorCode);
        byteBuffer.put(ErrorMsg.getBytes());
        byteBuffer.putShort((short) 0);
        System.err.println("Error code: "+ErrorCode+" Error OP code: "+OP_ERR+ " Error Message: "+ErrorMsg);
        return new DatagramPacket(byteBuffer.array(), 5+ErrorMsg.getBytes().length);
    }


}
