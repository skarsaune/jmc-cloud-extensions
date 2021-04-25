package org.openjdk.jmc.kubernetes;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Properties;
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
import org.openjdk.jmc.ui.common.jvm.JVMDescriptor;
import org.osgi.framework.FrameworkUtil;

import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListMultiDeletable;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import io.fabric8.kubernetes.client.utils.Utils;

public class KubernetesDiscoveryListener extends AbstractCachedDescriptorProvider {

	private final Pattern SECRET_PATTERN = Pattern
			.compile("\\$\\{kubernetes/secret/(?<secretName>[^/]+)/(?<itemName>[^\\}]+)}");

	public String getDescription() {
		return "List JVM in kubernetes cluster";
	}

	@Override
	public String getName() {
		return "kubernetes";
	}

	boolean notEmpty(String value) {
		return value != null && value.length() > 0;
	}

	private List<String> contexts;

	private List<String> allContexts() throws IOException {
		if (contexts != null) {// the YAML parsing is soo incredibly sloow, hence cache context names for later
								// runs
			return contexts;
		}
		final String path = Utils.getSystemPropertyOrEnvVar(Config.KUBERNETES_KUBECONFIG_FILE,
				new File(System.getProperty("user.home"), ".kube" + File.separator + "config").toString());
		io.fabric8.kubernetes.api.model.Config config = KubeConfigUtils.parseConfig(new File(path));
		return contexts = config.getContexts().stream().map(NamedContext::getName).collect(Collectors.toList());
	}

	@Override
	protected Map<String, ServerConnectionDescriptor> discoverJvms() {
		Map<String, ServerConnectionDescriptor> found = new HashMap<>();
		KubernetesScanningParameters parameters = JmcKubernetesPlugin.getDefault();
		boolean hasScanned = false;

		if (parameters.scanAllContexts()) {
			try {
				for (final String context : allContexts()) {
					hasScanned = true;
					scanContext(found, parameters, context);
				}
			} catch (IOException e) {
				Platform.getLog(FrameworkUtil.getBundle(getClass())).error("Unable to find all kubernetes contexts", e);
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
			Platform.getLog(FrameworkUtil.getBundle(getClass())).error("Unable to scan kubernetes context " + context,
					e);
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
		for (Pod pod : podList) {

			final ObjectMeta metadata = pod.getMetadata();
			HashMap<String, String> headers = new HashMap<>();
			if (notEmpty(parameters.username())) {
				if (!notEmpty(parameters.password())) {
					throw new IllegalArgumentException("Password must be specified when username is specified");
				}
				authorize(headers, client, parameters.username(), parameters.password(), metadata.getNamespace());
			}
			final StringBuilder url = new StringBuilder(metadata.getSelfLink());
			final StringBuilder jmxUrl = new StringBuilder("service:jmx:kubernetes:///").append(metadata.getNamespace())
					.append('/').append(metadata.getName());
			if (notEmpty(parameters.portAnnotation())) {
				String port = metadata.getAnnotations().get(parameters.portAnnotation());
				if (port != null) {
					url.append(":").append(port);
					jmxUrl.append(':').append(port);
				}
			}
			url.append("/proxy");

			if (notEmpty(parameters.pathAnnotation())) {
				String path = metadata.getAnnotations().get(parameters.pathAnnotation());
				if (!path.startsWith("/")) {
					path = "/" + path;
				}
				url.append(path);
				jmxUrl.append(path);
			}

			Map<String, Object> env = new HashMap<>();
			if (context != null) {
				env.put(KubernetesJmxConnector.KUBERNETES_CLIENT_CONTEXT, context);
			}
			J4pClient jvmClient = KubernetesJmxConnector.probeProxyPath(env, client, url, headers);
			if (jvmClient != null) {
				JmcKubernetesJmxConnection connection;
				try {
					connection = new JmcKubernetesJmxConnection(jvmClient);
					JVMDescriptor jvmDescriptor = JolokiaAgentDescriptor.attemptToGetJvmInfo(connection);
					JMXServiceURL jmxServiceURL = new JMXServiceURL(jmxUrl.toString());
					KubernetesJvmDescriptor descriptor = new KubernetesJvmDescriptor(metadata, jvmDescriptor,
							jmxServiceURL, env);
					found.put(descriptor.getGUID(), descriptor);
				} catch (IOException e) {
					Platform.getLog(FrameworkUtil.getBundle(getClass())).error("Error connecting to JVM in pod", e);

				}
			}
		}
		return found;
	}

	private void authorize(HashMap<String, String> headers, KubernetesClient client, String username, String password,
			String namespace) {

		final Matcher userNameMatcher = SECRET_PATTERN.matcher(username);
		String secretName = null;
		Map<String, String> secretValues = null;
		if (userNameMatcher.find()) {
			secretName = userNameMatcher.group("secretName");
			secretValues = findSecret(client, namespace, secretName);
			username = secretValues.get(userNameMatcher.group("itemName"));
		}

		final Matcher passwordMatcher = SECRET_PATTERN.matcher(password);
		if (passwordMatcher.find()) {
			if (!secretName.equals(passwordMatcher.group("secretName"))) {
				secretValues = findSecret(client, namespace, passwordMatcher.group("secretName"));
			}
			password = secretValues.get(passwordMatcher.group("itemName"));
		}

		headers.put(AuthorizationHeaderParser.JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER,
				"Basic " + Base64Util.encode((username + ":" + password).getBytes()));
		// TODO Auto-generated method stub

	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Map<String, String> findSecret(KubernetesClient client, String namespace, String secretName) {

		for (Secret secret : client.secrets().inNamespace(namespace).list().getItems()) {
			if (secret.getMetadata().getName().equals(secretName)) {
				if ("kubernetes.io/basic-auth".equals(secret.getType())) {
					Map<String, String> data = secret.getData();
					data.replaceAll((key, value) -> new String(Base64.decodeBase64(value)));
					return data;
				} else if ("Opaque".equals(secret.getType())) {
					for (Entry<String, String> entry : secret.getData().entrySet()) {
						if (entry.getKey().endsWith(".properties")) {
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
		throw new NoSuchElementException("Could not find secret named " + secretName + " in namespace " + namespace);

	}

	@Override
	protected boolean isEnabled() {
		return JmcKubernetesPlugin.getDefault().scanForInstances();
	}
}
