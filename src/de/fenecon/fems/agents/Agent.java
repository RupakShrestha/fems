/**
 * Copyright (c) 2015 Stefan Feilmeier <stefan.feilmeier@fenecon.de>.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.fenecon.fems.agents;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Agent" is the base entity for all agents.
 * 
 * @author Stefan Feilmeier
 */
public abstract class Agent extends Thread {
	private static Logger logger = LoggerFactory.getLogger(Agent.class);
	protected final ConcurrentLinkedQueue<Message> messages = new ConcurrentLinkedQueue<>();
	protected final Semaphore lock = new Semaphore(0);

	/**
	 * Create and start a new agent
	 */
	public Agent() {
	}
	
	/**
	 * Create and start a new agent
	 * 
	 * @param agentName Name of the agent
	 */
	public Agent(String name) {
		this();
		this.setName(name);
	}
	
	/**
	 * Send a message to this agent
	 * (This method is thread-safe)
	 * 
	 * @param message
	 */
	protected void message(Message message) {
		messages.add(message);
		lock.release();
	}
	
	/**
	 * The main execution control of the agent 
	 */
	@Override
	public void run() {
		try {
			while(true) {
				lock.acquire();
				foreverLoop();
			}
		} catch (InterruptedException e) { 
			e.printStackTrace();
		}
		logger.info("ForeverLoop ended");
	}
	
	/**
	 * This method is called over and over again after lock is released
	 * 
	 * @throws InterruptedException
	 */
	public abstract void foreverLoop() throws InterruptedException;
}
