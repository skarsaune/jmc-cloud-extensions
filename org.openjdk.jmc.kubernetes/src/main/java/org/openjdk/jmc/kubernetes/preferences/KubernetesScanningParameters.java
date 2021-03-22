package org.openjdk.jmc.kubernetes.preferences;

public interface KubernetesScanningParameters {
	boolean scanForInstances();
	boolean scanAllContexts();
	String portAnnotation();
	String username();
	String password();
	String pathAnnotation();
	String requireLabel();
}
