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
import java.util.UUID;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.mylyn.builds.core.IBuildPlan;
import org.eclipse.mylyn.builds.core.spi.BuildServerBehaviour;
import org.eclipse.mylyn.builds.core.spi.BuildServerConfiguration;
import org.eclipse.mylyn.commons.net.WebLocation;
import org.eclipse.mylyn.commons.repositories.RepositoryLocation;
import org.eclipse.mylyn.internal.commons.net.CommonsNetPlugin;
import org.eclipse.mylyn.internal.commons.repositories.InMemoryCredentialsStore;
import org.eclipse.mylyn.internal.hudson.core.HudsonConnector;
import org.eclipse.mylyn.internal.hudson.core.client.HudsonConfigurationCache;
import org.eclipse.mylyn.internal.hudson.core.client.HudsonException;
import org.eclipse.mylyn.internal.hudson.core.client.RestfulHudsonClient;
import org.eclipse.mylyn.internal.hudson.model.HudsonModelJob;
import org.eclipse.osgi.util.NLS;

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
		location.setProperty(RepositoryLocation.PROPERTY_ID, UUID.randomUUID().toString());
		location.setCredentialsStore(new InMemoryCredentialsStore(null));

		location.setProperty(RepositoryLocation.PROPERTY_URL, "http://ci.mylyn.org/");
//		AuthenticationCredentials credentials = new UsernamePasswordCredentials("username", "password");
//		location.setCredentials(org.eclipse.mylyn.commons.repositories.auth.AuthenticationType.REPOSITORY, credentials);

		HudsonConnector connector = new HudsonConnector();
		BuildServerBehaviour behavior = connector.getBehaviour(location);

		System.out.println(NLS.bind("= Listing all jobs on {0} =", location.getUrl()));
		BuildServerConfiguration configuration = behavior.refreshConfiguration(null);
		for (IBuildPlan plan : configuration.getPlans()) {
			System.out.println(plan.getName());
		}
	}

	private static void hudsonApiExample() throws HudsonException {
		WebLocation location = new WebLocation("http://ci.mylyn.org/");
//		location.setCredentials(AuthenticationType.HTTP, "username", "password");

		RestfulHudsonClient client = new RestfulHudsonClient(location, new HudsonConfigurationCache());

		System.out.println(NLS.bind("= Listing all jobs on {0} =", location.getUrl()));
		List<HudsonModelJob> jobs = client.getJobs(null, null);
		for (HudsonModelJob job : jobs) {
			System.out.println(job.getName());
		}
	}

}
