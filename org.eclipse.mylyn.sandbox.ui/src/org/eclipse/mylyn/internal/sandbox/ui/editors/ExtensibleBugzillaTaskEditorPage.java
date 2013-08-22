/*******************************************************************************
 * Copyright (c) 2004, 2009 Jingwen Ou and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Jingwen Ou - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.sandbox.ui.editors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.mylyn.commons.ui.CommonImages;
import org.eclipse.mylyn.commons.workbench.forms.CommonFormUtil;
import org.eclipse.mylyn.internal.bugzilla.ui.editor.BugzillaTaskEditorPage;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorCommentPart;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorCommentPart.CommentGroupViewer;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorCommentPart.CommentViewer;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorDescriptionPart;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorPlanningPart;
import org.eclipse.mylyn.internal.tasks.ui.editors.TaskEditorSummaryPart;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPart;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditorPartDescriptor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.forms.IFormPart;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;

/**
 * A bugzilla task editor page that has find functionality
 * 
 * @author Jingwen Ou
 * @author Lily Guo
 */
public class ExtensibleBugzillaTaskEditorPage extends BugzillaTaskEditorPage {

	private Action toggleFindAction;

	private static final Color HIGHLIGHTER_YELLOW = new Color(Display.getDefault(), 255, 238, 99);

	private static final Color ERROR_NO_RESULT = new Color(Display.getDefault(), 255, 150, 150);

	private final List<StyledText> textToSearchAndHighlight = new ArrayList<StyledText>();

	public ExtensibleBugzillaTaskEditorPage(TaskEditor editor) {
		super(editor);
	}

	private void addFindAction(IToolBarManager toolBarManager) {
		if (toggleFindAction != null && toggleFindAction.isChecked()) {
			ControlContribution findTextboxControl = new ControlContribution("Find") {
				@Override
				protected Control createControl(Composite parent) {
					FormToolkit toolkit = getTaskEditor().getHeaderForm().getToolkit();
					final Composite findComposite = toolkit.createComposite(parent);

					GridLayout findLayout = new GridLayout();
					findLayout.marginHeight = 4;
					findComposite.setLayout(findLayout);
					findComposite.setBackground(null);

					final Text findText = toolkit.createText(findComposite, "", SWT.FLAT);
					findText.setLayoutData(new GridData(100, SWT.DEFAULT));
					findText.setData(FormToolkit.KEY_DRAW_BORDER, FormToolkit.TEXT_BORDER);
					findText.setFocus();
					toolkit.adapt(findText, false, false);

					findText.addModifyListener(new ModifyListener() {
						@Override
						public void modifyText(ModifyEvent e) {
							if (findText.getText().equals("")) {
								removePreviousHighlight();
								findText.setBackground(null);
							}
						}
					});

					findText.addSelectionListener(new SelectionAdapter() {

						@Override
						public void widgetDefaultSelected(SelectionEvent event) {
							try {
								setReflow(false);
								findText.setBackground(null);
								if (findText.getText().equals("")) {
									return;
								}
								String searchText = findText.getText().toLowerCase();
								IFormPart[] parts = getManagedForm().getParts();

								removePreviousHighlight();

								for (IFormPart part : parts) {
									if (part instanceof TaskEditorSummaryPart) {
										if (getModel().getTaskData()
												.getRoot()
												.getMappedAttribute(TaskAttribute.SUMMARY) != null) {
											searchPart(textToSearchAndHighlight, getModel().getTaskData()
													.getRoot()
													.getMappedAttribute(TaskAttribute.SUMMARY)
													.getValue(), searchText,
													((TaskEditorSummaryPart) part).getControl());
										}
									} else if (part instanceof TaskEditorPlanningPart) {
										if (((TaskEditorPlanningPart) part).getPlanningPart().getTask() != null) {
											searchPart(textToSearchAndHighlight,
													((TaskEditorPlanningPart) part).getPlanningPart()
															.getTask()
															.getNotes(), searchText,
													((TaskEditorPlanningPart) part).getControl());
										}
									} else if (part instanceof TaskEditorDescriptionPart) {
										if (getModel().getTaskData()
												.getRoot()
												.getMappedAttribute(TaskAttribute.DESCRIPTION) != null) {
											searchPart(textToSearchAndHighlight, getModel().getTaskData()
													.getRoot()
													.getMappedAttribute(TaskAttribute.DESCRIPTION)
													.getValue(), searchText,
													((TaskEditorDescriptionPart) part).getControl());
										}
									} else if (part instanceof TaskEditorCommentPart) {
										searchCommentPart(textToSearchAndHighlight, searchText,
												(TaskEditorCommentPart) part);
									}
								}

								if (!textToSearchAndHighlight.isEmpty()) {
									for (StyledText styledText : textToSearchAndHighlight) {
										highlightStyledText(styledText, searchText, 0);
									}
								} else {
									findText.setBackground(ERROR_NO_RESULT);
								}
							} finally {
								setReflow(true);
							}
							reflow();
							findText.setFocus();
						}
					});
					toolkit.paintBordersFor(findComposite);
					return findComposite;
				}

			};
			toolBarManager.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, findTextboxControl);
		}

		if (toggleFindAction == null) {
			toggleFindAction = new Action("", SWT.TOGGLE) {
				@Override
				public void run() {
					if (!this.isChecked()) {
						removePreviousHighlight();
					}
					getTaskEditor().updateHeaderToolBar();
				}

			};
			toggleFindAction.setImageDescriptor(CommonImages.FIND);
			toggleFindAction.setToolTipText("Find");
		}
		toolBarManager.appendToGroup(IWorkbenchActionConstants.MB_ADDITIONS, toggleFindAction);
	}

	@Override
	public boolean canPerformAction(String actionId) {
		if (actionId.equals(ActionFactory.FIND.getId())) {
			return true;
		}

		return super.canPerformAction(actionId);
	}

	@Override
	protected Set<TaskEditorPartDescriptor> createPartDescriptors() {
		Set<TaskEditorPartDescriptor> descriptors = super.createPartDescriptors();
		boolean hasPartNewComment = false;
		for (TaskEditorPartDescriptor taskEditorPartDescriptor : descriptors) {
			if (taskEditorPartDescriptor.getId().equals(ID_PART_NEW_COMMENT)) {
				descriptors.remove(taskEditorPartDescriptor);
				hasPartNewComment = true;
				break;
			}
		}
		if (hasPartNewComment) {
			descriptors.add(new TaskEditorPartDescriptor(ID_PART_NEW_COMMENT) {
				@Override
				public AbstractTaskEditorPart createPart() {
					return new ExtensibleTaskEditorNewCommentPart();
				}
			}.setPath(PATH_COMMENTS));
		}
		return descriptors;
	}

	@Override
	public void doAction(String actionId) {
		if (actionId.equals(ActionFactory.FIND.getId())) {
			if (toggleFindAction != null) {
				toggleFindAction.setChecked(!toggleFindAction.isChecked());
				toggleFindAction.run();
			}
		}
		super.doAction(actionId);
	}

	@Override
	public void fillToolBar(IToolBarManager toolBarManager) {
		super.fillToolBar(toolBarManager);

		addFindAction(toolBarManager);
	}

	private void searchCommentPart(final List<StyledText> listStyledText, final String text,
			final TaskEditorCommentPart part) {
		List<TaskAttribute> commentAttributes = getModel().getTaskData()
				.getAttributeMapper()
				.getAttributesByType(getModel().getTaskData(), TaskAttribute.TYPE_COMMENT);

		if (!hasAnyResultInComments(commentAttributes, text)) {
			return;
		}

		if (!part.isSectionExpanded()) {
			try {
				part.setReflow(true);
				part.expandAllComments(false);
			} finally {
				part.setReflow(false);
			}
		}
		List<CommentGroupViewer> commentGroupViewers = part.getCommentGroupViewers();

		int commentIndex = commentAttributes.size() - 1;
		boolean hasResultInGroup = false;
		boolean expanded = false;
		for (int i = commentGroupViewers.size() - 1; i >= 0; i--) {
			final CommentGroupViewer group = commentGroupViewers.get(i);
			if (!expanded) {
				int index = commentIndex;
				for (int j = group.getCommentViewers().size() - 1; j >= 0; j--) {
					if (hasResultInComment(text, commentAttributes.get(index))) {
						hasResultInGroup = true;
						break;
					}
					index--;
				}
			}
			if (hasResultInGroup) {
				if (!group.isExpanded()) {
					try {
						part.setReflow(true);
						group.setExpanded(true);
					} finally {
						part.setReflow(false);
					}
				}
				// only expand the next group if the latest comments don't contain the search text
				expanded = true;
				hasResultInGroup = false;
			}
			int numResultsInGroup = searchCommentInGroup(text, listStyledText, commentIndex, group.getCommentViewers());

			if (!group.isSectionExpanded() && numResultsInGroup != 0) {
				final int indexGroup = commentIndex;

				HyperlinkAdapter listener = new HyperlinkAdapter() {
					@Override
					public void linkActivated(HyperlinkEvent e) {
						try {
							setReflow(false);
							List<StyledText> commentStyledText = new ArrayList<StyledText>();
							part.setReflow(true);
							group.setExpanded(true);
							searchCommentInGroup(text, commentStyledText, indexGroup, group.getCommentViewers());
							for (StyledText styledText : commentStyledText) {
								highlightStyledText(styledText, text, 0);
								listStyledText.add(styledText);
							}
							group.clearSectionTextClient();
						} finally {
							setReflow(true);
						}
						reflow();
					}
				};
				group.createSectionHyperlink(
						NLS.bind(Messages.ExtensibleBugzillaTaskEditorPage_showNumResults, numResultsInGroup), listener);
			} else {
				group.clearSectionTextClient();
			}
			commentIndex = commentIndex - group.getCommentViewers().size();
		}
	}

	private boolean hasAnyResultInComments(List<TaskAttribute> commentAttributes, String text) {
		for (int i = 0; i < commentAttributes.size(); i++) {
			if (hasResultInComment(text, commentAttributes.get(i))) {
				return true;
			}
		}
		return false;
	}

	private boolean hasResultInComment(String text, TaskAttribute comment) {
		TaskAttribute attribute = comment.getMappedAttribute(TaskAttribute.COMMENT_TEXT);
		return attribute.getValue().toLowerCase().contains(text);
	}

	// Expands matching comments and add their StyledText to the list. Returns total results in a group.
	public int searchCommentInGroup(String text, List<StyledText> listStyledText, int commentIndex,
			List<CommentViewer> commentViewers) {
		int numResultsInGroup = 0;
		for (int i = commentViewers.size() - 1; i >= 0; i--) {
			CommentViewer viewer = commentViewers.get(i);
			try {
				ExpandableComposite composite = (ExpandableComposite) viewer.getControl();
				if (hasResultInComment(
						text,
						getModel().getTaskData()
								.getAttributeMapper()
								.getAttributesByType(getModel().getTaskData(), TaskAttribute.TYPE_COMMENT)
								.get(commentIndex))) {
					viewer.suppressSelectionChanged(true);
					if (composite != null && !composite.isExpanded()) {
						CommonFormUtil.setExpanded(composite, true);
					}
					findStyledText(composite, listStyledText);
					numResultsInGroup++;
				}
			} finally {
				viewer.suppressSelectionChanged(false);
			}
			commentIndex--;
		}
		return numResultsInGroup;
	}

	private void searchPart(List<StyledText> listStyledText, String text, String searchKey, Control control) {
		if (text != null && text.toLowerCase().contains(searchKey)) {
			if (control instanceof ExpandableComposite) {
				ExpandableComposite composite = (ExpandableComposite) control;
				if (composite != null && !composite.isExpanded()) {
					CommonFormUtil.setExpanded(composite, true);
				}
				findStyledText(composite, listStyledText);
			} else if (control instanceof Composite) {
				findStyledText((Composite) control, listStyledText);
			}
		}
	}

	private void findStyledText(Composite composite, List<StyledText> listStyledText) {
		if (composite != null && !composite.isDisposed()) {
			for (Control child : composite.getChildren()) {
				if (child instanceof StyledText) {
					listStyledText.add((StyledText) child);
					return;
				}
				if (child instanceof Composite) {
					findStyledText((Composite) child, listStyledText);
				}
			}
		}
	}

	private static void highlightStyledText(StyledText text, String findString, int startOffset) {
		if (startOffset >= text.getText().length() - 1 || text.getText() == null || text.getText().length() == 0) {
			return;
		}
		String textRange = text.getText(startOffset, text.getText().length() - 1);
		if (textRange.length() != 0) {
			textRange = textRange.toLowerCase();
			if (textRange.indexOf(findString) != -1) {
				int index = textRange.indexOf(findString) + startOffset;
				int length = findString.length();
				StyleRange highlightStyleRange = new StyleRange(index, length, null, HIGHLIGHTER_YELLOW);
				text.setStyleRange(highlightStyleRange);
				highlightStyledText(text, findString, index + length);
			}
		}
	}

	private void removePreviousHighlight() {
		for (StyledText oldText : textToSearchAndHighlight) {
			List<StyleRange> newRange = new ArrayList<StyleRange>();
			if (!oldText.isDisposed()) {
				for (StyleRange styleRange : oldText.getStyleRanges()) {
					if (styleRange.background == null || !styleRange.background.equals(HIGHLIGHTER_YELLOW)) {
						newRange.add(styleRange);
					}
				}
				oldText.setStyleRanges(newRange.toArray(new StyleRange[newRange.size()]));
			}
		}
		textToSearchAndHighlight.clear();
	}
}
