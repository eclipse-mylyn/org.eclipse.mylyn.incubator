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
package org.eclipse.mylyn.internal.tasks.index.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.fieldassist.ContentProposal;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.mylyn.internal.tasks.core.AbstractTask;
import org.eclipse.mylyn.internal.tasks.index.core.TaskListIndex;
import org.eclipse.mylyn.internal.tasks.index.core.TaskListIndex.IndexField;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.internal.tasks.ui.search.AbstractSearchHandler;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.fieldassist.ContentAssistCommandAdapter;

/**
 * @author David Green
 */
public class IndexSearchHandler extends AbstractSearchHandler {

	private static TaskListIndex theIndex;

	private static AtomicInteger referenceCount = new AtomicInteger();

	private TaskListIndex index;

	public IndexSearchHandler() {
	}

	@Override
	public Composite createSearchComposite(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		GridLayoutFactory.swtDefaults().applyTo(container);

		final Button button = new Button(container, SWT.CHECK);
		button.setText("Summary only");
		button.setToolTipText("Search only the summary when checked");
		button.setSelection(true);

		button.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IndexField newDefaultField = button.getSelection() ? IndexField.SUMMARY : IndexField.CONTENT;
				index.setDefaultField(newDefaultField);
				fireFilterChanged();
			}
		});

		return container;
	}

	@Override
	public PatternFilter createFilter() {
		synchronized (IndexSearchHandler.class) {
			if (index == null) {
				if (theIndex == null) {
					// FIXME: multiple instances
					theIndex = new TaskListIndex(TasksUiPlugin.getTaskList(), TasksUiPlugin.getTaskDataManager(),
							Platform.getBundle(TasksIndexUi.BUNDLE_ID).getDataFile(".taskListIndex")); //$NON-NLS-1$
				}
				index = theIndex;
				referenceCount.incrementAndGet();
			}
		}
		return new IndexedSubstringPatternFilter(index);
	}

	@Override
	public void adaptTextSearchControl(Text textControl) {
		IContentProposalProvider proposalProvider = new IContentProposalProvider() {
			public IContentProposal[] getProposals(String contents, int position) {
				List<IContentProposal> proposals = new ArrayList<IContentProposal>(10);

				String fieldPrefix = ""; //$NON-NLS-1$
				String prefix = ""; //$NON-NLS-1$
				if (position >= 0 && position <= contents.length()) {
					int i = position;
					while (i > 0 && !Character.isWhitespace(contents.charAt(i - 1)) && contents.charAt(i - 1) != ':') {
						--i;
					}
					if (i > 0 && contents.charAt(i - 1) == ':') {
						int fieldEnd = i - 1;
						int fieldStart = i - 1;
						while (fieldStart > 0 && Character.isLetter(contents.charAt(fieldStart - 1))) {
							--fieldStart;
						}
						fieldPrefix = contents.substring(fieldStart, fieldEnd);
					}

					prefix = contents.substring(i, position);
				}

				// if we have a field prefix
				if (fieldPrefix.length() > 0) {
					IndexField indexField = null;
					try {
						indexField = IndexField.fromFieldName(fieldPrefix);
					} catch (IllegalArgumentException e) {
					}

					// if it's a person field then suggest
					// people from the task list
					if (indexField != null && indexField.isPersonField()) {
						Set<String> addresses = new TreeSet<String>();

						Collection<AbstractTask> allTasks = TasksUiPlugin.getTaskList().getAllTasks();
						for (AbstractTask task : allTasks) {
							addAddresses(addresses, task);
						}

						for (String address : addresses) {
							if (address.startsWith(prefix)) {
								proposals.add(new ContentProposal(address.substring(prefix.length()), address, null));
							}
						}
					}

				} else {
					// suggest field name prefixes
					for (IndexField field : IndexField.values()) {

						// searching on URL is not useful
						if (!field.isUserVisible()) {
							continue;
						}

						if (field.fieldName().startsWith(prefix)) {
							String description;
							switch (field) {
							case CONTENT:
								description = "Search for a term in the summary, description and comments";
								break;
							case PERSON:
								description = "Search for a user (reporter, assignee, watcher, commenter)";
								break;
							default:
								description = NLS.bind("Search on a term in the {0} field", field.fieldName());
							}
							proposals.add(new ContentProposal(field.fieldName().substring(prefix.length()) + ":",
									field.fieldName(), description));
						}
					}
				}

				return proposals.toArray(new IContentProposal[proposals.size()]);
			}

			private void addAddresses(Set<String> addresses, AbstractTask task) {
				String name = task.getOwner();
				if (name != null && name.trim().length() > 0) {
					addresses.add(name.trim());
				}
			}
		};
		ContentAssistCommandAdapter adapter = new ContentAssistCommandAdapter(textControl, new TextContentAdapter(),
				proposalProvider, null, new char[0]);
		adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_INSERT);

		// if we decorate the control it lets the user know that they can use content assist...
		// BUT it looks pretty bad.
//		ControlDecoration controlDecoration = new ControlDecoration(textControl, (SWT.TOP | SWT.LEFT));
//		controlDecoration.setShowOnlyOnFocus(true);
//		FieldDecoration contentProposalImage = FieldDecorationRegistry.getDefault().getFieldDecoration(
//				FieldDecorationRegistry.DEC_CONTENT_PROPOSAL);
//		controlDecoration.setImage(contentProposalImage.getImage());
	}

	@Override
	public void dispose() {
		synchronized (IndexSearchHandler.class) {
			if (index != null) {
				index = null;
				if (referenceCount.decrementAndGet() == 0) {
					theIndex.close();
					theIndex = null;
				}
			}
		}
	}

}