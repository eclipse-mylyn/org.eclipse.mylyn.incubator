/*******************************************************************************
 * Copyright (c) 2011 Tasktop Technologies.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.sandbox.search.ui.provider;

import java.io.IOException;
import java.io.Reader;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

/**
 * A CharSequence that is based on a reader, providing a specified number of characters. If the reader cannot provide
 * enough character data, the sequence is padded with whitespace.
 * 
 * @author David Green
 */
class ReaderCharSequence implements CharSequence {

	private final int length;

	private Reader reader;

	private final StringBuilder buffer;

	private final IProgressMonitor monitor;

	public ReaderCharSequence(int length, Reader reader, IProgressMonitor monitor) {
		this.length = length;
		this.reader = reader;
		this.monitor = monitor;
		buffer = new StringBuilder(length > 1024 ? 1024 : length);
	}

	public int length() {
		return length;
	}

	public char charAt(int index) {
		if (index >= length || index < 0) {
			throw new IndexOutOfBoundsException();
		}
		if (index >= buffer.length()) {
			fill(index);
		}
		if (index >= buffer.length()) {
			return ' ';
		}
		return buffer.charAt(index);
	}

	private void fill(int maxIndex) {
		if (reader == null) {
			return;
		}
		int c;
		while (buffer.length() <= maxIndex) {
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
			try {
				c = reader.read();
				if (c == -1) {
					reader = null;
					break;
				}
			} catch (IOException e) {
				reader = null;
				break;
			}
			buffer.append((char) c);
		}
	}

	public CharSequence subSequence(int start, int end) {
		if (end > length || end < 0 || start < 0 || start > end) {
			throw new IndexOutOfBoundsException();
		}
		if (end > buffer.length()) {
			fill(end - 1);
		}
		if (end <= buffer.length()) {
			return buffer.subSequence(start, end);
		}
		StringBuilder buf = new StringBuilder(end - start);
		for (int x = start; x < end; ++x) {
			buf.append(charAt(x));
		}
		return buf;
	}
}
