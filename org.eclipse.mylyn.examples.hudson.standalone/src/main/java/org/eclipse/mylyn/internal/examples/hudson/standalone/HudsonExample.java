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
package org.eclipse.mylyn.internal.examples.hudson.standalone;

import java.util.List;

/**
 * @author Steffen Pingel
 */
public class HudsonExample {

	public static void main(String[] args) throws Exception {
		System.out.println("== Demo using Hudson Connector API ==");
		hudsonApiExample();

		System.out.println();

		System.out.println("== Demo using Builds Framework API ==");
		framworkApiExample();

		// shutdown
		CommonsNetPlugin.getExecutorService().shutdown();
	}

	private static void framworkApiExample() throws CoreException {
		RepositoryLocation location = new RepositoryLocation();
		location.setUrl("http://ci.mylyn.org/");
		//location.setCredentials(AuthenticationType.REPOSITORY, new UserCredentials("username", "password"));

		BuildConnector connector = HudsonCore.createConnector(null);
		BuildServerBehaviour behavior = connector.getBehaviour(location);

		System.out.println(NLS.bind("= Listing all jobs on {0} =", location.getUrl()));
		BuildServerConfiguration configuration = behavior.refreshConfiguration(null);
		for (IBuildPlan plan : configuration.getPlans()) {
			System.out.println(plan.getName());
		}
	}

	private static void hudsonApiExample() throws HudsonException {
		RepositoryLocation location = new RepositoryLocation();
		location.setUrl("http://mylyn.org/jenkins-latest");
		//location.setCredentials(AuthenticationType.REPOSITORY, new UserCredentials("username", "password"));

		RestfulHudsonClient client = new RestfulHudsonClient(location, new HudsonConfigurationCache());

		System.out.println(NLS.bind("= Listing all jobs on {0} =", location.getUrl()));
		List<HudsonModelJob> jobs = client.getJobs(null, null);
		for (HudsonModelJob job : jobs) {
			System.out.println(job.getName());
		}
	}

}
