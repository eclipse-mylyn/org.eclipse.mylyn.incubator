/*******************************************************************************
 * Copyright (c) 2011 Mylyn project committers and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.mylyn.internal.examples.bugzilla;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.mylyn.commons.net.AuthenticationCredentials;
import org.eclipse.mylyn.commons.net.AuthenticationType;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaCorePlugin;
import org.eclipse.mylyn.internal.bugzilla.core.BugzillaRepositoryConnector;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.TaskAttributeMapper;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.core.data.TaskMapper;

/**
 * @author Steffen Pingel
 */
public class CreateTask {

	private static final String URL = "http://mylyn.org/tractest";

	public static void main(String[] args) throws CoreException {
		// create task repository
		TaskRepository repository = new TaskRepository(BugzillaCorePlugin.CONNECTOR_KIND, URL);

		// set repository credentials
		if (args.length >= 2) {
			AuthenticationCredentials credentials = new AuthenticationCredentials(args[0], args[1]);
			repository.setCredentials(AuthenticationType.REPOSITORY, credentials, false);
		}

		// create bugzilla connector
		BugzillaRepositoryConnector connector = new BugzillaRepositoryConnector();

		// initialize task data
		TaskAttributeMapper attributeMapper = connector.getTaskDataHandler().getAttributeMapper(repository);
		TaskData taskData = new TaskData(attributeMapper, repository.getConnectorKind(), repository.getRepositoryUrl(),
				"");
		connector.getTaskDataHandler().initializeTaskData(repository, taskData, null, null);

		// set attributes
		TaskMapper mapping = connector.getTaskMapping(taskData);
		mapping.setSummary("new task");
		mapping.setDescription("Task created.");

		connector.getTaskDataHandler().postTaskData(repository, taskData, null, null);
	}

}
