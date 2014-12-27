/**
 * Copyright (c) 2014 Stefan Feilmeier <stefan.feilmeier@fenecon.de>.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.fenecon.fems;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import net.wimpi.modbus.Modbus;
import net.wimpi.modbus.ModbusException;
import net.wimpi.modbus.io.ModbusSerialTransaction;
import net.wimpi.modbus.msg.ExceptionResponse;
import net.wimpi.modbus.msg.ModbusResponse;
import net.wimpi.modbus.msg.ReadMultipleRegistersRequest;
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse;
import net.wimpi.modbus.net.SerialConnection;
import net.wimpi.modbus.util.SerialParameters;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;

import de.fenecon.fems.exceptions.FEMSException;
import de.fenecon.fems.exceptions.InternetException;
import de.fenecon.fems.exceptions.IPException;
import de.fenecon.fems.exceptions.RS485Exception;
import de.fenecon.fems.tools.FEMSIO;
import de.fenecon.fems.tools.FEMSIO.UserLED;
import de.fenecon.fems.tools.FEMSYaler;

public class FEMSCore {
	private final static SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
	private final static long minimumInitSecs = 180;
	
	private static String femsmonitorUrl;
	private static String apikey;
	private static String ess;
	
	public static void main(String[] args) {
		// read FEMS properties from /etc/fems
		Properties properties = new Properties();
		BufferedInputStream stream = null;
		try {
			stream = new BufferedInputStream(new FileInputStream("/etc/fems"));
			properties.load(stream);
			if(stream != null) stream.close();
		} catch (IOException e) {
			logError(e.getMessage());
		}
		femsmonitorUrl = properties.getProperty("url", "https://fenecon.de/femsmonitor");
		apikey = properties.getProperty("apikey");
		ess = properties.getProperty("ess", "dess");
		
		// handle commandline parameters		
		Options options = new Options();
		options.addOption("h", "help", false, "");
		options.addOption(null, "init", false, "Initialize system");
		
		CommandLineParser parser = new GnuParser();
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
			
			if(cmd.hasOption("init")) {
				init();
		    } else {
		    	help(options);
			}
		} catch (ParseException e) {
			help(options);
		}
	}
	
	private static String logText = null;
	private static void logInfo(String text) {
		System.out.println(text);
		if(logText == null) {
			FEMSCore.logText = text;
		} else {
			FEMSCore.logText += "\n" + text;
		}
	}
	private static void logError(String text) {
		System.out.println("ERROR: " + text);
		if(logText == null) {
			FEMSCore.logText = "ERROR: " + text;
		} else {
			FEMSCore.logText += "\nERROR: " + text;
		}
	}

	/**
	 * Show all commandline options
	 * @param options
	 */
	private static void help(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "FemsTester", options );		
	}

	/**
	 * Checks if DPKG (package manager for Debian) is running, e.g. during an 
	 * "aptitude full-upgrade" session
	 * @return
	 */
	private static boolean isDpkgRunning() {
		if(Files.exists(Paths.get("/var/lib/dpkg/lock"))) {
			Runtime rt = Runtime.getRuntime();
			Process proc;
			InputStream in = null;
			int lsof = -1;
			try {
				int c;
				proc = rt.exec("/usr/bin/lsof /var/lib/dpkg/lock");
				in = proc.getInputStream();
				while ((c = in.read()) != -1) { lsof = c; }
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if(in != null) { try { in.close(); } catch (IOException e) { e.printStackTrace(); } }
			}
			if(lsof != -1) {
				return true;
			}
		};	
		return false;
	}
	
	/**
	 * Turns all FEMS outputs off
	 */
	private static void turnAllOutputsOff(FEMSIO femsIO) {
		// turn all user leds off
		try { femsIO.switchUserLED(UserLED.LED1, false); } catch (IOException e) { logError(e.getMessage()); }
		try { femsIO.switchUserLED(UserLED.LED2, false); } catch (IOException e) { logError(e.getMessage()); }
		try { femsIO.switchUserLED(UserLED.LED3, false); } catch (IOException e) { logError(e.getMessage()); }
		try { femsIO.switchUserLED(UserLED.LED4, false); } catch (IOException e) { logError(e.getMessage()); }
		
		// turn all relay outputs off
		femsIO.RelayOutput_1.low();
		femsIO.RelayOutput_2.low();
		femsIO.RelayOutput_3.low();
		femsIO.RelayOutput_4.low();
		
		// turn all analog outputs off and set divider to voltage
		FEMSIO.setAnalogOutput(femsIO.AnalogOutput_1, 0);
		FEMSIO.setAnalogOutput(femsIO.AnalogOutput_2, 0);
		FEMSIO.setAnalogOutput(femsIO.AnalogOutput_3, 0);
		FEMSIO.setAnalogOutput(femsIO.AnalogOutput_4, 0);
		femsIO.AnalogOutput_1_divider.high();
		femsIO.AnalogOutput_2_divider.high();
		femsIO.AnalogOutput_3_divider.high();
		femsIO.AnalogOutput_4_divider.high();
	}
	
	/**
	 * Checks if the current system date is valid
	 * @return
	 */
	private static boolean isDateValid() {
		Calendar now = Calendar.getInstance();
		int year = now.get(Calendar.YEAR);  
		if(year < 2014 || year > 2025) {
			return false;
		} else {
			return true;
		}
	}
	
	/**
	 * Checks if modbus connection to storage system is working
	 * @param ess "dess" or "cess"
	 * @return
	 */
	private static boolean isModbusWorking(String ess) {
		// remove old lock file
		try {
			Files.deleteIfExists(Paths.get("/var/lock/LCK..ttyUSB0"));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		String portName = "/dev/ttyUSB0";
		// default: DESS 
		int baudRate = 9600;
		int socAddress = 10143;
		int unit = 4;
		if(ess.compareTo("cess")==0) {
			baudRate = 19200;
			socAddress = 0x1402;
			unit = 100;
		}
		SerialParameters params = new SerialParameters();
		params.setPortName(portName);
		params.setBaudRate(baudRate);
		params.setDatabits(8);
		params.setParity("None");
		params.setStopbits(1);
		params.setEncoding(Modbus.SERIAL_ENCODING_RTU);
		params.setEcho(false);
		params.setReceiveTimeout(500);
		SerialConnection serialConnection = new SerialConnection(params);
		try {
			serialConnection.open();
		} catch (Exception e) {
			logError("Modbus connection error");
			serialConnection.close();
			return false;
		}
		ModbusSerialTransaction modbusSerialTransaction = null;
		ReadMultipleRegistersRequest req = new ReadMultipleRegistersRequest(socAddress, 1);
		req.setUnitID(unit);
		req.setHeadless();	
		modbusSerialTransaction = new ModbusSerialTransaction(serialConnection);
		modbusSerialTransaction.setRequest(req);
		modbusSerialTransaction.setRetries(1);
		try {
			modbusSerialTransaction.execute();
		} catch (ModbusException e) {
			logError("Modbus execution error");
			serialConnection.close();
			return false;
		}
		ModbusResponse res = modbusSerialTransaction.getResponse();
		serialConnection.close();
		
		if (res instanceof ReadMultipleRegistersResponse) {
			return true;
    	} else if (res instanceof ExceptionResponse) {
    		logError("Modbus error: " + ((ExceptionResponse)res).getExceptionCode());
    	} else {
    		logError("Modbus undefined response");
    	}
		return false;
	}
	
	/**
	 * Gets an IPv4 network address
	 * @return
	 */
	private static InetAddress getIPaddress() {
    	try {
			NetworkInterface n = NetworkInterface.getByName("eth0");
			Enumeration<InetAddress> ee = n.getInetAddresses();
			while (ee.hasMoreElements()) {
				InetAddress i = (InetAddress) ee.nextElement();
				if(i instanceof Inet4Address) {
					return i;
		        }
		    }
    	} catch (SocketException e) { /* no IP-Address */ }
    	return null; 
	}
	
	/**
	 * Send message to Online-Monitoring
	 */
	public static JSONObject sendMessage(String message) {
		// create JSON		
		JSONObject mainJson = new JSONObject();
		mainJson.put("version", 1);
		mainJson.put("apikey", apikey);
		mainJson.put("timestamp", new Date().getTime());
		mainJson.put("content", "system");
		mainJson.put("system", message);
		mainJson.put("ipv4", getIPaddress().getHostAddress()); // local ipv4 address
		mainJson.put("yaler", FEMSYaler.getFEMSYaler().isActive());
		// send to server
		HttpsURLConnection con;
		try {
			URL url = new URL(femsmonitorUrl);
			con = (HttpsURLConnection)url.openConnection();
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type","application/json"); 
			con.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 5.0;Windows98;DigExt)"); 
			con.setDoOutput(true); 
			con.setDoInput(true);
			DataOutputStream output = new DataOutputStream(con.getOutputStream());
			try {
				output.writeBytes(mainJson.toString());
			} finally {
				output.close();
			}
			// evaluate response
			if(con.getResponseCode() == 200) {
				logInfo("Successfully sent system-data; server answered: " + con.getResponseMessage());
			} else {
				logError("Error while sending system-data; server response: " + con.getResponseCode() + "; will try again later");
			}
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			JSONObject retJson = null;
	        try {
	            String inputLine = in.readLine();
	            if(inputLine != null) {
		        	try {
		        		retJson = new JSONObject(inputLine);
		        	} catch (Exception e) {}
		        }
	        } finally {
	        	in.close();
	        }
	        return retJson;
		} catch (IOException e) {
			logError(e.getMessage());
		}
		return null;
	}
	
	/**
	 * Handle json returned from Online-Monitoring
	 * @param json
	 * @throws Exception 
	 */
	private static void handleReturnJson(JSONObject json) throws Exception {
    	if(json != null && json.has("yaler")) {
    		logInfo("Activate Yaler tunnel");
    		String relayDomain = json.getString("yaler");
    		FEMSYaler.getFEMSYaler().activateTunnel(relayDomain);
    	} else {
    		logInfo("Deactivate Yaler tunnel");
    		FEMSYaler.getFEMSYaler().deactivateTunnel();
    	}
	} 
	
	/**
	 * Initialize FEMS/FEMSmonitor system
	 */
	private static void init() {
		Date startInitTimestamp = new Date();
		int returnCode = 0; 
		boolean dpkgIsRunning = false;
		try {
			logInfo("Start FEMS Initialization");
	 
			// check if dpkg is running during startup of initialization
			dpkgIsRunning = isDpkgRunning();
			if(dpkgIsRunning) {
				logInfo("DPKG is running -> no system update");
			} else {
				logInfo("DPKG is not running -> will start system update");
			}
			
			// init LCD display
			Runtime rt = Runtime.getRuntime();
			Process proc;
			FEMSIO femsIO = FEMSIO.getFEMSIO();
			FEMSDisplayAgent displayAgent = FEMSDisplayAgent.getFEMSLcdAgent();
			displayAgent.setFirstRow("FEMS Selbsttest");
			
			// turn outputs off
			logInfo("Turn outputs off");
			turnAllOutputsOff(femsIO);
			
			try {
				// check for valid ip address
				InetAddress ip = getIPaddress();
				if(ip == null) {
			        try {
						proc = rt.exec("/sbin/dhclient eth0");
						proc.waitFor();
						ip = getIPaddress(); /* try again */
						if(ip == null) { /* still no IP */
							throw new IPException();
						}
					} catch (IOException | InterruptedException e) {
						throw new IPException(e.getMessage());
					}
				}
				logInfo("IP: " + ip.getHostAddress());
				displayAgent.status.setIp(true);
				displayAgent.offer("IP ok");
				try { femsIO.switchUserLED(UserLED.LED1, true); } catch (IOException e) { logError(e.getMessage()); }		
		
				// check time
				if(isDateValid()) { /* date is valid, so we check internet access only */
					logInfo("Date was ok: " + dateFormat.format(new Date()));
					try {
						URL url = new URL("https://fenecon.de");
						URLConnection con = url.openConnection();
						con.setConnectTimeout(1000);
						con.getContent();
					} catch (IOException e) {
						throw new InternetException(e.getMessage());
					}	
				} else {
					logInfo("Date was not ok: " + dateFormat.format(new Date()));
					try {
						proc = rt.exec("/usr/sbin/ntpdate -b -u fenecon.de 0.pool.ntp.org 1.pool.ntp.org 2.pool.ntp.org 3.pool.ntp.org");
						proc.waitFor();
						if(!isDateValid()) {
							throw new InternetException("Date is still wrong: " + dateFormat.format(new Date()));
						}
						logInfo("Date is now ok: " + dateFormat.format(new Date()));
					} catch (IOException | InterruptedException e) {
						throw new InternetException(e.getMessage());
					}
				}
				logInfo("Internet access is available");
				displayAgent.status.setInternet(true);
				displayAgent.offer("Internet ok");
				try { femsIO.switchUserLED(UserLED.LED2, true); } catch (IOException e) { logError(e.getMessage()); }	
						
				// test modbus
				if(!isModbusWorking(ess)) {
					throw new RS485Exception();
				}
				logInfo("Modbus is ok");
				displayAgent.status.setModbus(true);
				displayAgent.offer("RS485 ok");
				
				// Exit message
				logInfo("Finished without error");
				displayAgent.offer(" erfolgreich");
				
				// announce systemd finished
				logInfo("Announce systemd: ready");
				try {
					proc = rt.exec("/bin/systemd-notify --ready");
					proc.waitFor();
				} catch (IOException | InterruptedException e) {
					logError(e.getMessage());
				}
			} catch (FEMSException e) {
				logError(e.getMessage());
				logError("Finished with error");
				displayAgent.offer(e.getMessage());
				returnCode = 1;
			}
			
			// stop lcdAgent
			displayAgent.stopAgent();
			try { displayAgent.join(); } catch (InterruptedException e) { ; }
			
			// Check if Yaler is active
			if(FEMSYaler.getFEMSYaler().isActive()) {
				logInfo("Yaler is activated");
			} else {
				logInfo("Yaler is deactivated");
			}
			
			// Send message
			if(apikey == null) {
				logError("Apikey is not available");
			} else {		
				JSONObject returnJson = sendMessage(logText);
				try {
					// start yaler if necessary
					handleReturnJson(returnJson);
				} catch (Exception e) {
					logError(e.getMessage());
				}
			}
			
			if(displayAgent.status.getInternet() && !dpkgIsRunning) {
				// start update if internet is available and dpkg is not running
				logInfo("Start system update");
				try {
					proc = rt.exec("/etc/cron.daily/fems-autoupdate");
					proc.waitFor();
				} catch (IOException | InterruptedException e) {
					logError(e.getMessage());
				}
			} else {
				logInfo("Do not start system update");
			}
			
			// wait if we are to early and dpkg is not running
			long initTime = new Date().getTime() - startInitTimestamp.getTime();
			if(!dpkgIsRunning && TimeUnit.MILLISECONDS.toSeconds(initTime) < minimumInitSecs) {
				logInfo("Too fast (" + TimeUnit.MILLISECONDS.toSeconds(initTime) + " secs) ... waiting");
				Thread.sleep(TimeUnit.SECONDS.toMillis(minimumInitSecs) - initTime);
			}
			
		} catch (Throwable e) { // Catch everything else
			returnCode = 2;
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			logError("Critical error: " + sw.toString());
			e.printStackTrace();
			JSONObject returnJson = sendMessage(logText); // try to send log
			try {
				// start yaler if necessary
				handleReturnJson(returnJson);
			} catch (Exception e1) {
				logError(e1.getMessage());
			}
			// wait if we are to early
			long initTime = new Date().getTime() - startInitTimestamp.getTime();
			if(!dpkgIsRunning && TimeUnit.MILLISECONDS.toSeconds(initTime) < minimumInitSecs) {
				logInfo("Too fast (" + TimeUnit.MILLISECONDS.toSeconds(initTime) + " secs) ... waiting");
				try {
					Thread.sleep(TimeUnit.SECONDS.toMillis(minimumInitSecs) - initTime);
				} catch (InterruptedException e1) {
					e1.printStackTrace(new PrintWriter(sw));
					logError("Sleep error: " + sw.toString());
					e1.printStackTrace();
					returnJson = sendMessage(logText); // try to send log
					try {
						// start yaler if necessary
						handleReturnJson(returnJson);
					} catch (Exception e2) {
						logError(e2.getMessage());
					}
				}
			}
		}
		// Exit
		System.exit(returnCode);
	}
}
