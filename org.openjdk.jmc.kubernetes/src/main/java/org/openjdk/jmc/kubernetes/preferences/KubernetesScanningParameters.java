package org.openjdk.jmc.kubernetes.preferences;

public interface KubernetesScanningParameters {
	boolean scanForInstances();
	boolean scanAllContexts();
	String usePort();
	String username();
	String password();
}
