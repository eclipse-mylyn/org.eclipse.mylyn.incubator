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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.mylyn.sandbox.search.ui.SearchCallback;
import org.eclipse.mylyn.sandbox.search.ui.SearchCriteria;
import org.eclipse.mylyn.sandbox.search.ui.SearchProvider;
import org.eclipse.mylyn.sandbox.search.ui.SearchResult;
import org.eclipse.osgi.util.NLS;

/**
 * A search provider that operates over java.io
 * 
 * @author David Green
 */
public class BasicSearchProvider extends SearchProvider {

	private abstract class FileMatcher {
		public abstract boolean matches(IFileStore file, IProgressMonitor monitor);
	}

	private class CompositeFileMatcher extends FileMatcher {

		private final List<FileMatcher> delegates;

		private final boolean allMatch;

		/**
		 * @param allMatch
		 *            indicate if one delegate must match (false) or if all delegates must match (true)
		 */
		public CompositeFileMatcher(boolean allMatch) {
			this.allMatch = allMatch;
			delegates = new ArrayList<FileMatcher>();
		}

		public CompositeFileMatcher(List<FileMatcher> delegates, boolean allMatch) {
			this.delegates = delegates;
			this.allMatch = allMatch;
		}

		public void add(FileMatcher matcher) {
			delegates.add(matcher);
		}

		@Override
		public boolean matches(IFileStore file, IProgressMonitor monitor) {
			for (FileMatcher matcher : delegates) {
				if (monitor.isCanceled()) {
					return false;
				}
				if (!matcher.matches(file, monitor)) {
					if (allMatch) {
						return false;
					}
				} else if (!allMatch) {
					return true;
				}
			}
			return allMatch ? true : false;
		}
	}

	private class FileNameMatcher extends FileMatcher {

		private final Pattern pattern;

		public FileNameMatcher(String matchPattern) {
			String regex = ".*?"; //$NON-NLS-1$
			regex += patternToRegex(matchPattern);
			regex += ".*"; //$NON-NLS-1$
			pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		}

		@Override
		public boolean matches(IFileStore file, IProgressMonitor monitor) {
			String name = file.getName();
			return pattern.matcher(name).matches();
		}

	}

	private class FileContentMatcher extends FileMatcher {

		private final Pattern pattern;

		private final int maxMatchingCharacters = 1024 * 16;

		public FileContentMatcher(SearchCriteria searchSpecification) {
			pattern = Pattern.compile(patternToRegex(searchSpecification.getText().trim()), Pattern.CASE_INSENSITIVE);
		}

		@Override
		public boolean matches(IFileStore file, IProgressMonitor monitor) {
			monitor.subTask(file.toString());
			try {
				InputStream inputStream = file.openInputStream(EFS.NONE, monitor);
				try {
					InputStreamReader reader = new InputStreamReader(new BufferedInputStream(inputStream));
					try {
						ReaderCharSequence charSequence = new ReaderCharSequence(maxMatchingCharacters, reader, monitor);
						return pattern.matcher(charSequence).find();
					} finally {
						reader.close();
					}
				} finally {
					inputStream.close();
				}
			} catch (IOException e) {
				// ignore
			} catch (CoreException e) {
				// ignore
			}
			return false;
		}

	}

	@Override
	public void performSearch(SearchCriteria searchSpecification, SearchCallback callback, IProgressMonitor m)
			throws CoreException {
		SubMonitor monitor = SubMonitor.convert(m);
		monitor.beginTask(NLS.bind(Messages.BasicSearchProvider_0, searchSpecification.getText()),
				searchSpecification.getMaximumResults() > 0
						? searchSpecification.getMaximumResults()
						: IProgressMonitor.UNKNOWN);
		try {
			FileMatcher matcher = computeMatcher(searchSpecification);

			File[] roots = File.listRoots();
			if (roots != null) {
				int matchCount = 0;

				Stack<IFileStore> state = new Stack<IFileStore>();

				// reverse-order iteration so that the first one is the last pushed on the stack
				for (int x = roots.length - 1; x >= 0; --x) {
					if (monitor.isCanceled()) {
						break;
					}
					File root = roots[x];
					IFileStore fileStore = EFS.getLocalFileSystem().fromLocalFile(root);

					state.push(fileStore);
				}
				try {
					while (!state.isEmpty() && !monitor.isCanceled()) {
						IFileStore fileStore = state.pop();

						IFileInfo fileInfo = fileStore.fetchInfo();
						if (isDefaultIgnore(fileStore, fileInfo)) {
							// ignore
						} else if (fileInfo.isDirectory()) {
							monitor.subTask(fileStore.toString());

							IFileStore[] childStores = fileStore.childStores(EFS.NONE, monitor.newChild(0));
							for (IFileStore child : childStores) {
								state.push(child);
							}
						} else {

							if (matcher.matches(fileStore, monitor.newChild(0))) {
								monitor.worked(1);

								callback.searchResult(new SearchResult(fileStore.toLocalFile(EFS.NONE,
										monitor.newChild(0))));

								if (++matchCount >= searchSpecification.getMaximumResults()) {
									break;
								}
							}
						}
					}
				} catch (OperationCanceledException oce) {
					// ignore
				} catch (CoreException e) {
					if (e.getStatus().getSeverity() != IStatus.CANCEL) {
						throw e;
					}
				}
			}
		} finally {
			monitor.done();
		}
	}

	private boolean isDefaultIgnore(IFileStore fileStore, IFileInfo fileInfo) {
		if (fileInfo.getAttribute(EFS.ATTRIBUTE_SYMLINK) || fileInfo.getAttribute(EFS.ATTRIBUTE_HIDDEN)) {
			// ignore, we don't follow symbolic links or hidden files
			return true;
		} else {
			String name = fileStore.getName();
			if (fileInfo.isDirectory()) {
				if ((name.equals("Windows") || name.equals("$Recycle.Bin")) && fileStore.getParent() != null && fileStore.getParent().getParent() == null) { //$NON-NLS-1$ //$NON-NLS-2$
					return true;
				} else if (name.startsWith(".")) { //$NON-NLS-1$
					return true;
				}
			} else {
				if (name.endsWith(".dll") || name.endsWith(".exe") || name.endsWith(".sys") || name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".bin")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
					return true;
				}
			}
		}
		return false;
	}

	private FileMatcher computeMatcher(SearchCriteria searchSpecification) {
		List<FileMatcher> filenameMatchers = new ArrayList<FileMatcher>();

		for (String filenamePattern : searchSpecification.getFilenamePatterns()) {
			if (filenamePattern.length() > 0) {
				if (filenamePattern.equals("*") || filenamePattern.equals("*.*")) { //$NON-NLS-1$//$NON-NLS-2$
					// every file matches
					filenameMatchers.clear();
					break;
				} else {
					filenameMatchers.add(new FileNameMatcher(filenamePattern));
				}
			}
		}

		CompositeFileMatcher fileMatcher = new CompositeFileMatcher(true);
		if (!filenameMatchers.isEmpty()) {
			fileMatcher.add(filenameMatchers.size() == 1 ? filenameMatchers.get(0) : new CompositeFileMatcher(
					filenameMatchers, false));
		}

		if (searchSpecification.getText() != null && searchSpecification.getText().trim().length() > 0) {
			fileMatcher.add(new FileContentMatcher(searchSpecification));
		}

		return fileMatcher;
	}

	private String patternToRegex(String matchPattern) {
		String regex = ""; //$NON-NLS-1$
		for (char c : matchPattern.toCharArray()) {
			if (Character.isLetterOrDigit(c)) {
				regex += c;
			} else {
				if (c == '*') {
					regex += ".*"; //$NON-NLS-1$
				} else if (c == '?') {
					regex += "."; //$NON-NLS-1$
				} else {
					regex += "\\"; //$NON-NLS-1$
					regex += c;
				}
			}
		}
		return regex;
	}
}
