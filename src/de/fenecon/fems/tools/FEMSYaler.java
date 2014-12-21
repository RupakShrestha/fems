/**
 * Copyright (c) 2014 Stefan Feilmeier <stefan.feilmeier@fenecon.de>.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

/** ATTENTION: keep in sync with de.fenecon.fems.tools.FEMSYaler.java **/
package de.fenecon.fems.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import de.fenecon.fems.FEMSCore;

public class FEMSYaler {
	private static final String serviceFileName = "fems-yalertunnel.service";
	private static final Path serviceFile = Paths.get("/lib/systemd/system/", serviceFileName);
	private static final Path systemctlExecutable = Paths.get("/bin/systemctl");
	private static final Path yalerExecutable = Paths.get("/usr/bin/fems-yalertunnel");
	
	private static boolean serviceIsActive = false;
	private static FEMSYaler femsYaler = null;
	private static Lock systemctlLock = new ReentrantLock();
    
	public static FEMSYaler getFEMSYaler() {
    	if(femsYaler == null) {
    		femsYaler = new FEMSYaler(); 
    	}
    	return femsYaler;
    }
	
	public FEMSYaler() {
		/* get status information */
		Runtime rt = Runtime.getRuntime();
		Process proc;
        try {
        	systemctlLock.lock();
        	proc = rt.exec(systemctlExecutable + " is-active " + serviceFileName + " --quiet");
			if(proc.waitFor() == 0) {
				serviceIsActive = true;
			} else {
				serviceIsActive = false;
			}
		} catch (InterruptedException | IOException e) {
			System.out.println("Status of yaler service is unknown: " + e.getMessage());
		} finally {
			systemctlLock.unlock();
		}
        System.out.println("Yaler service status is " + (serviceIsActive ? "online" : "offline"));	
    }
	
	public boolean isActive() {
		return serviceIsActive;
	}
	
	public void activateTunnel(String relayDomain) throws Exception {
		/* do nothing if service is active */
		if(serviceIsActive) { return; }
		
		/* check if relayDomain is consistent */
		if(!relayDomain.matches("fenecon-\\w{4}-\\w{4}")) {
			throw new Exception("Invalid relayDomain: " + relayDomain);
		};
		/* stop service if it is running */
		Runtime rt = Runtime.getRuntime();
		Process proc;
		systemctlLock.lock();
        proc = rt.exec(systemctlExecutable + " stop " + serviceFileName);
        proc.waitFor();
        systemctlLock.unlock();
        
		/* remove old serviceFile */
		try {
			Files.deleteIfExists(serviceFile);
		} catch (IOException e) {
			System.out.println("Unable to deleteIfExists " + serviceFile + ": " + e.getMessage());
		}
		/* create unitFile */
		String unitText = "[Unit]\n"
				+ "Description=FEMS yalertunnel on port 22\n"
				+ "\n"
				+ "[Service]\n"
				+ "ExecStart=" + yalerExecutable + " proxy 127.0.0.1:22 via.yaler.net:80 " + relayDomain + "\n"
				+ "RestartSec=30\n"
				+ "Restart=always\n"
				+ "\n"
				+ "[Install]\n"
				+ "WantedBy=multi-user.target\n";
		Files.write(serviceFile, unitText.getBytes());
		/* enable service */
		systemctlLock.lock();
        proc = rt.exec(systemctlExecutable + " enable " + serviceFileName);
        int success = proc.waitFor();
		systemctlLock.unlock();
        if(success != 0) {
        	throw new Exception("Unable to enable yaler service");
        }
        /* start service */
		systemctlLock.lock();
        proc = rt.exec(systemctlExecutable + " start " + serviceFileName);
        success = proc.waitFor();
		systemctlLock.unlock();
        if(success != 0) {
        	throw new Exception("Unable to start yaler service");
        } 
        /* set as active */
        serviceIsActive = true;
        FEMSCore.sendMessage("Yalertunnel is now activated");
	}
	
	public void deactivateTunnel() throws IOException, InterruptedException {
		/* do nothing if service is not active */
		if(!serviceIsActive) { return; }
		
		/* stop service */
		Runtime rt = Runtime.getRuntime();
		Process proc;
		systemctlLock.lock();
        proc = rt.exec(systemctlExecutable + " stop " + serviceFileName);
        int success = proc.waitFor();
        systemctlLock.unlock();
        if(success != 0) {
        	System.out.println("Unable to stop yaler service");
        }
        /* disable service */
        rt = Runtime.getRuntime();
        systemctlLock.lock();
        proc = rt.exec(systemctlExecutable + " disable " + serviceFileName);
        success = proc.waitFor();
        systemctlLock.unlock();
        if(success != 0) {
        	System.out.println("Unable to disable yaler service");
        }
        /* delete service file */
		try {
			Files.deleteIfExists(serviceFile);
		} catch (IOException e) {
			System.out.println("Unable to deleteIfExists " + serviceFile + ": " + e.getMessage());
		}
		/* set as inactive */
		serviceIsActive = false;
		FEMSCore.sendMessage("Yalertunnel is now deactivated");
	}
}
