package org.eclipse.jmc.jolokia;

import java.util.Map;

public interface JolokiaConnector {

	Map<String, ?> post(String jsonString);

}
