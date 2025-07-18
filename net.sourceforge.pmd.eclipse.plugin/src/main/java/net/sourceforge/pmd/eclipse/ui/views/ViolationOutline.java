/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.views;

import java.util.Objects;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.IPage;

import net.sourceforge.pmd.eclipse.plugin.UISettings;
import net.sourceforge.pmd.eclipse.ui.PMDUiConstants;
import net.sourceforge.pmd.eclipse.ui.model.FileRecord;
import net.sourceforge.pmd.eclipse.ui.nls.StringKeys;
import net.sourceforge.pmd.eclipse.ui.views.actions.DisableRuleAction;
import net.sourceforge.pmd.eclipse.ui.views.actions.PriorityFilterAction;
import net.sourceforge.pmd.eclipse.ui.views.actions.QuickFixAction;
import net.sourceforge.pmd.eclipse.ui.views.actions.RemoveViolationAction;
import net.sourceforge.pmd.eclipse.ui.views.actions.ReviewAction;
import net.sourceforge.pmd.eclipse.ui.views.actions.ShowViolationDetailsAction;
import net.sourceforge.pmd.lang.rule.RulePriority;

/**
 * A View to show a list of PMD-Violations for a file
 *
 * @author SebastianRaffel ( 08.05.2005 )
 */
public class ViolationOutline extends AbstractPMDPagebookView implements ISelectionProvider {

    private FileRecord resourceRecord;
    private PriorityFilter priorityFilter;

    protected static final String COLUMN_WIDTHS = "tableColumnWidths";
    protected static final String COLUMN_SORTER = "tableColumnSorter";

    @Override
    public void createPartControl(Composite parent) {
        addFilterControls();
        super.createPartControl(parent);
        getSite().setSelectionProvider(this);
    }

    @Override
    protected String pageMessageId() {
        return StringKeys.VIEW_OUTLINE_DEFAULT_TEXT;
    }

    @Override
    protected String mementoFileId() {
        return PMDUiConstants.MEMENTO_OUTLINE_FILE;
    }

    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);
        priorityFilter = PriorityFilter.getInstance();
    }

    @Override
    protected PageRec doCreatePage(IWorkbenchPart part) {
        if (resourceRecord != null) {
            // creates a new ViolationOutlinePageBR, when a Resource exists
            ViolationOutlinePageBR page = new ViolationOutlinePageBR(resourceRecord, this);
            initPage(page);
            page.createControl(getPageBook());
            loadColumnData(page);
            return new PageRec(part, page);
        }
        return null;
    }

    @Override
    protected void doDestroyPage(IWorkbenchPart part, PageRec pageRecord) {
        ViolationOutlinePageBR page = (ViolationOutlinePageBR) pageRecord.page;

        // get the State of the destroyed Page for loading it into the
        // next Page -> different Pages look like one
        if (page != null) {
            storeColumnData(page);
            memento.save();
            page.dispose();
        }

        pageRecord.dispose();
    }

    /**
     * Creates a DropDownMenu for the view.
     */
    private void addFilterControls() {
        IMenuManager manager = getViewSite().getActionBars().getMenuManager();

        // we add the PriorityFilter-Actions to this Menu
        RulePriority[] priorities = UISettings.currentPriorities(true);
        for (RulePriority priority : priorities) {
            Action filterAction = new PriorityFilterAction(priority, this);
            if (priorityFilter.isPriorityEnabled(priority)) {
                filterAction.setChecked(true);
            }

            manager.add(filterAction);
        }
    }

    /**
     * Creates a Context Menu for the View.
     * 
     * @param viewer
     */
    public Menu createContextMenu(final TableViewer viewer) {
        MenuManager manager = new MenuManager();
        manager.setRemoveAllWhenShown(true);
        // here we add the Context Menus Actions
        manager.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                buildMenu(manager, viewer);
            }
        });

        Table table = viewer.getTable();
        return manager.createContextMenu(table);
    }

    private void buildMenu(IMenuManager manager, TableViewer viewer) {
        // show the Violation Details Dialog
        Action showViolationDetailsAction = new ShowViolationDetailsAction(this.getSite().getShell(), viewer);
        manager.add(showViolationDetailsAction);

        // add Review Comment
        ReviewAction reviewAction = new ReviewAction(viewer);
        manager.add(reviewAction);

        // Remove Violation
        RemoveViolationAction removeAction = new RemoveViolationAction(viewer);
        manager.add(removeAction);

        // Disable rule
        DisableRuleAction disableAction = new DisableRuleAction(viewer);
        disableAction.setEnabled(disableAction.hasActiveRules());
        manager.add(disableAction);

        // Quick Fix (where possible)
        QuickFixAction quickFixAction = new QuickFixAction(viewer);
        quickFixAction.setEnabled(quickFixAction.hasQuickFix());
        manager.add(quickFixAction);

        // additions Action: Clear reviews
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS + "-end"));
    }

    @Override
    public void partActivated(IWorkbenchPart part) {
        // We only care about the editor
        if (part instanceof IEditorPart) {
            // If there is a file opened in the editor, we create a record for it
            resourceRecord = tryForFileRecordFrom(part); 

        } else {
            // We also want to get the editors when it's not active
            // so we pretend, that the editor has been activated
            IEditorPart editorPart = getSitePage().getActiveEditor();
            if (editorPart != null) {
                partActivated(editorPart);
            }
        }

        IWorkbenchPage page = getSitePage();
        IWorkbenchPart activePart = page.getActivePart();
        if (activePart == null) {
            page.activate(this);
        }

        super.partActivated(part);
        refresh();
    }

    @Override
    protected void showPageRec(PageRec pageRec) {
        ViolationOutlinePageBR oldPage = getCurrentOutlinePage();
        ViolationOutlinePageBR newPage = null;
        if (pageRec.page instanceof ViolationOutlinePageBR) {
            newPage = (ViolationOutlinePageBR) pageRec.page;
        }

        // here we change from one Page to another
        // so we get the State of the old Page, put it in a Memento
        // and load it into the new Page, so it looks like the old one
        if (!Objects.equals(oldPage, newPage)) {
            if (oldPage != null) {
                storeColumnData(oldPage);
            }
            // we load the stuff into the new Page
            loadColumnData(newPage);
        }

        super.showPageRec(pageRec);
    }

    private void storeColumnData(ViolationOutlinePageBR page) {
        if (page != null) {
            // we care about the column widths
            save(COLUMN_WIDTHS, page.getColumnWidths());
            // ... and what Element is sorted, and in which way
            save(COLUMN_SORTER, page.getSorterProperties());
        }
    }

    private void loadColumnData(ViolationOutlinePageBR page) {
        if (page != null) {
            page.setColumnWidths(getIntegerList(COLUMN_WIDTHS));
            page.setSorterProperties(getIntegerList(COLUMN_SORTER));
        }
    }

    /**
     * @return the currently displayed Page
     */
    private ViolationOutlinePageBR getCurrentOutlinePage() {
        IPage page = super.getCurrentPage();
        if (!(page instanceof ViolationOutlinePageBR)) {
            return null;
        }

        return (ViolationOutlinePageBR) page;
    }

    /**
     * @return a List of the current ViewerFilters
     */
    public ViewerFilter[] getFilters() {
        return new ViewerFilter[] { priorityFilter };
    }

    /**
     * Refreshes, reloads the View.
     */
    public void refresh() {
        ViolationOutlinePageBR page = getCurrentOutlinePage();
        if (page != null) {
            page.refresh();
        }
    }

    @Override
    public void addSelectionChangedListener(ISelectionChangedListener listener) {
        ViolationOutlinePageBR page = getCurrentOutlinePage();
        if (page != null) {
            page.getTableViewer().addSelectionChangedListener(listener);
        }
    }

    @Override
    public ISelection getSelection() {
        ViolationOutlinePageBR page = getCurrentOutlinePage();
        if (page != null) {
            return page.getTableViewer().getSelection();
        }
        return null;
    }

    @Override
    public void removeSelectionChangedListener(ISelectionChangedListener listener) {
        ViolationOutlinePageBR page = getCurrentOutlinePage();
        if (page != null) {
            page.getTableViewer().removeSelectionChangedListener(listener);
        }
    }

    @Override
    public void setSelection(ISelection selection) {
        ViolationOutlinePageBR page = getCurrentOutlinePage();
        if (page != null) {
            page.getTableViewer().setSelection(selection);
        }
    }
}
