package org.openjdk.jmc.kubernetes.preferences;

import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.openjdk.jmc.kubernetes.JmcKubernetesPlugin;
import org.openjdk.jmc.ui.misc.PasswordFieldEditor;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class JmcKubernetesPreferences extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	
	private Map<FieldEditor, Object> dependantControls=new WeakHashMap<>();

	public JmcKubernetesPreferences() {
		super(GRID);
		setPreferenceStore(JmcKubernetesPlugin.getDefault().getPreferenceStore());
		setDescription("Options for scanning kubernetes cluster for JVMs to connect to");
	}

	/**
	 * Creates the field editors. Field editors are abstractions of the common GUI
	 * blocks needed to manipulate various types of preferences. Each field editor
	 * knows how to save and restore itself.
	 */
	public void createFieldEditors() {
		BooleanFieldEditor mainEnabler = new BooleanFieldEditor(PreferenceConstants.P_SCAN_FOR_INSTANCES, "&Scan for instances in cluster",
				getFieldEditorParent()) {
			@Override
			protected void valueChanged(boolean oldValue, boolean newValue) {
				super.valueChanged(oldValue, newValue);
				enableDependantFields(newValue);
			}
		};
		addField(mainEnabler);

		this.addDependantField(new BooleanFieldEditor(PreferenceConstants.P_SCAN_ALL_CONTEXTS, "Scan all &contexts in cluster",
						getFieldEditorParent()));
		this.addDependantField(new StringFieldEditor(PreferenceConstants.P_PATH_LABEL, "Path in &label", getFieldEditorParent()));
		this.addDependantField(new StringFieldEditor(PreferenceConstants.P_USE_PORT, "Use a dedicated &port named", getFieldEditorParent()));
		this.addDependantField(new StringFieldEditor(PreferenceConstants.P_USERNAME, "Require &username", getFieldEditorParent()));
		this.addDependantField(new PasswordFieldEditor(PreferenceConstants.P_PASSWORD, "Require pass&word", getFieldEditorParent()));
		//set initial enablement
		enableDependantFields(JmcKubernetesPlugin.getDefault().scanForInstances());

	}

	private void addDependantField(FieldEditor field) {
		this.dependantControls.put(field, null);
		addField(field);
	}
	
	private void enableDependantFields(boolean enabled) {
		for(FieldEditor field : this.dependantControls.keySet()) {
			field.setEnabled(enabled,getFieldEditorParent());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {
	}

}