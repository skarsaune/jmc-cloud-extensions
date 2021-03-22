package org.openjdk.jmc.kubernetes;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import org.eclipse.core.runtime.Platform;
import org.jolokia.client.J4pClient;
import org.jolokia.kubernetes.client.KubernetesJmxConnector;
import org.jolokia.util.AuthorizationHeaderParser;
import org.jolokia.util.Base64Util;
import org.openjdk.jmc.jolokia.AbstractCachedDescriptorProvider;
import org.openjdk.jmc.jolokia.JolokiaAgentDescriptor;
import org.openjdk.jmc.jolokia.ServerConnectionDescriptor;
import org.openjdk.jmc.kubernetes.preferences.KubernetesScanningParameters;
import org.openjdk.jmc.ui.common.jvm.JVMDescriptor;
import org.osgi.framework.FrameworkUtil;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;

public class KubernetesDiscoveryListener extends AbstractCachedDescriptorProvider {

	public String getDescription() {
		return "List JVM in kubernetes cluster";
	}

	boolean notEmpty(String value) {
		return value != null && value.length() > 0;
	}


	@Override
	protected Map<String, ServerConnectionDescriptor> discoverJvms() {
		Map<String, ServerConnectionDescriptor> found=new HashMap<>();
		KubernetesScanningParameters parameters = JmcKubernetesPlugin.getDefault();
		KubernetesClient client = KubernetesJmxConnector.getApiClient();
		FilterWatchListMultiDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> query = client.pods().inAnyNamespace();
		List<Pod> podList;
		String pathLabel = parameters.requireLabel();
		boolean hasPathLabel = notEmpty(pathLabel);
		if(hasPathLabel) {
			podList=query.withLabel(pathLabel).list().getItems();
		} else {
			podList=query.list().getItems();
		}
		for (Pod pod : podList) {
			
			HashMap<String,String> headers=new HashMap<>();
			if(notEmpty(parameters.username())) {
				if(!notEmpty(parameters.password())) {
					throw new IllegalArgumentException("Password must be specified when username is specified");
				}
				headers.put(AuthorizationHeaderParser.JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER,"Basic " + Base64Util
				          .encode(( parameters.username() + ":" + parameters.password()).getBytes()));
			}
			final ObjectMeta metadata = pod.getMetadata();
			final StringBuilder url=new StringBuilder(metadata.getSelfLink());
			final StringBuilder jmxUrl=new StringBuilder("service:jmx:kubernetes:///")
					.append(metadata.getNamespace()).append('/').append(metadata.getName());
			if(notEmpty(parameters.portAnnotation())) {
				String port = metadata.getAnnotations().get(parameters.portAnnotation());
				if(port!=null) {					
					url.append(":").append(port);
					jmxUrl.append(':').append(port);				
				}
			}
			url.append("/proxy");
		
			if(notEmpty(parameters.pathAnnotation())) {
				String path = metadata.getAnnotations().get(parameters.pathAnnotation());
				if(!path.startsWith("/")) {
					path="/"+path;
				}
				url.append(path);
				jmxUrl.append(path);
			}
			
			Map<String, Object> env = Collections.emptyMap();
			J4pClient jvmClient = KubernetesJmxConnector.probeProxyPath(env, client, url, headers);
			if(jvmClient != null) {
				JmcKubernetesJmxConnection connection;
				try {
					connection = new JmcKubernetesJmxConnection(jvmClient);
					JVMDescriptor jvmDescriptor = JolokiaAgentDescriptor.attemptToGetJvmInfo(connection);
					JMXServiceURL jmxServiceURL = new JMXServiceURL(jmxUrl.toString());
					KubernetesJvmDescriptor descriptor = new KubernetesJvmDescriptor(metadata, jvmDescriptor, jmxServiceURL, env);
					found.put(descriptor.getGUID(), descriptor);
				} catch (IOException e) {
					Platform.getLog(FrameworkUtil.getBundle(getClass())).error("Error connecting to JVM in pod", e);

				}				 
			}
		}
		return found;
	}


	@Override
	protected boolean isEnabled() {
		return JmcKubernetesPlugin.getDefault().scanForInstances();
	}
}
