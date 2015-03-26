package de.fenecon.fems.agents.OnlineMonitoring;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

import de.fenecon.fems.agents.Agent;

public abstract class OnlineMonitoringAbstractAgent extends Agent {
	private Logger logger = LoggerFactory.getLogger(OnlineMonitoringAbstractAgent.class);
	
	protected static final URL ONLINE_MONITORING_URL = makeUrl("https://fenecon.de/fems");
	private static URL makeUrl(String urlString) {
	    try {
	        return new java.net.URL(urlString);
	    } catch (java.net.MalformedURLException e) {
	        return null;
	    }
	}
	
	protected volatile String apikey = null;
	
	/**
	 * {@inheritDoc}
	 */
	public OnlineMonitoringAbstractAgent(String name) {
		super(name);
	}
	
	/** Set apikey */
	public void setApikey(String apikey) {
		logger.info("Set Apikey");
		this.apikey = apikey;
	}
	
	/**
	 * Send message to online-monitoring 
	 */
	protected Map<?, ?> sendToOnlineMonitoring(JSONRPC2Request request) throws JSONRPC2SessionException, IOException {
		// was apikey set? otherwise send to cache
		if(this.apikey == null) {
			logger.info("No apikey - caching data");
			//TODO: bug: we would cache a json without apikey... can never be sent! Solve with MapDB
			//jsonCache.push(json);
			throw new IOException("No apikey");
		}
		
		// send JSON-RPC to server
		JSONRPC2Session session = new JSONRPC2Session(ONLINE_MONITORING_URL);
		JSONRPC2Response response = session.send(request);			

		// handle result
		Date timestamp = null;
		try {
			Map<String, Object> params = request.getNamedParams();
			if(params.containsKey("timestamp")) { // read timestamp from request for log message
				timestamp = new Date(((Long)(params.get("timestamp")))*1000);
			}
		} catch (ClassCastException e) {
			logger.warn("Unable to get timestamp: " + e.getMessage());
			timestamp = null;
		}
		if(response.indicatesSuccess()) {
			logger.info("Successfully sent " + request.getMethod() + "-data" 
					+ (timestamp != null ? " from " + timestamp.toString() : "") );
			Object result = response.getResult();
			if(result instanceof Map<?, ?>) {
				return (Map<?, ?>) result;
			} else {
				return null;
			}
		} else {
			throw new IOException(request.getMethod() + "-data"
					+ (timestamp != null ? " from " + timestamp.toString() : "")
					+ "; server response: " + response.getError().getMessage());
		}
	}
}
