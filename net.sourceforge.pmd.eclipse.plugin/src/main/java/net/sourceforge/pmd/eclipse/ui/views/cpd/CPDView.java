/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.views.cpd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IPropertyListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;

import net.sourceforge.pmd.cpd.Mark;
import net.sourceforge.pmd.cpd.Match;
import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.PMDRuntimeConstants;
import net.sourceforge.pmd.eclipse.ui.nls.StringKeys;

/**
 * A class for showing the Copy / Paste Detection View.
 *
 * @author Sven
 *
 */

public class CPDView extends ViewPart implements IPropertyListener {
    private TreeViewer treeViewer;
    private TreeNodeContentProvider contentProvider;
    private CPDViewLabelProvider labelProvider;
    private CPDViewDoubleClickEventListener doubleClickListener;
    private CPDViewTooltipListener tooltipListener;
    private static final int MAX_MATCHES = 100;

    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);
        contentProvider = new TreeNodeContentProvider();
        labelProvider = new CPDViewLabelProvider();
        doubleClickListener = new CPDViewDoubleClickEventListener(this);
        tooltipListener = new CPDViewTooltipListener(this);
    }

    @Override
    public void createPartControl(Composite parent) {
        int treeStyle = SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI | SWT.FULL_SELECTION;
        treeViewer = new TreeViewer(parent, treeStyle);
        treeViewer.setUseHashlookup(true);
        Tree tree = treeViewer.getTree();
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);

        treeViewer.setContentProvider(contentProvider);
        treeViewer.setLabelProvider(labelProvider);
        treeViewer.addDoubleClickListener(doubleClickListener);

        tooltipListener.initialize();
        tree.addListener(SWT.Dispose, tooltipListener);
        tree.addListener(SWT.KeyDown, tooltipListener);
        tree.addListener(SWT.MouseMove, tooltipListener);
        tree.addListener(SWT.MouseHover, tooltipListener);
        createColumns(tree);
    }

    /**
     * Creates the columns of the tree.
     * @param tree Tree from the treeViewer
     */
    private void createColumns(Tree tree) {
        // the "+"-sign for expanding packages
        final TreeColumn plusColumn = new TreeColumn(tree, SWT.RIGHT);
        plusColumn.setWidth(20);
        //      plusColumn.setResizable(false);

        // shows the image
        TreeColumn imageColumn = new TreeColumn(tree, SWT.CENTER);
        imageColumn.setWidth(20);
        //      imageColumn.setResizable(false);

        // shows the message
        TreeColumn messageColumn = new TreeColumn(tree, SWT.LEFT);
        messageColumn.setText(getString(StringKeys.VIEW_COLUMN_MESSAGE));
        messageColumn.setWidth(300);

        // shows the class
        TreeColumn classColumn = new TreeColumn(tree, SWT.LEFT);
        classColumn.setText(getString(StringKeys.VIEW_COLUMN_CLASS));
        classColumn.setWidth(300);

    }

    /**
     * @return the tree viewer.
     */
    public TreeViewer getTreeViewer() {
        return treeViewer;
    }

    /**
     * Helper method to return an NLS string from its key.
     */
    private String getString(String key) {
        return PMDPlugin.getDefault().getStringTable().getString(key);
    }

    @Override
    public void setFocus() {
        treeViewer.getTree().setFocus();
    }

    /**
     * Sets input for the table.
     * @param matches CPD Command that contain the matches from the CPD
     */
    public void setData(Iterator<Match> matches) {
        List<TreeNode> elements = new ArrayList<>();
        if (matches != null) {
            // iterate the matches
            for (int count = 0; matches.hasNext() && count < MAX_MATCHES; count++) {
                Match match = matches.next();

                // create a treenode for the match and add to the list
                TreeNode matchNode = new TreeNode(match); // NOPMD by Sven on 02.11.06 11:27
                elements.add(matchNode);

                // create the children of the match
                TreeNode[] children = new TreeNode[match.getMarkCount()]; // NOPMD by Sven on 02.11.06 11:28
                Iterator<Mark> entryIterator = match.getMarkSet().iterator();
                for (int j = 0; entryIterator.hasNext(); j++) {
                    final Mark entry = entryIterator.next();
                    children[j] = new TreeNode(entry); // NOPMD by Sven on 02.11.06 11:28
                    children[j].setParent(matchNode);
                }
                matchNode.setChildren(children);
            }
        }

        // set the children of the rootnode: the matches
        treeViewer.setInput(elements.toArray(new TreeNode[0]));
    }

    /**
     * After the CPD command is executed, it will trigger an propertyChanged event.
     */
    @Override
    public void propertyChanged(Object source, int propId) {
        if (propId == PMDRuntimeConstants.PROPERTY_CPD && source instanceof Iterator<?>) {
            Iterator<Match> iter = (Iterator<Match>) source;
            // after setdata(iter) iter.hasNext will always return false
            boolean hasResults = iter.hasNext();
            setData(iter);
            if (!hasResults) {
                // no entries
                MessageBox box = new MessageBox(this.treeViewer.getControl().getShell());
                box.setText(getString(StringKeys.DIALOG_CPD_NORESULTS_HEADER));
                box.setMessage(getString(StringKeys.DIALOG_CPD_NORESULTS_BODY));
                box.open();
            }
        }
    }
}
