/*******************************************************************************
 * Copyright (c) 2004 - 2005 University Of British Columbia and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     University Of British Columbia - initial API and implementation
 *******************************************************************************/

package org.eclipse.mylar.monitor.reports.internal;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.mylar.core.InteractionEvent;
import org.eclipse.mylar.monitor.reports.IUsageCollector;
import org.eclipse.mylar.monitor.reports.ReportGenerator;

/**
 * @author Mik Kersten and Leah Findlater
 */
public class CommandUsageCollector implements IUsageCollector {

    private InteractionByTypeSummary commands = new InteractionByTypeSummary();
    private Set<Integer> userIdSet = new HashSet<Integer>();
	
	public void consumeEvent(InteractionEvent event, int userId, String phase) {
        userIdSet.add(userId);
        if (event.getKind().equals(InteractionEvent.Kind.COMMAND)) {
        	commands.setUserCount(userId, ReportGenerator.getCleanOriginId(event), commands.getUserCount(userId, ReportGenerator.getCleanOriginId(event)) + 1);
        }
	}

	public List<String> getReport() {
		return Collections.emptyList();
	}

	public String getReportTitle() {
		return "Command Usage";
	}

	public void generateCsvFile(File file) {
		// TODO Auto-generated method stub
		
	}

	public InteractionByTypeSummary getCommands() {
		return commands;
	} 
}