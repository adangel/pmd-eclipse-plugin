/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.eclipse.plugin.UISettings;
import net.sourceforge.pmd.eclipse.ui.PageBuilder;
import net.sourceforge.pmd.eclipse.ui.StringArranger;
import net.sourceforge.pmd.eclipse.ui.nls.StringKeys;
import net.sourceforge.pmd.eclipse.ui.preferences.br.ValueChangeListener;
import net.sourceforge.pmd.properties.PropertyDescriptor;

/**
 * 
 * @author Brian Remedios
 */
public class SummaryPanelManager extends AbstractRulePanelManager {

    private StyledText viewField;
    private PageBuilder pb = new PageBuilder(3, SWT.COLOR_BLUE, UISettings.CODE_FONT_BUILDER);
    private StringArranger arranger = new StringArranger("   ");

    public SummaryPanelManager(String theId, String theTitle, EditorUsageMode theMode,
            ValueChangeListener theListener) {
        super(theId, theTitle, theMode, theListener);
    }

    public static String asLabel(Class<?> type) {

        return type.isArray() ? type.getComponentType().getSimpleName() + "[]"
                : type.getSimpleName();
    }

    public static int longestNameIn(Map<PropertyDescriptor<?>, Object> valuesByDescriptor) {
        int longest = 0;
        for (Map.Entry<PropertyDescriptor<?>, Object> entry : valuesByDescriptor.entrySet()) {
            longest = Math.max(longest, entry.getKey().name().length());
        }
        return longest;
    }

    public static String pad(String text, int length, char padChar) {

        int delta = length - text.length();
        if (delta == 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder(length);
        sb.append(text);
        for (int i = 0; i < delta; i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    @Override
    protected void adapt() {
        Rule rule = soleRule();
        showFor(rule);
    }

    public void showFor(Rule rule) {
        pb.clear();
        if (rule == null) {
            pb.showOn(viewField);
            return;
        }

        pb.addHeading(StringKeys.PREF_SUMMARY_LABEL_NAME);
        pb.addText(rule.getName());

        pb.addHeading(StringKeys.PREF_SUMMARY_LABEL_DESCRIPTION);
        pb.addRawText(arranger.format(rule.getDescription()).toString());

        String url = rule.getExternalInfoUrl();
        if (StringUtils.isNotBlank(url)) {
            pb.addRawText(arranger.withIndent("More information can be found "));
            pb.addLink("here.\n", url);
        }

        Map<PropertyDescriptor<?>, Object> valuesByDescriptor = Configuration.filteredPropertiesOf(rule);
        if (!valuesByDescriptor.isEmpty()) {
            pb.addHeading(StringKeys.PREF_SUMMARY_LABEL_PARAMETERS);
            int longest = longestNameIn(valuesByDescriptor);
            for (Map.Entry<PropertyDescriptor<?>, Object> entry : valuesByDescriptor.entrySet()) {
                PropertyDescriptor<?> desc = entry.getKey();
                pb.addText(pad(desc.name(), longest, ' ') + '\t' + asLabel(entry.getValue().getClass()));
            }
        }

        List<String> examples = rule.getExamples();
        if (examples.isEmpty()) {
            pb.showOn(viewField);
            return;
        }

        pb.setLanguage(rule.getLanguage());

        pb.addHeading(StringKeys.PREF_SUMMARY_LABEL_EXAMPLE);
        pb.addText("");
        for (String example : rule.getExamples()) {
            pb.addCode(example.trim());
            pb.addText("");
        }

        pb.showOn(viewField);

        if (pb.hasLinks()) {
            pb.addLinkHandler(viewField);
        }

        viewField.setEditable(false);
    }

    @Override
    public void setVisible(boolean flag) {
        viewField.setVisible(flag);
    }

    @Override
    protected boolean canManageMultipleRules() {
        return false;
    }

    @Override
    protected boolean canWorkWith(Rule rule) {
        return true;
    }

    @Override
    protected void clearControls() {
        // TODO Auto-generated method stub
    }

    @Override
    public Control setupOn(Composite parent) {
        Composite panel = new Composite(parent, 0);
        panel.setLayout(new FillLayout());

        viewField = new StyledText(panel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
        viewField.setWordWrap(true);
        viewField.setTabs(20);

        return panel;
    }

    @Override
    public void showControls(boolean flag) {
        viewField.setVisible(flag);
    }
}
