package org.eclipse.jmc.jolokia;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServerConnection;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;

import org.jolokia.config.Configuration;
import org.jolokia.request.JmxRequest;
import org.jolokia.request.JmxRequestFactory;
import org.jolokia.util.RequestType;

public class JolokiaJmxAdapter implements MBeanServerConnection {

	private final JolokiaConnector connector;
	
	public JolokiaJmxAdapter(final JolokiaConnector connector) {
		this.connector = connector;
	}

	@Override
	public ObjectInstance createMBean(String className, ObjectName name)
			throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException,
			NotCompliantMBeanException, IOException {
		throw new UnsupportedOperationException("createMBean not supported over Jolokia");
	}

	@Override
	public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName)
			throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException,
			NotCompliantMBeanException, InstanceNotFoundException, IOException {
		throw new UnsupportedOperationException("createMBean not supported over Jolokia");
	}

	@Override
	public ObjectInstance createMBean(String className, ObjectName name, Object[] params, String[] signature)
			throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException,
			NotCompliantMBeanException, IOException {
		throw new UnsupportedOperationException("createMBean not supported over Jolokia");
	}

	@Override
	public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName, Object[] params,
			String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException,
			MBeanException, NotCompliantMBeanException, InstanceNotFoundException, IOException {
		throw new UnsupportedOperationException("createMBean not supported over Jolokia");
	}

	@Override
	public void unregisterMBean(ObjectName name)
			throws InstanceNotFoundException, MBeanRegistrationException, IOException {
		throw new UnsupportedOperationException("unregisterMBean not supported over Jolokia");

	}

	@Override
	public ObjectInstance getObjectInstance(ObjectName name) throws InstanceNotFoundException, IOException {
		return new ObjectInstance(name, (String) callJolokia(arguments().list().mbean(name)).get("class"));

	}

	private Map<String, ?> callJolokia(JolokiaArguments arguments) {
		return connector.post(arguments.postRequest().toJSON().toJSONString());

	}

	@Override
	public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws IOException {
		Set<ObjectName> result=new HashSet<>();
		String mbeanFilter = "*:*";
		if(name != null) {
			mbeanFilter=name.getCanonicalName();
		}
		List<String> names=(List<String>) callJolokia(arguments().search().mbean(mbeanFilter)).get("values");
		for(final String nameAsString : names) {
			ObjectName objectName;
			try {
				objectName = ObjectName.getInstance(nameAsString);
				if(query == null || query.apply(objectName)) {
					result.add(objectName);
				}
			} catch (Exception ignore) {
			}
		}
		return result;
	}

	@Override
	public boolean isRegistered(ObjectName name) throws IOException {
		
		return !queryNames(name, null).isEmpty();
	}

	@Override
	public Integer getMBeanCount() throws IOException {
		return this.queryNames(null, null).size();
	}

	@Override
	public Object getAttribute(ObjectName name, String attribute) throws MBeanException, AttributeNotFoundException,
			InstanceNotFoundException, ReflectionException, IOException {
		return callJolokia(arguments().read().mbean(name).path(attribute)).get("value");
	}

	@Override
	public AttributeList getAttributes(ObjectName name, String[] attributes)
			throws InstanceNotFoundException, ReflectionException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setAttribute(ObjectName name, Attribute attribute)
			throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException,
			MBeanException, ReflectionException, IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public AttributeList setAttributes(ObjectName name, AttributeList attributes)
			throws InstanceNotFoundException, ReflectionException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
			throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getDefaultDomain() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getDomains() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter,
			Object handback) throws InstanceNotFoundException, IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void addNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter,
			Object handback) throws InstanceNotFoundException, IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeNotificationListener(ObjectName name, ObjectName listener)
			throws InstanceNotFoundException, ListenerNotFoundException, IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter,
			Object handback) throws InstanceNotFoundException, ListenerNotFoundException, IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeNotificationListener(ObjectName name, NotificationListener listener)
			throws InstanceNotFoundException, ListenerNotFoundException, IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter,
			Object handback) throws InstanceNotFoundException, ListenerNotFoundException, IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public MBeanInfo getMBeanInfo(ObjectName name)
			throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
		callJolokia(arguments().list().path(name.getCanonicalName().replaceFirst(":", "/")));
		//FIXME: parse response
		return null;
	}

	@Override
	public boolean isInstanceOf(ObjectName name, String className) throws InstanceNotFoundException, IOException {
		// TODO Auto-generated method stub
		return false;
	}

	static JolokiaArguments arguments() {
		return new JolokiaArguments();
	}

	static class JolokiaArguments {

		final private Map<String, Object> arguments = new HashMap<>();

		JolokiaArguments read() {
			arguments.put("type", RequestType.READ);
			return this;
		}

		public JolokiaArguments path(String attribute) {
			arguments.put("path", attribute);
			return this;
		}

		JolokiaArguments exec() {
			arguments.put("type", RequestType.EXEC);
			return this;
		}

		JolokiaArguments search() {
			arguments.put("type", RequestType.SEARCH);
			return this;
		}

		JolokiaArguments list() {
			arguments.put("type", RequestType.LIST);
			return this;
		}

		JolokiaArguments mbean(ObjectName mbean) {
			if(mbean != null) {				
				this.mbean(mbean.getCanonicalName());
			}
			return this;
		}

		JolokiaArguments mbean(String mbean) {
			arguments.put("mbean", mbean);
			return this;
		}

		Map<String, Object> arguments() {
			return this.arguments;
		}

		JmxRequest postRequest() {
			return JmxRequestFactory.createPostRequest(this.arguments,
					new Configuration().getProcessingParameters(configuration()));
		}


		@SuppressWarnings("unchecked")
		private Map<String, String> configuration() {
			return (Map<String, String>) this.arguments().computeIfAbsent("config", key -> new HashMap<String,String>());
		}

	}

}
