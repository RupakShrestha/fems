package de.fenecon.fems.agents.OnlineMonitoring.Message;

import java.util.HashMap;
import java.util.Map;

import de.fenecon.fems.agents.OnlineMonitoring.Message.DataMessage;

/*
 * Simple Systemmessage-Helper
 */
public class SystemMessage extends DataMessage {
	public SystemMessage(String text) {
		super(MethodType.SYSTEM,
				null,
				generateParamsMap(text));
	}
	
	private static Map<String, Object> generateParamsMap(String text) {
		HashMap<String, Object> params = new HashMap<String, Object>();
		params.put("text", text);
		return params;
	}
}
