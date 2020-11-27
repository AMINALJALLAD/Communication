import java.net.DatagramSocket;
import java.util.Scanner;

public class Driver {
	Request request;
	String requestMessage;
	final int serverPortNumber = 9090;
	
	public static void main(String[] args)  {
		Driver driver = new Driver();
		Scanner key = new Scanner(System.in);
		DatagramSocket socket = null;
		String input = key.nextLine();
		while (!input.toLowerCase().equals("fin")) {
			try {
				socket = new DatagramSocket(driver.serverPortNumber);
				driver.request = new Request(socket, true);
				socket.setSoTimeout(0);  					// blocks until client is connected for indefinitely
				driver.request.receivePackets(false); // this is for connection false means state of receiving message instead of receiving an ACK for a response
				driver.request.clear();
			}catch(Exception e) {
				e.printStackTrace();
				break;
			}
			
			input = key.nextLine();
			driver.request.clear();
		}
		socket.close();
		key.close();
	}

}
