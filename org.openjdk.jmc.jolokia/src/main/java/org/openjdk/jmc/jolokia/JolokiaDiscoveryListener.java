package org.openjdk.jmc.jolokia;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;

import org.jolokia.client.jmxadapter.RemoteJmxAdapter;
import org.jolokia.discovery.JolokiaDiscovery;
import org.jolokia.util.JulLogHandler;
import org.json.simple.JSONObject;
import org.openjdk.jmc.rjmx.descriptorprovider.IDescriptorListener;
import org.openjdk.jmc.rjmx.descriptorprovider.IDescriptorProvider;
import org.openjdk.jmc.ui.common.jvm.JVMDescriptor;

@SuppressWarnings("restriction")
public class JolokiaDiscoveryListener implements IDescriptorProvider {

	private List<IDescriptorListener> listeners = new LinkedList<IDescriptorListener>();

	public String getName() {
		return "Jolokia Discovery Listener";
	}

	public String getDescription() {
		return "List JVM with Jolokia agent broadcasting its presence";
	}

	@Override
	public void addDescriptorListener(IDescriptorListener l) {
		if (listeners.isEmpty()) {
			startScanning();
		}
		listeners.add(l);

	}

	private void startScanning() {
		new Thread(new Runnable() {

			@Override
			public void run() {
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

							for (IDescriptorListener listener : listeners) {
								listener.onDescriptorDetected(agentDescriptor,
										agentDescriptor.getServiceUrl().toString(), agentDescriptor.getServiceUrl(),
										agentDescriptor.getDescriptor(), agentDescriptor);
							}

						} catch (URISyntaxException ignore) {
						}
					}
				} catch (IOException ignore) {
				}

			}

		}, "Jolokia Discovery Listener").start();

	}

	@Override
	public void removeDescriptorListener(IDescriptorListener l) {
		listeners.remove(l);
	}

}
