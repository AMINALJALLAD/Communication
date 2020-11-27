package Send;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
//import packetForming.ArrayManipulation;
import java.net.SocketTimeoutException;

public class Request {
	String pathName;
	String payLoad;
	ArrayList<DatagramPacket> allPacketsSend;
	ArrayList<Integer> allPacketsSendStatus;
	DatagramPacket[] windowSending;
	DatagramPacket[] windowReceiving;
	int mySequencNumber;
	final int routerPortNumber = 3000;
	String receivedMessage;
	int numberOfReceivedMessage;
	int serverSequenceNumber;
	int instanceSequenceNumberReceived;  // to trace the sequence of the packet from server so that it could be acknowledged
	boolean isConnected;
	boolean debug;
	boolean firstTime;
	int estimatedRRT;
	int devRTT;
	long SampleRTT;
	DatagramSocket socket;
	DatagramPacket handShakingPacket;
	DatagramPacket ackPacket;
	
	public Request(String payLoad, String fileName, DatagramSocket socket, boolean debugging) {
		mySequencNumber = 0;
		numberOfReceivedMessage =0;
		estimatedRRT = 0;
		SampleRTT = 0;
		devRTT = 0;
		serverSequenceNumber = 0;
		instanceSequenceNumberReceived = 0;
		isConnected = false;
		firstTime = true;
		this.pathName = fileName;
		this.payLoad = payLoad;
		receivedMessage = "";
		allPacketsSend = new ArrayList<DatagramPacket>();
		allPacketsSendStatus = new ArrayList<Integer>();
		windowSending = new DatagramPacket[3];
		windowReceiving = new DatagramPacket[3];
		handShakingPacket = null;
		ackPacket = null;
		this.debug = debugging;
		this.socket = socket;
	}
	
	public void clear() {
		this.socket = null;
		handShakingPacket = null;
		ackPacket = null;
		firstTime = true;
		allPacketsSend = null;
		allPacketsSendStatus = null;
		debug = false;
		estimatedRRT = 0;
		numberOfReceivedMessage =0;
		instanceSequenceNumberReceived = 0;
		serverSequenceNumber = 0;
		SampleRTT = 0;
		mySequencNumber = 0;
		devRTT = 0;
		receivedMessage = "";
		isConnected = false;
		pathName = "";
		this.payLoad = "";
		windowSending = null;
		windowReceiving = null;
	}
	
	public void createMessage() throws Exception {
		ArrayManipulation pack = null;
		if(!isConnected) { // at handShaking
			pack = createArrayManipulation(1, "");
			handShakingPacket = new DatagramPacket(pack.getBytesArr(), pack.getBytesArr().length, InetAddress.getByName("localhost"), routerPortNumber);
		}else {
			payLoad = (payLoad.length() + " " + payLoad); 	// length of the message is added to the message itself
			int numOfMessage = (payLoad.length() / 1013) + 1;
			if(debug) {
				System.out.println("Number of message that will be sent is " + numOfMessage);
				System.out.println("Number of character in the message that will be sent is " + numOfMessage);
			}
			int start =0, end = 1013, interation = 0;
			if(numOfMessage == 1) { // only first time because we take all the message
				end =payLoad.length() ;
			}
			do {
				if(interation != 0) { // doesn't consider first iteration as iteration == 0 we need to change start and end at each iteration
					start = end;
					if(numOfMessage != 1) { // escape last iteration
						end += 1013;
					}else {
						end =payLoad.length();	// last iteration
					}
				}
				DatagramPacket sePacket= null;
				String payLoadSub = payLoad.substring(start, end); 
				pack = createArrayManipulation(4, payLoadSub);	 // mySequencNumber increment each time
				sePacket = new DatagramPacket(pack.getBytesArr(), pack.getBytesArr().length, InetAddress.getByName("localhost"), routerPortNumber);
				allPacketsSend.add(sePacket);
				allPacketsSendStatus.add(0); 		// mark this packet as unused
				if(debug) {
					System.out.println("Message number " + (interation + 1) + " was created");
					pack.printDebug();
				}
				if(interation < 3) { 		// 3 refers to windowSending.length
					windowSending[interation] = sePacket;					// iteration here it refers to sequence number
					changeArrayManipulationtatus(interation, 1);		 // mark this packet as used
				}
				if(debug) {
					System.out.println("We ctreate a new packet and add it to the list");
					pack.printDebug();
				}
				numOfMessage--;
				interation++;
			}while(numOfMessage > 0);
			if(debug) {
				System.out.println("Number of ArrayManipulation that were send is " + mySequencNumber);
				System.out.println("Number of ArrayManipulation that were made is " + allPacketsSendStatus.size());
				System.out.println("The status of all of the ArrayManipulation");
				System.out.println(Arrays.toString(allPacketsSendStatus.toArray()));
			}
		}
		if(debug) {
			System.out.println("We print the status of all of the ArrayManipulation");
			System.out.println(Arrays.toString(allPacketsSendStatus.toArray()));
		}
		sendPackets(1);  // send the message for 1st time
	}
	
	/*
	 * typeOfPacket is an integer to determine the packet type 
	 * if typeOfPacket is 1, then it is "SYN"
	 * it typeOfPacket is  3, then it is "ACK" that means only ack packet
	 * it typeOfPacket is  4, then it is "PCK" that means real message packet
	 * 
	 */
	public ArrayManipulation createArrayManipulation(int typeOfPacket, String payLoadSub)throws IOException {
		ArrayManipulation pack = new ArrayManipulation((11 + payLoadSub.length()));	 // where 11 refers to the headers byte 
		ctreateHeaders(pack, typeOfPacket);                    		//+ length of the payload
		if(typeOfPacket == 4) { 		// create a message packet
			byte[] payLoadArr = payLoadSub.getBytes();
			pack.putValues(payLoadArr, 11, payLoadArr.length);
		} 								// Otherwise, hand-shaking connection or an ACK, no data is required
		return pack;
	}
	
	
	
	/*
	 * typeOfSending is an integer to determine the sending-packet type 
	 * if type == 1, then it is the first time
	 * if type == 10, then send the packet as time out. Can't be waited anymore
	 * if type == 3, an acknowledge for a packet from the server should be received
	 */
	public void sendPackets(int typeOfSending) throws  Exception{ // true if ACK packet is sending
		int i = 0, instanceSequenceNumber = 0, whichIterstionToSetTime = -1;
		boolean firstTimeSend = false;
		if(debug) {
			System.out.println("#############################################");
			System.out.println("The type of sending is " + typeOfSending);
			System.out.println("if type == 1, then it is the first time");
			System.out.println("if type == 10, then send the packet as time out");
			System.out.println("if type == 3, then it is an acknowledge");
			System.out.println("I there a connection " + isConnected);
			System.out.println("firstTime is " + firstTime);
		}
		if(!isConnected) { 		// sending a packet to establish the connection.
			socket.send(handShakingPacket);
			socket.setSoTimeout(1400);					// A maximum of time to receive an ACK for a connection. If not, another request will be sent 				
			SampleRTT = System.currentTimeMillis();		 // save the send time
			if(firstTime) {			// receiveArrayManipulation() function can't be called more than one time 
				receiveArrayManipulation(true);    					  // state of receiving is receiving an ACK for a message that is sent by a client
				firstTime = false;
			}
		}else {  
			if( (typeOfSending == 1) || (typeOfSending == 10) ) { 
				while(i < windowSending.length) { 
					if(windowSending[i] != null) {		 // in case the window is not full 
						instanceSequenceNumber =  getSequenceOfSpecificIndexInWindow(1, i);  // i is the index
						if((typeOfSending == 1) && (getStatusOfWindowAtIndexOf(instanceSequenceNumber) != 2)) {    // send the packet for first time and not already sent
							socket.send(windowSending[i]);
							if(firstTimeSend){
								whichIterstionToSetTime =i;
								firstTimeSend = false;
							}
							changeArrayManipulationtatus(instanceSequenceNumber, 2);			// mark the packet as is send
						}else if ((typeOfSending == 10) &&(getStatusOfWindowAtIndexOf(instanceSequenceNumber) != 3)) { 	// This is for timeout ArrayManipulation // in order not to sent alreasy unACK packet			
							socket.send(windowSending[i]);
							if(firstTimeSend){
								whichIterstionToSetTime =i;
								firstTimeSend = false;
							}
							if(debug) {
								System.out.println("Time out, send unacknowledged message with sequece " + (typeOfSending == 1) + ", at index of " + i);
							}
						}
					}
					if(i == whichIterstionToSetTime) {
						socket.setSoTimeout(500);  //set the time of sending when first packet was sent
						SampleRTT = System.currentTimeMillis(); // save this time
					}
				}
			}else { 					// if it is ACK. Only one ACK at a time. typeOfSending == 3
				socket.send(ackPacket);
			}
		}
	}
	
	/*
	 * To trace the status of packet
	 * the range of statusOfPacket as the following
	 * 0-> not used,  	1 -> used in window, 		2-> sent, 		 3-> acknowledge
	 * sequenceNumber here is as the index of the arrayList
	 */
	public void changeArrayManipulationtatus(int sequenceNumber, int statusOfPacket) {
		allPacketsSendStatus.set(sequenceNumber, statusOfPacket);
	}
	
	public void sendAcknowledge(int serverSequenceNumberOfReceivedPacket) throws Exception {
		instanceSequenceNumberReceived = serverSequenceNumberOfReceivedPacket; // save this value to ack
		ArrayManipulation pack = createArrayManipulation(3, "");
		ackPacket = new DatagramPacket(pack.getBytesArr(), pack.getBytesArr().length, InetAddress.getByName("localhost"), routerPortNumber);
		sendPackets(3);		// send an ack
	}
	
	
	/*
	 * Calling from receivePacket method if not timeout Exception is thrown
	 */
	public void retrieveData(DatagramPacket recPacket) throws Exception {
		byte[] bytes = recPacket.getData();
		int lengthOfInformation = recPacket.getLength();
		int serverSequenceNumberOfReceivedPacket = ArrayManipulation.getIntFromByteArray(bytes, 1, 2);				// convert to binary, and then to integer (serverSequence first from sender perspective)
		int mySequenceNumberOfReceivedPacket = ArrayManipulation.getIntFromByteArray(bytes, 3, 2);
		if(debug) {
			System.out.println("A message  was received");
			System.out.println("The message is " + ArrayManipulation.getByteSubSet(bytes, 11, lengthOfInformation));
			System.out.println("serverSequenceNumberOfReceivedPacket is " + serverSequenceNumberOfReceivedPacket);
			System.out.println("mySequenceNumberOfReceivedPacket is " + mySequenceNumberOfReceivedPacket);
			ArrayManipulation pack = new ArrayManipulation(11);
			pack.putValues(bytes, 0, 11);
			pack.printDebug();
		}
		if(bytes[0] == 2) { 					// this packet for SYN-ACK, and establish the connection
			if(!isConnected) {
				isConnected = true;
				serverSequenceNumber = serverSequenceNumberOfReceivedPacket;		 // Here, we know the starting sequence number of server
				createMessage(); 										// Start preparing the message to send
				if(debug) {
					System.out.println("The connection now is " + isConnected);
					System.out.println("We will ctreate the total message");
					System.out.println("Server sequence number is " + serverSequenceNumber);
				}
			}else {
				System.out.println("This is not first SYN-ACK");
			}
		}else if (bytes[0] == 3) {					 // it is acknowledge for a packeted that was sent
			int statusOfReplaying = statusMySequenceNumberReceving(mySequenceNumberOfReceivedPacket);
			if(statusOfReplaying >= 0) {				//exactly what I expect inside the range 
				changeArrayManipulationtatus(mySequenceNumberOfReceivedPacket, 3);	 // mark the message as acknowledge
				shiftWindowIfNeed(1); 					//shift window if it needs
			} 					// otherwise, just drop it 
			if(debug) {
				System.out.println("statusOfReplaying is " + statusOfReplaying);
				System.out.println("It is an ACK from server for my sequence which is " + mySequenceNumberOfReceivedPacket);
			}
		}else if (bytes[0] == 4) { 				// new packet from the server
			int statusOfReplaying = statusServerSequenceNumberReceiving(serverSequenceNumberOfReceivedPacket);
			boolean isOK = false;
				if(statusOfReplaying == 1000) { //exactly what I expect
					deliver(bytes, lengthOfInformation);  // true here to deliver to the application
					isOK = true;
					serverSequenceNumber++;
					shiftWindowIfNeed(2);
				}else if (statusOfReplaying >=0) { // in the range, but not in the order. It should be dropped
					isOK = true;
					windowReceiving[statusOfReplaying] = recPacket;
				}
				if(isOK) { // if it is not OK, the packet should be dropped					
					sendAcknowledge(serverSequenceNumberOfReceivedPacket);
				}else {
					sendAcknowledge(serverSequenceNumber);  // an ACK for what we expect
				}
				if(debug) {
					System.out.println("statusOfReplaying is " + statusOfReplaying);
					System.out.println("It is a new packet form client with sequence " + serverSequenceNumberOfReceivedPacket);
					System.out.println("We send an ACK to client " + isOK);
				}
		}
	}
	
	/*
	 * isACK to trace the lifetime of the receive ArrayManipulation. 
	 * true -> receive an ACK for a message send to the server
	 * false -> receive a response from the server  
	 */
	public void receiveArrayManipulation(boolean isACK) throws IOException, Exception{ // we should take into account the timer exception
		byte[] bytes = new byte[1024];
		DatagramPacket recPacket = new DatagramPacket(bytes, bytes.length);
		boolean timeOut = false;
		while(true) {
			System.out.println("******************************************");
			try {
				socket.receive(recPacket);
				if(debug) {
					String s = new String(ArrayManipulation.getByteSubSet(recPacket.getData(), 11, recPacket.getLength()));
					System.out.println("We receive "+ recPacket.getLength());
				}
			}catch(SocketTimeoutException e) {
				if(debug) {
					System.out.println("Time is expired. It should be sent again");
				}
				timeOut = true;
			}
			if(!timeOut) {
				System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
				System.out.println("The length of the recPacket is " + recPacket.getLength());
				System.out.println("The length of the recPacket byte array is " + recPacket.getData().length);
				retrieveData(recPacket);
			}else {						// time has expired 
				if(isACK) {			// unACK ArrayManipulation should be sent again
					if(!isConnected) { 			// lost the handshaking ArrayManipulation
						sendPackets(1000);   // any value is valid as long as  isConnected is false
					}else {
						sendPackets(10);		 // time out send it again only unACK packet
					}
				}								// timeout when receive the response we don't care
			}
			
			if(!needsMoreACK_Packet()) {	// change the state to receive the response 
				isACK = true;
				socket.setSoTimeout(0);
			}
			if( isFirstPacketRceivedFromClient() && (!hasMorePacketFromServer()) ) { 	// receive the first packet from the server and wait for more 
				break;
			}
		}
		System.out.println("The message from the server is\n"+ receivedMessage );
	}

	public int calculateTimeOut() {
		int delay = 0;
		SampleRTT = Math.abs(SampleRTT -System.currentTimeMillis());
		devRTT = (int) (((0.75) * devRTT) + ((0.25) * Math.abs(devRTT - (int) SampleRTT)) );
		estimatedRRT =  (int)( ((0.875) * estimatedRRT) + ((0.125) * SampleRTT) ); 
		delay = estimatedRRT + (4 * devRTT);
		return delay;
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
	
	public boolean isWindowFull(int typeOfIntersection) {
		int lastIndex = getIndexOfLastPackedInWindow(typeOfIntersection);
		return (lastIndex == 2);
	}
	
	public boolean isThereMoreArrayManipulationToProcess(int typeOfIntersection) {
		if(typeOfIntersection == 1) { 					// if typeOfIntersection == 1 shifting the windowSending
			return (mySequencNumber < allPacketsSend.size() );
		}else { 							// if typeOfIntersection == 2 shifting the windowReceiving
			return (numberOfReceivedMessage > 0);
		}
	}
	
	/*
	 * The status of the first packet is marked as received
	 */
	public boolean isFirstPacketAcknowldged(int typeOfIntersection) {
		return (allPacketsSendStatus.get(getSequenceOfFirstPacketInWindow(typeOfIntersection)) == 3);
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
	
	public int getStatusOfWindowAtIndexOf(int index) {
		return allPacketsSendStatus.get(index);		
	}
	
	public void shift(int typeOfIntersection) throws Exception {
		if(debug) {
			System.out.print("We will shift to the right in the ");
		}
		if(typeOfIntersection == 1) { // if typeOfIntersection == 1 shifting the windowSending
			for(int i=0 ; i < 2 ; i++) {
				windowSending[i] = windowSending[i+1];
			}
			int sequenecNumberOfFirstPacketAfterWindow = getSequenceOfSpecificIndexInWindow(1,2) + 1;		// we get the packet is directly after the last one in the sending window. We 100% there is more packet as it is a precondition     
			windowSending[2] = allPacketsSend.get(sequenecNumberOfFirstPacketAfterWindow);				
			changeArrayManipulationtatus(sequenecNumberOfFirstPacketAfterWindow, 2);   	// mark the packet as sent **  no need to set into 1 ** set from 0 to 2 directly. That means use it in the window and then sent it
			socket.send(windowSending[2]);
			socket.setSoTimeout(500);
			shiftWindowIfNeed(typeOfIntersection);
		}else { // if typeOfIntersection == 2 shifting the windowReceiving
			for(int i=0 ; i < 2 ; i++) {
				windowReceiving[i] = windowReceiving[i+1];
			}
		}
	}
	
	
	public void shiftWindowIfNeed(int typeOfIntersection) throws Exception  {
		if(debug) {
			System.out.println("typeOfIntersection is " + typeOfIntersection);
			System.out.println("Is the window full ? " + isWindowFull(typeOfIntersection));
			System.out.println("Is There More Packet To process ? " + isThereMoreArrayManipulationToProcess(typeOfIntersection));
			System.out.println("Is First Packet Acknowldged ? " + isWindowFull(typeOfIntersection));
		}
			if( (isWindowFull(typeOfIntersection)) && isThereMoreArrayManipulationToProcess(typeOfIntersection) && (isFirstPacketAcknowldged(typeOfIntersection)) ){
				shift(typeOfIntersection);
			}
	}
	
	/*
	 * // to check weather this packet is the first Packet from server
	 */
	public boolean isFirstPacketRceivedFromServer() {
		return(numberOfReceivedMessage == 0);
	}
	
	/*
	 * // to check weather this packet is the last one from the ser
	 */
	public boolean isOnlyOnePacketFromServer() {
		return(numberOfReceivedMessage == 1);
	}
	
	/*
	 * // to check weather this packet is the last one from the ser
	 */
	public boolean hasMorePacketFromServer() {
		return(numberOfReceivedMessage > 1);
	}
	
	public void setNumberOfReceiveMessage(byte[] data) {
		String totalCharAsString = ArrayManipulation.getIntAsChar(data, 11);
		int totalChar = Integer.parseInt(totalCharAsString);
		numberOfReceivedMessage = (totalChar /1013) + 1;
	}
	
	public void deliver(byte[] data, int lengthOfData)throws Exception {
		String totalCharAsString = ArrayManipulation.getIntAsChar(data, 11);
		int totalChar = Integer.parseInt(totalCharAsString), escapChar = 0;
		int lengthOfThisMessage = lengthOfData - 11;
		byte[] message = null; 
		if(debug) {
			System.out.println("From deliver");
			System.out.println("lengthOfThisMessage is " + lengthOfThisMessage);
			System.out.println("totalCharAsString is " + totalCharAsString);
			System.out.println("totalChar is " + totalChar);
		}
		int sizeOfMessageArray=0, startReadingData = 11;
		if(isFirstPacketRceivedFromServer()) {  
			numberOfReceivedMessage = (totalChar /1013) + 1;  // We need to know how many messages are there
			escapChar = totalCharAsString.length() + 1; // // As this value to discover how many character are there + space
			if(isOnlyOnePacketFromServer()) {
				sizeOfMessageArray = lengthOfThisMessage - escapChar;;
				startReadingData += escapChar;
			}else {
				sizeOfMessageArray = 1013 - escapChar;
				startReadingData += escapChar;
			}
			message = new byte[sizeOfMessageArray];
			message = ArrayManipulation.getByteSubSet(data, startReadingData, sizeOfMessageArray);
		}else {   					// This is not the first message that is delivered from server
			if (isOnlyOnePacketFromServer()) {
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
	
	/*
	 * SequenceNumberReceved
	 * when receive a new packet should be make sure that either 
	 * what I expect, then status = 1000
	 * or in the range of the window, the status should be the index of the window 1 or 2. Therefore, it should be buffered
	 * Otherwise, this packet should be dropped
	 * Ex, now serverSequenceNumber = 104, received = 106 
	 */
	public int statusServerSequenceNumberReceiving(int serverSequenceNumberReceved) { //here
		int status = -1;
		if(serverSequenceNumberReceved == serverSequenceNumber) { 		// which exactly I expect sequence No.
			status =  1000;
		}else {												 // I have to buffer it if it is in the range
			if ( (serverSequenceNumberReceved > serverSequenceNumber) &&
			(serverSequenceNumberReceved < serverSequenceNumber + 3) ){ 
				status = serverSequenceNumberReceved - serverSequenceNumber; // get the index inside the array, the expected range here {0, 2}
			} 			// away from my window size, so it should be dropped			
		}
		return status;
	}
	
	public int statusMySequenceNumberReceving(int mySequenceNumberReceved) {
		int status = -1; 
		int sequenceNumberOfFirstPackedInSendingWindow = getSequenceOfFirstPacketInWindow(1);   // check in sendingWindow
		if(mySequenceNumberReceved == sequenceNumberOfFirstPackedInSendingWindow) { 	// which I expect sequence
			status =  1000;
		}else { // I have to buffer it if it is in the range
			if( (mySequenceNumberReceved > sequenceNumberOfFirstPackedInSendingWindow) &&
			(mySequenceNumberReceved < sequenceNumberOfFirstPackedInSendingWindow + 3) ){
				status = mySequenceNumberReceved - sequenceNumberOfFirstPackedInSendingWindow;
			}// away from my window size I have to drop it
		
		}
		return status;
	}
	
	public void ctreateHeaders(ArrayManipulation packet, int typeOfPacket) throws IOException{
		packet.putValues((byte) typeOfPacket, 0); 					// add the type of packet see ArrayManipulation class for more info
		byte[] mysequence = packet.getBytes(getMySequenceNumber(typeOfPacket),2);
		packet.putValues(mysequence, 1, 2); 						// add my sequence at position 1 with length of 2
		byte[] serverSequence = packet.getBytes(getServerSequenceNumber(typeOfPacket),2);
		packet.putValues(serverSequence, 3, 2); 						// add server sequence at position 3 with length of 2
		InetAddress ip = InetAddress.getByName("localhost");
		byte[] byteAdress = ip.getAddress();
		packet.putValues(byteAdress, 5, 4); 						// add ip at position 5 with length of 4
		byte[] serverPortNumber = packet.getBytes(9090, 2);
		packet.putValues(serverPortNumber, 9, 2); 					// add server port number at position 9 with length of 2	
		if(debug) {
			System.out.println("From ctreateHeaders client side.");
			packet.printDebug();
		}
	}
	
	public int getMySequenceNumber(int typeOfPacket) {
		if((typeOfPacket == 1) || (typeOfPacket == 3))  {		 // give the server the initial sequence number at first time
			return mySequencNumber;			
		}else {// typeOfPacket == 4 // increment the sequence number at each iteration
			return mySequencNumber++;
		}
	}
	
	public int getServerSequenceNumber(int typeOfPacket) {
		int serverSeq = -1;
		if(typeOfPacket == 1) { // zero server sequence number as we don't know the value
			serverSeq =  0;
		}else if (typeOfPacket == 3) {
			serverSeq = instanceSequenceNumberReceived; // to acknowledge last packed received
		}else if(typeOfPacket == 4) {
			serverSeq = serverSequenceNumber;
		}
		return serverSeq;
	}
	
	public boolean needsMoreACK_Packet() {
		boolean noMorePacket = false;
		for(int i=0 ; i<allPacketsSendStatus.size();i++ ) {
			if(allPacketsSendStatus.get(i) != 3) {
				noMorePacket = true;
			}
		}
		return noMorePacket;
	}
	
	public void printwindowSequenceNumber(int typeOfPacket) {
		System.out.print("[ ");
		for(int i=0 ; i<3 ;i++) {
			System.out.print(getSequenceOfSpecificIndexInWindow(typeOfPacket, i));
			if(i != 3) {
				System.out.print(", ");
			}
		}
		System.out.println(" ]");
	}
	
	public boolean isFirstPacketRceivedFromClient() {
		return (!receivedMessage.equals(""));
	}
}