/**
 * Copyright (c) 2014 Stefan Feilmeier <stefan.feilmeier@fenecon.de>.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.fenecon.fems.exceptions;

public class NoIPException extends FEMSException {
	private static final long serialVersionUID = 3974066714557694838L;
	public NoIPException() {
		super("No IP-Address available");
	}
	public NoIPException(String string) {
		super(string);
	}
}
