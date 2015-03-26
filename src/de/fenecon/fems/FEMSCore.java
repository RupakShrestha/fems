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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;

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
import org.bulldog.beagleboneblack.BBBNames;
import org.bulldog.core.gpio.Pwm;
import org.bulldog.core.platform.Board;
import org.bulldog.core.platform.Platform;

import de.fenecon.fems.agents.OnlineMonitoring.OnlineMonitoringAgent;
import de.fenecon.fems.agents.OnlineMonitoring.OnlineMonitoringCacheAgent;
import de.fenecon.fems.exceptions.FEMSException;
import de.fenecon.fems.exceptions.IPException;
import de.fenecon.fems.exceptions.InternetException;
import de.fenecon.fems.exceptions.RS485Exception;
import de.fenecon.fems.tools.FEMSIO;
import de.fenecon.fems.tools.FEMSIO.UserLED;
import de.fenecon.fems.tools.FEMSYaler;

public class FEMSCore {
	public final static OnlineMonitoringCacheAgent ONLINE_MONITORING_CACHE_AGENT = 
		new OnlineMonitoringCacheAgent("Online-Monitoring Cache");
	public final static OnlineMonitoringAgent ONLINE_MONITORING_AGENT = 
		new OnlineMonitoringAgent("Online-Monitoring", ONLINE_MONITORING_CACHE_AGENT);
	
	private final static SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
	
	private static String apikey;
	private static String ess;
	private static boolean debug;
	
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
		apikey = properties.getProperty("apikey");
		ess = properties.getProperty("ess", "dess");
		debug = Boolean.parseBoolean(properties.getProperty("debug", "false"));
		
		// handle commandline parameters		
		Options options = new Options();
		options.addOption("h", "help", false, "");
		options.addOption(null, "init", false, "Initialize system");
		options.addOption(null, "aout", true, "Set Analog Output: ID,%");
		options.addOption(null, "lcd-text", true, "Set LCD-Text");
		options.addOption(null, "lcd-backlight", true, "Set LCD-Backlight in %");
		
		CommandLineParser parser = new GnuParser();
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
			
			if(cmd.hasOption("init")) {
				init();
			} else if(cmd.hasOption("aout")) {
				setAnalogOutput(cmd.getOptionValue("aout"));
			} else if(cmd.hasOption("lcd-text")) {
				setLcdText(cmd.getOptionValue("lcd-text"));
			} else if(cmd.hasOption("lcd-backlight")) {
				setLcdBrightness(Integer.parseInt(cmd.getOptionValue("lcd-backlight")));
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
	//TODO: this check is now happening also in the fems-autoupdate bash script, so it could be removed here
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
			if(Files.deleteIfExists(Paths.get("/var/lock/LCK..ttyUSB0"))) {
				logInfo("Deleted old lock file");
			}
		} catch (IOException e) {
			logError("Error deleting old lock file: " + e.getMessage());
			e.printStackTrace();
		}
		
		// Prepare portName: move ttyUSB* to ttyUSB0 for compability
		Path portFile = Paths.get("/dev/ttyUSB0");
		if(!Files.exists(portFile, LinkOption.NOFOLLOW_LINKS)) {
			// /dev/ttyUSB0 does not exist. Try to find ttyUSB*
			try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get("/dev"), "ttyUSB*")) {
			    for(Path file : files) {
			    	Files.move(file, portFile);
			    	logInfo("Moved " + file.toString() + " to " + portFile.toString());
			    }
			} catch(Exception e) {
				logError("Error trying to find ttyUSB*: " + e.getMessage());
				e.printStackTrace();
			}
		}

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
		params.setPortName(portFile.toAbsolutePath().toString());
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
	 * Initialize FEMS/FEMSmonitor system
	 */
	private static void init() {	
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
				if(isModbusWorking(ess)) {
					logInfo("Modbus is ok");
					displayAgent.status.setModbus(true);
					displayAgent.offer("RS485 ok");
				} else {	
					if(debug) { // if we are in debug mode: ignore RS485-errors
						logInfo("Ignore RS485-Error");
					} else {
						throw new RS485Exception();
					}
				}
				
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
				// start Agents
				ONLINE_MONITORING_AGENT.setApikey(apikey);
				ONLINE_MONITORING_AGENT.start();
				ONLINE_MONITORING_CACHE_AGENT.setApikey(apikey);
				ONLINE_MONITORING_CACHE_AGENT.start();
				
				ONLINE_MONITORING_AGENT.sendSystemMessage(logText);
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
			
		} catch (Throwable e) { // Catch everything else
			returnCode = 2;
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			logError("Critical error: " + sw.toString());
			e.printStackTrace();
			ONLINE_MONITORING_AGENT.sendSystemMessage(logText); // try to send log
		}

		try {
			Thread.sleep(2000);  // give the agents some time to try sending
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// Exit
		System.exit(returnCode);
	}
	
	/** Set Analog Output
	 */
	private static void setAnalogOutput(String cmd) {
		logInfo("Analog Output: " + cmd);
		String[] cmds = cmd.split(",");
		FEMSIO femsIO = FEMSIO.getFEMSIO();
		try {
			if(cmds.length < 2)	throw new Exception("Missing parameters");
			// parse ID of analog output
			int id = Integer.parseInt(cmds[0]);
			logInfo("No: " + id);
			Pwm aout;
			switch(id) {
			case 1:
				aout = femsIO.AnalogOutput_1;
				break;
			case 2:
				aout = femsIO.AnalogOutput_2;
				break;
			case 3:
				aout = femsIO.AnalogOutput_3;
				break;
			case 4:
				aout = femsIO.AnalogOutput_4;
				break;
			default:
				throw new Exception("ID must be between 1 and 4");
			}
			// parse percent/duty
			int percent = Integer.parseInt(cmds[1]);
			double duty = percent/100.;
			logInfo("Duty: " + duty);
			// set analog output
			FEMSIO.setAnalogOutput(aout, duty);
			// set divider to VOLTAGE (0..10 V)
			femsIO.AnalogOutput_1_divider.high();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/** Set LCD-Text
	 */
	private static void setLcdText(String text) {
		logInfo("LCD-Text: " + text);
		FEMSIO femsIO = FEMSIO.getFEMSIO();
		femsIO.writeAt(0, 0, text.substring(0, text.length() > 16 ? 16 : text.length()));
		if(text.length() > 15) {
			femsIO.writeAt(1, 0, text.substring(16, text.length() > 32 ? 32 : text.length()));
		}
	}
	
	/** Set LCD-Text
	 */
	private static void setLcdBrightness(int percent) {
		logInfo("LCD-Brightness: " + percent + " %");
		Board bbb = Platform.createBoard();
		Pwm backlight = bbb.getPin(BBBNames.P9_22).as(Pwm.class);
    	backlight.setFrequency(5000 /*5 kHz, as defined in org.openhab.binding.fems.internal.io.IOAnalogOutput */);
    	backlight.setDuty(0.7); // turn light on
    	backlight.enable();
	}	
}
