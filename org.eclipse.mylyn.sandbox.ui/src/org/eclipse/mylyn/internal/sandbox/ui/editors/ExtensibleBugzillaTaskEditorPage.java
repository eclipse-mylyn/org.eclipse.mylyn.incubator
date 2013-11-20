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

import java.util.Set;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.mylyn.internal.bugzilla.ui.editor.BugzillaTaskEditorPage;
import org.eclipse.mylyn.tasks.ui.editors.AbstractTaskEditorPart;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditorPartDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.actions.ActionFactory;

/**
 * A bugzilla task editor page that has wiki facilities.
 * 
 * @author Jingwen Ou
 */
public class ExtensibleBugzillaTaskEditorPage extends BugzillaTaskEditorPage {

	public ExtensibleBugzillaTaskEditorPage(TaskEditor editor) {
		super(editor);
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

	/*
	 * Find implementation. To be moved to AbstractTaskEditorPage.
	 */

	private TaskEditorFindSupport findSupport;

	@Override
	public void init(IEditorSite site, IEditorInput input) {
		super.init(site, input);
		findSupport = createFindSupport();
	}

	/**
	 * Subclasses may return null to disable the find functionality.
	 */
	protected TaskEditorFindSupport createFindSupport() {
		return new TaskEditorFindSupport(this);
	}

	@Override
	public boolean canPerformAction(String actionId) {
		if (findSupport != null && actionId.equals(ActionFactory.FIND.getId())) {
			return true;
		}
		return super.canPerformAction(actionId);
	}

	@Override
	public void doAction(String actionId) {
		if (findSupport != null && actionId.equals(ActionFactory.FIND.getId())) {
			findSupport.toggleFind();
		}
		super.doAction(actionId);
	}

	@Override
	public void fillToolBar(IToolBarManager toolBarManager) {
		super.fillToolBar(toolBarManager);
		if (findSupport != null) {
			findSupport.addFindAction(toolBarManager);
		}
	}
}
