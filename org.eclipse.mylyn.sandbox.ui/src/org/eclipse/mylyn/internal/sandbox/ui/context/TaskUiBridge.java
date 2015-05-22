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

package org.eclipse.mylyn.internal.sandbox.ui.context;

import java.util.Collections;
import java.util.List;

import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.mylyn.context.core.IInteractionElement;
import org.eclipse.mylyn.context.ui.AbstractContextUiBridge;
import org.eclipse.mylyn.tasks.ui.editors.TaskEditor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;

/**
 * @author Mik Kersten
 */
public class TaskUiBridge extends AbstractContextUiBridge {

	@Override
	public void open(IInteractionElement node) {
		// ignore
	}

	@Override
	public void close(IInteractionElement node) {
		// ignore
	}

	@Override
	public boolean acceptsEditor(IEditorPart editorPart) {
		return editorPart instanceof TaskEditor;
	}

	@Override
	public List<TreeViewer> getContentOutlineViewers(IEditorPart editorPart) {
		return Collections.emptyList();
	}

	@Override
	public Object getObjectForTextSelection(TextSelection selection, IEditorPart editor) {
		return null;
	}

	@Override
	public IInteractionElement getElement(IEditorInput input) {
		return null;
	}

	@Override
	public String getContentType() {
		return TaskStructureBridge.CONTENT_TYPE;
	}
}
