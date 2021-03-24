import java.net.DatagramPacket;
import java.nio.ByteBuffer;

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

    /**
     * Create a data packet to be sent and returns it.
     */
    public DatagramPacket dataPacket(){
        ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.putShort(OP_DAT);
        buffer.putShort(block);
        buffer.put(data, 0, DataLength);

        return new DatagramPacket(buffer.array(), tftpHeader+ DataLength);
    }
    /**
     returns the ack number from the incoming datagram
     */
    public short getAckNumber (DatagramPacket ack){
        ByteBuffer buffer = ByteBuffer.wrap(ack.getData());
        short opcode = buffer.getShort();
        if (opcode == OP_ERR) {
            //Do something and close the connection
        }
        return buffer.getShort();
    }

    /**
     * returns the block number of an incoming datagram
     */
    public short getBlockNumber (DatagramPacket packet){
        ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
        short opcode = buffer.getShort();
        if (opcode == OP_ERR) {
            //Do something and close the connection
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


}
