/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.preferences.br;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.widgets.Text;

import net.sourceforge.pmd.eclipse.ui.IndexedString;
import net.sourceforge.pmd.eclipse.ui.Shape;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.Configuration;
import net.sourceforge.pmd.eclipse.util.FontBuilder;
import net.sourceforge.pmd.lang.rule.Rule;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyTypeId;

/**
 * 
 * @author Brian Remedios
 */
public final class RuleUIUtil {
    
    private RuleUIUtil() {
        
    }

    // TODO - move to defaults area
    public static final Shape PRIORITY_SHAPE = Shape.diamond;
    public static final Shape REGEX_FILTER_SHAPE = Shape.square;
    public static final Shape XPATH_FILTER_SHAPE = Shape.circle;

    public static final FontBuilder BLUE_BOLD_11 = new FontBuilder("Tahoma", 11, SWT.BOLD, SWT.COLOR_BLUE);
    public static final FontBuilder RED_BOLD_11 = new FontBuilder("Tahoma", 11, SWT.BOLD, SWT.COLOR_RED);
    public static final FontBuilder CHANGED_PROPERTY_FONT = BLUE_BOLD_11;

    public static final VerifyListener RULE_NAME_VERIFIER = new VerifyListener() {
        @Override
        public void verifyText(VerifyEvent event) {

            event.doit = false; // Assume we don't allow it

            char ch = event.character; // Get the character typed
            String text = ((Text) event.widget).getText();

            // No leading digits
            if (Character.isDigit(ch) && text.length() == 0) {
                event.doit = false;
                return;
            }

            if (Character.isJavaIdentifierPart(ch)) {
                event.doit = true;
            }

            if (ch == '\b') { // Allow backspace
                event.doit = true;
            }
        }
    };

    public static final VerifyListener RULE_LABEL_VERIFIER = new VerifyListener() {
        @Override
        public void verifyText(VerifyEvent event) {
            event.doit = false; // Assume we don't allow it

            char ch = event.character; // Get the character typed
            String text = ((Text) event.widget).getText();

            // No leading blanks
            if (Character.isWhitespace(ch) && text.length() == 0) {
                event.doit = false;
                return;
            }

            event.doit = true;
        }
    };

    public static String ruleSetNameFrom(Rule rule) {
        return ruleSetNameFrom(rule.getRuleSetName());
    }

    // FIXME clean up the ruleset names in PMD proper!
    public static String ruleSetNameFrom(String rulesetName) {
        int pos = rulesetName.toUpperCase(Locale.ROOT).indexOf("RULES");
        return pos < 0 ? rulesetName : rulesetName.substring(0, pos - 1);
    }

    /**
     * Parks the formatted value onto the buffer and determines whether it is a
     * default value or not. If it is it will return its formatted length to
     * denote this or just zero if not.
     * 
     * @param target
     * @param entry
     * @param modifiedTag
     * @return
     */
    private static int formatValueOn(StringBuilder target, Map.Entry<PropertyDescriptor<?>, Object> entry,
            String modifiedTag) {

        Object value = entry.getValue();
        Class<?> datatype = null;
        PropertyTypeId typeId = entry.getKey().getTypeId();
        switch (typeId) {
        case BOOLEAN:
            datatype = Boolean.class;
            break;
        case CHARACTER:
            datatype = Character.class;
            break;
        case CHARACTER_LIST:
            datatype = Array.newInstance(Character.class, 0).getClass();
            break;
        case DOUBLE:
            datatype = Double.class;
            break;
        case DOUBLE_LIST:
            datatype = Array.newInstance(Double.class, 0).getClass();
            break;
        case INTEGER:
            datatype = Integer.class;
            break;
        case INTEGER_LIST:
            datatype = Array.newInstance(Integer.class, 0).getClass();
            break;
        case LONG:
            datatype = Long.class;
            break;
        case LONG_LIST:
            datatype = Array.newInstance(Long.class, 0).getClass();
            break;
        case REGEX:
            datatype = Pattern.class;
            break;
        case STRING:
            datatype = String.class;
            break;
        case STRING_LIST:
            datatype = Array.newInstance(String.class, 0).getClass();
            break;
        default:
            throw new IllegalStateException("Unsupported type: " + typeId);
        }

        boolean isModified = !RuleUtil.isDefaultValue(entry);
        if (isModified) {
            target.append(modifiedTag);
        }

        ValueFormatter formatter = FormatManager.formatterFor(datatype);
        if (formatter != null) {
            String output = formatter.format(value);
            target.append(output);
            return isModified ? output.length() : 0;
        }

        String out = String.valueOf(value);
        target.append(out); // should not get here..breakpoint here
        return isModified ? out.length() : 0;
    }

    /**
     * @param rule
     *            Rule
     * @return String
     */
    public static String propertyStringFrom(Rule rule, String modifiedTag) {

        Map<PropertyDescriptor<?>, Object> valuesByProp = Configuration.filteredPropertiesOf(rule);

        if (valuesByProp.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(80);

        Iterator<Map.Entry<PropertyDescriptor<?>, Object>> iter = valuesByProp.entrySet().iterator();

        Map.Entry<PropertyDescriptor<?>, Object> entry = iter.next();
        sb.append(entry.getKey().name()).append(": ");
        formatValueOn(sb, entry, modifiedTag);

        while (iter.hasNext()) {
            entry = iter.next();
            sb.append(", ").append(entry.getKey().name()).append(": ");
            formatValueOn(sb, entry, modifiedTag);
        }
        return sb.toString();
    }

    public static IndexedString indexedPropertyStringFrom(Rule rule) {
        Map<PropertyDescriptor<?>, Object> valuesByProp = Configuration.filteredPropertiesOf(rule);

        if (valuesByProp.isEmpty()) {
            return IndexedString.EMPTY;
        }
        StringBuilder sb = new StringBuilder();

        Iterator<Map.Entry<PropertyDescriptor<?>, Object>> iter = valuesByProp.entrySet().iterator();

        List<int[]> modifiedValueIndexes = new ArrayList<>(valuesByProp.size());

        Map.Entry<PropertyDescriptor<?>, Object> entry = iter.next();
        sb.append(entry.getKey().name()).append(": ");
        int start = sb.length();
        int stop = start + formatValueOn(sb, entry, "");
        if (stop > start) {
            modifiedValueIndexes.add(new int[] { start, stop });
        }

        while (iter.hasNext()) {
            entry = iter.next();
            sb.append(", ").append(entry.getKey().name()).append(": ");
            start = sb.length();
            stop = start + formatValueOn(sb, entry, "");
            if (stop > start) {
                modifiedValueIndexes.add(new int[] { start, stop });
            }
        }
        return new IndexedString(sb.toString(), modifiedValueIndexes);
    }
}
