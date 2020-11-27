import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class Post {
	boolean printDebuggin;
	
	Post(boolean debug){
		printDebuggin = debug;
	}
	
	public String doAction(File wanted, boolean found, File rootFile, String bodyContent, int indexOf, ArrayList<String> paths) {
		String response = "";
		if(wanted != null) {
			if(found) {
				if(wanted.isFile()) {
					if(wanted.canWrite()) {
						postFileContents(wanted, bodyContent);
						if(printDebuggin) {
							System.out.println("We can write in this file");
							System.out.println("The contents are posted successfully");
						}
						response = "We can write in this file The contents are posted successfully "; 
					}else {
						if(printDebuggin) {
							System.out.println("We can't write in this file");
						}
						response = "We can't write in this file ";
					}
				}else {
					response = "bad request. No data will be posted to the file ";
					if(printDebuggin) {
						System.out.println("bad request not file to post");
					}
				}
			}else {
				if(printDebuggin) {
					System.out.println("One of their father are missing");
					System.out.println("We have to create from the last folder");
				}
				response = "One of their father are missing. We have to create from the last folder that is similar. ";
				response += createDiretery(wanted, indexOf, paths, bodyContent);
			}
		}else {
			response += createDiretery(rootFile, indexOf, paths, bodyContent);
		}
		return response;
	}
	
	public void postFileContents(File file, String contents) {
		//String body = new String("");
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new FileOutputStream(file, true));
			pw.println(contents);
		}catch(Exception e) {
			System.out.println(e.getStackTrace());
		}
		pw.close();
	}
	
	public String createDiretery(File folder, int index, ArrayList<String> paths, String bodyContent) {
		int temp = index;
		String newFolders = "", path = "", response = "";
		boolean pathNewFolder = false;
		path = folder.getPath();
			for(int i = index; i < paths.size() -1 ; i++) { // new Folders missing path
				if(!pathNewFolder) {
					pathNewFolder = true;
				}
				newFolders +=  "\\" + paths.get(i);
			}
		if(pathNewFolder) {
			path +=   newFolders; // the path for the new folder / s
			if(printDebuggin) {
				System.out.println("path for folder/s is/are " + path);
			}
		}
		File tempFile = null;
		try {							//System.out.println("temp is " + temp);System.out.println("Size is " + paths.size());
			if(pathNewFolder) { // that means missing one or more folder/s
				if(printDebuggin) {
					System.out.println("We create new folders " );
				}
				tempFile = new File(path);
				tempFile.mkdirs(); 	
			}
			System.out.println(paths.size()-1);
			System.out.println(paths);
			path += "//" + paths.get(paths.size()-1); // the path for the file
			if(printDebuggin) {
				System.out.println("path for the new file is " + path);
			}
			tempFile = new File(path);
			if(tempFile.createNewFile()) {
				postFileContents(tempFile, bodyContent);
				if(printDebuggin) {
					System.out.println("We post the contents to the specified file. ");
				}
				response = "We post the contents ";
			}else {
				if(printDebuggin) {
					System.out.println("We can't make this file ");
				}
				response = "We can't make this file. ";
			}
		}catch(IOException e) {
			e.printStackTrace();
		}
		return response;
	}

}
