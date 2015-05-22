/*******************************************************************************
 * Copyright (c) 2004, 2011 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.sandbox.ui;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.jface.resource.ImageDescriptor;

/**
 * @author Mik Kersten
 */
public class SandboxUiImages {

	private static final String T_ELCL = "elcl16"; //$NON-NLS-1$

	private static final String T_TOOL = "etool16"; //$NON-NLS-1$

	private static final URL baseURL = SandboxUiPlugin.getDefault().getBundle().getEntry("/icons/"); //$NON-NLS-1$

	public static final ImageDescriptor COLOR_PALETTE = create(T_ELCL, "color-palette.gif"); //$NON-NLS-1$

	public static final ImageDescriptor STOP_SEARCH = create(T_ELCL, "stop_all.gif"); //$NON-NLS-1$

	public static final ImageDescriptor QUALIFY_NAMES = create(T_TOOL, "qualify-names.gif"); //$NON-NLS-1$

	public static final ImageDescriptor EDGE_INHERITANCE = create(T_ELCL, "edge-inheritance.gif"); //$NON-NLS-1$

	public static final ImageDescriptor EDGE_REFERENCE = create(T_ELCL, "edge-reference.gif"); //$NON-NLS-1$

	public static final ImageDescriptor EDGE_ACCESS_READ = create(T_ELCL, "edge-read.gif"); //$NON-NLS-1$

	public static final ImageDescriptor EDGE_ACCESS_WRITE = create(T_ELCL, "edge-write.gif"); //$NON-NLS-1$

	private static ImageDescriptor create(String prefix, String name) {
		return create(prefix, name, baseURL);
	}

	private static ImageDescriptor create(String prefix, String name, URL baseURL) {
		try {
			return ImageDescriptor.createFromURL(makeIconFileURL(prefix, name, baseURL));
		} catch (MalformedURLException e) {
			return ImageDescriptor.getMissingImageDescriptor();
		}
	}

	private static URL makeIconFileURL(String prefix, String name, URL baseURL) throws MalformedURLException {
		if (baseURL == null) {
			throw new MalformedURLException();
		}

		StringBuffer buffer = new StringBuffer(prefix);
		buffer.append('/');
		buffer.append(name);
		return new URL(baseURL, buffer.toString());
	}

}
