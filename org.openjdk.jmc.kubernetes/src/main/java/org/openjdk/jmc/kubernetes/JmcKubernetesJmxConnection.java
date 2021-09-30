package org.openjdk.jmc.kubernetes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ObjectName;

import org.jolokia.client.J4pClient;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.jolokia.client.request.J4pResponse;
import org.openjdk.jmc.jolokia.JmcJolokiaJmxConnection;
import org.openjdk.jmc.rjmx.ConnectionException;

import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.CopyOrReadable;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.FileSelector;
import io.fabric8.kubernetes.client.dsl.LogWatch;

public class JmcKubernetesJmxConnection extends JmcJolokiaJmxConnection {

	private static final String[] HEAP_DUMP_PARAMS = new String[] {"java.lang.String","boolean"};
	final static Collection<Pattern> DISCONNECT_SIGNS = Arrays.asList(Pattern.compile("Error: pods \".+\" not found")); //$NON-NLS-1$
	static final String JMC_POD_HANDLE="jmc.pod.handle";
	private FileSelector<CopyOrReadable<Boolean, InputStream, Boolean>> apiHandle;

	public JmcKubernetesJmxConnection(J4pClient client, ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch, Boolean, InputStream, Boolean> apiHandle) throws IOException {
		super(client);
		this.apiHandle=apiHandle;
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected J4pResponse unwrapException(J4pException e) throws IOException, InstanceNotFoundException {
		// recognize signs of disconnect and signal to the application for better
		// handling
		if (isKnownDisconnectException(e)) {
			throw new ConnectionException(e.getMessage());
		} else {
			return super.unwrapException(e);
		}
	}

	private boolean isKnownDisconnectException(J4pException e) {
		if (!(e instanceof J4pRemoteException)) {
			return false;
		}
		if (!"io.fabric8.kubernetes.client.KubernetesClientException".equals(((J4pRemoteException) e).getErrorType())) { //$NON-NLS-1$
			return false;
		}
		return DISCONNECT_SIGNS.stream().anyMatch(pattern -> pattern.matcher(e.getMessage()).matches());
	}
	
	@Override
	public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
			throws InstanceNotFoundException, MBeanException, IOException {
		if(attemptToOptimizeHeapDump(name, operationName, signature)) {
			String localFile = String.valueOf(params[0]);
			String substituteFileName="/tmp/" + new File(localFile).getName();
			//replace path in JMX call
			params[0]=substituteFileName;
			Object result=super.invoke(name, operationName, params, signature);
			if(!this.apiHandle.file(substituteFileName).copy(Paths.get(localFile))) {
				throw new RuntimeException("Successfully dumped heap to file " + substituteFileName + " in pod but failed to copy file to " + localFile);
			}
			return result;
			
		} else {			
			return super.invoke(name, operationName, params, signature);
		}
	}

	private boolean attemptToOptimizeHeapDump(ObjectName name, String operationName, String[] signature) {
		return this.apiHandle != null && JmcKubernetesPlugin.getDefault().optimizeHeapDumps() && "com.sun.management:type=HotSpotDiagnostic".equals(name.getCanonicalName()) && "dumpHeap".equals(operationName) && Arrays.equals(signature, HEAP_DUMP_PARAMS);
	}

}
