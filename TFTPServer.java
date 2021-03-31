

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class TFTPServer
{
	public static final int TFTPPORT = 4970;
	public static final int BUFSIZE = 516;
	private int dataSize = 512;
	public static final String READDIR = "./public/"; //custom address at your PC
	public static final String WRITEDIR = "./public/"; //custom address at your PC
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
							HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ,mode.toString());
						}
						// Write request
						else if (reqtype==OP_WRQ)
						{
							requestedFile.insert(0, WRITEDIR);
							HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ,mode.toString());
						}
						else {
							HandleRQ(sendSocket,requestedFile.toString(),OP_ERR,mode.toString());
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
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode, String mode)
	{

		if (opcode == OP_RRQ) {
			// See "TFTP Formats" in TFTP specification for the DATA and ACK packet contents
			System.out.println("Sending....");
			if (send_DATA_receive_ACK(sendSocket, requestedFile))
				System.out.println("Sending ended successfully!");
			else {
				System.out.println("Lost connection or something");
			}

		} else if (opcode == OP_WRQ) {
			try {
				boolean result = receive_DATA_send_ACK(sendSocket, requestedFile);
			} catch (IOException e) {
				System.err.println("We tried receiving data, it ain't looking good chief.");
				e.printStackTrace();
			}
		} else if (opcode == OP_ERR){
			System.err.println("Invalid request. Sending an error packet.");
			short errorCode = 0;
			send_ERR(sendSocket, errorCode, "Not defined");

		}

	}

	/**
	 * Handles RRQ by sending data to the client and waits for an ACK. if the ACK fails it will try to send 5 times,
	 * if no ACK is received it will return false. It will also check if the requested file exist.
	 * Handles error code 1,2. It also timeout and receive() for the DatagramSocket will block
	 * for only this amount of time. If the timeout expires, a java.net.SocketTimeoutException is raised.
	 * When catching the exception we check if we can resend we continue. if not the it will return false and send
	 * an error message to the client.
	 *
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @return true when the last block of the file is sent. false when error happens.
	 */
	private boolean send_DATA_receive_ACK(DatagramSocket sendSocket , String requestedFile)  {
		short blockNumber = 1;
		//Max number of re-sending attempts
		int transmissionLimit = 5;
		short ackNumber = 0;
		int reSend = 0;

		File file = new File(requestedFile);
		FileInputStream fileInputStream = null;

		//buf[] will contain the bytes read from the file
		byte[] buf = new byte[dataSize];
		//receiveAck[] will contain the ACK messages from the client
		byte[] receiveAck = new byte[BUFSIZE];

		DatagramPacket receiver = new DatagramPacket(receiveAck, receiveAck.length);
		DatagramPacket sender = null;
		try {
			fileInputStream = new FileInputStream(file);

			//check if the requested file is private.txt then send an error 2 and exit the connection
			if (file.getName().equals("private.txt")){
				System.out.println("Access denied to the requested file");
				send_ERR(sendSocket, (short) 2,"Access violation" );
				return false;
			}

		} catch (FileNotFoundException e) {
			send_ERR(sendSocket, (short) 1," File not found");
			System.err.println("The file requested was not found!!");
			e.printStackTrace();
			return false;
		}
		while (true) {
			int byteRead = 0;
			try {
				byteRead = fileInputStream.read(buf);
			} catch (IOException e) {
				System.err.println("something went wrong reading the requested file!");
				e.printStackTrace();
			}
			//Creating a TFTP packet to be used in the sender DatagramPacket
			TFTPPacket tftpPacket = new TFTPPacket(blockNumber, buf, byteRead);
			sender = tftpPacket.dataPacket();
			do {
				reSend++;

				try {
					sendSocket.send(sender);
					System.out.println("Sent block # " + blockNumber);
					sendSocket.setSoTimeout(10);
					sendSocket.receive(receiver);
					ackNumber = tftpPacket.getAckNumber(receiver);
					System.out.println("transmission attempt # " + reSend);
				} catch (SocketTimeoutException e) {
					System.out.println("It timed out and will try to send again");
					System.out.println("transmission attempt # " + reSend);

					if (reSend>=transmissionLimit){
						System.out.println("ACK: "+ackNumber+", Block: "+blockNumber+", Attempt "+reSend+
								", Limit: "+transmissionLimit);
						send_ERR(sendSocket, (short) 0, "Server timeout and tried to send many times!");
						return false;
					}

				}catch (IOException e){
					e.printStackTrace();
					break;
				}

				if (ackNumber == blockNumber) {
					System.out.println("The correct block was received by the client!	 block # " + blockNumber);
					blockNumber++;
					reSend=0;
					break;
				}
				//If an error message was sent from the client we respond with error message and exit the connection
				if (ackNumber == -2){
					send_ERR(sendSocket, (short)0,"Client sent an error..Exiting the connection");
					return false;
				}
			}while (reSend<transmissionLimit);

			if (byteRead < 512) {
				try {
					fileInputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				return true;

			}
		}
	}

	/**
	 *
	 * @param sendSocket (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @return  true when the last block of the file is sent. false when error happens.
	 * @throws IOException
	 */
	private boolean receive_DATA_send_ACK(DatagramSocket sendSocket, String requestedFile) throws IOException {
		System.out.println("Responding to WRQ issued by: "+sendSocket.getInetAddress()+" Using port: "+ sendSocket.getPort());
		boolean lastPacketReceived = false;
		byte[] buffer = new byte[BUFSIZE];
		File file = new File(requestedFile);
		DatagramPacket receiverPacket = new DatagramPacket(buffer, buffer.length, sendSocket.getRemoteSocketAddress());
		DatagramSocket dataSocket = new DatagramSocket(5320);
		System.out.println("Receiving data at port: 5320");

		//Check if file exists, if it does report error #6.
		if(!Files.exists(Path.of(requestedFile)))
			Files.createFile(Path.of(requestedFile));
		else{//file does exist error 6.
			TFTPPacket errorCreator = new TFTPPacket();
			DatagramPacket errorPacket = errorCreator.errorPacket((short) 6,"File Already Exists.");
			errorPacket.setSocketAddress(sendSocket.getRemoteSocketAddress());
			dataSocket.send(errorPacket);
			System.err.println("Sending an already exist error to client.");
			return false;
		}

		//first we need to send the first ack to let the client know where to send the data.
		ByteBuffer byteBuffer = ByteBuffer.allocate(BUFSIZE);
		byteBuffer.putShort((short)4); //op_ac
		byteBuffer.putShort((short)0); //block number
		DatagramPacket firstAck = new DatagramPacket(byteBuffer.array(), 4, sendSocket.getRemoteSocketAddress());
		dataSocket.send(firstAck);

		//now let's start receiving data through the socket.
		while (!lastPacketReceived) {
			dataSocket.receive(receiverPacket);
			buffer = receiverPacket.getData();
			TFTPPacket tftpPacket = new TFTPPacket(buffer,buffer.length);

			//it's time to parse the packet. also write it to the file immediately.

			ByteBuffer wrap = ByteBuffer.wrap(buffer);
			//extracting the opcode and the block number.
			wrap.getShort();
			short blockNum = wrap.getShort();


			//System.out.println("Block number is: " + blockNum);

			// write to the file.
			byte[] data = Arrays.copyOfRange(buffer, 4, receiverPacket.getLength());
			wrap.clear();
			Files.write(Path.of(requestedFile), data, StandardOpenOption.APPEND);

			//System.out.println("Wrote the data to the file.");

			//check if it was the last packet.
			if (receiverPacket.getLength() < 512) {
				lastPacketReceived = true;
				System.out.println("got the last packet.");
			}

			//send ack.
			DatagramPacket ackPacket = tftpPacket.ackPacket(blockNum);
			ackPacket.setSocketAddress(sendSocket.getRemoteSocketAddress());
			dataSocket.send(ackPacket);
			//System.out.println("Ack sent.");

		}

		System.out.println("Done with receiving file.");
		dataSocket.close();
		return Files.exists(Path.of( requestedFile));
	}

	/**
	 *Handles sending error packets
	 *
	 * @param sendSocket (socket used to send/receive packets)
	 * @param errorCode (0,1,2 or 6)
	 * @param errorMessage (String corresponding to what went wrong)
	 */
	private void send_ERR(DatagramSocket sendSocket,short errorCode, String errorMessage) {
		DatagramPacket sender = new TFTPPacket().errorPacket(errorCode,errorMessage);
		System.err.println("error code: " +errorCode);
		try {
			sendSocket.send(sender);
		} catch (IOException e) {
			System.out.println("failed sending error message!");
			e.printStackTrace();
		}

	}
}



