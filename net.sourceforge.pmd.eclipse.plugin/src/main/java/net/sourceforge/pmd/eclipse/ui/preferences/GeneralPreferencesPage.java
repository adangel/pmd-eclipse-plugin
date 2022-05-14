/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.preferences;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.ColorSelector;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import net.sourceforge.pmd.PMDVersion;
import net.sourceforge.pmd.RulePriority;
import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.plugin.UISettings;
import net.sourceforge.pmd.eclipse.runtime.builder.MarkerUtil;
import net.sourceforge.pmd.eclipse.runtime.preferences.IPreferences;
import net.sourceforge.pmd.eclipse.ui.BasicTableLabelProvider;
import net.sourceforge.pmd.eclipse.ui.Shape;
import net.sourceforge.pmd.eclipse.ui.ShapePicker;
import net.sourceforge.pmd.eclipse.ui.model.RootRecord;
import net.sourceforge.pmd.eclipse.ui.nls.StringKeys;
import net.sourceforge.pmd.eclipse.ui.preferences.br.BasicTableManager;
import net.sourceforge.pmd.eclipse.ui.priority.PriorityColumnUI;
import net.sourceforge.pmd.eclipse.ui.priority.PriorityDescriptor;
import net.sourceforge.pmd.eclipse.ui.priority.PriorityDescriptorCache;
import net.sourceforge.pmd.eclipse.ui.priority.PriorityDescriptorIcon;

/**
 * The top-level page for PMD preferences
 *
 * @see CPDPreferencePage
 * @see PMDPreferencePage
 *
 * @author Philippe Herlin
 * @author Brian Remedios
 * @author Phillip Krall
 */
public class GeneralPreferencesPage extends PreferencePage implements IWorkbenchPreferencePage {

    private static final String[] LOG_LEVELS = { "OFF", "FATAL", "ERROR", "WARN", "INFO", "DEBUG", "ALL" };
    private static final RGB SHAPE_COLOR = new RGB(255, 255, 255);

    private Text additionalCommentText;
    private Label sampleLabel;
    private Button showPerspectiveBox;
    private Button showViolationsOverviewViewBox;
    private Button showViolationsOutlineViewBox;
    private Button useProjectBuildPath;
    private Button checkCodeOnSave;
    private Button useCustomPriorityNames;
    private Button reviewPmdStyleBox;
    private Text logFileNameText;
    private Scale logLevelScale;
    private Label logLevelValueLabel;
    private TableViewer tableViewer;
    private IPreferences preferences;
    private BasicTableManager<?> priorityTableMgr;
    private Button determineFiletypesAutomatically;

    private Control[] nameFields;

    /**
     * Initialize the page.
     *
     * @see PreferencePage#init
     */
    @Override
    public void init(IWorkbench arg0) {
        // setDescription(getMessage(StringKeys.MSGKEY_PREF_GENERAL_TITLE));
        preferences = PMDPlugin.getDefault().loadPreferences();
    }

    /**
     * Create and initialize the controls of the page.
     *
     * @see PreferencePage#createContents
     */
    @Override
    protected Control createContents(Composite parent) {

        // Create parent composite
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.verticalSpacing = 10;
        composite.setLayout(layout);

        // Create groups
        Group generalGroup = buildGeneralGroup(composite);
        Group priorityGroup = buildPriorityGroup(composite);
        Group reviewGroup = buildReviewGroup(composite);
        Group logGroup = buildLoggingGroup(composite);
        Group aboutGroup = buildAboutGroup(composite);

        // Layout children
        generalGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        priorityGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        logGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        aboutGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        GridData data = new GridData();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        reviewGroup.setLayoutData(data);

        return composite;
    }

    /**
     * Build the group of general preferences.
     * 
     * @param parent
     *            the parent composite
     * @return the group widget
     */
    private Group buildGeneralGroup(final Composite parent) {

        Group group = new Group(parent, SWT.SHADOW_IN);
        group.setText(getMessage(StringKeys.PREF_GENERAL_GROUP_GENERAL));
        group.setLayout(new GridLayout(1, false));

        showPerspectiveBox = buildShowPerspectiveBoxButton(group);
        showViolationsOverviewViewBox = buildShowViolationOverviewBoxButton(group);
        showViolationsOutlineViewBox = buildShowViolationOutlineBoxButton(group);
        useProjectBuildPath = buildUseProjectBuildPathButton(group);
        checkCodeOnSave = buildCheckCodeOnSaveButton(group);
        determineFiletypesAutomatically = buildDetermineFiletypesAutomatically(group);
        Label separator = new Label(group, SWT.SEPARATOR | SWT.SHADOW_IN | SWT.HORIZONTAL);

        GridData data = new GridData();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        showPerspectiveBox.setLayoutData(data);

        data = new GridData();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        showViolationsOverviewViewBox.setLayoutData(data);
        
        data = new GridData();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        showViolationsOutlineViewBox.setLayoutData(data);
        
        data = new GridData();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        useProjectBuildPath.setLayoutData(data);

        data = new GridData();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        separator.setLayoutData(data);

        return group;
    }

    private Link createPreferenceLink(Composite parent, String label, final String prefPageId) {

        Link link = new Link(parent, SWT.None);
        link.setText(label);
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent se) {
                PreferenceDialog pref = PreferencesUtil.createPreferenceDialogOn(getShell(), prefPageId,
                        new String[] {}, null);
                if (pref != null) {
                    pref.open();
                }
            }
        });

        return link;
    }

    private void useCustomPriorityNames(boolean flag) {

        priorityTableMgr.visible(PriorityColumnUI.NAME, flag);
        priorityTableMgr.visible(PriorityColumnUI.PMD_NAME, !flag);

        UISettings.useCustomPriorityLabels(flag);
        for (Control field : nameFields) {
            field.setEnabled(flag);
        }
    }

    /**
     * Build the group of priority preferences.
     * 
     * @param parent
     *            the parent composite
     * @return the group widget
     */
    private Group buildPriorityGroup(final Composite parent) {

        Group group = new Group(parent, SWT.SHADOW_IN);
        group.setText(getMessage(StringKeys.PREF_GENERAL_GROUP_PRIORITIES));
        group.setLayout(new GridLayout(2, false));

        Link link = createPreferenceLink(group,
                "PMD folder annotations can be enabled on the <A>label decorations</A> page",
                "org.eclipse.ui.preferencePages.Decorators");
        link.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false, 1, 1));

        useCustomPriorityNames = buildUseCustomPriorityNamesButton(group);
        useCustomPriorityNames.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false, 1, 1));

        IStructuredContentProvider contentProvider = new AbstractStructuredContentProvider() {
            @Override
            public Object[] getElements(Object inputElement) {
                return (RulePriority[]) inputElement;
            }
        };
        BasicTableLabelProvider labelProvider = new BasicTableLabelProvider(PriorityColumnUI.VISIBLE_COLUMNS);

        priorityTableMgr = new BasicTableManager<>("prio", null, PriorityColumnUI.VISIBLE_COLUMNS);
        tableViewer = priorityTableMgr.buildTableViewer(group);
        priorityTableMgr.setupColumns(PriorityColumnUI.VISIBLE_COLUMNS);

        Table table = tableViewer.getTable();
        table.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, true, 2, 1));

        tableViewer.setLabelProvider(labelProvider);
        tableViewer.setContentProvider(contentProvider);
        table.setHeaderVisible(true);
        // labelProvider.addColumnsTo(table);
        tableViewer.setInput(UISettings.currentPriorities(true));

        // TableColumn[] columns = table.getColumns();
        // for (TableColumn column : columns) column.pack();

        Composite editorPanel = new Composite(group, SWT.None);
        editorPanel.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, true, 2, 1));
        editorPanel.setLayout(new GridLayout(3, false));

        Label shapeLabel = new Label(editorPanel, SWT.None);
        shapeLabel.setLayoutData(new GridData());
        shapeLabel.setText("Shape and Color:");

        final ShapePicker<Shape> ssc = new ShapePicker<>(editorPanel, SWT.None, 14);
        ssc.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        ssc.setSize(280, 30);
        ssc.setShapeMap(UISettings.shapeSet(SHAPE_COLOR, 10));
        ssc.setItems(UISettings.allShapes());

        final ColorSelector colorPicker = new ColorSelector(editorPanel);

        new Label(editorPanel, SWT.NONE).setText("Icon:");
        final IconSelector iconSelector = new IconSelector(editorPanel);
        iconSelector.setLayoutData(new GridData(SWT.LEFT, GridData.CENTER, true, true, 2, 1));

        Label nameLabel = new Label(editorPanel, SWT.NONE);
        nameLabel.setLayoutData(new GridData());
        nameLabel.setText("Name:");

        final Text priorityName = new Text(editorPanel, SWT.BORDER);
        priorityName.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, true, 2, 1));

        nameFields = new Control[] { nameLabel, priorityName };

        // final Label descLabel = new Label(editorPanel, SWT.None);
        // descLabel.setLayoutData( new GridData(GridData.FILL, GridData.CENTER,
        // false, true, 1, 1));
        // descLabel.setText("Description:");

        // final Text priorityDesc = new Text(editorPanel, SWT.BORDER);
        // priorityDesc.setLayoutData( new GridData(GridData.FILL,
        // GridData.CENTER, true, true, 5, 1) );

        tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                selectedPriorities(selection.toList(), ssc, colorPicker, priorityName, iconSelector);
            }
        });

        ssc.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                IStructuredSelection selection = (IStructuredSelection) event.getSelection();
                if (!selection.isEmpty()) {
                    setShape((Shape) selection.getFirstElement());
                    iconSelector.setSelectedIcon(null);
                }
            }
        });

        iconSelector.addListener(new IPropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                if (event.getNewValue() != null) {
                    ssc.removeSelection();
                    for (PriorityDescriptor pd : selectedDescriptors()) {
                        pd.iconId = ((PriorityDescriptorIcon) event.getNewValue()).getIconId();
                    }

                    tableViewer.refresh();
                }
            }
        });

        colorPicker.addListener(new IPropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                setColor((RGB) event.getNewValue());
            }
        });

        priorityName.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                setName(priorityName.getText());
            }
        });

        // only set this once the name fields are built
        useCustomPriorityNames.setSelection(preferences.useCustomPriorityNames());

        return group;
    }

    private void setShape(Shape shape) {

        if (shape == null) {
            return; // renderers can't handle this
        }

        for (PriorityDescriptor pd : selectedDescriptors()) {
            pd.shape.shape = shape;
            pd.iconId = null;
        }

        tableViewer.refresh();
    }

    private void setColor(RGB clr) {
        for (PriorityDescriptor pd : selectedDescriptors()) {
            pd.shape.rgbColor = clr;
        }
        tableViewer.refresh();
    }

    private void setName(String newName) {

        if (StringUtils.isBlank(newName)) {
            return;
        }

        for (PriorityDescriptor pd : selectedDescriptors()) {
            pd.label = newName;
        }
        tableViewer.refresh();
    }

    private PriorityDescriptor[] selectedDescriptors() {

        Object[] items = ((IStructuredSelection) tableViewer.getSelection()).toArray();
        PriorityDescriptor[] descs = new PriorityDescriptor[items.length];
        for (int i = 0; i < descs.length; i++) {
            descs[i] = PriorityDescriptorCache.INSTANCE.descriptorFor((RulePriority) items[i]);
        }
        return descs;
    }

    private static void selectedPriorities(List<RulePriority> items, ShapePicker<Shape> ssc, ColorSelector colorPicker,
            Text nameField, IconSelector iconSelector) {

        if (items.size() != 1) {
            ssc.setSelection((Shape) null);
            nameField.setText("");
            return;
        }

        RulePriority priority = items.get(0);
        PriorityDescriptor desc = PriorityDescriptorCache.INSTANCE.descriptorFor(priority);

        nameField.setText(desc.label);
        colorPicker.setColorValue(desc.shape.rgbColor);
        if (desc.iconId == null) {
            ssc.setSelection(desc.shape.shape);
        } else {
            ssc.removeSelection();
        }
        iconSelector.setSelectedIcon(PriorityDescriptorIcon.getById(desc.iconId));
    }

    /**
     * Build the group of review preferences
     * 
     * @param parent
     *            the parent composite
     * @return the group widget
     */
    private Group buildReviewGroup(final Composite parent) {

        // build the group
        Group group = new Group(parent, SWT.SHADOW_IN);
        group.setText(getMessage(StringKeys.PREF_GENERAL_GROUP_REVIEW));
        group.setLayout(new GridLayout(1, false));

        // build children
        reviewPmdStyleBox = buildReviewPmdStyleBoxButton(group);
        Label separator = new Label(group, SWT.SEPARATOR | SWT.SHADOW_IN | SWT.HORIZONTAL);
        buildLabel(group, StringKeys.PREF_GENERAL_LABEL_ADDCOMMENT);
        additionalCommentText = buildAdditionalCommentText(group);
        buildLabel(group, StringKeys.PREF_GENERAL_LABEL_SAMPLE);
        sampleLabel = buildSampleLabel(group);
        updateSampleLabel();

        // layout children
        GridData data = new GridData();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        reviewPmdStyleBox.setLayoutData(data);

        data = new GridData();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        separator.setLayoutData(data);

        data = new GridData();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        additionalCommentText.setLayoutData(data);

        data = new GridData();
        data.horizontalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;
        sampleLabel.setLayoutData(data);

        return group;
    }

    /**
     * Build the log group. Note that code is a cut & paste from the Eclipse
     * Visual Editor
     *
     */
    private Group buildLoggingGroup(Composite parent) {
        GridData gridData2 = new GridData();
        gridData2.horizontalSpan = 2;
        gridData2.horizontalAlignment = SWT.FILL;
        GridData gridData11 = new GridData();
        gridData11.horizontalAlignment = org.eclipse.swt.layout.GridData.FILL;
        gridData11.horizontalSpan = 3;
        GridData gridData3 = new GridData();
        gridData3.horizontalAlignment = org.eclipse.swt.layout.GridData.FILL;
        gridData3.horizontalSpan = 3;
        GridData gridData1 = new GridData();
        gridData1.grabExcessHorizontalSpace = true;
        gridData1.horizontalAlignment = org.eclipse.swt.layout.GridData.FILL;
        GridData gridData = new GridData();
        gridData.grabExcessHorizontalSpace = false;
        GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 3;

        Group loggingGroup = new Group(parent, SWT.NONE);
        loggingGroup.setText(getMessage(StringKeys.PREF_GENERAL_GROUP_LOGGING));
        loggingGroup.setLayout(gridLayout);

        Label logFileNameLabel = new Label(loggingGroup, SWT.NONE);
        logFileNameLabel.setText(getMessage(StringKeys.PREF_GENERAL_LABEL_LOG_FILE_NAME));
        logFileNameLabel.setLayoutData(gridData);

        logFileNameText = new Text(loggingGroup, SWT.BORDER);
        logFileNameText.setText(this.preferences.getLogFileName());
        logFileNameText.setToolTipText(getMessage(StringKeys.PREF_GENERAL_TOOLTIP_LOG_FILE_NAME));
        logFileNameText.setLayoutData(gridData1);

        Button browseButton = new Button(loggingGroup, SWT.NONE);
        browseButton.setText(getMessage(StringKeys.PREF_GENERAL_BUTTON_BROWSE));
        browseButton.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                browseLogFile();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent event) {
                // do nothing
            }
        });

        Label separator = new Label(loggingGroup, SWT.SEPARATOR | SWT.HORIZONTAL);
        separator.setLayoutData(gridData11);

        Label logLevelLabel = new Label(loggingGroup, SWT.NONE);
        logLevelLabel.setText(getMessage(StringKeys.PREF_GENERAL_LABEL_LOG_LEVEL));

        logLevelValueLabel = new Label(loggingGroup, SWT.NONE);
        logLevelValueLabel.setText("");
        logLevelValueLabel.setLayoutData(gridData2);

        logLevelScale = new Scale(loggingGroup, SWT.NONE);
        logLevelScale.setMaximum(6);
        logLevelScale.setPageIncrement(1);
        logLevelScale.setLayoutData(gridData3);
        logLevelScale.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                updateLogLevelValueLabel();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent event) {
                updateLogLevelValueLabel();
            }
        });

        logLevelScale.setSelection(intLogLevel(this.preferences.getLogLevelName()));
        updateLogLevelValueLabel();

        return loggingGroup;
    }

    /**
     * Build the about group.
     */
    private Group buildAboutGroup(Composite parent) {
        GridData gridData1 = new GridData();
        gridData1.grabExcessHorizontalSpace = false;

        GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 2;

        Group aboutGroup = new Group(parent, SWT.NONE);
        aboutGroup.setText(getMessage(StringKeys.PREF_GENERAL_GROUP_ABOUT));
        aboutGroup.setLayout(gridLayout);

        StringBuilder aboutText = new StringBuilder();
        aboutText.append(getMessage(StringKeys.PREF_GENERAL_LABEL_PMD_ECLIPSE_VERSION)).append(" ")
                .append(PMDPlugin.version).append("\n");
        aboutText.append(getMessage(StringKeys.PREF_GENERAL_LABEL_PMD_VERSION)).append(" ")
                .append(PMDVersion.VERSION).append("\n");

        Label aboutLabel = new Label(aboutGroup, SWT.NONE);
        aboutLabel.setText(aboutText.toString());
        aboutLabel.setLayoutData(gridData1);

        return aboutGroup;
    }

    /**
     * Build a label.
     */
    private Label buildLabel(Composite parent, String msgKey) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(msgKey == null ? "" : getMessage(msgKey));
        return label;
    }

    /**
     * Build the sample.
     */
    private Label buildSampleLabel(Composite parent) {
        return new Label(parent, SWT.WRAP);
    }

    /**
     * Build the text for additional comment input
     *
     * @param parent
     * @return
     */
    private Text buildAdditionalCommentText(Composite parent) {
        Text text = new Text(parent, SWT.SINGLE | SWT.BORDER);
        text.setText(preferences.getReviewAdditionalComment());
        text.setToolTipText(getMessage(StringKeys.PREF_GENERAL_TOOLTIP_ADDCOMMENT));

        text.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                updateSampleLabel();
            }
        });

        return text;
    }

    /**
     * Build the check box for showing the PMD perspective
     * 
     * @param viewGroup
     *            the parent composite
     *
     */
    private Button buildUseCustomPriorityNamesButton(Composite parent) {
        Button button = new Button(parent, SWT.CHECK);
        button.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false, 1, 1));
        button.setText("Use custom names");
        button.setSelection(preferences.useCustomPriorityNames());
        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent se) {
                useCustomPriorityNames(((Button) se.getSource()).getSelection());
            }
        });
        return button;
    }

    /**
     * Build the check box for showing the PMD perspective.
     * 
     * @param viewGroup
     *            the parent composite
     *
     */
    private Button buildCheckCodeOnSaveButton(Composite viewGroup) {
        Button button = new Button(viewGroup, SWT.CHECK);
        button.setText(getMessage(StringKeys.PREF_GENERAL_LABEL_CHECK_AFTER_SAVING));
        button.setSelection(preferences.isCheckAfterSaveEnabled());
        return button;
    }

    /**
     * Build the check box for showing the PMD perspective.
     * 
     * @param viewGroup
     *            the parent composite
     *
     */
    private Button buildShowPerspectiveBoxButton(Composite viewGroup) {
        Button button = new Button(viewGroup, SWT.CHECK);
        button.setText(getMessage(StringKeys.PREF_GENERAL_LABEL_SHOW_PERSPECTIVE));
        button.setSelection(preferences.isPmdPerspectiveEnabled());
        return button;
    }
    
    /**
     * Build the button and label to show the violations overview when running a code review.
     * 
     * @param viewGroup the view group
     * @return
     */
    private Button buildShowViolationOverviewBoxButton(Composite viewGroup) {
        Button button = new Button(viewGroup, SWT.CHECK);
        button.setText(getMessage(StringKeys.PREF_GENERAL_LABEL_SHOW_VIOLATIONS_OVERVIEW));
        button.setSelection(preferences.isPmdViolationsOverviewEnabled());
        return button;
    }
    
    /**
     * Build the button and label to show the violations outline when running a code review.
     * 
     * @param viewGroup the view group
     * @return
     */
    private Button buildShowViolationOutlineBoxButton(Composite viewGroup) {
        Button button = new Button(viewGroup, SWT.CHECK);
        button.setText(getMessage(StringKeys.PREF_GENERAL_LABEL_SHOW_VIOLATIONS_OUTLINE));
        button.setSelection(preferences.isPmdViolationsOutlineEnabled());
        return button;
    }

    /**
     * Build the check box for enabling using Project Build Path.
     * 
     * @param viewGroup
     *            the parent composite
     */
    private Button buildUseProjectBuildPathButton(Composite viewGroup) {
        Button button = new Button(viewGroup, SWT.CHECK);
        button.setText(getMessage(StringKeys.PREF_GENERAL_LABEL_USE_PROJECT_BUILD_PATH));
        button.setSelection(preferences.isProjectBuildPathEnabled());
        return button;
    }

    private Button buildDetermineFiletypesAutomatically(Composite viewGroup) {
        Button button = new Button(viewGroup, SWT.CHECK);
        button.setText(getMessage(StringKeys.PREF_GENERAL_LABEL_DETERMINE_FILETYPES_AUTOMATICALLY));
        button.setSelection(preferences.isDetermineFiletypesAutomatically());
        return button;
    }

    /**
     * Build the check box for enabling PMD review style.
     * 
     * @param viewGroup
     *            the parent composite
     *
     */
    private Button buildReviewPmdStyleBoxButton(final Composite parent) {
        Button button = new Button(parent, SWT.CHECK);
        button.setText(getMessage(StringKeys.PREF_GENERAL_REVIEW_PMD_STYLE));
        button.setSelection(preferences.isReviewPmdStyleEnabled());

        return button;
    }

    public static void setSelection(Button button, boolean flag) {
        if (button == null || button.isDisposed()) {
            return;
        }
        button.setSelection(flag);
    }

    public static void setText(Text field, String txt) {
        if (field == null || field.isDisposed()) {
            return;
        }
        field.setText(txt);
    }

    @Override
    protected void performDefaults() {
        for (RulePriority priority : RulePriority.values()) {
            PriorityDescriptor defaultDescriptor = PMDPlugin.getDefault().getPreferencesManager().defaultDescriptorFor(priority);
            PriorityDescriptor descriptor = PriorityDescriptorCache.INSTANCE.descriptorFor(priority);
            descriptor.shape.shape = defaultDescriptor.shape.shape;
            descriptor.shape.rgbColor = defaultDescriptor.shape.rgbColor;
            descriptor.label = defaultDescriptor.label;
            descriptor.iconId = defaultDescriptor.iconId;
        }
        tableViewer.refresh();

        setText(additionalCommentText, IPreferences.REVIEW_ADDITIONAL_COMMENT_DEFAULT);

        setSelection(showPerspectiveBox, IPreferences.PMD_PERSPECTIVE_ENABLED_DEFAULT);
        setSelection(showViolationsOverviewViewBox, IPreferences.PMD_VIOLATIONS_OVERVIEW_ENABLED_DEFAULT);
        setSelection(showViolationsOutlineViewBox, IPreferences.PMD_VIOLATIONS_OUTLINE_ENABLED_DEFAULT);
        setSelection(checkCodeOnSave, IPreferences.PMD_CHECK_AFTER_SAVE_DEFAULT);
        setSelection(useCustomPriorityNames, IPreferences.PMD_USE_CUSTOM_PRIORITY_NAMES_DEFAULT);
        setSelection(useProjectBuildPath, IPreferences.PROJECT_BUILD_PATH_ENABLED_DEFAULT);
        setSelection(reviewPmdStyleBox, IPreferences.REVIEW_PMD_STYLE_ENABLED_DEFAULT);
        setSelection(determineFiletypesAutomatically, IPreferences.DETERMINE_FILETYPES_AUTOMATICALLY_DEFAULT);

        setText(logFileNameText, IPreferences.LOG_FILENAME_DEFAULT);

        if (logLevelScale != null) {
            logLevelScale.setSelection(intLogLevel(IPreferences.LOG_LEVEL_DEFAULT));
            updateLogLevelValueLabel();
        }
    }

    /**
     * Update the sample label when the additional comment text is modified.
     */
    protected void updateSampleLabel() {
        String pattern = additionalCommentText.getText();
        try {
            String commentText = MessageFormat.format(pattern,
                    new Object[] { System.getProperty("user.name", ""), new Date() });

            sampleLabel.setText(commentText);
            setMessage(getMessage(StringKeys.PREF_GENERAL_HEADER), NONE);
            setValid(true);

        } catch (IllegalArgumentException e) {
            setMessage(getMessage(StringKeys.PREF_GENERAL_MESSAGE_INCORRECT_FORMAT), ERROR);
            setValid(false);
        }
    }

    /**
     * Update the label of the log level to reflect the log level selected.
     *
     */
    protected void updateLogLevelValueLabel() {
        this.logLevelValueLabel.setText(LOG_LEVELS[this.logLevelScale.getSelection()]);
    }

    /**
     * Display a file selection dialog in order to let the user select a log
     * file.
     *
     */
    protected void browseLogFile() {
        FileDialog dialog = new FileDialog(getShell(), SWT.SAVE);
        dialog.setText(getMessage(StringKeys.PREF_GENERAL_DIALOG_BROWSE));
        String fileName = dialog.open();
        if (fileName != null) {
            logFileNameText.setText(fileName);
        }
    }

    private void updateMarkerIcons() {
        if (!PriorityDescriptorCache.INSTANCE.hasChanges()) {
            return;
        }

        PriorityDescriptorCache.INSTANCE.storeInPreferences();

        // refresh the resources so that the rule label decorator is updated
        RootRecord root = new RootRecord(ResourcesPlugin.getWorkspace().getRoot());
        Set<IFile> files = MarkerUtil.allMarkedFiles(root);
        PMDPlugin.getDefault().changedFiles(files);

        // Refresh the views to pick up the marker change
        PMDPlugin.getDefault().refreshView(PMDPlugin.VIOLATIONS_OVERVIEW_ID); 
        PMDPlugin.getDefault().refreshView(PMDPlugin.VIOLATIONS_OUTLINE_ID);

        // Take the color to set the overview ruler color
        // net.sourceforge.pmd.eclipse.plugin.annotation.prio1.color
        ScopedPreferenceStore editorsPreferenceStore = new ScopedPreferenceStore(InstanceScope.INSTANCE, "org.eclipse.ui.editors");
        for (RulePriority priority : RulePriority.values()) {
            PriorityDescriptor descriptor = PriorityDescriptorCache.INSTANCE.descriptorFor(priority);
            editorsPreferenceStore.setValue("net.sourceforge.pmd.eclipse.plugin.annotation.prio" + priority.getPriority() + ".color",
                    descriptor.shape.rgbColor.red + "," + descriptor.shape.rgbColor.green + "," + descriptor.shape.rgbColor.blue);
        }
    }

    @Override
    public boolean performCancel() {
        // clear out any changes for next possible usage
        PriorityDescriptorCache.INSTANCE.loadFromPreferences();
        return true;
    }

    @Override
    public boolean performOk() {

        updateMarkerIcons();

        if (additionalCommentText != null) {
            preferences.setReviewAdditionalComment(additionalCommentText.getText());
        }

        if (showPerspectiveBox != null) {
            preferences.setPmdPerspectiveEnabled(showPerspectiveBox.getSelection());
        }
        
        if (showViolationsOverviewViewBox != null) {
            preferences.setPmdViolationsOverviewEnabled(showViolationsOverviewViewBox.getSelection());
        }
        
        if (showViolationsOutlineViewBox != null) {
            preferences.setPmdViolationsOutlineEnabled(showViolationsOutlineViewBox.getSelection());
        }

        if (checkCodeOnSave != null) {
            boolean doCheck = checkCodeOnSave.getSelection();
            preferences.isCheckAfterSaveEnabled(doCheck);
            PMDPlugin.getDefault().fileChangeListenerEnabled(doCheck);
        }

        if (useCustomPriorityNames != null) {
            preferences.useCustomPriorityNames(useCustomPriorityNames.getSelection());
        }

        if (useProjectBuildPath != null) {
            preferences.setProjectBuildPathEnabled(useProjectBuildPath.getSelection());
        }

        if (determineFiletypesAutomatically != null) {
            preferences.setDetermineFiletypesAutomatically(determineFiletypesAutomatically.getSelection());
        }

        if (reviewPmdStyleBox != null) {
            preferences.setReviewPmdStyleEnabled(reviewPmdStyleBox.getSelection());
        }

        if (logFileNameText != null) {
            preferences.setLogFileName(logFileNameText.getText());
        }

        if (logLevelScale != null) {
            preferences.setLogLevel(LOG_LEVELS[logLevelScale.getSelection()]);
        }

        preferences.sync();

        PMDPlugin.getDefault().applyLogPreferences(preferences);

        return true;
    }

    /**
     * Return the selection index corresponding to the log level
     */
    private int intLogLevel(String level) {
        int result = Arrays.asList(LOG_LEVELS).indexOf(level);
        if (result < 0 || result > 6) {
            result = 0;
        }
        return result;
    }

    /**
     * Helper method to shorten message access
     * 
     * @param key
     *            a message key
     * @return requested message
     */
    private String getMessage(String key) {
        return PMDPlugin.getDefault().getStringTable().getString(key);
    }

}
