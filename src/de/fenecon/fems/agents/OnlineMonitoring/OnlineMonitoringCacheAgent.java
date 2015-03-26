package de.fenecon.fems.agents.OnlineMonitoring;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

import de.fenecon.fems.tools.JSONRPC2RequestCache;

public class OnlineMonitoringCacheAgent extends OnlineMonitoringAbstractAgent {
	private Logger logger = LoggerFactory.getLogger(OnlineMonitoringCacheAgent.class);
	
	private JSONRPC2RequestCache requestCache = null;
	
	/**
	 * {@inheritDoc}
	 */
	public OnlineMonitoringCacheAgent(String name) {
		super(name);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {
		if(!getRequestCache().isEmpty()) { // initialize JSON Cache
			lock.release(); // immediately start sending if we have cached messages
		}
		super.run();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void foreverLoop() throws InterruptedException {
		Thread.sleep(1000); // wait for 1 second
		if(!getRequestCache().isEmpty()) {
			JSONRPC2Request request = requestCache.pop();
			logger.info("Trying to send cached data");
			try {
				sendToOnlineMonitoring(request); // ignoring return message for cached messages
			} catch (IOException | JSONRPC2SessionException e) {
				sendLater(request);
			}
			if(!getRequestCache().isEmpty()) {
				lock.release();
			}
		}
	}
	
	/**
	 * Add a message to the Cache Agent
	 * @param message
	 */
	public void sendLater(JSONRPC2Request request) {
		getRequestCache().push(request);
		lock.release();
	}
	
	/** make sure we have a valid JSONRPC2Request Cache object */
	private JSONRPC2RequestCache getRequestCache() {
		if(requestCache == null) {
			requestCache = new JSONRPC2RequestCache();
		}
		return requestCache;
	}
}
