package de.fenecon.fems.agents;

public class Message {
	protected final Agent sender;
	
	public Message() {
		if(Thread.currentThread() instanceof Agent) {
			sender = (Agent)(Thread.currentThread());
		} else {
			sender = null;
		}
	}
	
	/** Get the sender agent of the message; null if it was not an agent
	 * 
	 * @return sender agent or null
	 */
	public Agent getSender() {
		return sender;
	}
	
	public String getSenderName() {
		if(sender != null) {
			return sender.getName();
		} else {
			return "(unknown)";
		}
	}
	
	/** Specific messages 
	 */
	public static class AddListener extends Message {
		private final Agent listener;
		public AddListener(Agent listener) {
			this.listener = listener;
		}
		public Agent getListener() {
			return listener;
		}
	}
}
