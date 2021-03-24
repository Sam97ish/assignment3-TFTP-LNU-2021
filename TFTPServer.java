

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TFTPServer
{
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	private int dataSize = 512;
	public static final String READDIR = "public\\"; //custom address at your PC
	public static final String WRITEDIR = "/public"; //custom address at your PC
	// OP codes
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	public static void main(String[] args) {
		if (args.length > 0)
		{
			System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		//Starting the server
		try
		{
			TFTPServer server= new TFTPServer();
			server.start();
		}
		catch (SocketException e)
		{e.printStackTrace();}
	}

	private void start() throws SocketException
	{
		byte[] buf= new byte[BUFSIZE];

		// Create socket
		DatagramSocket socket= new DatagramSocket(null);

		// Create local bind point 
		SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

		// Loop to handle client requests 
		while (true)
		{

			final InetSocketAddress clientAddress = receiveFrom(socket, buf);

			// If clientAddress is null, an error occurred in receiveFrom()
			if (clientAddress == null)
				continue;

			final StringBuffer requestedFile= new StringBuffer();
			final StringBuffer mode= new StringBuffer();
			final int reqtype = ParseRQ(buf, requestedFile, mode);

			new Thread()
			{
				public void run()
				{
					try
					{
						DatagramSocket sendSocket= new DatagramSocket(0);

						// Connect to client
						sendSocket.connect(clientAddress);

						/*System.out.printf("%s request for %s from %s using port %d\n",
								(reqtype == OP_RRQ)?"Read":"Write",
								clientAddress.getHostName(), clientAddress.getPort());*/

						// Read request
						if (reqtype == OP_RRQ)
						{
							requestedFile.insert(0, READDIR);
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
						}
						// Write request
						else
						{
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);
						}
						sendSocket.close();
					}
					catch (SocketException e)
					{e.printStackTrace();}
				}
			}.start();
		}
	}

	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 * @param socket (socket to read from)
	 * @param buf (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf)
	{
		// Create datagram packet
		DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
		// Receive packet
		try {
			socket.receive(receivePacket);
		} catch (IOException e) {
			System.err.println("Error while receiving packet from socket with port: " + socket.getPort() + "\n" + e);
		}
		// Get client address and port from the packet
		InetSocketAddress socketAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
		System.out.println("Client's address: " + socketAddress.toString());

		;

		return socketAddress;
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 *
	 * @param buf (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 * */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile, StringBuffer mode) {
		// See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		int opcode = wrap.getShort();
		int index = wrap.position();
		//parsing file name.

		while (index < buf.length) {
			if (buf[index] == 0x00) {
				break;
			} else {
				requestedFile.append((char) buf[index]);
			}
			index++;
		}
		//End of filename, start of mode Notice this will be used for ERROR 0 in problem 4
		if (buf[index] == 0x00) {
			index++;
			while (index < buf.length) {
				if (buf[index] == 0x00) {
					break;
				} else {
					mode.append((char) buf[index]);
				}
				index++;
			}
		}
		System.out.println("REQUESTED: OP:" + opcode +", For file: "
				+ requestedFile.toString() +", Using mode: "+ mode.toString());
		return opcode;
	}

	/**
	 * Handles RRQ and WRQ requests
	 *
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode)
	{
		if(opcode == OP_RRQ)
		{
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			try {
				System.out.println("Sending....");
				send_DATA_receive_ACK(sendSocket, requestedFile);
				System.out.println("Sending ended successfully!");

			} catch (IOException e) {
				System.err.println("Something went wrong when parsing the file!!");
				e.printStackTrace();
			}
		}
		else if (opcode == OP_WRQ)
		{
			try {
				boolean result = receive_DATA_send_ACK(sendSocket,requestedFile);
			} catch (IOException e) {
				System.err.println("We tried receiving data, it ain't looking good chief.");
				e.printStackTrace();
			}
		}
		else
		{
			System.err.println("Invalid request. Sending an error packet.");
			// See "TFTP Formats" in TFTP specification for the ERROR packet contents
			//send_ERR(params);
			return;
		}
	}

	/**
	 To be implemented
	 */
	private boolean send_DATA_receive_ACK(DatagramSocket sendSocket , String requestedFile) throws IOException {
		System.out.println("Responding to RRQ issued by: "+sendSocket.getInetAddress()+" Using port: "+ sendSocket.getPort());
		short blockNumber = 1;
		boolean transferEnd = false;
		boolean lastPacket = false;
		File file = new File(requestedFile);
		FileInputStream fileInputStream = null;
		//For now assuming that the file is 512 or less this is the array that will be sent
		byte[] buf = new byte[dataSize];
		//receiveAck array will be use to get the ack message
		byte [] receiveAck = new byte[BUFSIZE];
		DatagramPacket receiver = new DatagramPacket(receiveAck,receiveAck.length);

		try {
			fileInputStream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			//TODO handle error  1  File not found.
			//for now We will just print an error message for debugging
			System.err.println("The file requested was not found!!");
			e.printStackTrace();
		}
		//TODO timeout functionality. problem 2 In case of a read request.
		int byteRead;
		while (fileInputStream.available()>=0){
			//reading data from the file into the buffer
			byteRead = fileInputStream.read(buf);
			//if this is the last packet i.e less than 512 bytes

			//using the TFTPPacket class to construct a datagram.
			TFTPPacket tftpPacket = new TFTPPacket(blockNumber,buf,byteRead);
			DatagramPacket sender = tftpPacket.dataPacket();
			//sending the datagram
			sendSocket.send(sender);
			System.out.println("Sent block # " + blockNumber);
			// Receiving ack message before continuing
			sendSocket.receive(receiver);
			short ackNumber = tftpPacket.getAckNumber(receiver);
			if (ackNumber==blockNumber){
				System.out.println("The correct block was received by the client!	 block # " + blockNumber);
				blockNumber++;
			}else{
				//TODO implement retransmission later!!!
				System.err.println("ackNumber is not correct");
				return false;
			}
			if (byteRead<512){
				fileInputStream.close();
				return true;

			}
		}
		return true;
	}

		private boolean receive_DATA_send_ACK(DatagramSocket sendSocket, String requestedFile) throws IOException {
			boolean lastPacketReceived = false;
			byte[] buffer = new byte[512];
			DatagramPacket receiverPacket = new DatagramPacket(buffer, buffer.length);

			//first let's create the file using the file name.
			Files.createFile(Path.of(WRITEDIR + requestedFile));

			//now let's start receiving data through the socket.
			while (!lastPacketReceived) {
				sendSocket.receive(receiverPacket);

				buffer = receiverPacket.getData();
				TFTPPacket tftpPacket = new TFTPPacket(buffer,buffer.length);

				//it's time to parse the packet. also write it to the file immediately.

				ByteBuffer wrap = ByteBuffer.wrap(buffer);
				//extracting the opcode and the block number.
				short blockNum = tftpPacket.getBlockNumber(receiverPacket);

				//extracting the data.
				byte[] data = new byte[512];
				wrap.get(data,5,buffer.length);

				// write to the file.
				Files.write(Path.of(WRITEDIR + requestedFile), data, StandardOpenOption.APPEND);

				//check if it was the last packet.
				if (receiverPacket.getLength()-4 < 512) {
					lastPacketReceived = true;
				}

				//send ack.
				DatagramPacket ackPacket = tftpPacket.ackPacket(blockNum);
				sendSocket.send(ackPacket);

			}

			return Files.exists(Path.of(WRITEDIR + requestedFile));
		}
			/*
		private void send_ERR(params)
			{}*/

}



