package org.openjdk.jmc.jolokia;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.jolokia.client.jmxadapter.RemoteJmxAdapter;
import org.jolokia.discovery.JolokiaDiscovery;
import org.jolokia.util.JulLogHandler;
import org.json.simple.JSONObject;
import org.openjdk.jmc.ui.common.jvm.JVMDescriptor;

public class JolokiaDiscoveryListener extends AbstractCachedDescriptorProvider {

	@Override
	protected Map<String, ServerConnectionDescriptor> discoverJvms() {
		Map<String, ServerConnectionDescriptor> found=new HashMap<>();
		try {
			for (Object object : new JolokiaDiscovery("jmc", new JulLogHandler()).lookupAgents()) {
				try {

					JSONObject response = (JSONObject) object;
					JVMDescriptor jvmInfo = JolokiaAgentDescriptor.NULL_DESCRIPTOR;
					try {// if it is connectable, see if we can get info from connection
						jvmInfo = JolokiaAgentDescriptor
								.attemptToGetJvmInfo(new RemoteJmxAdapter(String.valueOf(response.get("url"))));
					} catch (Exception ignore) {
					}
					JolokiaAgentDescriptor agentDescriptor = new JolokiaAgentDescriptor(response, jvmInfo);
					found.put(agentDescriptor.getGUID(), agentDescriptor);

				} catch (URISyntaxException ignore) {
				}
			}
		} catch (IOException ignore) {
		}
		return found;
	}


	@Override
	public String getDescription() {
		return "Uses Jolokia Discovery to report any active JVMs with Jolokia broadcasting";
	}
	
	@Override
	public String getName() {
		return "jolokia";
	}

	@Override
	protected boolean isEnabled() {
		return true;
	}
	
	

}
