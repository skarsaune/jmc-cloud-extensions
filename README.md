# Jolokia plugins for Java Mission Control

# Objective
Allow Java Mission Control to connect to [Jolokia](https://jolokia.org) enabled JVMs runing on prem or in [kubernetes](https://kubernetes.io)

# Installing
1. If you have not already, download [eclipse IDE](https://www.eclipse.org/downloads/)
2. Install Java Mission Control 8.1 or newer from update site : https://github.com/AdoptOpenJDK/openjdk-jmc-overrides/releases/download/8.1.0/org.openjdk.jmc.updatesite.ide-8.1.0.zip
   - Download
   - Help \ Install New Software ... \ Add ... \ Archive ... \ Select the downloaded file
   - Select Java Mission Control and any other features you want
3. Add Jolokia JMC plugins from the plugin site: https://skarsaune.github.io/jolokia-jmc-update-site
   - If Jolokia and Kubernetes features do not show up, deselect "Group items by category"
   - Install Jolokia and Kubernetes plugins

# Connecting to JVMs
## To connect directly to JVMs with Jolokia over a regular network connection:
   - Open the JVM Browser view
   - Add JMX service url
   - Enter `service:jmx:jolokia///host:port/path/`
## To use Jolokia





# To develop or troubleshoot the Jolokia or Kubernetes plugins
1. Clone jmc `git clone git@github.com:openjdk/jmc.git`
2. Open the projects in an Eclipse workspace
3. Clone this repo or a fork of it
4. Open the projects in the same workspace as where you have the jmc projects
5. Debug JMC or Eclipse with JMC with one of the Jolokia or Kubernetes launchers
6. Feel free to register issues with suggestions or problems in this repo