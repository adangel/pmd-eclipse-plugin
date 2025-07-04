/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.views;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.IPage;
import org.eclipse.ui.part.MessagePage;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.PageBookView;

import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.ui.model.FileRecord;

/**
 * 
 * @author Brian Remedios
 */
public abstract class AbstractPMDPagebookView extends PageBookView {

    protected ViewMemento memento;

    protected AbstractPMDPagebookView() {
        // protected constructor for subclassing
    }

    public static FileRecord tryForFileRecordFrom(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            // If there is a file opened in the editor, we create a record for
            // it
            IEditorInput input = ((IEditorPart) part).getEditorInput();
            if (input instanceof IFileEditorInput) {
                IFile file = ((IFileEditorInput) input).getFile();
                return new FileRecord(file);
            }
        }
        return null;
    }

    protected abstract String pageMessageId();

    protected abstract String mementoFileId();

    protected boolean hasMemento() {
        return memento != null;
    }

    protected IWorkbenchPage getSitePage() {
        return getSite().getPage();
    }

    /**
     * Gets the fileRecord from the currently active editor.
     * 
     * @param part
     *            IWorkbenchPart
     * @return a new FileRecord
     */
    protected FileRecord getFileRecordFromWorkbenchPart(IWorkbenchPart part) {
        if (part instanceof IEditorPart) {
            // If there is a file opened in the editor, we create a record for
            // it
            IEditorInput input = ((IEditorPart) part).getEditorInput();
            if (input instanceof IFileEditorInput) {
                IFile file = ((IFileEditorInput) input).getFile();
                return new FileRecord(file);
            }
        } else {
            // We also want to get the editors when it's not active
            // so we pretend, that the editor has been activated
            IEditorPart editorPart = getSite().getPage().getActiveEditor();
            if (editorPart != null) {
                return getFileRecordFromWorkbenchPart(editorPart);
            }
        }
        return null;
    }

    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);

        memento = new ViewMemento(mementoFileId()); // load Memento from a File, if existing
    }

    protected void save(String mementoId, List<Integer> integerList) {
        memento.putList(mementoId, integerList);
    }

    protected List<Integer> getIntegerList(String mementoId) {
        return memento == null ? Collections.<Integer>emptyList() : memento.getIntegerList(mementoId);
    }

    @Override
    public void partBroughtToTop(IWorkbenchPart part) {
        partActivated(part);
    }

    @Override
    protected IWorkbenchPart getBootstrapPart() {
        IWorkbenchPage page = getSite().getPage();
        return page == null ? null : page.getActiveEditor();
    }

    @Override
    protected boolean isImportant(IWorkbenchPart part) {
        // We only care about the editor
        return part instanceof IEditorPart;
    }

    @Override
    protected IPage createDefaultPage(PageBook book) {
        // builds a message page showing a text
        MessagePage page = new MessagePage();
        initPage(page);
        page.createControl(book);
        page.setMessage(getString(pageMessageId()));
        return page;
    }

    protected static String getString(String textId) {
        return PMDPlugin.getDefault().getStringTable().getString(textId);
    }

    @Override
    public void dispose() {
        memento.save();
        super.dispose();
    }
}
