/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.preferences.br;

import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertySource;

/**
 * Accepts notifications for changes to a single rule or a number of them.
 * 
 * @author BrianRemedios
 */
public interface ValueChangeListener {

    /**
     * Method changed.
     * 
     * @param rule
     *            RuleSelection
     * @param desc
     *            PropertyDescriptor<?>
     * @param newValue
     *            Object
     */
    void changed(RuleSelection rule, PropertyDescriptor<?> desc, Object newValue);

    /**
     * Method changed.
     * 
     * @param rule
     *            Rule
     * @param desc
     *            PropertyDescriptor<?>
     * @param newValue
     *            Object
     */
    void changed(PropertySource source, PropertyDescriptor<?> desc, Object newValue);
}
