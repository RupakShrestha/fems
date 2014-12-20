/**
 * Copyright (c) 2014 Stefan Feilmeier <stefan.feilmeier@fenecon.de>.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.fenecon.fems;

import java.util.concurrent.ConcurrentLinkedQueue;

import de.fenecon.fems.tools.FEMSIO;

public class FEMSLcdAgent extends Thread {
	public class Status {
		private boolean changed = false; 
		private boolean ip = false;
		private boolean internet = false;
		private boolean modbus = false;
		public boolean getChanged() {
			return changed;
		}
		public void setIp(boolean ip) {
			this.ip = ip;
			changed = true;
		}
		public boolean getInternet() {
			return internet;
		}
		public void setInternet(boolean time) {
			this.internet = time;
			changed = true;
		}
		public void setModbus(boolean modbus) {
			this.modbus = modbus;
			changed = true;
		}
		public String toString() {
			changed = false;
			return (ip ? "X" : "-") + (internet ? "X" : "-") + (modbus ? "X" : "-");
		}
	}
	
	private static ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<String>();
    private static FEMSLcdAgent femsLcdAgent = null;
    
    public static FEMSLcdAgent getFEMSLcdAgent() {
    	if(femsLcdAgent != null) {
    		return femsLcdAgent;
    	} else {
    		return new FEMSLcdAgent();
    	}
    }
    
    private volatile boolean stop = false; 
    private String firstRow = "";
    public Status status = new Status();
    
    public void stopAgent() {
    	stop = true;
    }
    
    public void setFirstRow(String text) {
    	firstRow = text;
    }
    
	@Override
	public void run() {
		FEMSIO femsIO = FEMSIO.getFEMSIO();
		String text = "";
		String nextText = null;
		while(nextText != null || !stop) {
			if(nextText != null) text = nextText;
			for(int i=0; i<10; i++) {
				if(nextText != null || status.changed) {
					femsIO.writeAt(0, 0, String.format("%-16s", firstRow));
					femsIO.writeAt(1, 0, String.format("%-3s %-11s", status, text));
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) { ; }
			}
			nextText = queue.poll();
		}
	}
	
	public void offer(String text) {
		queue.offer(text);
	}
}
