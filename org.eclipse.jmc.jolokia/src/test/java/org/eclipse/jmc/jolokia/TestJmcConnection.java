package org.eclipse.jmc.jolokia;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import javax.management.ImmutableDescriptor;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.OpenDataException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;

import org.jolokia.client.J4pClient;
import org.jolokia.client.J4pClientBuilder;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.request.J4pVersionRequest;
import org.jolokia.jvmagent.JvmAgent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.core.ThrowingRunnable;

public class TestJmcConnection {
	private MBeanServerConnection adapter;

	@Before
	  public void startAgent()
	      throws MBeanException, ReflectionException, IOException, InstanceAlreadyExistsException,
	      NotCompliantMBeanException, OpenDataException {

	    JvmAgent.agentmain("port=0", null);
	        
	    // wait for agent to be running
	    Awaitility.await()
	        .until(
	            Awaitility.matches(
	                new ThrowingRunnable() {
	                  @Override
	                  public void run() throws J4pException {
	                	  String jolokiaUrl=System.getProperty("jolokia.agent");
	                	  if(jolokiaUrl == null) {
	                		  throw new AssertionError("Jolokia not ready yet");
	                	  }
	                	  J4pClient connector=new J4pClientBuilder().url(jolokiaUrl).build();
	                    connector.execute(new J4pVersionRequest());
	                  }
	                }));
	    @SuppressWarnings("unchecked")
		JMXConnector jmxConnector= new JmcJolokiaJmxConnectionProvider().newJMXConnector(new JMXServiceURL(System.getProperty("jolokia.agent").replace("http","service:jmx:jolokia")), (Map<String,Object>)Collections.EMPTY_MAP);
	    jmxConnector.connect();
	    this.adapter = jmxConnector.getMBeanServerConnection();
	  }
	
	@Test
	public void testDiagnosticOptions() throws InstanceNotFoundException, IntrospectionException, MalformedObjectNameException, ReflectionException, IOException {
		MBeanInfo mBeanInfo = this.adapter.getMBeanInfo(new ObjectName("com.sun.management:type=DiagnosticCommand"));
		for (MBeanOperationInfo mBeanOperationInfo : mBeanInfo.getOperations()) {
			Assert.assertNotEquals(ImmutableDescriptor.EMPTY_DESCRIPTOR, mBeanOperationInfo.getDescriptor());
		}
	}
}
