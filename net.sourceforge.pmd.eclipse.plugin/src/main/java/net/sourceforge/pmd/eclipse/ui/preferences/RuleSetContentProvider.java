/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.preferences;

import net.sourceforge.pmd.eclipse.util.Util;
import net.sourceforge.pmd.lang.rule.RuleSet;

/**
 * This class implements a content provider for the rule table of
 * the PMD Preference page
 * 
 * @author Philippe Herlin
 *
 */
public class RuleSetContentProvider extends AbstractStructuredContentProvider {

    @Override
    public Object[] getElements(Object inputElement) {
        
        if (inputElement instanceof RuleSet) {
            RuleSet ruleSet = (RuleSet) inputElement;
            return ruleSet.getRules().toArray();
        }
        
        return Util.EMPTY_ARRAY;
    }
}
