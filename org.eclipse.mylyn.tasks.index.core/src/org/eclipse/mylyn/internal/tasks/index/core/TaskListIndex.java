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

package org.eclipse.mylyn.internal.tasks.index.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.Version;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.internal.tasks.core.AbstractTask;
import org.eclipse.mylyn.internal.tasks.core.ITaskListChangeListener;
import org.eclipse.mylyn.internal.tasks.core.ITaskListRunnable;
import org.eclipse.mylyn.internal.tasks.core.TaskContainerDelta;
import org.eclipse.mylyn.internal.tasks.core.TaskList;
import org.eclipse.mylyn.internal.tasks.core.data.ITaskDataManagerListener;
import org.eclipse.mylyn.internal.tasks.core.data.TaskDataManager;
import org.eclipse.mylyn.internal.tasks.core.data.TaskDataManagerEvent;
import org.eclipse.mylyn.tasks.core.IRepositoryElement;
import org.eclipse.mylyn.tasks.core.IRepositoryPerson;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskCommentMapper;
import org.eclipse.mylyn.tasks.core.data.TaskData;

/**
 * An index on a task list.
 * 
 * @author David Green
 */
public class TaskListIndex implements ITaskDataManagerListener, ITaskListChangeListener {

	public abstract static class TaskCollector {

		public abstract void collect(ITask task);

	}

	private static final Object COMMAND_RESET_INDEX = "index:reset"; //$NON-NLS-1$

	public static enum IndexField {
		IDENTIFIER(false, null), //
		TASK_KEY(false, null), //
		SUMMARY(true, null), //
		CONTENT(true, null), //
		ASSIGNEE(true, TaskAttribute.USER_ASSIGNED), //
		REPORTER(true, TaskAttribute.USER_REPORTER), //
		PERSON(true, null), //
		COMPONENT(true, TaskAttribute.COMPONENT), //
		COMPLETION_DATE(true, null), //
		CREATION_DATE(true, null), //
		DUE_DATE(true, null), //
		MODIFICATION_DATE(true, null), //
		DESCRIPTION(true, TaskAttribute.DESCRIPTION), //
		KEYWORDS(true, TaskAttribute.KEYWORDS), //
		PRODUCT(true, TaskAttribute.PRODUCT), //
		RESOLUTION(true, TaskAttribute.RESOLUTION), //
		SEVERITY(true, TaskAttribute.SEVERITY), //
		STATUS(true, TaskAttribute.STATUS);

		private final boolean userVisible;

		private final String attributeId;

		private IndexField(boolean userVisible, String attributeId) {
			this.userVisible = userVisible;
			this.attributeId = attributeId;
		}

		public String fieldName() {
			return name().toLowerCase();
		}

		/**
		 * get the task attribute id, or null if this field has special handling
		 */
		public String getAttributeId() {
			return attributeId;
		}

		public boolean isUserVisible() {
			return userVisible;
		}

		public static IndexField fromFieldName(String fieldName) {
			try {
				return IndexField.valueOf(fieldName.toUpperCase());
			} catch (IllegalArgumentException e) {
				return null;
			}
		}

		public boolean isPersonField() {
			return this == PERSON || this == REPORTER || this == ASSIGNEE;
		}
	}

	private static enum MaintainIndexType {
		STARTUP, REINDEX
	}

	private Directory directory;

	private MaintainIndexJob maintainIndexJob;

	private final Map<ITask, TaskData> reindexQueue = new HashMap<ITask, TaskData>();

	private IndexReader indexReader;

	private boolean rebuildIndex = false;

	private String lastPatternString;

	private Set<String> lastResults;

	private IndexField defaultField = IndexField.SUMMARY;

	private final TaskList taskList;

	private final TaskDataManager dataManager;

	private long startupDelay = 6000L;

	private long reindexDelay = 3000L;

	private int maxMatchSearchHits = 1500;

	private TaskListIndex(TaskList taskList, TaskDataManager dataManager) {
		if (taskList == null) {
			throw new IllegalArgumentException();
		}
		if (dataManager == null) {
			throw new IllegalArgumentException();
		}
		this.taskList = taskList;
		this.dataManager = dataManager;
	}

	public TaskListIndex(TaskList taskList, TaskDataManager dataManager, File indexLocation) {
		this(taskList, dataManager, indexLocation, 6000L);
	}

	public TaskListIndex(TaskList taskList, TaskDataManager dataManager, File indexLocation, long startupDelay) {
		this(taskList, dataManager);
		if (startupDelay < 0L || startupDelay > (1000L * 60)) {
			throw new IllegalArgumentException();
		}
		if (indexLocation == null) {
			throw new IllegalArgumentException();
		}
		this.startupDelay = startupDelay;
		if (!indexLocation.exists()) {
			rebuildIndex = true;
			indexLocation.mkdirs();
		}
		if (indexLocation.exists() && indexLocation.isDirectory()) {
			try {
				Logger.getLogger(TaskListIndex.class.getName()).fine("task list index: " + indexLocation); //$NON-NLS-1$

				directory = new NIOFSDirectory(indexLocation);
			} catch (IOException e) {
				StatusHandler.log(new Status(IStatus.ERROR, TasksIndexCore.BUNDLE_ID,
						"Cannot create task list index", e)); //$NON-NLS-1$
			}
		}
		initialize();
	}

	public TaskListIndex(TaskList taskList, TaskDataManager dataManager, Directory directory) {
		this(taskList, dataManager);
		this.directory = directory;
		initialize();
	}

	public long getReindexDelay() {
		return reindexDelay;
	}

	public void setReindexDelay(long reindexDelay) {
		this.reindexDelay = reindexDelay;
	}

	public IndexField getDefaultField() {
		return defaultField;
	}

	public void setDefaultField(IndexField defaultField) {
		this.defaultField = defaultField;
		lastResults = null;
	}

	public int getMaxMatchSearchHits() {
		return maxMatchSearchHits;
	}

	public void setMaxMatchSearchHits(int maxMatchSearchHits) {
		this.maxMatchSearchHits = maxMatchSearchHits;
	}

	private void initialize() {
		if (!rebuildIndex) {
			IndexReader indexReader = null;
			try {
				indexReader = getIndexReader();
			} catch (Exception e) {
				// ignore, this can happen if the index is corrupt
			}
			if (indexReader == null) {
				rebuildIndex = true;
			}
		}
		maintainIndexJob = new MaintainIndexJob();
		dataManager.addListener(this);
		taskList.addChangeListener(this);

		scheduleIndexMaintenance(MaintainIndexType.STARTUP);
	}

	private void scheduleIndexMaintenance(MaintainIndexType type) {
		long delay = 0L;
		switch (type) {
		case STARTUP:
			delay = startupDelay;
			break;
		case REINDEX:
			delay = reindexDelay;
		}

		if (delay == 0L) {
			// primarily for testing purposes

			maintainIndexJob.cancel();
			try {
				maintainIndexJob.join();
			} catch (InterruptedException e) {
				// ignore
			}
			maintainIndexJob.run(new NullProgressMonitor());
		} else {
			maintainIndexJob.schedule(delay);
		}
	}

	public boolean matches(ITask task, String patternString) {
		if (patternString.equals(COMMAND_RESET_INDEX)) {
			reindex();
		}
		IndexReader indexReader = getIndexReader();
		if (indexReader != null) {
			Set<String> hits;

			synchronized (indexReader) {

				if (lastResults == null || (lastPatternString == null || !lastPatternString.equals(patternString))) {
					this.lastPatternString = patternString;

					long startTime = System.currentTimeMillis();

					hits = new HashSet<String>();

					IndexSearcher indexSearcher = new IndexSearcher(indexReader);
					try {
						Query query = computeQuery(patternString);
						TopDocs results = indexSearcher.search(query, maxMatchSearchHits);
						for (ScoreDoc scoreDoc : results.scoreDocs) {
							Document document = indexReader.document(scoreDoc.doc);
							hits.add(document.get(IndexField.IDENTIFIER.fieldName()));
						}
						lastResults = hits;
					} catch (IOException e) {
						StatusHandler.fail(new Status(IStatus.ERROR, TasksIndexCore.BUNDLE_ID,
								"Unexpected failure within task list index", e)); //$NON-NLS-1$
					} finally {
						try {
							indexSearcher.close();
						} catch (IOException e) {
							// ignore
						}
					}

					Logger.getLogger(TaskListIndex.class.getName()).fine(
							"New query in " + (System.currentTimeMillis() - startTime) + "ms"); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					hits = lastResults;
				}
			}
			String taskIdentifier = task.getHandleIdentifier();
			return hits != null && hits.contains(taskIdentifier);
		}
		return false;
	}

	public void reindex() {
		rebuildIndex = true;
		scheduleIndexMaintenance(MaintainIndexType.REINDEX);
	}

	/**
	 * call to wait until index maintenance has completed
	 * 
	 * @throws InterruptedException
	 */
	public void waitUntilIdle() throws InterruptedException {
		if (!Platform.isRunning() && reindexDelay != 0L) {
			// job join() behaviour is not the same when platform is not running
			Logger.getLogger(TaskListIndex.class.getName()).warning(
					"Index job joining may not work properly when Eclipse platform is not running"); //$NON-NLS-1$
		}
		maintainIndexJob.join();
	}

	public void find(String patternString, TaskCollector collector, int resultsLimit) {
		IndexReader indexReader = getIndexReader();
		if (indexReader != null) {
			IndexSearcher indexSearcher = new IndexSearcher(indexReader);
			try {
				Query query = computeQuery(patternString);
				TopDocs results = indexSearcher.search(query, resultsLimit);
				for (ScoreDoc scoreDoc : results.scoreDocs) {
					Document document = indexReader.document(scoreDoc.doc);
					String taskIdentifier = document.get(IndexField.IDENTIFIER.fieldName());
					AbstractTask task = taskList.getTask(taskIdentifier);
					if (task != null) {
						collector.collect(task);
					}
				}
			} catch (IOException e) {
				StatusHandler.fail(new Status(IStatus.ERROR, TasksIndexCore.BUNDLE_ID,
						"Unexpected failure within task list index", e)); //$NON-NLS-1$
			} finally {
				try {
					indexSearcher.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private Query computeQuery(String patternString) {
		String upperPatternString = patternString.toUpperCase();

		boolean hasBooleanSpecifiers = upperPatternString.contains(" OR ") || upperPatternString.contains(" AND ") //$NON-NLS-1$ //$NON-NLS-2$
				|| upperPatternString.contains(" NOT "); //$NON-NLS-1$

		if (patternString.indexOf(':') == -1 && !hasBooleanSpecifiers && defaultField == IndexField.SUMMARY
				&& patternString.indexOf('"') == -1) {
			return new PrefixQuery(new Term(defaultField.fieldName(), patternString));
		}
		QueryParser qp = new QueryParser(Version.LUCENE_CURRENT, defaultField.fieldName(), new StandardAnalyzer(
				Version.LUCENE_CURRENT));
		Query q;
		try {
			q = qp.parse(patternString);
		} catch (ParseException e) {
			return new PrefixQuery(new Term(defaultField.fieldName(), patternString));
		}

		// relax term clauses to be prefix clauses so that we get results close
		// to what we're expecting
		// from previous task list search
		if (q instanceof BooleanQuery) {
			BooleanQuery query = (BooleanQuery) q;
			for (BooleanClause clause : query.getClauses()) {
				if (clause.getQuery() instanceof TermQuery) {
					TermQuery termQuery = (TermQuery) clause.getQuery();
					clause.setQuery(new PrefixQuery(termQuery.getTerm()));
				}
				if (!hasBooleanSpecifiers) {
					clause.setOccur(Occur.MUST);
				}
			}
		} else if (q instanceof TermQuery) {
			return new PrefixQuery(((TermQuery) q).getTerm());
		}
		return q;
	}

	public void close() {
		dataManager.removeListener(this);
		taskList.removeChangeListener(this);

		maintainIndexJob.cancel();
		try {
			maintainIndexJob.join();
		} catch (InterruptedException e) {
			// ignore
		}

		synchronized (this) {
			if (indexReader != null) {
				synchronized (indexReader) {
					try {
						indexReader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				indexReader = null;
			}
		}
		try {
			directory.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private IndexReader getIndexReader() {
		try {
			synchronized (this) {
				if (indexReader == null) {
					indexReader = IndexReader.open(directory, true);
				}
				return indexReader;
			}
		} catch (CorruptIndexException e) {
			rebuildIndex = true;
			if (maintainIndexJob != null) {
				scheduleIndexMaintenance(MaintainIndexType.REINDEX);
			}
		} catch (FileNotFoundException e) {
			rebuildIndex = true;
			// expected if the index doesn't exist
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public void taskDataUpdated(TaskDataManagerEvent event) {
		reindex(event.getTask(), event.getTaskData());
	}

	public void editsDiscarded(TaskDataManagerEvent event) {
		reindex(event.getTask(), event.getTaskData());
	}

	public void containersChanged(Set<TaskContainerDelta> containers) {
		for (TaskContainerDelta delta : containers) {
			switch (delta.getKind()) {
			case ADDED:
			case REMOVED:
			case CONTENT:
				IRepositoryElement element = delta.getElement();
				if (element instanceof ITask) {
					ITask task = (ITask) element;
					if ("local".equals(((AbstractTask) task).getConnectorKind())) { //$NON-NLS-1$
						reindex(task, null);
					}
				}
			}
		}
	}

	private void reindex(ITask task, TaskData taskData) {
		if (task == null) {
			// this can happen when edits are discarded
			return;
		}
		synchronized (reindexQueue) {
			reindexQueue.put(task, taskData);
		}
		scheduleIndexMaintenance(MaintainIndexType.REINDEX);
	}

	private void addIndexedAttributes(Document document, ITask task, TaskAttribute root) {
		addIndexedAttribute(document, IndexField.SUMMARY, root.getMappedAttribute(TaskAttribute.SUMMARY));
		addIndexedAttribute(document, IndexField.TASK_KEY, task.getTaskKey());
		addIndexedAttribute(document, IndexField.CONTENT, root.getMappedAttribute(TaskAttribute.SUMMARY));
		addIndexedAttribute(document, IndexField.CONTENT, root.getMappedAttribute(TaskAttribute.DESCRIPTION));
		addIndexedAttribute(document, IndexField.CONTENT, root.getAttribute("status_whiteboard")); //$NON-NLS-1$

		addIndexedDateAttributes(document, task);

		List<TaskAttribute> commentAttributes = root.getTaskData()
				.getAttributeMapper()
				.getAttributesByType(root.getTaskData(), TaskAttribute.TYPE_COMMENT);
		for (TaskAttribute commentAttribute : commentAttributes) {
			TaskCommentMapper commentMapper = TaskCommentMapper.createFrom(commentAttribute);
			String text = commentMapper.getText();
			if (text.length() != 0) {
				addIndexedAttribute(document, IndexField.CONTENT, text);
			}
			IRepositoryPerson author = commentMapper.getAuthor();
			if (author != null) {
				addIndexedAttribute(document, IndexField.PERSON, author.getPersonId());
			}
		}

		List<TaskAttribute> personAttributes = root.getTaskData()
				.getAttributeMapper()
				.getAttributesByType(root.getTaskData(), TaskAttribute.TYPE_PERSON);
		for (TaskAttribute personAttribute : personAttributes) {
			addIndexedAttribute(document, IndexField.PERSON, personAttribute);
		}

		for (IndexField field : IndexField.values()) {
			if (field.getAttributeId() != null) {
				addIndexedAttribute(document, field, root.getMappedAttribute(field.getAttributeId()));
			}
		}
	}

	private void addIndexedAttributes(Document document, ITask task) {
		addIndexedAttribute(document, IndexField.SUMMARY, task.getSummary());
		addIndexedAttribute(document, IndexField.TASK_KEY, task.getTaskKey());
		addIndexedAttribute(document, IndexField.CONTENT, task.getSummary());
		addIndexedAttribute(document, IndexField.CONTENT, ((AbstractTask) task).getNotes());
		addIndexedDateAttributes(document, task);
	}

	private void addIndexedDateAttributes(Document document, ITask task) {
		addIndexedAttribute(document, IndexField.COMPLETION_DATE, task.getCompletionDate());
		addIndexedAttribute(document, IndexField.CREATION_DATE, task.getCreationDate());
		addIndexedAttribute(document, IndexField.DUE_DATE, task.getDueDate());
		addIndexedAttribute(document, IndexField.MODIFICATION_DATE, task.getModificationDate());
	}

	private void addIndexedAttribute(Document document, IndexField indexField, TaskAttribute attribute) {
		if (attribute == null) {
			return;
		}
		// if (indexField == IndexField.ASSIGNEE) {
		// System.out.println(indexField + "=" + attribute.getValue());
		// }
		List<String> values = attribute.getValues();
		for (String value : values) {
			if (value.length() != 0) {
				addIndexedAttribute(document, indexField, value);
			}
		}
	}

	private void addIndexedAttribute(Document document, IndexField indexField, String value) {
		if (value == null) {
			return;
		}
		Field field = document.getField(indexField.fieldName());
		if (field == null) {
			field = new Field(indexField.fieldName(), value, Store.YES, org.apache.lucene.document.Field.Index.ANALYZED);
			document.add(field);
		} else {
			String existingValue = field.stringValue();
			if (indexField != IndexField.PERSON || !existingValue.contains(value)) {
				field.setValue(existingValue + " " + value); //$NON-NLS-1$
			}
		}
	}

	private void addIndexedAttribute(Document document, IndexField indexField, Date date) {
		if (date == null) {
			return;
		}
		// FIXME: date tools converts dates to GMT, and we don't really want that.  So
		// move the date by the GMT offset if there is any

		String value = DateTools.dateToString(date, Resolution.HOUR);
		Field field = document.getField(indexField.fieldName());
		if (field == null) {
			field = new Field(indexField.fieldName(), value, Store.YES, org.apache.lucene.document.Field.Index.ANALYZED);
			document.add(field);
		} else {
			field.setValue(value);
		}
	}

	private class MaintainIndexJob extends Job {

		public MaintainIndexJob() {
			super(Messages.TaskListIndex_indexerJob);
			setUser(false);
			setSystem(false); // true?
			setPriority(Job.LONG);
		}

		@Override
		public IStatus run(IProgressMonitor m) {
			final int WORK_PER_SEGMENT = 1000;
			SubMonitor monitor = SubMonitor.convert(m, 3 * WORK_PER_SEGMENT);
			try {
				try {
					if (monitor.isCanceled()) {
						return Status.CANCEL_STATUS;
					}
					if (!rebuildIndex) {
						try {
							IndexReader reader = IndexReader.open(directory, false);
							reader.close();
						} catch (CorruptIndexException e) {
							rebuildIndex = true;
						}
					}

					if (rebuildIndex) {
						synchronized (reindexQueue) {
							reindexQueue.clear();
						}

						SubMonitor reindexMonitor = monitor.newChild(WORK_PER_SEGMENT);

						final IndexWriter writer = new IndexWriter(directory, new TaskAnalyzer(), true,
								IndexWriter.MaxFieldLength.UNLIMITED);
						try {

							final List<ITask> allTasks = new ArrayList<ITask>(5000);

							taskList.run(new ITaskListRunnable() {
								public void execute(IProgressMonitor monitor) throws CoreException {
									allTasks.addAll(taskList.getAllTasks());
								}
							}, monitor.newChild(1));

							int reindexErrorCount = 0;

							reindexMonitor.beginTask(Messages.TaskListIndex_task_rebuildingIndex, allTasks.size());
							for (ITask task : allTasks) {
								try {
									TaskData taskData = dataManager.getTaskData(task);
									add(writer, task, taskData);

									reindexMonitor.worked(1);
								} catch (CoreException e) {
									// an individual task data error should not prevent the index from updating
									// but don't flood the log in the case of multiple errors
									if (reindexErrorCount++ == 0) {
										StatusHandler.log(e.getStatus());
									}
								} catch (IOException e) {
									throw e;
								}
							}
							synchronized (TaskListIndex.this) {
								rebuildIndex = false;
							}
						} finally {
							writer.close();
							reindexMonitor.done();
						}
					} else {
						monitor.worked(WORK_PER_SEGMENT);
					}
					for (;;) {

						synchronized (reindexQueue) {
							if (reindexQueue.isEmpty()) {
								break;
							}
						}

						Map<ITask, TaskData> queue = new HashMap<ITask, TaskData>();

						IndexReader reader = IndexReader.open(directory, false);
						try {
							synchronized (reindexQueue) {
								queue.putAll(reindexQueue);
								for (ITask task : queue.keySet()) {
									reindexQueue.remove(task);
								}
							}
							Iterator<Entry<ITask, TaskData>> it = queue.entrySet().iterator();
							while (it.hasNext()) {
								Entry<ITask, TaskData> entry = it.next();

								reader.deleteDocuments(new Term(IndexField.IDENTIFIER.fieldName(), entry.getKey()
										.getHandleIdentifier()));

							}
						} finally {
							reader.close();
						}
						monitor.worked(WORK_PER_SEGMENT);

						IndexWriter writer = new IndexWriter(directory, new TaskAnalyzer(), false,
								IndexWriter.MaxFieldLength.UNLIMITED);
						try {
							for (Entry<ITask, TaskData> entry : queue.entrySet()) {
								ITask task = entry.getKey();
								TaskData taskData = entry.getValue();

								add(writer, task, taskData);
							}
						} finally {
							writer.close();
						}
						monitor.worked(WORK_PER_SEGMENT);
					}
					synchronized (TaskListIndex.this) {
						indexReader = null;
					}
				} catch (CoreException e) {
					throw e;
				} catch (Throwable e) {
					throw new CoreException(new Status(IStatus.ERROR, TasksIndexCore.BUNDLE_ID,
							"Unexpected exception: " + e.getMessage(), e)); //$NON-NLS-1$
				}
			} catch (CoreException e) {
				MultiStatus logStatus = new MultiStatus(TasksIndexCore.BUNDLE_ID, 0,
						"Failed to update task list index", e); //$NON-NLS-1$
				logStatus.add(e.getStatus());
				StatusHandler.log(logStatus);
			} finally {
				monitor.done();
			}
			return Status.OK_STATUS;
		}

		/**
		 * @param writer
		 * @param task
		 *            the task
		 * @param taskData
		 *            may be null for local tasks
		 * @throws CorruptIndexException
		 * @throws IOException
		 */
		private void add(IndexWriter writer, ITask task, TaskData taskData) throws CorruptIndexException, IOException {

			Document document = new Document();

			document.add(new Field(IndexField.IDENTIFIER.fieldName(), task.getHandleIdentifier(), Store.YES,
					org.apache.lucene.document.Field.Index.ANALYZED));
			if (taskData == null) {
				if ("local".equals(((AbstractTask) task).getConnectorKind())) { //$NON-NLS-1$
					addIndexedAttributes(document, task);
				} else {
					return;
				}
			} else {
				addIndexedAttributes(document, task, taskData.getRoot());
			}
			writer.addDocument(document);
		}

	}

}
