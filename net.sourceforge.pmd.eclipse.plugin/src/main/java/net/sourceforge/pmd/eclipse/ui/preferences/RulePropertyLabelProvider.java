/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a label provider for the item of the rule properties table of the
 * PMD Preference page.
 * 
 * @author Philippe Herlin
 *
 */
public class RulePropertyLabelProvider extends AbstractTableLabelProvider {
    private static final Logger LOG = LoggerFactory.getLogger(RulePropertyLabelProvider.class);

    @Override
    public String getColumnText(Object element, int columnIndex) {
        String result = "";
        if (element instanceof RuleProperty) {
            RuleProperty ruleProperty = (RuleProperty) element;
            if (columnIndex == 0) {
                result = ruleProperty.getProperty();
                LOG.debug("Retour du nom de la propriété : " + result);
            } else if (columnIndex == 1) {
                result = ruleProperty.getValue();
                LOG.debug("Retour de la valeur de la propriété : " + result);
            }
        }
        return result;
    }
}
