package Send;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import packetForming.Packets;
import java.net.InetAddress;

public class Driver {
	Request request;
	int portNumber; 
	int intitialSequnecNo;
	int serverSeuenceNO;
	int lengthpacket;
	String pathName;
	String payLoad;
	boolean isFinish;
	
	public Driver(){
		pathName = "";
		payLoad = "";
		portNumber = 1200;
		isFinish = false;

	}
	
	public void clearData() {
		pathName = "";
		portNumber = 1200; 
		payLoad = "";
		intitialSequnecNo = 0;
		serverSeuenceNO = 0;
		lengthpacket = 0;
	}
	
	public void error() {
		System.out.println("Sorry. It is an invaild input");
	}
	
	
	
	public boolean validation(String input) {
		boolean isValid = true;
		StringTokenizer st = new StringTokenizer(input);
		String token = "", requestType = "";
		int ind = 0;
		while(st.hasMoreTokens()) {
			token = st.nextToken();
			payLoad+= token + " ";
			switch(ind) {
				case 0:
					if(token.equals("POST") || token.equals("GET")) {
						requestType = token;
					}else {
						if(token.toLowerCase().equals("fin")) {
							
						}else{
							isValid = false;
						}	
					}
					break;
				case 1:
					pathName = token;
					break;
			}
			ind++;
		}
		payLoad = payLoad.strip();
		int lengthOfRequest = payLoad.length(), sumRequest = 0;
		if(requestType.equals("GET")) {
			sumRequest =requestType.length() + pathName.length() + 1; 
			if(lengthOfRequest != sumRequest) {
				isValid = false;
			}	
		}else {
			sumRequest =requestType.length() + pathName.length() + 1;
			if(lengthOfRequest <= sumRequest) {
				isValid = false;
			}
		}
		return isValid;
	}
	

	
	
	public static void main(String[] args)  {
		Driver driver = new Driver();
		Scanner key = new Scanner(System.in);
		String keyInput = "";
		DatagramSocket socket = null; 
		driver.request = null;  
		while(true) {
			
			keyInput = key.nextLine();
			if(!driver.validation(keyInput)) {
				driver.error();
				driver.clearData();
				continue;
			}
			if(driver.isFinish) {
				break;
			}
			System.out.println("You can do it");
			try {
				socket = new DatagramSocket(1000); 
				driver.request = new Request(driver.payLoad, driver.pathName, socket, true);
				driver.request.createMessage();
				
			}catch(Exception e) {
				e.printStackTrace();
				break;
			}
			driver.request.clear();
			driver.clearData();
		}
		key.close();
		socket.close();
	}
	
	public boolean isFinish(String keyInput) {
		boolean isFinish = false;
		if(keyInput.equals("fin")) {
			isFinish = true;
		}
		return isFinish;
	}
	
	
}



//System.out.println("payload is " + payLoad);
//System.out.println("ind is " + ind);


//System.out.println("payLoad is " + payLoad);
//System.out.println("lengthOfRequest get is " + lengthOfRequest);
//System.out.println("sumRequest post is " + sumRequest);



//POST dfdf/ddff/dgf  