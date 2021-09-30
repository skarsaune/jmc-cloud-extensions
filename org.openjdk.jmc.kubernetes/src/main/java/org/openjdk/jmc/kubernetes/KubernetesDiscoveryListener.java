package org.openjdk.jmc.kubernetes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.management.remote.JMXServiceURL;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.core.runtime.Platform;
import org.jolokia.client.J4pClient;
import org.jolokia.kubernetes.client.KubernetesJmxConnector;
import org.jolokia.util.AuthorizationHeaderParser;
import org.jolokia.util.Base64Util;
import org.openjdk.jmc.jolokia.AbstractCachedDescriptorProvider;
import org.openjdk.jmc.jolokia.JolokiaAgentDescriptor;
import org.openjdk.jmc.jolokia.ServerConnectionDescriptor;
import org.openjdk.jmc.kubernetes.preferences.KubernetesScanningParameters;
import org.openjdk.jmc.ui.common.jvm.Connectable;
import org.openjdk.jmc.ui.common.jvm.JVMDescriptor;
import org.osgi.framework.FrameworkUtil;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import io.fabric8.kubernetes.client.utils.Utils;

public class KubernetesDiscoveryListener extends AbstractCachedDescriptorProvider {

	private final Pattern SECRET_PATTERN = Pattern.compile(Messages.KubernetesDiscoveryListener_0);
	private final Pattern ATTRIBUTE_PATTERN = Pattern.compile("\\$\\{kubernetes/annotation/(?<annotationName>[^/]+)}"); //$NON-NLS-1$
	private final Set<String> VALID_JOLOKIA_PROTOCOLS = new HashSet<>(Arrays.asList("http", "https")); //$NON-NLS-1$ //$NON-NLS-2$

	public final String getDescription() {
		return Messages.KubernetesDiscoveryListener_Description;
	}

	@Override
	public String getName() {
		return "kubernetes"; //$NON-NLS-1$
	}

	boolean notEmpty(String value) {
		return value != null && value.length() > 0;
	}

	private List<String> contexts;
	private long contextsCached=0L;

	private List<String> allContexts() throws IOException {
		final String path = Utils.getSystemPropertyOrEnvVar(Config.KUBERNETES_KUBECONFIG_FILE,
				new File(System.getProperty("user.home"), ".kube" + File.separator + "config").toString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		File configPath = new File(path);
		if (contexts != null && contextsCached > configPath.lastModified()) {// the YAML parsing is soo incredibly sloow, hence cache context names for later
								// runs
			return contexts;
		}
		//reload config if kubeconfig has been modified since we cached the config
		io.fabric8.kubernetes.api.model.Config config = KubeConfigUtils.parseConfig(configPath);
		this.contextsCached=System.currentTimeMillis();
		KubernetesJmxConnector.resetKubernetesConfig();
		return contexts = config.getContexts().stream().map(NamedContext::getName).collect(Collectors.toList());
	}

	@Override
	protected Map<String, ServerConnectionDescriptor> discoverJvms() {
		Map<String, ServerConnectionDescriptor> found = new HashMap<>();
		KubernetesScanningParameters parameters = JmcKubernetesPlugin.getDefault();
		if(!isEnabled()) {
			return found;
		}
		boolean hasScanned = false;

		if (parameters.scanAllContexts()) {
			try {
				for (final String context : allContexts()) {
					hasScanned = true;
					scanContext(found, parameters, context);
				}
			} catch (IOException e) {
				Platform.getLog(FrameworkUtil.getBundle(getClass()))
						.error(Messages.KubernetesDiscoveryListener_UnableToFindContexts, e);
			}
		}
		if (!hasScanned) {// scan default context
			return scanContext(found, parameters, null);
		}
		return found;

	}

	private Map<String, ServerConnectionDescriptor> scanContext(Map<String, ServerConnectionDescriptor> found,
			KubernetesScanningParameters parameters, String context) {
		try {
			scanContextUnsafe(found, parameters, context);
		} catch (Exception e) {
			Platform.getLog(FrameworkUtil.getBundle(getClass()))
					.error(Messages.KubernetesDiscoveryListener_UnableToScan + context, e);
		}
		return found;
	}

	private Map<String, ServerConnectionDescriptor> scanContextUnsafe(Map<String, ServerConnectionDescriptor> found,
			KubernetesScanningParameters parameters, String context) {
		String pathLabel = parameters.requireLabel();
		KubernetesClient client = KubernetesJmxConnector.getApiClient(context);

		FilterWatchListMultiDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> query = client.pods()
				.inAnyNamespace();
		List<Pod> podList;
		boolean hasPathLabel = notEmpty(pathLabel);
		if (hasPathLabel) {
			podList = query.withLabel(pathLabel).list().getItems();
		} else {
			podList = query.list().getItems();
		}
		// can consider parallelization for big contexts, however since it is the
		// background await the situation a bit
		podList.stream().forEach(pod -> scanPod(found, parameters, context, client, pod));
		return found;
	}

	private void scanPod(Map<String, ServerConnectionDescriptor> found, KubernetesScanningParameters parameters,
			String context, KubernetesClient client, Pod pod) {

		final ObjectMeta metadata = pod.getMetadata();
		HashMap<String, String> headers = new HashMap<>();
		if (notEmpty(parameters.username())) {
			if (!notEmpty(parameters.password())) {
				throw new IllegalArgumentException(Messages.KubernetesDiscoveryListener_MustProvidePassword);
			}
			authorize(headers, client, parameters.username(), parameters.password(), metadata.getNamespace());
		}
		final StringBuilder url = new StringBuilder(metadata.getSelfLink());
		// JMX url must be reverse constructed, so that we can connect from the
		// resulting node in the JVM browser
		final StringBuilder jmxUrl = new StringBuilder("service:jmx:kubernetes:///").append(metadata.getNamespace()) //$NON-NLS-1$
				.append('/');

		final String protocol = getValueOrAttribute(parameters.jolokiaProtocol(), metadata);
		final String podName = metadata.getName();
		if (notEmpty(protocol)) {
			if (!VALID_JOLOKIA_PROTOCOLS.contains(protocol)) {
				throw new IllegalArgumentException(Messages.KubernetesDiscoveryListener_JolokiaProtocol + protocol
						+ Messages.KubernetesDiscoveryListener_HttpOrHttps);
			}
			// a bit clumsy, need to inject protocol _before_ podname in selflink
			url.insert(url.lastIndexOf(podName), protocol + ":"); //$NON-NLS-1$
			jmxUrl.append(protocol).append(':');
		}

		jmxUrl.append(podName);

		final String port = getValueOrAttribute(parameters.jolokiaPort(), metadata);
		if (port != null) {
			url.append(":").append(port); //$NON-NLS-1$
			jmxUrl.append(':').append(port);
		}

		url.append("/proxy"); //$NON-NLS-1$

		final String path = getValueOrAttribute(parameters.jolokiaPath(), metadata);

		if (!path.startsWith("/")) { //$NON-NLS-1$
			url.append('/');
			jmxUrl.append('/');
		}
		url.append(path);
		jmxUrl.append(path);

		Map<String, Object> env = new HashMap<>();
		if (context != null) {
			env.put(KubernetesJmxConnector.KUBERNETES_CLIENT_CONTEXT, context);
		}
		J4pClient jvmClient = KubernetesJmxConnector.probeProxyPath(env, client, url, headers);
		if (jvmClient != null) {
			JmcKubernetesJmxConnection connection;
			try {
				ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch, Boolean, InputStream, Boolean> podHandle = probeAndGetApiHandle(
						client, pod, metadata, port);
				env.put(JmcKubernetesJmxConnection.JMC_POD_HANDLE, podHandle);
				connection = new JmcKubernetesJmxConnection(jvmClient, podHandle);
				Connectable connectable = Connectable.UNKNOWN;
				// our fine little hack. Connectable is currently _only_ used to determine
				// whether heap dumps are local or not
				// hence abuse this to appear connectable if we are able to copy content out of
				if (podHandle != null && parameters.optimizeHeapDumps()) {
					connectable = Connectable.ATTACHABLE;
				}
				JVMDescriptor jvmDescriptor = JolokiaAgentDescriptor.attemptToGetJvmInfo(connection, connectable);
				JMXServiceURL jmxServiceURL = new JMXServiceURL(jmxUrl.toString());
				KubernetesJvmDescriptor descriptor = new KubernetesJvmDescriptor(metadata, jvmDescriptor, jmxServiceURL,
						env);
				found.put(descriptor.getGUID(), descriptor);
			} catch (IOException e) {
				Platform.getLog(FrameworkUtil.getBundle(getClass()))
						.error(Messages.KubernetesDiscoveryListener_ErrConnectingToJvm, e);

			}
		}
	}

	private ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch, Boolean, InputStream, Boolean> probeAndGetApiHandle(
			KubernetesClient client, Pod pod, final ObjectMeta metadata, final String port) throws IOException {
		ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch, Boolean, InputStream, Boolean> podHandle = client
				.pods().inNamespace(metadata.getNamespace()).withName(metadata.getName());
		podHandle = qualifyWithContainerIfApplicable(pod, port, podHandle);
		try {
			ByteArrayOutputStream errorSink = new ByteArrayOutputStream();
			// Probe by running tar (it is needed to copy content out of container)
			// https://github.com/kubernetes/kubernetes/issues/58512
			ExecWatch exec = podHandle.redirectingError().writingErrorChannel(errorSink).exec("tar", "--usage");
			for (int i = 0; i < 10; i++) {
				if(errorSink.size()>0) {
					break;
				}
				try {
					Thread.sleep(10);
				} catch (InterruptedException ignore) {
				}
			}
			exec.close();
			OperationStatus success = parseResponse(errorSink);
			if (success != null && success.isSuccess()) {
				return podHandle;
			} else {
				return null;
			}

		} catch (KubernetesClientException e) {
			return null;
		}

	}

	private OperationStatus parseResponse(ByteArrayOutputStream source) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		try {
		OperationStatus success = objectMapper.readerFor(OperationStatus.class)
				.readValue(source.toByteArray());
		return success;
		} catch (Exception ignore) {
			return null;
		}
	}

	private ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch, Boolean, InputStream, Boolean> qualifyWithContainerIfApplicable(
			Pod pod, final String port,
			ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream, String, ExecWatch, Boolean, InputStream, Boolean> podHandle) {
		// only one container, no need to specify
		if (pod.getSpec().getContainers().size() == 1) {
			return podHandle;
		}
		// try to map to container based on jolokia port
		if (port != null) {
			for (Container container : pod.getSpec().getContainers()) {
				for (ContainerPort containerPort : container.getPorts()) {
					if (String.valueOf(containerPort.getContainerPort()).equals(port)) {
						return ((PodResource<Pod, DoneablePod>) podHandle).inContainer(container.getName());
					}
				}
			}

		}
		// return first container with any ports
		for (Container container : pod.getSpec().getContainers()) {
			if (!container.getPorts().isEmpty()) {
				return ((PodResource<Pod, DoneablePod>) podHandle).inContainer(container.getName());
			}
		}
		return podHandle;
	}

	static class OperationStatus {
		public String status;

		boolean isSuccess() {
			return "Success".equals(status);
		}
	}

	private String getValueOrAttribute(String configValue, ObjectMeta metadata) {
		if (notEmpty(configValue)) {
			Matcher pattern = ATTRIBUTE_PATTERN.matcher(configValue);
			if (pattern.find()) {
				return metadata.getAnnotations().get(pattern.group("annotationName")); //$NON-NLS-1$
			} else {
				return configValue;// the default is to use config value as is
			}
		}
		return null;
	}

	private void authorize(HashMap<String, String> headers, KubernetesClient client, String username, String password,
			String namespace) {

		final Matcher userNameMatcher = SECRET_PATTERN.matcher(username);
		String secretName = null;
		Map<String, String> secretValues = null;
		if (userNameMatcher.find()) {
			secretName = userNameMatcher.group("secretName"); //$NON-NLS-1$
			secretValues = findSecret(client, namespace, secretName);
			username = secretValues.get(userNameMatcher.group("itemName")); //$NON-NLS-1$
		}

		final Matcher passwordMatcher = SECRET_PATTERN.matcher(password);
		if (passwordMatcher.find()) {
			if (!secretName.equals(passwordMatcher.group("secretName"))) { //$NON-NLS-1$
				secretValues = findSecret(client, namespace, passwordMatcher.group("secretName")); //$NON-NLS-1$
			}
			password = secretValues.get(passwordMatcher.group("itemName")); //$NON-NLS-1$
		}

		headers.put(AuthorizationHeaderParser.JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER,
				"Basic " + Base64Util.encode((username + ":" + password).getBytes())); //$NON-NLS-1$ //$NON-NLS-2$

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Map<String, String> findSecret(KubernetesClient client, String namespace, String secretName) {

		for (Secret secret : client.secrets().inNamespace(namespace).list().getItems()) {
			if (secret.getMetadata().getName().equals(secretName)) {
				if ("kubernetes.io/basic-auth".equals(secret.getType())) { //$NON-NLS-1$
					Map<String, String> data = secret.getData();
					data.replaceAll((key, value) -> new String(Base64.decodeBase64(value)));
					return data;
				} else if ("Opaque".equals(secret.getType())) { //$NON-NLS-1$
					for (Entry<String, String> entry : secret.getData().entrySet()) {
						if (entry.getKey().endsWith(".properties")) { //$NON-NLS-1$
							try {
								Properties properties = new Properties();
								properties.load(new ByteArrayInputStream(Base64.decodeBase64(entry.getValue())));
								return (Map) properties;
							} catch (IOException ignore) {
							}
						}

					}

				}
			}

		}
		throw new NoSuchElementException(Messages.KubernetesDiscoveryListener_CouldNotFindSecret + secretName
				+ Messages.KubernetesDiscoveryListener_InNamespace + namespace);

	}

	@Override
	protected boolean isEnabled() {
		return JmcKubernetesPlugin.getDefault().scanForInstances();
	}
}
