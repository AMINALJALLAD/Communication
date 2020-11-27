import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Scanner;

public class Get {
	boolean printDebuggin;
	String bodyContentFile;
	String listOfFile;
	
	Get(boolean debug){
		this.printDebuggin = debug;
		bodyContentFile = "";
		listOfFile = "";
	}

	public String doAction(File wanted, boolean found, File rootFile, ArrayList<String> paths) { // after submission
		int statusCode = 0; String response = "";
		if(wanted != null) {
			if(found) {
				if(wanted.isDirectory()) {
					response =listFiles(wanted);
				}else {
					if(wanted.canRead()) {
						bodyContentFile = getFileContents(wanted.getPath());
						response = bodyContentFile;
						if(printDebuggin) {
							System.out.println("It is possible to read from this file");
							System.out.println("Print contents");
							System.out.println(response);
						}
					}else {
						statusCode = 4;
						if(printDebuggin) {
							System.out.println("You can't read from this file");
						}
					}
				}
			}else {
					if(printDebuggin) {
						System.out.println("We don't have such file");
					}
					statusCode = 1;
				}
		}else {
			if(paths.size() == 0) {
				response = listFiles(rootFile);
			}else {
				statusCode = 1;
			}	
		}
		if(statusCode != 0) {
			response = getResponse(statusCode);
		}
		return response; 
	}
	
	public String getResponse(int statCode) { 
		String status = "";
		switch(statCode) {
		case 1:
			status = "Page not Found";
			break;
		case 3:
			status = "It is a bad Request";
			break;
		case 4:
			status = "It is forbidden to access this file content";
			break;
		}
		status  += "\r\n";
		return status;
	}
	
	public String getFileContents(String directory) {
		String body = new String("");
		Scanner input = null;
		try {
			input = new Scanner(new FileInputStream(directory));
			while(input.hasNext()) {
				body += input.nextLine() + "\r\n";
			}
		}catch(Exception e) {
			System.out.println(e.getStackTrace());
		}
		input.close();
		return body;
	}
	
	public String listFiles(File folder) {
		boolean empty = true, firstTime = true;
		for(File fiTemp : folder.listFiles()) {
			if(firstTime) {
				empty = false;
				firstTime = false;
				listOfFile += "List of current files inside the direcerty you specify\n";
				//System.out.println("List of current files inside the direcerty you specify");
			}
				listOfFile +=fiTemp.getName() + "\n";
				//System.out.println(fiTemp.getName());			
		}
		if(empty) {
			listOfFile +="There is no any file here\n";
			//System.out.println("There is no any file here");
		}
		return listOfFile;
	}
}
