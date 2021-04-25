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

public class JmcKubernetesPreferenceForm extends FieldEditorPreferencePage
		implements IWorkbenchPreferencePage, PreferenceConstants {

	private Map<FieldEditor, Object> dependantControls = new WeakHashMap<>();

	public JmcKubernetesPreferenceForm() {
		super(GRID);
		setPreferenceStore(JmcKubernetesPlugin.getDefault().getPreferenceStore());
		setDescription("Options that allows you to scan kubernetes for JVMs running Jolokia\n\n");
	}

	/**
	 * Creates the field editors. Field editors are abstractions of the common GUI
	 * blocks needed to manipulate various types of preferences. Each field editor
	 * knows how to save and restore itself.
	 */
	public void createFieldEditors() {
		BooleanFieldEditor mainEnabler = new BooleanFieldEditor(P_SCAN_FOR_INSTANCES, "&Scan for kubernetes pods with Jolokia support",
				getFieldEditorParent()) {
			@Override
			protected void valueChanged(boolean oldValue, boolean newValue) {
				super.valueChanged(oldValue, newValue);
				enableDependantFields(newValue);
			}
		};
		addField(mainEnabler);

		this.addDependantField(new BooleanFieldEditor(P_SCAN_ALL_CONTEXTS, "Scan pods from all locally configured &contexts, if false: only scan the current contexts",
						getFieldEditorParent()));
		this.addTextField(new StringFieldEditor(P_REQUIRE_LABEL, "Require &label to scan pod", getFieldEditorParent()), "Only attempt to connect to pods with this label set, leave empty to scal all pods");
		this.addTextField(new StringFieldEditor(P_JOLOKIA_PORT, "Jolokia &path in pods", getFieldEditorParent()), "Use this path for jolokia, or specify ${kubernetes/attribute/attributeName} to be able to specify per pod");
		this.addTextField(new StringFieldEditor(P_JOLOKIA_PORT, "Jolokia p&ort in pods", getFieldEditorParent()), "Port to use, leave empty to use default port of Kubernetes proxy, alternatively ${kubernetes/attribute/attributeName} to be able to specify per pod" );
		this.addTextField(new StringFieldEditor(P_USERNAME, "Require &username", getFieldEditorParent()), "Username , alternatively use ${kubernetes/secret/secretName/secretItem} where the secret is in the same namespace as the pod and the type is either kubernetes.io/basic-auth or Opaque with java.util.Properties compatible values");
		PasswordFieldEditor passwordField = new PasswordFieldEditor(P_PASSWORD, "Require pass&word", getFieldEditorParent());
		String passwordTooltip = "Password , alternatively use ${kubernetes/secret/secretName/secretItem} where the secret is in the same namespace as the pod and the type is either kubernetes.io/basic-auth or Opaque with java.util.Properties compatible values";
		passwordField.getTextControl(getFieldEditorParent()).setToolTipText(passwordTooltip);
		this.addDependantField(passwordField);
		//set initial enablement
		enableDependantFields(JmcKubernetesPlugin.getDefault().scanForInstances());

	}

	private void addTextField(StringFieldEditor field, String tooltip) {
		this.addDependantField(field);
		field.getTextControl(getFieldEditorParent()).setToolTipText(tooltip);
		field.getLabelControl(getFieldEditorParent()).setToolTipText(tooltip);


	}

	private void addDependantField(FieldEditor field) {
		this.dependantControls.put(field, null);
		addField(field);
	}

	private void enableDependantFields(boolean enabled) {
		for (FieldEditor field : this.dependantControls.keySet()) {
			field.setEnabled(enabled, getFieldEditorParent());
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