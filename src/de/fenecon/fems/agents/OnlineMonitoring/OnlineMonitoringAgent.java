package de.fenecon.fems.agents.OnlineMonitoring;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;

import de.fenecon.fems.agents.Message;
import de.fenecon.fems.agents.OnlineMonitoring.Message.DataMessage;
import de.fenecon.fems.agents.OnlineMonitoring.Message.DataMessage.MethodType;
import de.fenecon.fems.agents.OnlineMonitoring.Message.SystemMessage;
import de.fenecon.fems.tools.FEMSYaler;

public class OnlineMonitoringAgent extends OnlineMonitoringAbstractAgent {
	private Logger logger = LoggerFactory.getLogger(OnlineMonitoringAgent.class);
	
	private final OnlineMonitoringCacheAgent cacheAgent;
	
	/**
	 * {@inheritDoc}
	 */
	public OnlineMonitoringAgent(String name, OnlineMonitoringCacheAgent cacheAgent) {
		super(name);
		this.cacheAgent = cacheAgent;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void foreverLoop() throws InterruptedException {
		Message message = messages.poll(); // wait for new message
		if(message == null) return;
		
		if(message instanceof DataMessage) {
			JSONRPC2Request request = ((DataMessage)message).getJsonRpcRequest();
			request = prepareForSending(request);
			try {
				Map<?, ?> response = sendToOnlineMonitoring(request);
				handleResponse(response);
			} catch (IOException | JSONRPC2SessionException e) {
				cacheAgent.sendLater(request);
			}
		}
	}
	
	/** Send data, taking current time as timestamp
	 * 
	 * be aware, that the states need to be transformed to be valid for JSON/InfluxDB:
	 * Use FEMSBindingTools.convertStatesForMessage(states)
	 * @param method
	 * @param states
	 * @param params
	 */
	 
	public void sendData(MethodType method, Map<String, Object> states, 
			Map<String, Object> params) {
		message(new DataMessage(method, states, params));
	}
	
	/** be aware, that the states need to be transformed to be valid for JSON/InfluxDB:
	 * Use FEMSBindingTools.convertStatesForMessage(states)
	 * @param method
	 * @param states
	 */
	public void sendData(MethodType method, Map<String, Object> states) {
		message(new DataMessage(method, states, null));
	}
	public void sendData(DataMessage message) {
		message(message);
	}
	
	/** Send system message */
	public void sendSystemMessage(String text) {
		message(new SystemMessage(text));
	}
	
	/** Handle return JSON */
	private void handleResponse(Map<?, ?> response) {
		if(response == null) return;
		try {
			if(response.containsKey("yaler") && response.get("yaler") instanceof String) {
				String relayDomain = (String)response.get("yaler");
				if(FEMSYaler.getFEMSYaler().activateTunnel(relayDomain)) {
					this.sendSystemMessage("Yalertunnel is now activated");
				};
			} else {
				if(FEMSYaler.getFEMSYaler().deactivateTunnel()) {
					this.sendSystemMessage("Yalertunnel is now deactivated");
				};
			}
		} catch (Exception e) {
			logger.error("Unable to handle response for yaler: " + e.getMessage());
		}
		// TODO: Handle City-ID from Server
    	/*if(json != null && json.has("cityid")) {
    		int newCityid = json.getInt("cityid");
			if(newCityid != cityid) {
				logger.info("Set cityid to " + newCityid + " on behalf of FEMS server");
				cityid = newCityid;
				run(); // send again
			}
    	}*/
	}
	
	/** Prepare JSONRPC2Request for sending */
	protected JSONRPC2Request prepareForSending(JSONRPC2Request request) {
		request.getNamedParams().put("apikey", apikey);
		return request;
	}
}
