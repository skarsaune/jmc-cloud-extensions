package org.eclipse.jmc.jolokia;

import com.jayway.awaitility.Awaitility;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import org.jolokia.jvmagent.JvmAgent;
import org.junit.Before;
import org.junit.Test;

public class JolokiaConnectionTest {

  private static boolean startedAgent;

  @Before
  public void startAgent()
      throws MalformedObjectNameException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException {
    if (startedAgent) {
      return;
    }
		final String vmName = (String) ManagementFactory.getPlatformMBeanServer()
				.getAttribute(new ObjectName("java.lang:type=Runtime"), "Name");
    final String pid=vmName.substring(0, vmName.indexOf('@'));


		JvmAgent.agentmain("port=0", null);

		//use presence of mbean as indication that agent is up and running
    Awaitility.await().until(() -> Arrays.asList(ManagementFactory.getPlatformMBeanServer().getDomains()).contains("jolokia"));
  }

  @Test
  public void testNames() {
    new JolokiaJmxAdapter(null);
    ManagementFactory.getPlatformMBeanServer().queryNames(null, null);
  }


}
