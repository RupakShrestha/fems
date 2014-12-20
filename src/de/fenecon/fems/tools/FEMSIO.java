/**
 * Copyright (c) 2014 Stefan Feilmeier <stefan.feilmeier@fenecon.de>.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.fenecon.fems.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.bulldog.beagleboneblack.BBBNames;
import org.bulldog.core.gpio.DigitalIO;
import org.bulldog.core.gpio.DigitalOutput;
import org.bulldog.core.gpio.Pwm;
import org.bulldog.core.io.PinIOGroup;
import org.bulldog.core.platform.Board;
import org.bulldog.core.platform.Platform;
import org.bulldog.devices.lcd.HD44780Compatible;
import org.bulldog.devices.lcd.HD44780Mode;
import org.bulldog.devices.lcd.Lcd;
import org.bulldog.devices.lcd.LcdFont;
import org.bulldog.devices.lcd.LcdMode;

public class FEMSIO {
    private final static Board bbb = Platform.createBoard();
    public static final double FREQUENCY = 5000;
    
    private static FEMSIO femsIO = null;
      
    public static FEMSIO getFEMSIO() {
    	if(femsIO == null) {
    		femsIO = new FEMSIO();
    	}
    	return femsIO;
    }
   
    private Lcd lcd = null;
    private Lock lcdLock = new ReentrantLock();
    private Pwm backlight = null;
    public DigitalOutput RelayOutput_1 = null;
    public DigitalOutput RelayOutput_2 = null;
    public DigitalOutput RelayOutput_3 = null;
    public DigitalOutput RelayOutput_4 = null;
    public Pwm AnalogOutput_1 = null;
    public Pwm AnalogOutput_2 = null;
    public Pwm AnalogOutput_3 = null;
    public Pwm AnalogOutput_4 = null;
    public DigitalOutput AnalogOutput_1_divider = null;
    public DigitalOutput AnalogOutput_2_divider = null;
    public DigitalOutput AnalogOutput_3_divider = null;
    public DigitalOutput AnalogOutput_4_divider = null;
    
    public FEMSIO() {
    	/* this is copied from org.openhab.binding.fems.tools.FEMSDisplay */
		/* LCD Display */
        PinIOGroup ioGroup = new PinIOGroup(bbb.getPin(BBBNames.P9_12).as(DigitalIO.class),  //enable pin
    		bbb.getPin(BBBNames.P8_30).as(DigitalIO.class),  //db 4
    		bbb.getPin(BBBNames.P8_28).as(DigitalIO.class),  //db 5
    		bbb.getPin(BBBNames.P8_29).as(DigitalIO.class),  //db 6
    		bbb.getPin(BBBNames.P8_27).as(DigitalIO.class)   //db 7
		);
        lcdLock.lock();
        lcd = new HD44780Compatible(bbb.getPin(BBBNames.P9_15).as(DigitalOutput.class), //rs pin
            bbb.getPin(BBBNames.P9_23).as(DigitalOutput.class), //rw pin
            ioGroup,
            HD44780Mode.FourBit);
        lcd.setMode(LcdMode.Display2x16, LcdFont.Font_5x8);
        lcd.blinkCursor(false);
        lcd.showCursor(false);
        lcdLock.unlock();
        /* LCD Dimm */
    	backlight = bbb.getPin(BBBNames.P9_22).as(Pwm.class);
    	backlight.setFrequency(5000 /*5 kHz, as defined in org.openhab.binding.fems.internal.io.IOAnalogOutput */);
    	backlight.setDuty(0.7); // turn light on
    	backlight.enable();
    	/* Relay Outputs */
        RelayOutput_1 = bbb.getPin(BBBNames.P8_12).as(DigitalOutput.class);
        RelayOutput_2 = bbb.getPin(BBBNames.P8_11).as(DigitalOutput.class);
        RelayOutput_3 = bbb.getPin(BBBNames.P8_16).as(DigitalOutput.class);
        RelayOutput_4 = bbb.getPin(BBBNames.P8_15).as(DigitalOutput.class);
        /* Analog Outputs */
        AnalogOutput_1 = bbb.getPin(BBBNames.EHRPWM1A_P9_14).as(Pwm.class);
        AnalogOutput_2 = bbb.getPin(BBBNames.EHRPWM1B_P9_16).as(Pwm.class);
        AnalogOutput_3 = bbb.getPin(BBBNames.EHRPWM2A_P8_19).as(Pwm.class);
        AnalogOutput_4 = bbb.getPin(BBBNames.EHRPWM2B_P8_13).as(Pwm.class);
        /* Analog Output dividers */
        AnalogOutput_1_divider = bbb.getPin(BBBNames.P9_28).as(DigitalOutput.class);
        AnalogOutput_2_divider = bbb.getPin(BBBNames.P9_29).as(DigitalOutput.class);
        AnalogOutput_3_divider = bbb.getPin(BBBNames.P9_30).as(DigitalOutput.class);
        AnalogOutput_4_divider = bbb.getPin(BBBNames.P9_31).as(DigitalOutput.class);
    }
    
    public enum UserLED {
    	LED1(0), LED2(1), LED3(2), LED4(3);
    	private int id;
        private UserLED(int id) {
        	this.id = id;
        }
        public int getId() {
        	return id;
        }
    }
    
    public void switchUserLED(UserLED userLED, boolean on) throws IOException {
    	Files.write(Paths.get("/sys/class/leds/beaglebone:green:usr" + userLED.getId(), "brightness"), (on ? "1" : "0").getBytes());
    }
    
    public static void setAnalogOutput(Pwm AnalogOutput, double duty) {
    	AnalogOutput.setFrequency(FREQUENCY);
    	AnalogOutput.setDuty(duty);
    	AnalogOutput.enable();		
	};
	
	public void writeAt(int row, int column, String text) {
		lcdLock.lock();
		lcd.writeAt(row, column, text);
		lcdLock.unlock();
	}
}
