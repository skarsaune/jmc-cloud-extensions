package org.eclipse.jmc.jolokia;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.Descriptor;
import javax.management.ImmutableDescriptor;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ObjectName;
import javax.management.modelmbean.DescriptorSupport;

import org.jolokia.client.J4pClient;
import org.jolokia.client.jmxadapter.RemoteJmxAdapter;

/**
 * 
 * @author martin@skarsaune.net
 * 
 *         Make JMC specific adjustments to Jolokia JMX connection May consider
 *         to create a decorator pattern if differences are big but begin with a
 *         subclass
 *
 */
public class JmcJolokiaJmxConnection extends RemoteJmxAdapter {

	private static final String UNKNOWN = "Unknown";
	private static final String DIAGNOSTIC_OPTIONS = "com.sun.management:type=DiagnosticCommand"; //$NON-NLS-1$
	private static final String PREFIX = "dcmd."; //$NON-NLS-1$
	private static final String IMPACT = PREFIX + "vmImpact"; //$NON-NLS-1$
	private static final String NAME = PREFIX + "name"; //$NON-NLS-1$
	private static final String DESCRIPTION = PREFIX + "description"; //$NON-NLS-1$
	private static final String ARGUMENTS = PREFIX + "arguments"; //$NON-NLS-1$
	private static final String ARGUMENT_NAME = PREFIX + "arg.name"; //$NON-NLS-1$
	private static final String ARGUMENT_DESCRIPTION = PREFIX + "arg.description"; //$NON-NLS-1$
	private static final String ARGUMENT_MANDATORY = PREFIX + "arg.isMandatory"; //$NON-NLS-1$
	private static final String ARGUMENT_TYPE = PREFIX + "arg.type"; //$NON-NLS-1$
	private static final String ARGUMENT_OPTION = PREFIX + "arg.isOption"; //$NON-NLS-1$
	private static final String ARGUMENT_MULITPLE = PREFIX + "arg.isMultiple"; //$NON-NLS-1$

	public JmcJolokiaJmxConnection(J4pClient client) throws IOException {
		super(client);
	}

	@Override
	public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IOException {
		MBeanInfo mBeanInfo = super.getMBeanInfo(name);
		// the diagnostic options tab and memory relies on descriptor info in MBeanInfo,
		// modify descriptors the first time
		if (DIAGNOSTIC_OPTIONS.equals(name.getCanonicalName())
				&& mBeanInfo.getOperations()[0].getDescriptor() == ImmutableDescriptor.EMPTY_DESCRIPTOR) {
			MBeanInfo localInfo = null;
			// try to "steal" descriptors from this VM
			try {
				localInfo = ManagementFactory.getPlatformMBeanServer().getMBeanInfo(name);
			} catch (Exception ignore) {
			}

			for (int i = 0; i < mBeanInfo.getOperations().length; i++) {
				mBeanInfo.getOperations()[i] = stealOrBuildOperationInfo(mBeanInfo.getOperations()[i], localInfo);
			}
		}
		return mBeanInfo;
	}

	/**
	 * build / reverse engineer MBeanOperationInfo by using the local one if it is a
	 * match or try to reverse engineer otherwise
	 * 
	 * @param original
	 * @param localInfo MBeanInfo from this JVM to use for getting descriptor
	 * @return Descriptor
	 */
	private MBeanOperationInfo stealOrBuildOperationInfo(MBeanOperationInfo original, MBeanInfo localInfo) {
		// first attempt to get descriptor from local copy
		if (localInfo != null) {

			for (MBeanOperationInfo localOperation : localInfo.getOperations()) {
				if (localOperation.getName().equals(original.getName())) {
					if (localOperation.getSignature().length == original.getSignature().length) {
						for (int i = 0; i < original.getSignature().length; i++) {
							MBeanParameterInfo param = original.getSignature()[i];
							if (!param.getType().equals(localOperation.getSignature()[i].getType())) {
								break;
							} else if (i == original.getSignature().length - 1) {
								// whole signature matches, use as replacement
								return localOperation;
							}

						}
					}
				}
			}
		}
		//if not reverse engineer descriptor from operation info
		DescriptorSupport result = new DescriptorSupport();
		result.setField(NAME, original.getName());
		result.setField(DESCRIPTION, original.getDescription());
		result.setField(IMPACT, UNKNOWN);
		result.setField(ARGUMENTS, buildArguments(original.getSignature()));
		return new MBeanOperationInfo(original.getName(), original.getDescription(), original.getSignature(),
				original.getReturnType(), MBeanOperationInfo.UNKNOWN, result);
	}

	private Descriptor buildArguments(MBeanParameterInfo[] signature) {
		DescriptorSupport parameters = new DescriptorSupport();
		for (MBeanParameterInfo parameter : signature) {
			parameters.setField(parameter.getName(), buildArgument(parameter));
		}
		return parameters;
	}

	private Descriptor buildArgument(MBeanParameterInfo parameter) {
		DescriptorSupport result = new DescriptorSupport();
		result.setField(ARGUMENT_NAME, parameter.getName());
		boolean isMultiple = parameter.getType().startsWith("[");
		result.setField(ARGUMENT_MULITPLE, String.valueOf(isMultiple));
		String type=parameter.getType();
		if(isMultiple) {
			if(type.startsWith("[L")) {
				type=type.substring(2);
			} else {
				type=type.substring(1);
			}
			
		} 
		//probably more reverse mapping of types should be done here, but we hope it is sufficient
		result.setField(ARGUMENT_TYPE, parameter.getType());
		result.setField(ARGUMENT_DESCRIPTION, parameter.getDescription());
		result.setField(ARGUMENT_MANDATORY, "false");
		result.setField(ARGUMENT_OPTION, "false");
		return result;
	}

}
