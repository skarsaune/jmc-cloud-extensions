package org.openjdk.jmc.jolokia;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.TabularDataSupport;
import javax.management.remote.JMXServiceURL;

import org.jolokia.client.jmxadapter.RemoteJmxAdapter;
import org.json.simple.JSONObject;
import org.openjdk.jmc.ui.common.jvm.Connectable;
import org.openjdk.jmc.ui.common.jvm.JVMArch;
import org.openjdk.jmc.ui.common.jvm.JVMDescriptor;
import org.openjdk.jmc.ui.common.jvm.JVMType;

public class JolokiaAgentDescriptor implements ServerConnectionDescriptor {

	public static final JVMDescriptor NULL_DESCRIPTOR = new JVMDescriptor(null, null, null, null, null, null, false,
			Connectable.UNKNOWN);
	private final JMXServiceURL serviceUrl;
	private final JSONObject agentData;
	private final JVMDescriptor jvmDescriptor;

	public JolokiaAgentDescriptor(JSONObject agentData, JVMDescriptor jvmDescriptor)
			throws URISyntaxException, MalformedURLException {
		super();
		URI uri = new URI((String) agentData.get("url"));
		this.serviceUrl = new JMXServiceURL(
				String.format("service:jmx:jolokia://%s:%s%s", uri.getHost(), uri.getPort(), uri.getPath()));
		this.agentData = agentData;
		this.jvmDescriptor = jvmDescriptor;
	}

	JMXServiceURL getServiceUrl() {
		return serviceUrl;
	}

	@Override
	public String getGUID() {
		return String.valueOf(agentData.get("agent_id"));
	}

	@Override
	public String getDisplayName() {
		return String.valueOf(agentData.get("agent_id"));
	}

	@Override
	public JVMDescriptor getJvmInfo() {
		return this.jvmDescriptor;
	}

	/**
	 * Best effort to extract JVM information from a connection if everything works.
	 * Can be adjusted to support different flavors of JVM
	 */
	public static JVMDescriptor attemptToGetJvmInfo(RemoteJmxAdapter adapter) {

		try {
			AttributeList attributes = adapter.getAttributes(new ObjectName(ManagementFactory.RUNTIME_MXBEAN_NAME),
					new String[] { "Pid", "Name", "InputArguments", "SystemProperties" });
			Integer pid = null;
			String arguments = null;
			String javaCommand = null;
			String javaVersion = null;
			boolean isDebug = false;
			JVMType type = JVMType.UNKNOWN;
			JVMArch arch = JVMArch.UNKNOWN;
			for (Attribute attribute : attributes.asList()) {
				// newer JVM have pid as separate attribute, older have to parse from name
				if (attribute.getName().equalsIgnoreCase("Pid")) {
					try {
						pid = Integer.valueOf(String.valueOf(attribute.getValue()));
					} catch (NumberFormatException ignore) {
					}
				} else if (attribute.getName().equalsIgnoreCase("Name") && pid == null) {
					String pidAndHost = String.valueOf(attribute.getValue());
					int separator = pidAndHost.indexOf('@');
					if (separator > 0) {
						try {
							pid = Integer.valueOf(pidAndHost.substring(0, separator));
						} catch (NumberFormatException e) {
						}
					}
				} else if (attribute.getName().equalsIgnoreCase("InputArguments")) {

					if (attribute.getValue() instanceof String[]) {
						arguments = Arrays.toString((String[]) attribute.getValue());
					} else {
						arguments = String.valueOf(attribute.getValue());
					}
					if (arguments.contains("-agentlib:jdwp")) {
						isDebug = true;
					}
				} else if (attribute.getName().equalsIgnoreCase("SystemProperties")
						&& attribute.getValue() instanceof TabularDataSupport) {
					TabularDataSupport systemProperties = (TabularDataSupport) attribute.getValue();

					// iterate over properties as we need to use the exact key, which is non trivial
					// to reproduce
					for (Object entry : systemProperties.values()) {
						String key = ((CompositeDataSupport) entry).get("key").toString();
						String value = ((CompositeDataSupport) entry).get("value").toString();
						if (key.equalsIgnoreCase("sun.management.compiler")) {
							if (value.toLowerCase().contains("hotspot")) {
								type = JVMType.HOTSPOT;
							}
						} else if (key.equalsIgnoreCase("sun.arch.data.model")) {
							String archIndicator = value;
							if (archIndicator.contains("64")) {
								arch = JVMArch.BIT64;
							} else if (archIndicator.contains("32")) {
								arch = JVMArch.BIT32;
							}
						} else if (key.equalsIgnoreCase("sun.java.command")) {
							javaCommand = value;
						} else if(key.equalsIgnoreCase("java.version")) {
							javaVersion = value;
						}
					}

				}

			}
			return new JVMDescriptor(javaVersion, type, arch, javaCommand, arguments, pid, isDebug,
					Connectable.ATTACHABLE);

		} catch (Exception ignore) {
		}

		return NULL_DESCRIPTOR;
	}

	@Override
	public JMXServiceURL createJMXServiceURL() throws IOException {
		return serviceUrl;
	}

	@Override
	public Map<String, Object> getEnvironment() {
		return null;
	}

	@Override
	public String getPath() {
		return "jolokia";
	}

	@Override
	public JMXServiceURL serviceUrl() {
		return this.serviceUrl;
	}

}