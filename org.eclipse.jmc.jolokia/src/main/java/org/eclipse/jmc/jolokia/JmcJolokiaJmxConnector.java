package org.eclipse.jmc.jolokia;

import java.io.IOException;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import org.jolokia.client.J4pClientBuilder;
import org.jolokia.client.jmxadapter.JolokiaJmxConnector;
import org.jolokia.client.jmxadapter.RemoteJmxAdapter;

public class JmcJolokiaJmxConnector extends JolokiaJmxConnector {

	public JmcJolokiaJmxConnector(JMXServiceURL serviceURL, Map<String, ?> environment) {
		super(serviceURL, environment);

	}
	@Override
	protected RemoteJmxAdapter instantiateAdapter(J4pClientBuilder clientBuilder) throws IOException {
		//Override to ensure we get our own guy in here
		return new JmcJolokiaJmxConnection(clientBuilder.build());
	}
}
