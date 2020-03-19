package org.eclipse.jmc.cloud.k8s;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;

/**
 * Hello world!
 *
 */
public class App {
	public static void main(String[] args) throws IOException, ApiException {

		// file path to your KubeConfig
		String kubeConfigPath = String.format("%s/.kube/config", System.getProperty("user.home"));

		// loading the out-of-cluster config, a kubeconfig from file-system
		ApiClient client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();

		// set the global default api-client to the in-cluster one from above
		Configuration.setDefaultApiClient(client);

		// the CoreV1Api loads default api-client from global configuration.
		CoreV1Api api = new CoreV1Api();

		V1PodList list = api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null);
		for (V1Pod item : list.getItems()) {
			if(item.getMetadata().getName().startsWith(args[0])) {
				System.out.println(item);
				System.out.println(item.getMetadata());
				System.out.println(item.getSpec());
				String path=String.format("/api/v1/namespaces/%s/pods/%s/proxy/%s", item.getMetadata().getNamespace(), item.getMetadata().getName(), args[1]);
				System.out.println(path);
				System.out.println(client.buildCall(path, "POST", Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.singletonMap("type", "version"), new HashMap<String, String>(), new HashMap<String,Object>(), new String[] { "BearerToken" }, null).execute().body().string());
				
			}
			System.out.println(item.getMetadata().getName());
		}
		
		V1ServiceList slist = api.listServiceForAllNamespaces(null, null, null, null, null, null, null, null, null);
		for (V1Service item : slist.getItems()) {
			if(item.getMetadata().getName().startsWith(args[0])) {
				System.out.println(item);
				System.out.println(item.getMetadata());
				System.out.println(item.getSpec());
				String path=String.format("/api/v1/namespaces/%s/services/%s/proxy/%s", item.getMetadata().getNamespace(), item.getMetadata().getName(), args[1]);
				System.out.println(path);
				System.out.println(client.buildCall(path, "POST", Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.singletonMap("type", "version"), new HashMap<String, String>(), new HashMap<String,Object>(), new String[] { "BearerToken" }, null).execute().body().string());
				
			}
			System.out.println(item.getMetadata().getName());
		}

	}
}
