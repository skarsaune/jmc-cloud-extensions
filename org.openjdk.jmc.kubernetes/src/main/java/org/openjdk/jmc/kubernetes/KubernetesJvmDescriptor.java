package org.openjdk.jmc.kubernetes;

import java.io.IOException;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import org.openjdk.jmc.jolokia.ServerConnectionDescriptor;
import org.openjdk.jmc.ui.common.jvm.JVMDescriptor;

import io.fabric8.kubernetes.api.model.ObjectMeta;

public class KubernetesJvmDescriptor implements ServerConnectionDescriptor {
	
	private final JVMDescriptor jvmDescriptor;
	private final ObjectMeta metadata;
	private final Map<String, Object> env;
	private final JMXServiceURL connectUrl;
	
	public KubernetesJvmDescriptor(ObjectMeta metadata, JVMDescriptor jvmDescriptor, JMXServiceURL connectUrl, Map<String, Object> env) {
		this.jvmDescriptor = jvmDescriptor;
		this.metadata = metadata;	
		this.env = env;
		this.connectUrl = connectUrl;
	}

	@Override
	public String getGUID() {
		return this.metadata.getName();
	}

	@Override
	public String getDisplayName() {
		return this.metadata.getName();
	}

	@Override
	public JVMDescriptor getJvmInfo() {
		return this.jvmDescriptor;
	}


	public String getPath() {
		return metadata.getClusterName()+"/"+metadata.getNamespace();
	}


	@Override
	public JMXServiceURL createJMXServiceURL() throws IOException {
		return this.connectUrl;
	}


	@Override
	public Map<String, Object> getEnvironment() {
		return this.env;
	}


	@Override
	public JMXServiceURL serviceUrl() {
		return this.connectUrl;
	}

}
