package org.eclipse.jmc.jolokia;

import org.openjdk.jmc.rjmx.descriptorprovider.IDescriptorListener;
import org.openjdk.jmc.rjmx.descriptorprovider.IDescriptorProvider;


public class JolokiaDiscoveryListener implements IDescriptorProvider {

	public String getName() {
		return "Jolokia Discovery Listener";
	}

	public String getDescription() {
		return "List JVM with Jolokia agent broadcasting its presence";
	}

	@Override
	public void addDescriptorListener(IDescriptorListener l) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeDescriptorListener(IDescriptorListener l) {
		// TODO Auto-generated method stub
		
	}
	
	/**
	 * This is where we periodically scan the local machines and report deltas to the listeners.
	 *
	private class LocalScanner implements Runnable {
		boolean isRunning;

		@Override
		public void run() {
			isRunning = true;
			while (isRunning) {
				try {
					scan();
					Thread.sleep(LOCAL_REFRESH_INTERVAL);
				} catch (InterruptedException e) {
					// Don't mind being interrupted.
				}
			}
		}

		/**
		 * Marks this scanner as terminated.
		 *
		public void shutdown() {
			isRunning = false;
		}

		private void scan() {
			HashMap<Integer, DiscoveryEntry> newOnes = new HashMap<>();
			DiscoveryEntry[] props = LocalJVMToolkit.getAttachableJVMs();
			for (DiscoveryEntry prop : props) {
				newOnes.put(prop.getServerDescriptor().getJvmInfo().getPid(), prop);
			}

			synchronized (lastDescriptors) {
				// Remove stale ones...
				for (Iterator<Entry<Integer, DiscoveryEntry>> entryIterator = lastDescriptors.entrySet()
						.iterator(); entryIterator.hasNext();) {
					Entry<Integer, DiscoveryEntry> entry = entryIterator.next();
					if (newOnes.containsKey(entry.getKey())) {
						continue;
					}
					DiscoveryEntry d = entry.getValue();
					entryIterator.remove();
					onDescriptorRemoved(d.getServerDescriptor().getGUID());
				}

				// Add new ones...
				for (Entry<Integer, DiscoveryEntry> entry : newOnes.entrySet()) {
					if (lastDescriptors.containsKey(entry.getKey())) {
						continue;
					}
					DiscoveryEntry d = entry.getValue();
					onDescriptorDetected(d.getServerDescriptor(), null, null, d.getConnectionDescriptor());
				}
				lastDescriptors.clear();
				lastDescriptors.putAll(newOnes);
			}
		}
	}
*/

}
