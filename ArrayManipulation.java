
public class ArrayManipulation {
	byte[] bytesArr;
	
	public ArrayManipulation(int num) {
		bytesArr = new byte[num];
	}
	
	public byte[] getBytesArr() {
		return bytesArr;
	}
	
	
	/*
	 * Convert any integer into array of bytes
	 */
	public byte[] getBytes(int num, int lengthOfArray) {
		byte[] bytes = new byte[lengthOfArray];
		int ind =1;	
		Integer integerObject = num;
		String bitString = Integer.toBinaryString(num);
		
//		System.out.println("num is " + num);
//		System.out.println("bitString is " + bitString);
		
		
		String byteTemp ="";
		byte byteValue =  0;
		boolean loop = false, finish = false;
		int index = 0;
		for(int i=bitString.length()-1 ; i>=0;i--) {
			loop = false; // that means so no need to get inside the last block as get either 8 or 16 bits
			if(i == 0) { // when we reach end of string, 
				finish = true;
			}
			byteTemp = bitString.charAt(i) + byteTemp;
			if( ((index) ==7) || (index ==15)  ) { // When we get full byte string either at index 7 or at 15
				int aminVersion = getIntFromBinary(byteTemp) ;
				bytes[ind] = (byte)aminVersion ;
				ind--;
				byteTemp ="";
				loop = true;
			}
			if(finish && !loop) { // This hapens only when no. of bits are less than 8, 0 through 7
				int aminVersion = getIntFromBinary(byteTemp) ;
				bytes[ind] = (byte) aminVersion;
				ind--;
			}
			index++;
		}
		return bytes;
	}
	
	/*
	 * convert each byte into integer, and then into binary String. Finally converting
	 * the binary string itself into an integer
 	 */
	public static int getIntFromByteArray(byte[] bytes, int start, int length) {
		int tempInt =0;
		String tempSt = "", binaryString = "";
		for(int i= start ; i<(start +length);i++) {
			tempInt = (int) bytes[i] & 0xFF; // to be signed byte
			tempSt = Integer.toBinaryString(tempInt);
			if(tempSt.length() < 8) {
				tempSt = addZeroBit(tempSt);
			}
			binaryString = binaryString + tempSt;
		//	System.out.println("binaryString from getIntFromByteArray is " + binaryString);	
		}
		tempInt = getIntFromBinary(binaryString);
	//	System.out.println("Final int  from getIntFromByteArray is " + tempInt);	
		return tempInt;
	}
	
	/*
	 * Addign zero to the left to complete byte (8 bits)
	 */
	public static String addZeroBit( String tempSt) {
		do{
			tempSt = "0" + tempSt;
		}while(tempSt.length() < 8);
		return tempSt;
	}
	
	public void readByteArrayAsUnsignedBit(byte[] bytes, int start, int length) {
		int temp = 0;
		System.out.print("[");
		for(int i=start ; i< (start +length);i++) {
			temp = (int) bytes[i] & 0xFF;
			System.out.print(temp);
			if(i != (bytes.length - 1)) {
				System.out.print(", ");
			}
		}
		System.out.println("]");
	}
	
	/*
	 * Convert a binary string into an int
	 */
	public static int getIntFromBinary(String binaryString) {
		int num = 0;
		int iteration = 0;
		for(int ind = binaryString.length()-1 ; ind >= 0; ind--) {
			int temp = 0;
			if(binaryString.charAt(ind) == '1') {
				temp = 1;
			}else {
				temp = 0;
			}
			num += (int) Math.pow(2, iteration) * temp;
			iteration++;
		}
		return num;
	}
	
	/*
	 * insert values of byte array inside a big byte array
	 * we take as many values as given length to put it in the big array
	 * becareful for the given number 
	 */
	public void putValues(byte[] bytes, int start, int length) {
		int ind = 0;
		for(int i = start ; i <(start + length) ; i++) {
			bytesArr[i] = bytes[ind];
			ind++;
		}
	}
	
	public void putValues(byte b, int index) {
			bytesArr[index] = b;
	}
	
	public  void printDebug() {
		if(bytesArr.length > 10) {
			System.out.println("The type of message is " + getTypeOfMessage(bytesArr[0]));
			System.out.println("My sequence number is " + getIntFromByteArray(bytesArr,1,2));
			System.out.println("Expected sequence number is " + getIntFromByteArray(bytesArr,3,2));
			System.out.println("The receving adress is " );readByteArrayAsUnsignedBit(bytesArr,5, 4);
			System.out.println("The receving port number is "  + getIntFromByteArray(bytesArr,9,2));
			System.out.println("The packet has " + (bytesArr.length - 11) + " byte/s of data");
			if(bytesArr.length > 31) {
				System.out.println("The first 10 characters are");
				System.out.println(new String(getByteSubSet(bytesArr, 12, 10)));
				System.out.println("The last 10 characters are");
				System.out.println(new String(getByteSubSet(bytesArr, bytesArr.length - 11, 10)));
			}else if (bytesArr.length > 21){
				System.out.println("The last 10 characters are");
				System.out.println(new String(getByteSubSet(bytesArr, bytesArr.length - 11, 10)));
			}
		}
	}
	
	/*
	 * Read in integer was stored as as char. Therefore, no need to convert to binary digit
	 */
	public static byte[] getByteSubSet(byte[] argByteArray, int start, int length) {
		byte[] byteNeed = new byte[length];
		for(int i=0; i<length; i++ ) {
			byteNeed[i]= argByteArray[start+i];
		}
		return byteNeed;
	}
	
	public static String getIntAsChar(byte[] bytes, int start) {
		String intAsChar = "";
		for(int i=start ; i<bytes.length;i++) {
			if(bytes[i] == 32) {
				intAsChar = new String(ArrayManipulation.getByteSubSet(bytes, start, (i - start)));
				break;
			}
		}
		return intAsChar;
	}
	
	public static String getTypeOfMessage(byte b) {
		String st = "";
		switch(b) {
		case 1:
			st = "SYN";
			break;
		case 2:
			st = "SYN-ACK";
			break;
		case 3:
			st = "ACK";
			break;
		case 4:
			st = "PCK";
			break;	
		}
		return st;
	}

}
