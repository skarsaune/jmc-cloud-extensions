package org.openjdk.jmc.jolokia;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;

public class JmcJolokiaJmxConnectionProvider implements JMXConnectorProvider {
	@Override
	public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map<String, ?> environment) throws IOException {
        if(!"jolokia".equals(serviceURL.getProtocol())) {
            throw new MalformedURLException("I only serve Jolokia connections");
        }
        return new JmcJolokiaJmxConnector(serviceURL, environment);
	}
}
