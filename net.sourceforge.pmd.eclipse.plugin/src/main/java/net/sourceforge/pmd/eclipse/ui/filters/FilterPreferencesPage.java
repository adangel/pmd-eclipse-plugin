/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.filters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.preferences.IPreferencesManager;
import net.sourceforge.pmd.eclipse.ui.BasicTableLabelProvider;
import net.sourceforge.pmd.eclipse.ui.PMDUiConstants;
import net.sourceforge.pmd.eclipse.ui.actions.internal.InternalRuleSetUtil;
import net.sourceforge.pmd.eclipse.ui.nls.StringKeys;
import net.sourceforge.pmd.eclipse.ui.preferences.br.AbstractPMDPreferencePage;
import net.sourceforge.pmd.eclipse.ui.preferences.br.BasicTableManager;
import net.sourceforge.pmd.eclipse.ui.preferences.br.RuleSelection;
import net.sourceforge.pmd.eclipse.ui.preferences.br.SizeChangeListener;
import net.sourceforge.pmd.eclipse.ui.preferences.br.ValueChangeListener;
import net.sourceforge.pmd.eclipse.util.ResourceManager;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertySource;

/**
 * The available report formats and their properties.
 *
 * @author Brian Remedios
 */
public class FilterPreferencesPage extends AbstractPMDPreferencePage
        implements ValueChangeListener, SizeChangeListener {

    private TableViewer tableViewer;

    private Button removeButton;
    private Button excludeButt;
    private Button includeButt;
    // private Button cpdButt;
    // private Button pmdButt;
    private Text patternField;

    private Collection<Control> editorWidgets = new ArrayList<>();

    private static Image includeIcon = plugin.getImage("include", "icons/ok.gif");
    private static Image excludeIcon = plugin.getImage("exclude", "icons/forbidden.png");

    private static final String NEW_FILTER_PATTERN = "<finish this>";

    public static Image typeIconFor(FilterHolder holder) {
        return holder.isInclude ? includeIcon : excludeIcon;
    }

    private static Label createLabel(Composite panel, String text) {
        Label label = new Label(panel, SWT.None);
        label.setLayoutData(new GridData());
        label.setText(text);
        return label;
    }

    // private static Button createButton(Composite panel, int type, String
    // label) {
    // Button butt = new Button(panel, type);
    // butt.setLayoutData( new GridData(SWT.LEFT, SWT.CENTER, false, false, 1,
    // 1));
    // butt.setText(label);
    // return butt;
    // }

    private static Button createButton(Composite panel, int type, Image image, String tooltip) {
        Button butt = new Button(panel, type);
        butt.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
        butt.setImage(image);
        butt.setToolTipText(tooltip);
        return butt;
    }

    /**
     * Create and initialize the controls of the page
     *
     * 
     * @param parent
     *            Composite
     * @return Control
     * @see PreferencePage#createContents
     */
    @Override
    protected Control createContents(Composite parent) {

        // Create parent composite
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(2, false);
        layout.verticalSpacing = 10;
        composite.setLayout(layout);

        // Create panels
        Composite filterGroup = buildFilterGroup(composite);
        Composite buttonPanel = buildTableButtons(composite);

        // Layout children
        filterGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        buttonPanel.setLayoutData(new GridData());
        buildFilterEditor(parent);

        updateControls();

        return composite;
    }

    private FilterHolder[] currentFilters() {

        List<FilterHolder> holders = new ArrayList<>();

        RuleSet ruleSet = plugin.getPreferencesManager().getRuleSet();

        for (Pattern pattern : ruleSet.getFileExclusions()) {
            holders.add(new FilterHolder(pattern.pattern(), true, false, false));
        }
        for (Pattern pattern : ruleSet.getFileInclusions()) {
            holders.add(new FilterHolder(pattern.pattern(), true, false, true));
        }
        return holders.toArray(new FilterHolder[0]);
    }

    private void enableEditor(boolean flag) {

        for (Control control : editorWidgets) {
            control.setEnabled(flag);
        }
    }

    private List<Pattern> tableFilters(boolean isInclude) {

        List<Pattern> filters = new ArrayList<>();

        for (TableItem ti : tableViewer.getTable().getItems()) {
            FilterHolder fh = (FilterHolder) ti.getData();
            if (fh.isInclude == isInclude) {
                try {
                    Pattern pattern = Pattern.compile(fh.pattern);
                    filters.add(pattern);
                } catch (PatternSyntaxException e) {
                    PMDPlugin.getDefault().showError("Invalid Pattern: " + fh.pattern, e);
                }
            }
        }

        return filters;
    }

    /**
     * Build the group of priority preferences
     * 
     * @param parent
     *            the parent composite
     * 
     * @return the group widget
     */
    private Composite buildFilterGroup(Composite parent) {

        IStructuredContentProvider contentProvider = new IStructuredContentProvider() {
            @Override
            public void dispose() {
                // nothing to do
            }

            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
                // nothing to do
            }

            @Override
            public Object[] getElements(Object inputElement) {
                return (FilterHolder[]) inputElement;
            }
        };
        BasicTableLabelProvider labelProvider = new BasicTableLabelProvider(FilterColumnUI.VISIBLE_COLUMNS);

        BasicTableManager<FilterHolder> reportTableMgr = new BasicTableManager<>("renderers", null, FilterColumnUI.VISIBLE_COLUMNS);
        tableViewer = reportTableMgr.buildTableViewer(parent,
                SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI | SWT.FULL_SELECTION | SWT.CHECK);
        reportTableMgr.setupColumns(FilterColumnUI.VISIBLE_COLUMNS);

        Table table = tableViewer.getTable();
        table.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true, 1, 1));

        tableViewer.setLabelProvider(labelProvider);
        tableViewer.setContentProvider(contentProvider);
        table.setHeaderVisible(true);

        tableViewer.setInput(currentFilters());

        selectCheckedFilters();

        TableColumn[] columns = table.getColumns();
        for (TableColumn column : columns) {
            column.pack();
        }

        tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                patternsSelected();
            }
        });

        tableViewer.getTable().addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event event) {
                if (event.detail == SWT.CHECK) {
                    checked(event.item);
                }
            }
        });

        return parent;
    }

    private void patternsSelected() {
        IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
        selectedPatterns(filtersIn(selection.toList()));
        updateControls();
    }

    private void selectedPatterns(Collection<FilterHolder> holders) {

        setState(holders, excludeButt, FilterHolder.EXCLUDE_ACCESSOR);
        setState(holders, includeButt, FilterHolder.INCLUDE_ACCESSOR);
        // setState(holders, pmdButt, FilterHolder.PMDAccessor);
        // setState(holders, cpdButt, FilterHolder.CPDAccessor);
        setValue(holders, patternField, FilterHolder.PATTERN_ACCESSOR);
    }

    private static void setState(Collection<FilterHolder> holders, Button button, FilterHolder.Accessor accessor) {

        Boolean state = FilterHolder.boolValueOf(holders, accessor);
        if (state == null) {
            button.setGrayed(true);
            return;
        }

        button.setSelection(state);
    }

    private static void setValue(Collection<FilterHolder> holders, Text field, FilterHolder.Accessor accessor) {

        String text = FilterHolder.textValueOf(holders, accessor);
        field.setText(text);
    }

    // private void setAllPMD(boolean state) {
    // for (FilterHolder fh : selectedFilters()) {
    // fh.forPMD = state;
    // }
    // }
    //
    // private void setAllCPD(boolean state) {
    // for (FilterHolder fh : selectedFilters()) {
    // fh.forCPD = state;
    // }
    // }

    private void setAllInclude(boolean state) {
        for (FilterHolder fh : selectedFilters()) {
            fh.isInclude = state;
        }
    }

    private void setAllPatterns(String pattern) {
        for (FilterHolder fh : selectedFilters()) {
            fh.pattern = pattern;
        }
    }

    private void buildFilterEditor(Composite parent) {

        Composite editorPanel = new Composite(parent, SWT.None);
        editorPanel.setLayoutData(new GridData(GridData.FILL, GridData.FILL, false, true));
        editorPanel.setLayout(new GridLayout(3, false));

        Label typeLabel = new Label(editorPanel, SWT.None);
        typeLabel.setLayoutData(new GridData());
        typeLabel.setText("Type:");
        editorWidgets.add(typeLabel);

        excludeButt = createButton(editorPanel, SWT.RADIO, excludeIcon, "Exclude");
        excludeButt.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent se) {
                setAllInclude(!includeButt.getSelection());
                tableViewer.refresh();
            }
        });

        includeButt = createButton(editorPanel, SWT.RADIO, includeIcon, "Include");
        includeButt.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent se) {
                setAllInclude(includeButt.getSelection());
                tableViewer.refresh();
            }
        });

        editorWidgets.add(excludeButt);
        editorWidgets.add(includeButt);

        // Label contextLabel = createLabel(editorPanel, "Applies to:");
        // editorWidgets.add(contextLabel);

        // pmdButt = createButton(editorPanel, SWT.CHECK, "PMD");
        // pmdButt.addSelectionListener( new SelectionAdapter() {
        // public void widgetSelected(SelectionEvent se) {
        // setAllPMD(pmdButt.getSelection());
        // tableViewer.refresh();
        // }
        // });
        //
        // cpdButt = createButton(editorPanel, SWT.CHECK, "CPD");
        // cpdButt.addSelectionListener( new SelectionAdapter() {
        // public void widgetSelected(SelectionEvent se) {
        // setAllCPD(cpdButt.getSelection());
        // tableViewer.refresh();
        // }
        // });

        // editorWidgets.add(pmdButt);
        // editorWidgets.add(cpdButt);

        Label patternLabel = createLabel(editorPanel, "Pattern:");
        editorWidgets.add(patternLabel);

        patternField = new Text(editorPanel, SWT.BORDER);
        patternField.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false, 2, 1));
        patternField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent fe) {
                setAllPatterns(patternField.getText());
                tableViewer.refresh();
            }
        });
        editorWidgets.add(patternField);

        createLabel(editorPanel, ""); // spacer
        Label description = new Label(editorPanel, SWT.None);
        description.setLayoutData(new GridData(GridData.FILL, GridData.BEGINNING, true, false, 2, 1));
        description.setText("name or path pattern (* = any string, ? = any character)");

        editorWidgets.add(description);
    }

    /**
     * Create buttons for rule table management.
     * 
     * @param parent
     *            Composite
     * @return Composite
     */
    public Composite buildTableButtons(Composite parent) {

        Composite composite = new Composite(parent, SWT.NULL);
        GridLayout gridLayout = new GridLayout();
        gridLayout.numColumns = 1;
        gridLayout.verticalSpacing = 3;
        composite.setLayout(gridLayout);

        Button addButton = buildAddButton(composite);
        removeButton = buildRemoveButton(composite);

        GridData data = new GridData();
        addButton.setLayoutData(data);

        return composite;
    }

    /**
     * Check the filters as noted from the preferences.
     */
    private void selectCheckedFilters() {

        Set<String> activeOnes = preferences.activeExclusionPatterns();
        activeOnes.addAll(preferences.activeInclusionPatterns());

        for (TableItem item : tableViewer.getTable().getItems()) {
            FilterHolder holder = (FilterHolder) item.getData();
            item.setChecked(activeOnes.contains(holder.pattern));
        }
    }

    /**
     *
     * @return Set<String>
     */
    private Set<FilterHolder> currentCheckedFilters() {

        Set<FilterHolder> holders = new HashSet<>();
        for (Object holder : checkedItems(tableViewer.getTable())) {
            holders.add((FilterHolder) holder);
        }
        return holders;
    }

    /**
     *
     * @return Set<String>
     */
    private Set<FilterHolder> selectedFilters() {

        Set<FilterHolder> holders = new HashSet<>();
        for (Object tItem : tableViewer.getTable().getSelection()) {
            holders.add((FilterHolder) (((TableItem) tItem).getData()));
        }
        return holders;
    }

    /**
     *
     * @return Set<String>
     */
    private static Collection<FilterHolder> filtersIn(List<?> tableItems) {

        Set<FilterHolder> holders = new HashSet<>();
        for (Object tItem : tableItems) {
            holders.add((FilterHolder) tItem);
        }
        return holders;
    }

    /**
     * Method checkedItems.
     * 
     * @param table
     *            Table
     * @return Set<Object>
     */
    private static Set<Object> checkedItems(Table table) {

        Set<Object> checkedItems = new HashSet<>();

        for (TableItem ti : table.getItems()) {
            if (ti.getChecked()) {
                checkedItems.add(ti.getData());
            }
        }
        return checkedItems;
    }

    /**
     *
     * @param item
     *            Object
     */
    private void checked(Object item) { //NOPMD unused formal parameter

        // FIXME
        boolean matches = currentCheckedFilters().equals(preferences.activeExclusionPatterns());

        setModified(!matches);
    }

    @Override
    protected void performDefaults() {
        // TODO
    }

    @Override
    public boolean performCancel() {
        // clear out any changes for next possible usage
        selectCheckedFilters();
        return true;
    }

    private static Set<String> patternsIn(Collection<FilterHolder> holders, boolean getInclusions) {

        if (holders.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> patterns = new HashSet<>();
        for (FilterHolder holder : holders) {
            if (holder.isInclude == getInclusions) {
                patterns.add(holder.pattern);
            }
        }
        return patterns;
    }

    protected Button newImageButton(Composite parent, String imageId, String toolTipId) {

        Button button = new Button(parent, SWT.PUSH | SWT.LEFT);
        button.setImage(ResourceManager.imageFor(imageId));
        button.setToolTipText(getMessage(toolTipId));
        button.setEnabled(true);
        return button;
    }

    /**
     * Build the edit rule button
     * 
     * @param parent
     *            Composite
     * @return Button
     */
    public Button buildAddButton(final Composite parent) {

        Button button = newImageButton(parent, PMDUiConstants.ICON_BUTTON_ADD,
                StringKeys.PREF_RULESET_BUTTON_ADDFILTER);

        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                addNewFilter();
            }
        });

        return button;
    }

    private FilterHolder[] tableFiltersWith(FilterHolder anotherOne) {

        FilterHolder[] holders = new FilterHolder[tableViewer.getTable().getItemCount() + (anotherOne == null ? 0 : 1)];

        TableItem[] items = tableViewer.getTable().getItems();
        for (int i = 0; i < items.length; i++) {
            holders[i] = (FilterHolder) items[i].getData();
        }

        if (anotherOne != null) {
            holders[holders.length - 1] = anotherOne;
        }

        return holders;
    }

    private void addNewFilter() {
        FilterHolder newHolder = new FilterHolder(NEW_FILTER_PATTERN, true, false, false);

        FilterHolder[] holders = tableFiltersWith(newHolder);
        tableViewer.setInput(holders);

        tableViewer.getTable().setSelection(holders.length - 1);

        patternsSelected();
        patternField.selectAll();
        patternField.forceFocus();
    }

    /**
     * Build the edit rule button.
     * 
     * @param parent
     *            Composite
     * @return Button
     */
    public Button buildRemoveButton(final Composite parent) {

        Button button = newImageButton(parent, PMDUiConstants.ICON_BUTTON_DELETE,
                StringKeys.PREF_RULESET_BUTTON_REMOVEFILTER);

        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                deleteSelected();
            }
        });

        return button;
    }

    private void deleteSelected() {
        IStructuredSelection sel = (IStructuredSelection) tableViewer.getSelection();
        if (sel.isEmpty()) {
            return;
        }

        Object[] selections = sel.toArray();
        tableViewer.remove(selections);
    }

    @Override
    public boolean performOk() {

        IPreferencesManager ipMgr = plugin.getPreferencesManager();
        RuleSet ruleSet = ipMgr.getRuleSet();
        ruleSet = InternalRuleSetUtil.setFileExclusions(ruleSet, tableFilters(false));
        ruleSet = InternalRuleSetUtil.setFileInclusions(ruleSet, tableFilters(true));
        ipMgr.setRuleSet(ruleSet);

        Set<FilterHolder> filters = currentCheckedFilters();
        preferences.activeExclusionPatterns(patternsIn(filters, false));
        preferences.activeInclusionPatterns(patternsIn(filters, true));

        preferences.sync();

        if (isModified()) {
            rebuildProjects();
        }

        return super.performOk();
    }

    /**
     * Method descriptionId.
     * 
     * @return String
     */
    @Override
    protected String descriptionId() {
        return "???"; // TODO
    }

    @Override
    public void changed(PropertySource source, PropertyDescriptor<?> desc, Object newValue) {
        // TODO enable/disable save/cancel buttons
    }

    private void updateControls() {

        boolean hasSelections = !selectedFilters().isEmpty();
        removeButton.setEnabled(hasSelections);
        enableEditor(hasSelections);
    }

    // ignore these

    @Override
    public void addedRows(int newRowCount) {
        // ignored
    }

    @Override
    public void changed(RuleSelection rule, PropertyDescriptor<?> desc, Object newValue) {
        // ignored
    }
}
