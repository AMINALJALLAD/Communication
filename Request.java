import java.net.DatagramSocket;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringTokenizer;

import java.net.DatagramPacket;
import java.net.SocketTimeoutException;

public class Request {
	ArrayList<DatagramPacket> allPacketsSend;
	ArrayList<Integer> allPacketsSendStatus;
	//boolean state;   			  
	DatagramPacket[] windowSending;
	DatagramPacket[] windowReceiving;
	int mySequenceNumber;
	final int routerPortNumber = 3000;
	int clientPortNumber ;
	String receivedMessage;
	int numberOfReceivedMessage;
	int clientSequenceNumber;
	int instanceSequenceNumberReceived;  // to trace the sequence of the packet from client so that it could be acknowledged
	boolean isConnected;
	boolean debug;
	boolean found;
	int estimatedRRT;
	int devRTT;
	int indexOf;
	long SampleRTT;
	DatagramSocket socket;
	DatagramPacket handShakingPacket;
	DatagramPacket ackPacket;
	String response ;
	
	public Request(DatagramSocket socket, boolean debug) {
		mySequenceNumber = 100;
		numberOfReceivedMessage =0;
		clientPortNumber = 0;
		estimatedRRT = 0;
		SampleRTT = 0;
		devRTT = 0;
		indexOf = 0;
		clientSequenceNumber = 0;
		instanceSequenceNumberReceived = 0;
		isConnected = false;
		found = false;
		response = "";
		receivedMessage = "";
		allPacketsSend = new ArrayList<DatagramPacket>();
		allPacketsSendStatus = new ArrayList<Integer>();
		windowSending = new DatagramPacket[3];
		windowReceiving = new DatagramPacket[3];
		handShakingPacket = null;
		ackPacket = null;
		this.debug = debug;
		this.socket = socket;
	}
	
	public void clear() {
		this.socket = null;
		handShakingPacket = null;
		ackPacket = null;
		response = "";
		clientPortNumber = 0;
		allPacketsSend = null;
		allPacketsSendStatus = null;
		debug = false;
		estimatedRRT = 0;
		indexOf = 0;
		numberOfReceivedMessage =0;
		instanceSequenceNumberReceived = 0;
		clientSequenceNumber = 0;
		SampleRTT = 0;
		mySequenceNumber = 100;
		devRTT = 0;
		receivedMessage = null;
		isConnected = false;
		found = false;
		windowSending = null;
		windowReceiving = null;
	}
	
	public void retrieveData(DatagramPacket recPacket) throws Exception {
		byte[] bytes = recPacket.getData();
		int lengthOfInformation = recPacket.getLength();
		int clientSequenceNumberOfReceivedPacket = ArrayManipulation.getIntFromByteArray(bytes, 1, 2); // convert to binary, and then to integer (clientSequence first from sender perspective)
		int mySequenceNumberOfReceivedPacket = ArrayManipulation.getIntFromByteArray(bytes, 3, 2);
		int portNumberFromPacketReceived = ArrayManipulation.getIntFromByteArray(bytes, 9, 2);
		int portNumberFromBuiltInFunction = recPacket.getPort();
		if(debug) {
			System.out.println("The message is " + ArrayManipulation.getByteSubSet(bytes, 11, lengthOfInformation));
			System.out.println("A message  was received");
			System.out.println("Client sequence number expected is " + clientSequenceNumber);
			System.out.println("Client sequence number is " + clientSequenceNumberOfReceivedPacket);
			System.out.println("Port number from built-in function is " + portNumberFromBuiltInFunction);
			System.out.println("por tNumber From Packet Received is " + portNumberFromPacketReceived);
			ArrayManipulation pack = new ArrayManipulation(11);
			pack.putValues(bytes, 0, 11);
			pack.printDebug();
		}				
		if(bytes[0] == 1) { 					// this packet for SYN to establish the connection
			if(!isConnected) {
				clientSequenceNumber = clientSequenceNumberOfReceivedPacket;		 // Here, we know the starting sequence number of server
				clientPortNumber = ArrayManipulation.getIntFromByteArray(bytes, 9, 2);
				if(debug) {
					System.out.println("The connection now is " + isConnected);
					System.out.println("We will receive the total message");
				}
				createResponse(); 	
			}else {
				System.out.println("This is not first time to be received SYN packet. Now it will be dropped and ignored this packet");
			}
												// Start preparing the message to send
		}else if (bytes[0] == 3) {					 // it is acknowledge for a packeted that was sent
			int statusOfReplaying = statusMySequenceNumberReceving(mySequenceNumberOfReceivedPacket);
			if(statusOfReplaying >= 0) {				//exactly what I expect inside the range 
				changePacketStatus(mySequenceNumberOfReceivedPacket, 3);	 // mark the message as acknowledge
				shiftWindowIfNeed(1); 					//shift window if it needs
			} 					// otherwise, just drop it 
			if(debug) {
				System.out.println("statusOfReplaying is " + statusOfReplaying);
				System.out.println("It is an ACK from server for sequence " + mySequenceNumberOfReceivedPacket);
			}
		}else if (bytes[0] == 4) { 				// new packet from the client
			int statusOfReplaying = statusClientSequenceNumberReceiving(clientSequenceNumberOfReceivedPacket);
			boolean isOK = false;
				if(statusOfReplaying == 1000) {			 //exactly what I expect from client
					deliver(bytes, lengthOfInformation); 		// true here to deliver to the application
					isOK = true;
					clientSequenceNumber++; 
					shiftWindowIfNeed(2);
				}else if (statusOfReplaying >=0) { // in the range, but not in the order. It should be dropped
					isOK = true;
					windowReceiving[statusOfReplaying] = recPacket;	
				}
				if(isOK) { 								
					sendAcknowledge(clientSequenceNumberOfReceivedPacket);
				}else {			// if it is not OK, the packet should be dropped, but still
					sendAcknowledge(clientSequenceNumber);  	// an ACK for what we expect. For the pack that is out of the range 
				}
				if(debug) {
					System.out.println("statusOfReplaying is " + statusOfReplaying);
					System.out.println("It is a new packet form server with sequence " + mySequenceNumberOfReceivedPacket);
					System.out.println("We send an ACK to server " + isOK);
				}
		}
	}
	
	/*
	 * isACK. In order to determine the state of receiving ArrayManipulation. Either an ACK for a response that server send
	 *   or for receiving a message from client
	 * // receiving a message -> false    ,  receiving an ACK for a response packet -> true
	 */
	public void receivePackets(boolean isACK) throws IOException, Exception{ // we should take into account the timer exception
		byte[] bytes = new byte[1024];
		boolean isAck = isACK;
		DatagramPacket recPacket = new DatagramPacket(bytes, bytes.length);
		boolean timeOut = false, firstIteration = true;
		while(true) {
			System.out.println("******************************************");
			try {
				socket.receive(recPacket);
				if(debug) {
					String s = new String(ArrayManipulation.getByteSubSet(recPacket.getData(), 11, recPacket.getLength()));
					System.out.println("The length of the recPacket is " + recPacket.getLength());
					System.out.println("The length of the recPacket byte array is " + recPacket.getData().length);
				}
				System.out.println("Inside");
			}catch(SocketTimeoutException e) {
				if(debug) {
					System.out.println("Time is expired. It should be sent again");
				}		//****** there is a case when no more time
				timeOut = true;
				if(!isAck) {				// time out for waiting for a request message
					return; 				//	connection will suspend
				}else {
					sendPackets(10);		 // time out send it again
				}
			}
			
			if(!timeOut) {			//still there is a connection 
				System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
				retrieveData(recPacket);
			}else {				//time has finished
				if(!isAck) {						// in lifetime of receiving messages from client
					break;									// will suspend as it excceds time of connection
				}else {						// in lifetime of receiving ACK from client
					sendPackets(10);
				}
			}
			if(!isAck) {			// in lifetime of receiving messages from client
				if( isFirstPacketRceivedFromClient()) {
					if (!hasMorePacketFromClient() ) { 	// receive the first packet from the server and wait for more 
						isAck = true;
						processingMessage(receivedMessage);
					}
				}
			}
			if(firstIteration) {
				socket.setSoTimeout(1000);			// after this period, the connection will disconnect. Give the client 1.5 seconds to receive the whole message 
				firstIteration = false;
			}
		}
	}
	
	public void createHeaders(ArrayManipulation packet, int typeOfPacket) throws IOException{
		packet.putValues((byte) typeOfPacket, 0); 					// add the type of packet see ArrayManipulation class for more info
		byte[] mySequence = packet.getBytes(getMySequenceNumber(typeOfPacket),2);
		packet.putValues(mySequence, 1, 2); 						// add my sequence at position 1 with length of 2
		byte[] clientSequenceNumber = packet.getBytes(getClientSequenceNumber(typeOfPacket),2);
		packet.putValues(clientSequenceNumber, 3, 2); 						// add server sequence at position 3 with length of 2
		InetAddress ip = InetAddress.getByName("localhost");
		byte[] byteAdress = ip.getAddress();
		packet.putValues(byteAdress, 5, 4); 						// add ip at position 5 with length of 4
		byte[] clientPortNumberArr = packet.getBytes(clientPortNumber, 2);
		packet.putValues(clientPortNumberArr, 9, 2); 					// add server port number at position 9 with length of 2	
		if(debug) {		
			System.out.println("createHeaders from server side");
			packet.printDebug();
		}
	}
	
	public int getMySequenceNumber(int typeOfPacket) {
		if((typeOfPacket == 4) || (typeOfPacket == 3))  {		// no increment the sequence number at each iteration
			return mySequenceNumber++;			
		}else {// typeOfPacket == 2 
			return mySequenceNumber;  // give the client the initial sequence number at first time, or where I reach 
		}
	}
	
	public static ArrayList<String> getPaths(String urlPath) {
		ArrayList<String> paths = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(urlPath);
		String token = "";
			while(st.hasMoreTokens()) {	
				if(urlPath.length() > 1) {
					token = st.nextToken("/");
					paths.add(token);
				}else {
					token = st.nextToken();
				}
			}
		return paths;
	}
	
	public int getClientSequenceNumber(int typeOfPacket) {
		int serverSeq = -1;
		if( (typeOfPacket == 2) || (typeOfPacket == 4) ) { // zero server sequence number as we don't know the value
			serverSeq =  clientSequenceNumber;
		}else  {
			serverSeq =  instanceSequenceNumberReceived; // to acknowledge last packed received or the previous packet that is already received
		}
		return serverSeq;
	}
	
	public int statusClientSequenceNumberReceiving(int clientSequenceNumberReceived) { //here
		int status = -1;
		if(clientSequenceNumberReceived == clientSequenceNumber) { 		// which exactly I expect sequence No.
			status =  1000;
		}else {												 // I have to buffer it if it is in the range
			if ( (clientSequenceNumberReceived > clientSequenceNumber) &&
			(clientSequenceNumberReceived < clientSequenceNumber + 3) ){ 
				status = clientSequenceNumberReceived - clientSequenceNumber; // get the index inside the array, the expected range here {0, 2}
			} 			// away from my window size, so it should be dropped			
		}
		return status;
	}
	
	public int statusMySequenceNumberReceving(int mySequenceNumberReceved) {
		int status = -1; 
	//	int sequenceNumberOfFirstPackedInSendingWindow = getSequenceOfFirstPacketInWindow(1);   // check in sendingWindow
		if(mySequenceNumberReceved == mySequenceNumber) { 	// which I expect sequence
			status =  1000;
		}else { // I have to buffer it if it is in the range
			if( (mySequenceNumberReceved > mySequenceNumber) &&
			(mySequenceNumberReceved < mySequenceNumber + 3) ){
				status = mySequenceNumberReceved - mySequenceNumber;
			}// away from my window size I have to drop it
		
		}
		return status;
	}
	
	public void deliver(byte[] data, int lengthOfData)throws Exception {
		String totalCharAsString = ArrayManipulation.getIntAsChar(data, 11); 				// we take the total number of character as string 
		int totalChar = Integer.parseInt(totalCharAsString), escapChar = 0;			// convert string to an integer
		int lengthOfThisMessage = lengthOfData - 11;
		byte[] message = null;
		if(debug) {
			System.out.println("From deliver");
			System.out.println("lengthOfThisMessage is " + lengthOfThisMessage);
			System.out.println("totalCharAsString is " + totalCharAsString);
			System.out.println("totalChar is " + totalChar);
		}
		int sizeOfMessageArray=0, startReadingData = 11;
		if(!isFirstPacketRceivedFromClient()) {  
			numberOfReceivedMessage = (totalChar /1013) + 1;  // We need to know how many messages are there
			escapChar = totalCharAsString.length() + 1;					 // // As this value to discover how many character are there + space
			if(debug) {
				System.out.println("escapChar is " + escapChar);
				System.out.println("numberOfReceivedMessage is " + numberOfReceivedMessage);
			}
			if(isOnlyOnePacketFromClient()) {
				sizeOfMessageArray = lengthOfThisMessage - escapChar;
				startReadingData += escapChar;
			}else {
				escapChar = totalChar + 1; // // As this value to discover how many character are there + space
				sizeOfMessageArray = 1013 - escapChar;
				startReadingData += escapChar;
			}
			message = new byte[sizeOfMessageArray];
			message = ArrayManipulation.getByteSubSet(data, startReadingData, sizeOfMessageArray);
		}else {   					// This is not the first message that is delivered from server
			if (isOnlyOnePacketFromClient()) {
				sizeOfMessageArray = lengthOfThisMessage;
			} else {
				sizeOfMessageArray = 1013;
			}
			message = new byte[sizeOfMessageArray];
			message = ArrayManipulation.getByteSubSet(data, startReadingData, sizeOfMessageArray);
		}
		receivedMessage += new String(message);
		if(debug) {
			System.out.println("The receivedMessage is " + receivedMessage);
		}
		numberOfReceivedMessage--;
	}

	public void processingMessage(String message) throws Exception {
		String requestType = "", token = "", pathName= "";
		int ind =0;
		StringTokenizer st = new StringTokenizer(message);
		while(st.hasMoreTokens()) {
			token = st.nextToken();
			if(ind == 0) {
				requestType = token;
			}else if (ind ==1) {
				pathName = token;
			}else {
				break;
			}
			ind++;
		}
		ArrayList<String> paths = getPaths(pathName);
		int length = pathName.length() + requestType.length() + 1; // 1 refers to the space between pathName and requestType
		if(requestType.equals("POST")) {
			length++;                   					// here is the space between pathName and bodyContents
		}
		String bodyContent = message.substring(length);
		doAction(paths, requestType, bodyContent);
	}
	
	public void doAction(ArrayList<String> paths, String requestType, String bodyContent) throws Exception {
		File rootFile = null, wanted = null;
		String absouteUrl = "C:\\Users\\aalja\\OneDrive\\Desktop\\COMPP445\\A2_comp445\\LocalHost";
		rootFile = new File(absouteUrl);
		wanted = findFileDirectory(rootFile,paths,0);
		System.out.println("requestType is " + requestType);
		System.out.println("requestType length is " + requestType.length());
		if(requestType.toUpperCase().strip().equals("GET")) {
			Get getRequest = new Get(debug); 
			response = getRequest.doAction(wanted,found, rootFile, paths);
		}else {
			Post postRequest = new Post(debug);
			response = postRequest.doAction(wanted, found, rootFile, bodyContent, indexOf, paths);
		}
		createResponse();
	}
	
	public File findFileDirectory(File rootFile, ArrayList<String> target, int index) {
		File wanted = null;			//System.out.println("New calling " + index);
		indexOf =index ; 
		if(index >0) { 					// to tell that there is at least one of the path found
			wanted = rootFile;
		}
		if(target.size() > 0) {
			for(File fileTemp:rootFile.listFiles()) {				//		System.out.println("target.get(index) is " + target.get(index));System.out.println("fileTemp.getName() " + fileTemp.getName());
				if(fileTemp.getName().equals(target.get(index))) {									//	System.out.println("Good we find");System.out.println(fileTemp.getName());
					if(fileTemp.isDirectory()) {				//System.out.println("It is a directry, and index is " + index);System.out.println("It is a directry, and index is " + index);
						++index;
						if(target.size() > index) { 			// more directry
							wanted = findFileDirectory( fileTemp, target, index);
						}else {					// no more path string
							found =true; 
							wanted = fileTemp;												//System.out.println(" ---------------finally -----------");System.out.println("It is a directery");System.out.println(" ---------------finally -----------");
						}
						
					}else {
						wanted = fileTemp;
						found =true;															//System.out.println(" ---------------finally -----------");System.out.println("It is a file, and index is " + index);System.out.println(" ---------------finally -----------");indexOf =index;
					}
					break;
				}else {																						//	System.out.println("************");
				}
				
			}
		}
		if(debug) {
			System.out.println("Found the file "+ wanted);
		}
		return wanted;
	}
	
	public void sendAcknowledge(int clientSequenceNumberOfReceivedPacket) throws Exception {
		instanceSequenceNumberReceived = clientSequenceNumberOfReceivedPacket; // save this value to ack
		ArrayManipulation pack = createPackets(3, "");
		ackPacket = new DatagramPacket(pack.getBytesArr(), pack.getBytesArr().length, InetAddress.getByName("localhost"), routerPortNumber);
		sendPackets(3);		// send an ack
	}
	
	/*
	 * typeOfPacket is an integer to determine the packet type 
	 * if typeOfPacket is 2, then it is "SYN-ACK"
	 * it typeOfPacket is  3, then it is "ACK" that means only acknowledge packet
	 * it typeOfPacket is  4, then it is "PCK" that means real message packet
	 * 
	 */
	public ArrayManipulation createPackets(int typeOfPacket, String payLoadSub)throws IOException {
		ArrayManipulation pack = new ArrayManipulation((11 + payLoadSub.length()));	 // where 11 refers to the headers byte 
		createHeaders(pack, typeOfPacket);                    		//+ length of the payload
		if(typeOfPacket == 4) { 		// create a message packet
			byte[] payLoadArr = payLoadSub.getBytes();
			pack.putValues(payLoadArr, 11, payLoadArr.length);
		} 								// Otherwise, hand-shaking connection, no data is required
		return pack;
	}
	
	/*
	 * typeOfSending is an integer to determine the sending-packet type 
	 * if type == 1, then it is the first time
	 * if type == 10, then send the packet as time out. Can't be waited anymore
	 * if type == 3, an acknowledge for a packet from the server should be received
	 */
	public void sendPackets(int typeOfSending) throws  Exception{ // true if ACK packet is sending
		int i = 0, instanceSequenceNumber = 0, whichIterstionSendTimeOut = -1;
		if(debug) {
			System.out.println("The type of sending is " + typeOfSending);
			System.out.println("if type == 2, then it is the first time to acknowlw");
			System.out.println("if type == 10, then send the packet as time out");
			System.out.println("if type == 3, then it is an acknowledge");
			System.out.println("I there a connection " + isConnected);
		}
		if(!isConnected) { 		// sending a packet to establish the connection.
			socket.send(handShakingPacket);
			socket.setSoTimeout(2000);			// after this time I will disconnect if there is no  connection
			SampleRTT = System.currentTimeMillis(); // save the send time
			isConnected =true;
			if(debug) {
				System.out.println("Don't care about the type of sending here as long as isConnection is still false");
				System.out.println("The connection now is "+ isConnected);
			}	
		}else {  
			if( (typeOfSending == 1) || (typeOfSending == 10) ) { 
				while(i < windowSending.length) { 
					instanceSequenceNumber =  getSequenceOfSpecificIndexInWindow(1, i);  // i is the index
					if(windowSending[i] != null) {		 // in case the window is not full 
						if((typeOfSending == 1) && (getStatusOfWindowAtIndexOf(instanceSequenceNumber) != 2)) {    // send the packet for first time and not already sent
							socket.send(windowSending[i]);
							changePacketStatus(instanceSequenceNumber, 2);			// mark the packet as is send
						}else { 		
							if(debug) {
								System.out.println("Time out, send unacknowledged message");
							}
							if (getStatusOfWindowAtIndexOf(instanceSequenceNumber) != 3) {
								socket.send(windowSending[i]);
								whichIterstionSendTimeOut = i;
							}
						}
					}
					if(i == whichIterstionSendTimeOut) {
						socket.setSoTimeout(500);  			//set the time of sending when first packet was sent
						SampleRTT = System.currentTimeMillis(); // save this time
					}
				}
			}else { 			// if it is ACK. Only one ACK at a time
				socket.send(ackPacket);
			}
		}
//		if(typeOfSending != 3) { 	// As if it is ACK, nothing should be in return. In other words, sending ArrayManipulation either at the first time or as time out trigger 
//			receiveArrayManipulation(false);
//		}
	}
	
	public void createResponse() throws Exception {
		ArrayManipulation pack = null;
		if(!isConnected) { 				// at handShaking
			pack = createPackets(2, "");
			handShakingPacket = new DatagramPacket(pack.getBytesArr(), pack.getBytesArr().length, InetAddress.getByName("localhost"), routerPortNumber);
	//		sendArrayManipulation(10000); 			// any number is valid as long as isConnected is still false
		}else {
			response = (response.length() + " " + response); 	// length of the message is added to the message itself
			int numOfMessage = (response.length() / 1013) + 1;
			if(debug) {
				System.out.println("Number of message that will be sent is " + numOfMessage);
				System.out.println("Number of character in the message that will be sent is " + numOfMessage);
			}
			int start =0, end = 1013, interation = 0;
			if(numOfMessage == 1) { // only first time because we take all the message
				end =response.length() ;
			}
			do {
				if(interation != 0) { // doesn't consider first iteration as iteration == 0 we need to change start and end at each iteration
					start = end;
					if(numOfMessage != 1) { // escape last iteration
						end += 1013;
					}
				}
				if(numOfMessage == 1 && (interation != 0)) { // last iteration but more than one message
					start = end;
					end =response.length();
				}
				DatagramPacket sePacket= null;
				String payLoadSub = response.substring(start, end); 
				pack = createPackets(4, payLoadSub);	 // mySequencNumber increment each time
				sePacket = new DatagramPacket(pack.getBytesArr(), pack.getBytesArr().length, InetAddress.getByName("localhost"), routerPortNumber);
				allPacketsSend.add(sePacket);
				allPacketsSendStatus.add(0); 		// mark this packet as unused
				if(debug) {
					System.out.println("Message number " + (interation + 1) + " was created");
					pack.printDebug();
				}
				if(interation < 3) { 		// 3 refers to windowSending.length
					windowSending[interation] = sePacket;
					changePacketStatus(interation, 1);		 // mark this packet as used
				}
				if(debug) {
					System.out.println("We ctreate a new packet and add it to the list");
					pack.printDebug();
				}
				numOfMessage--;
				interation++;
			}while(numOfMessage > 0);
			if(debug) {
				System.out.println("Number of ArrayManipulation that were send is " + mySequenceNumber);
				System.out.println("Number of ArrayManipulation that were made is " + allPacketsSendStatus.size());
				System.out.println("The status of all of the ArrayManipulation");
				System.out.println(Arrays.toString(allPacketsSendStatus.toArray()));
			}
		}
		if(debug) {
			System.out.println("We print the status of all of the Packets");
			System.out.println(Arrays.toString(allPacketsSendStatus.toArray()));
		}
		sendPackets(1);  // send the message or the SYN_ACK for 1st time
	}
	
	public void shiftWindowIfNeed(int typeOfIntersection) throws Exception  {
		boolean isWindowFull = isWindowFull(typeOfIntersection);
		boolean thereMorePacketToProcess = isThereMorePacketsToProcess(typeOfIntersection);
		boolean isFirstPacketAcknowldged = isFirstPacketAcknowldged(typeOfIntersection);
		if(debug) {
			System.out.println("typeOfIntersection is " + typeOfIntersection);
			System.out.println("Is the window full ? " + isWindowFull );
			System.out.println("Is There More Packet To process ? " + thereMorePacketToProcess );
			System.out.println("Is First Packet Acknowldged ? " + isFirstPacketAcknowldged);
		}
			if( isWindowFull && thereMorePacketToProcess && isFirstPacketAcknowldged ){
				shift(typeOfIntersection);
			}
	}
	
	public void shift(int typeOfIntersection) throws Exception {
		if(debug) {
			System.out.print("We will shift to the right in the ");
		}
		if(typeOfIntersection == 1) { // if typeOfIntersection == 1 shifting the windowSending
			for(int i=0 ; i < 2 ; i++) {
				windowSending[i] = windowSending[i+1];
			}
			int sequenecNumberOfFirstPacketAfterWindow = getSequenceOfSpecificIndexInWindow(1,2) + 1;		// we get the packet is directly after the last one in the window    
			windowSending[2] = allPacketsSend.get(sequenecNumberOfFirstPacketAfterWindow);				
			socket.send(windowSending[2]);
			socket.setSoTimeout(500);
			shiftWindowIfNeed(typeOfIntersection);
		}else { // if typeOfIntersection == 2 shifting the windowReceiving
			for(int i=0 ; i < 2 ; i++) {
				windowReceiving[i] = windowReceiving[i+1];
			}
			// Don't care for the last index as it is already taken to the index 1
		}
	}
	
	public boolean isThereMorePacketsToProcess(int typeOfIntersection) {
		if(typeOfIntersection == 1) { 					// if typeOfIntersection == 1 shifting the windowSending
			return (mySequenceNumber < allPacketsSendStatus.size() );
		}else { 							// if typeOfIntersection == 2 shifting the windowReceiving
			return (numberOfReceivedMessage > 0);
		}
	}
	
	/*
	 * The status of the first packet is marked as received
	 */
	public boolean isFirstPacketAcknowldged(int typeOfIntersection) {
			return ( getSequenceOfFirstPacketInWindow(typeOfIntersection) == 3);
	}
	
	public int getIndexOfLastPackedInWindow(int typeOfIntersection) {
		int indexOfLastPacket = -1;
		for(int i=0 ; i< 3 ; i++) { // As the window length is 3
			if(typeOfIntersection == 1) { // windowSending 
				if(windowSending[i] != null) { 
					indexOfLastPacket = i; 
				}
			}else if(typeOfIntersection == 2) { // windowReceinging 
				if(windowReceiving[i] != null) { 
					indexOfLastPacket = i;
				}
			}
		}
		return indexOfLastPacket; 
	}
	
	public int getSequenceOfFirstPacketInWindow(int typeOfIntersection) {
		return getSequenceOfSpecificIndexInWindow(typeOfIntersection, 0); // at index zero
	}
	
	public int getSequenceOfSpecificIndexInWindow(int typeOfIntersection, int index) {
		int returnValue = -1;
		if(typeOfIntersection == 1) {				 // if typeOfIntersection == 1 shifting the windowSending
			if(windowSending[index] != null) {
				returnValue = ArrayManipulation.getIntFromByteArray(windowSending[index].getData(),1 , 2);
			}
		}else {  			// if typeOfIntersection == 2 shifting the windowReceiving
			if(windowReceiving[index] != null) {
				returnValue = ArrayManipulation.getIntFromByteArray(windowReceiving[index].getData(),1 , 2);
			}else {
				
			}	
		}
		return returnValue;
	}
	
	public boolean isWindowFull(int typeOfIntersection) {
		int lastIndex = getIndexOfLastPackedInWindow(typeOfIntersection);
		return (lastIndex == 2);
	}
	
	public boolean isFirstPacketRceivedFromClient() {
		return (!receivedMessage.equals(""));
	}
	
	public boolean isAnyBufferedReceivedPacket() {
		boolean noBufferPacket = true;
		for(int i=0; i< 3 ; i++) {
			if(windowReceiving[i] != null) {
				noBufferPacket = true;
				break;
			}
		}
		return noBufferPacket;
	}
	
	public boolean noMorePacketRceivedFromClient() {
		return(numberOfReceivedMessage == 0);
	}
	
	/*
	 * // to check weather this packet is the last one from the client
	 */
	public boolean isOnlyOnePacketFromClient() {
		return(numberOfReceivedMessage == 1);
	}
	
	/*
	 * // to check weather this packet is the last one from the ser
	 */
	public boolean hasMorePacketFromClient() {
		return(numberOfReceivedMessage > 1);
	}
	
	/*
	 * To trace the status of packet
	 * the range of statusOfPacket as the following
	 * 0-> not used,  	1 -> used in window, 		2-> sent, 		 3-> acknowledge
	 * sequenceNumber here is as the index of the arrayList
	 */
	public void changePacketStatus(int sequenceNumber, int statusOfPacket) {
		allPacketsSendStatus.set(sequenceNumber, statusOfPacket);
	}
	
	public int getStatusOfWindowAtIndexOf(int index) {
		return allPacketsSendStatus.get(index);		
	}
	
}
