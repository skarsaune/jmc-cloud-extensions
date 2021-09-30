package org.openjdk.jmc.kubernetes;

import java.io.IOException;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import org.jolokia.client.J4pClient;
import org.jolokia.client.jmxadapter.RemoteJmxAdapter;
import org.jolokia.kubernetes.client.KubernetesJmxConnector;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.PodResource;

public class JmcKubernetesJmxConnector extends KubernetesJmxConnector {

	private final PodResource<Pod, DoneablePod> podHandle;

	@SuppressWarnings("unchecked")
	public JmcKubernetesJmxConnector(JMXServiceURL serviceURL, Map<String, ?> environment) {
		super(serviceURL, environment);
		this.podHandle=(PodResource<Pod, DoneablePod>) environment.get(JmcKubernetesJmxConnection.JMC_POD_HANDLE);
	}
	
	@Override
	protected RemoteJmxAdapter createAdapter(J4pClient client) throws IOException {
		return new JmcKubernetesJmxConnection(client, this.podHandle);
	}
	
}
