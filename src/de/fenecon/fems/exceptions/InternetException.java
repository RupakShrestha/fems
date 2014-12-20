/**
 * Copyright (c) 2014 Stefan Feilmeier <stefan.feilmeier@fenecon.de>.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.fenecon.fems.exceptions;

public class InternetException extends FEMSException {
	private static final long serialVersionUID = 3918350407022744991L;
	public InternetException() {
		super("No internet connection available");
	}
	public InternetException(String string) {
		super(string);
	}
}
