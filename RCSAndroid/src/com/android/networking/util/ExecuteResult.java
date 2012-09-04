package com.android.networking.util;

import java.util.ArrayList;

public class ExecuteResult {
	public int exitCode = 0;
	public ArrayList<String> stdout = new ArrayList<String>();
	public ArrayList<String> stderr = new ArrayList<String>();
	
	public String getStdout(){
		return listToString(stdout);
	}
	
	public String getStdErr(){
		return listToString(stderr);
	}
	
	private String listToString(ArrayList<String> list) {
		StringBuilder fullRet=new StringBuilder();
		
		for (String string : list) {
			fullRet.append(string);
			fullRet.append("\n");
		}
		
		return fullRet.toString();
	}


}
