Testing Jolokia in JMC

Steps to try out Joloka with JMC
* Download and build the open source JMC (7) from https://hg.openjdk.java.net/jmc/jmc7/ (mercurial/hg)
* Open the projects of jmc in an Eclipse Plugin IDE (2019-09 or later)
* Temporary until Jolokia change is released:
  * clone from my fork : git@github.com:skarsaune/jolokia.git
  * checkout branch jmx_adapter
  * `mvn clean install`
* Get dependencies for this project ` mvn dependency:copy-dependencies`
* Build this project `mvn install`
* Open this project in the same workspace
* Use one of the launchers in this project (Either as Eclipse with plugins or standalone RCP)

