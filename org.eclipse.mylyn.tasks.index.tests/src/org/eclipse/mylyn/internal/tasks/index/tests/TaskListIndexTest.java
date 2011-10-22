/*******************************************************************************
 * Copyright (c) 2011 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Tasktop Technologies - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylyn.internal.tasks.index.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.mylyn.internal.tasks.core.LocalTask;
import org.eclipse.mylyn.internal.tasks.index.core.TaskListIndex;
import org.eclipse.mylyn.internal.tasks.index.core.TaskListIndex.IndexField;
import org.eclipse.mylyn.internal.tasks.index.core.TaskListIndex.TaskCollector;
import org.eclipse.mylyn.internal.tasks.index.tests.util.MockTestContext;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.core.data.TaskMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author David Green
 */
public class TaskListIndexTest {

	private static class TestTaskCollector extends TaskCollector {

		private final List<ITask> tasks = new ArrayList<ITask>();

		@Override
		public void collect(ITask task) {
			tasks.add(task);
		}

		public List<ITask> getTasks() {
			return tasks;
		}
	}

	private MockTestContext context;

	private TaskListIndex index;

	private File tempDir;

	@Before
	public void setup() throws IOException {
		tempDir = File.createTempFile(TaskListIndexTest.class.getSimpleName(), ".tmp");
		tempDir.delete();
		tempDir.mkdirs();

		assertTrue(tempDir.exists() && tempDir.isDirectory());

		context = new MockTestContext();
	}

	@After
	public void tearDown() {
		if (index != null) {
			try {
				index.waitUntilIdle();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			index.close();
			index = null;
		}
		if (tempDir != null) {
			delete(tempDir);
			assertFalse(tempDir.exists());
		}
	}

	private void delete(File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					delete(child);
				}
			}
		}
		file.delete();
	}

	private void setupIndex() {
		index = new TaskListIndex(context.getTaskList(), context.getDataManager(), tempDir, 0L);
		index.setDefaultField(IndexField.CONTENT);
		index.setReindexDelay(0L);
	}

	@Test
	public void testMatchesLocalTaskOnSummary() throws InterruptedException {
		setupIndex();

		ITask task = context.createLocalTask();

		index.waitUntilIdle();

		index.setDefaultField(IndexField.CONTENT);

		assertTrue(index.matches(task, task.getSummary()));
		assertFalse(index.matches(task, "" + System.currentTimeMillis()));

		index.setDefaultField(IndexField.SUMMARY);

		assertTrue(index.matches(task, task.getSummary()));
		assertFalse(index.matches(task, "" + System.currentTimeMillis()));
	}

	@Test
	public void testMatchesLocalTaskOnDescription() throws InterruptedException {
		setupIndex();

		ITask task = context.createLocalTask();

		index.waitUntilIdle();

		index.setDefaultField(IndexField.CONTENT);

		assertTrue(index.matches(task, ((LocalTask) task).getNotes()));
		assertFalse(index.matches(task, "unlikely-akjfsaow"));

		index.setDefaultField(IndexField.SUMMARY);

		assertFalse(index.matches(task, ((LocalTask) task).getNotes()));
	}

	@Test
	public void testMatchesRepositoryTaskOnSummary() throws InterruptedException, CoreException {
		setupIndex();

		ITask task = context.createRepositoryTask();

		index.waitUntilIdle();

		index.setDefaultField(IndexField.CONTENT);

		assertTrue(index.matches(task, task.getSummary()));
		assertFalse(index.matches(task, "" + System.currentTimeMillis()));

		index.setDefaultField(IndexField.SUMMARY);

		assertTrue(index.matches(task, task.getSummary()));
		assertFalse(index.matches(task, "" + System.currentTimeMillis()));
	}

	@Test
	public void testMatchesRepositoryTaskOnDescription() throws InterruptedException, CoreException {
		setupIndex();

		ITask task = context.createRepositoryTask();

		index.waitUntilIdle();

		index.setDefaultField(IndexField.CONTENT);

		TaskData taskData = context.getDataManager().getTaskData(task);
		assertNotNull(taskData);

		TaskMapper taskMapping = context.getMockRepositoryConnector().getTaskMapping(taskData);

		assertTrue(index.matches(task, taskMapping.getDescription()));
		assertFalse(index.matches(task, "unlikely-akjfsaow"));

		index.setDefaultField(IndexField.SUMMARY);

		assertFalse(index.matches(task, taskMapping.getDescription()));
	}

	@Test
	public void testFind() throws InterruptedException {
		setupIndex();

		ITask task = context.createLocalTask();

		index.waitUntilIdle();

		index.setDefaultField(IndexField.SUMMARY);

		TestTaskCollector collector = new TestTaskCollector();
		index.find(task.getSummary(), collector, 1000);

		assertEquals(1, collector.getTasks().size());
		assertTrue(collector.getTasks().contains(task));
	}

	@Test
	public void testMatchesRepositoryTaskOnCreationDate() throws InterruptedException, CoreException {
		setupIndex();

		ITask task = context.createRepositoryTask();

		Date creationDate = task.getCreationDate();
		assertNotNull(creationDate);

		index.waitUntilIdle();

		assertFalse(index.matches(task, IndexField.CREATION_DATE.fieldName() + ":[20010101 TO 20010105]"));

		String matchDate = new SimpleDateFormat("yyyyMMdd").format(creationDate);
		matchDate = Integer.toString(Integer.parseInt(matchDate) + 2);

		String patternString = IndexField.CREATION_DATE.fieldName() + ":[20111019 TO " + matchDate + "]";

		System.out.println(patternString);

		assertTrue(index.matches(task, patternString));
	}
}
