/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.preferences.br;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Tree;

import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.preferences.IPreferences;
import net.sourceforge.pmd.eclipse.runtime.preferences.impl.PreferenceUIStore;
import net.sourceforge.pmd.eclipse.ui.ModifyListener;
import net.sourceforge.pmd.eclipse.ui.actions.RuleSetUtil;
import net.sourceforge.pmd.eclipse.ui.nls.StringKeys;
import net.sourceforge.pmd.eclipse.ui.preferences.editors.SWTUtil;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.EditorUsageMode;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.ExclusionPanelManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.PerRulePropertyPanelManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.RulePanelManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.RulePropertyManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.SummaryPanelManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.XPathPanelManager;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.lang.rule.RuleSet;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertySource;

/**
 * 
 * @author Brian Remedios
 */
public class PMDPreferencePage2 extends AbstractPMDPreferencePage
        implements RuleSelectionListener, ModifyListener, ValueChangeListener, ValueResetHandler {

    private TabFolder tabFolder;
    private RulePropertyManager[] rulePropertyManagers;
    private RuleTableManager tableManager;
    private Button globalRuleManagementCheckButton;
    private Composite contentPanel;

    // columns shown in the rule treetable in the desired order
    public static final RuleColumnDescriptor[] AVAILABLE_COLUMNS = new RuleColumnDescriptor[] { RuleTableColumns.NAME,
        // PreferenceTableColumns.priorityName,
        // IconColumnDescriptor.priority,
        RuleTableColumns.IMG_PRIORITY,
        // PreferenceTableColumns.fixCount,
        RuleTableColumns.SINCE, RuleTableColumns.RULE_SET_NAME, RuleTableColumns.RULE_TYPE, RuleTableColumns.MIN_LANGUAGE_VERSION,
        RuleTableColumns.MAX_LANGUAGE_VERSION, RuleTableColumns.LANGUAGE,
        // regex text -> compact color squares (for comparison)
        RuleTableColumns.FILTER_VIOLATION_REGEX,
        // xpath text -> compact color circles (for comparison)
        RuleTableColumns.FILTER_VIOLATION_XPATH, 
        RuleTableColumns.MOD_COUNT,
        // PreferenceTableColumns.properties
        RuleTableColumns.IMG_PROPERTIES };

    // last item in this list is the grouping used at startup
    public static final Object[][] GROUPING_CHOICES = new Object[][] {
        { RuleTableColumns.RULE_SET_NAME, StringKeys.PREF_RULESET_COLUMN_RULESET },
        { RuleTableColumns.SINCE, StringKeys.PREF_RULESET_GROUPING_PMD_VERSION },
        { RuleTableColumns.PRIORITY_NAME, StringKeys.PREF_RULESET_COLUMN_PRIORITY },
        { RuleTableColumns.RULE_TYPE, StringKeys.PREF_RULESET_COLUMN_RULE_TYPE },
        { RuleTableColumns.LANGUAGE, StringKeys.PREF_RULESET_COLUMN_LANGUAGE },
        { RuleTableColumns.FILTER_VIOLATION_REGEX, StringKeys.PREF_RULESET_GROUPING_REGEX },
        { null, StringKeys.PREF_RULESET_GROUPING_NONE } };

    public static RulePropertyManager[] buildPropertyManagersOn(TabFolder folder, ValueChangeListener listener) {

        return new RulePropertyManager[] {
            buildFullViewTab(folder, 0, SWTUtil.stringFor(StringKeys.PREF_RULESET_TAB_FULLVIEW), listener),
            buildRuleTab(folder, 1, SWTUtil.stringFor(StringKeys.PREF_RULESET_TAB_RULE), listener),
            // buildDescriptionTab(folder, 2,
            // SWTUtil.stringFor(StringKeys.PREF_RULESET_TAB_DESCRIPTION),
            // listener),
            buildPropertyTab(folder, 2, SWTUtil.stringFor(StringKeys.PREF_RULESET_TAB_PROPERTIES), listener),
            buildExclusionTab(folder, 3, SWTUtil.stringFor(StringKeys.PREF_RULESET_TAB_EXCLUSIONS), listener),
            buildXPathTab(folder, 4, SWTUtil.stringFor(StringKeys.PREF_RULESET_TAB_XPATH), listener),
            // buildQuickFixTab(folder, 6,
            // SWTUtil.stringFor(StringKeys.MSGKEY_PREF_RULESET_TAB_FIXES),
            // listener),
            // buildExampleTab(folder, 6,
            // SWTUtil.stringFor(StringKeys.PREF_RULESET_TAB_EXAMPLES),
            // listener),
        };
    }

    @Override
    protected String descriptionId() {
        return StringKeys.PREF_RULESET_TITLE;
    }

    @Override
    protected Control createContents(Composite parent) {

        tableManager = new RuleTableManager("rules", AVAILABLE_COLUMNS, PMDPlugin.getDefault().loadPreferences(), this);
        tableManager.modifyListener(this);
        tableManager.selectionListener(this);

        populateRuleset();

        Composite composite = new Composite(parent, SWT.NULL);
        layoutControls(composite);

        tableManager.populateRuleTable();
        int i = PreferenceUIStore.INSTANCE.selectedPropertyTab();
        tabFolder.setSelection(i);

        setModified(false);

        return composite;
    }

    /**
     * Create buttons for rule properties table management.
     * 
     * @param parent
     *            Composite
     * @return Composite
     */
    public static Composite buildRulePropertiesTableButtons(Composite parent) {
        Composite composite = new Composite(parent, SWT.NULL);
        RowLayout rowLayout = new RowLayout();
        rowLayout.type = SWT.VERTICAL;
        rowLayout.wrap = false;
        rowLayout.pack = false;
        composite.setLayout(rowLayout);

        return composite;
    }

    private Composite createRuleSection(Composite parent) {

        Composite ruleSection = new Composite(parent, SWT.NULL);

        // Create the controls (order is important !)
        Composite groupCombo = tableManager.buildGroupCombo(ruleSection, StringKeys.PREF_RULESET_RULES_GROUPED_BY,
                GROUPING_CHOICES);

        Tree ruleTree = tableManager.buildRuleTreeViewer(ruleSection);
        tableManager.groupBy(null);

        Composite ruleTableButtons = tableManager.buildRuleTableButtons(ruleSection);
        Composite rulePropertiesTableButtons = buildRulePropertiesTableButtons(ruleSection);

        // Place controls on the layout
        GridLayout gridLayout = new GridLayout(3, false);
        ruleSection.setLayout(gridLayout);

        GridData data = new GridData();
        data.horizontalSpan = 3;
        groupCombo.setLayoutData(data);

        data = new GridData();
        data.heightHint = 200;
        data.widthHint = 350;
        data.horizontalSpan = 1;
        data.horizontalAlignment = GridData.FILL;
        data.verticalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        data.grabExcessVerticalSpace = true;
        ruleTree.setLayoutData(data);

        data = new GridData();
        data.horizontalSpan = 1;
        data.horizontalAlignment = GridData.FILL;
        data.verticalAlignment = GridData.FILL;
        ruleTableButtons.setLayoutData(data);

        data = new GridData();
        data.horizontalSpan = 1;
        data.horizontalAlignment = GridData.FILL;
        data.verticalAlignment = GridData.FILL;
        rulePropertiesTableButtons.setLayoutData(data);

        return ruleSection;
    }

    private TabFolder buildTabFolder(Composite parent) {

        tabFolder = new TabFolder(parent, SWT.TOP);

        rulePropertyManagers = buildPropertyManagersOn(tabFolder, this);

        tabFolder.pack();
        return tabFolder;
    }

    private static RulePropertyManager buildRuleTab(TabFolder parent, int index, String title,
            ValueChangeListener listener) {

        TabItem tab = new TabItem(parent, 0, index);
        tab.setText(title);

        RulePanelManager manager = new RulePanelManager(title, EditorUsageMode.Editing, listener, null);
        tab.setControl(manager.setupOn(parent));
        manager.tab(tab);
        return manager;
    }

    private static RulePropertyManager buildPropertyTab(TabFolder parent, int index, String title,
            ValueChangeListener listener) {

        TabItem tab = new TabItem(parent, 0, index);
        tab.setText(title);

        PerRulePropertyPanelManager manager = new PerRulePropertyPanelManager(title, EditorUsageMode.Editing, listener);
        tab.setControl(manager.setupOn(parent));
        manager.tab(tab);
        return manager;
    }

    private static RulePropertyManager buildXPathTab(TabFolder parent, int index, String title,
            ValueChangeListener listener) {

        TabItem tab = new TabItem(parent, 0, index);
        tab.setText(title);

        XPathPanelManager manager = new XPathPanelManager(title, EditorUsageMode.Editing, listener);
        tab.setControl(manager.setupOn(parent));
        manager.tab(tab);
        return manager;
    }

    private static RulePropertyManager buildFullViewTab(TabFolder parent, int index, String title,
            ValueChangeListener listener) {

        TabItem tab = new TabItem(parent, 0, index);
        tab.setText(title);

        SummaryPanelManager manager = new SummaryPanelManager("asdf", title, EditorUsageMode.Editing, listener);
        tab.setControl(manager.setupOn(parent));
        manager.tab(tab);
        return manager;
    }

    private static RulePropertyManager buildExclusionTab(TabFolder parent, int index, String title,
            ValueChangeListener listener) {

        TabItem tab = new TabItem(parent, 0, index);
        tab.setText(title);

        ExclusionPanelManager manager = new ExclusionPanelManager(title, EditorUsageMode.Editing, listener, true);
        tab.setControl(manager.setupOn(parent));
        manager.tab(tab);
        return manager;
    }

    @Override
    public void changed(PropertySource source, PropertyDescriptor<?> desc, Object newValue) {
        // TODO enhance to recognize default values
        setModified();
        tableManager.updated(source);
    }

    @Override
    public void changed(RuleSelection selection, PropertyDescriptor<?> desc, Object newValue) {
        // TODO enhance to recognize default values

        for (Rule rule : selection.allRules()) {
            if (newValue != null) {
                // non-reliable update behaviour, alternate
                // trigger option - weird
                tableManager.changed(selection, desc, newValue);
                // System.out.println("doing redraw");
            } else {
                tableManager.changed(rule, desc, newValue);
                // System.out.println("viewer update");
            }
        }
        for (RulePropertyManager manager : rulePropertyManagers) {
            manager.validate();
        }

        setModified();
    }

    /**
     * Main layout.
     * 
     * @param parent
     *            Composite
     */
    private void layoutControls(Composite parent) {
        GridLayout layout = new GridLayout(1, false);
        layout.verticalSpacing = 10;
        parent.setLayout(layout);

        Composite checkboxPanel = new Composite(parent, 0);
        RowLayout checkboxPanelLayout = new RowLayout(SWT.VERTICAL);
        checkboxPanelLayout.fill = true;
        checkboxPanelLayout.pack = false;
        checkboxPanel.setLayout(checkboxPanelLayout);
        checkboxPanel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        final Button checkButton = new Button(checkboxPanel, SWT.CHECK);
        globalRuleManagementCheckButton = checkButton;

        contentPanel = new Composite(parent, 0);
        contentPanel.setLayout(new FormLayout());
        contentPanel.setLayoutData(new GridData(GridData.FILL_BOTH));

        checkButton.setText(SWTUtil.stringFor(StringKeys.PREF_RULESET_BUTTON_GLOBALRULEMANAGEMENT));
        checkButton.setSelection(preferences.getGlobalRuleManagement());
        checkButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean sel = checkButton.getSelection();
                SWTUtil.setEnabledRecursive(contentPanel.getChildren(), sel);
                setModified();
            }
        });

        Label explanation = new Label(checkboxPanel, SWT.WRAP);
        RowData rowData = new RowData();
        rowData.width = 450;
        explanation.setLayoutData(rowData);
        explanation.setText(SWTUtil.stringFor(StringKeys.PREF_RULESET_BUTTON_GLOBALRULEMANAGEMENT_EXPL));

        int ruleTableFraction = 55; // PreferenceUIStore.instance.tableFraction();

        // Create the sash first, so the other controls can be attached to it.
        final Sash sash = new Sash(contentPanel, SWT.HORIZONTAL);
        FormData data = new FormData();
        data.left = new FormAttachment(0, 0); // attach to left
        data.right = new FormAttachment(100, 0); // attach to right
        data.top = new FormAttachment(ruleTableFraction, 0);
        sash.setLayoutData(data);
        sash.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                // Re-attach to the top edge, and we use the y value of the
                // event to determine the offset from the top
                ((FormData) sash.getLayoutData()).top = new FormAttachment(0, event.y);
                // PreferenceUIStore.instance.tableFraction(event.y);
                contentPanel.layout();
            }
        });

        // Create the first text box and attach its bottom edge to the sash
        Composite ruleSection = createRuleSection(contentPanel);
        data = new FormData();
        data.top = new FormAttachment(0, 0);
        data.bottom = new FormAttachment(sash, 0);
        data.left = new FormAttachment(0, 0);
        data.right = new FormAttachment(100, 0);
        ruleSection.setLayoutData(data);

        // Create the second text box and attach its top edge to the sash
        TabFolder propertySection = buildTabFolder(contentPanel);
        data = new FormData();
        data.top = new FormAttachment(sash, 0);
        data.bottom = new FormAttachment(100, 0);
        data.left = new FormAttachment(0, 0);
        data.right = new FormAttachment(100, 0);
        propertySection.setLayoutData(data);

        SWTUtil.setEnabledRecursive(contentPanel.getChildren(), checkButton.getSelection());
    }

    @Override
    public boolean performOk() {
        if (isModified()) {
            preferences.setGlobalRuleManagement(globalRuleManagementCheckButton.getSelection());
            updateRuleSet();
            storeActiveRules();
            rebuildProjects();
        }
        saveUIState();

        return super.performOk();
    }

    @Override
    public boolean performCancel() {
        saveUIState();
        return super.performCancel();
    }

    @Override
    protected void performDefaults() {
        globalRuleManagementCheckButton.setSelection(IPreferences.GLOBAL_RULE_MANAGEMENT_DEFAULT);
        SWTUtil.setEnabledRecursive(contentPanel.getChildren(), globalRuleManagementCheckButton.getSelection());

        RuleSet defaultRuleSet = plugin.getPreferencesManager().getDefaultRuleSet();
        tableManager.useRuleSet(defaultRuleSet);
        tableManager.setAllItemsActive();
        tableManager.populateRuleTable();

        super.performDefaults();
    }

    private void populateRuleset() {
        RuleSet defaultRuleSet = plugin.getPreferencesManager().getRuleSet();
        RuleSet ruleSet = RuleSetUtil.newCopyOf(defaultRuleSet);

        tableManager.useRuleSet(ruleSet);
    }

    @Override
    public void selection(RuleSelection selection) {

        if (rulePropertyManagers == null) {
            return;
        }

        for (RulePropertyManager manager : rulePropertyManagers) {
            manager.manage(selection);
            manager.validate();
        }
    }

    private void saveUIState() {
        tableManager.saveUIState();
        int i = tabFolder.getSelectionIndex();
        PreferenceUIStore.INSTANCE.selectedPropertyTab(i);
        PreferenceUIStore.INSTANCE.save();
    }

    private void storeActiveRules() {
        List<Rule> chosenRules = tableManager.activeRules();
        Set<String> activeRules = new HashSet<>();
        for (Rule rule : chosenRules) {
            activeRules.add(rule.getName());
        }

        // override all the active rules
        preferences.setActiveRuleNames(activeRules);

        // System.out.println("Active rules: " +
        // preferences.getActiveRuleNames());
    }

    private void updateRuleSet() {
        try {
            ProgressMonitorDialog monitorDialog = new ProgressMonitorDialog(getShell());
            monitorDialog.run(true, true, new IRunnableWithProgress() {
                @Override
                public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                    plugin.getPreferencesManager().setRuleSet(tableManager.ruleSet());
                }
            });
        } catch (Exception e) {
            plugin.logError("Exception updating all projects after a preference change", e);
        }
    }

    @Override
    public void resetValuesIn(RuleSelection rules) {
        rules.useDefaultValues();
        tableManager.refresh();
        for (RulePropertyManager rpm : rulePropertyManagers) {
            rpm.loadValues();
        }
    }
}
