/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.preferences.editors;

import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Spinner;

import net.sourceforge.pmd.eclipse.ui.preferences.br.SizeChangeListener;
import net.sourceforge.pmd.eclipse.ui.preferences.br.ValueChangeListener;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertyFactory;
import net.sourceforge.pmd.properties.PropertySource;
import net.sourceforge.pmd.properties.constraints.NumericConstraints;

/**
 * @author Brian Remedios
 */
public final class DoubleEditorFactory extends AbstractRealNumberEditor<Double> {

    public static final DoubleEditorFactory INSTANCE = new DoubleEditorFactory();


    private DoubleEditorFactory() { }


    @Override
    public PropertyDescriptor<Double> createDescriptor(String name, String description, Control[] otherData) {
        return PropertyFactory.doubleProperty(name).desc(description)
            .defaultValue(defaultIn(otherData).doubleValue())
            .require(NumericConstraints.inRange(minimumIn(otherData).doubleValue(), maximumIn(otherData).doubleValue()))
            .build();
    }


    @Override
    protected Double valueFrom(Control valueControl) {
        return ((Spinner) valueControl).getSelection() / SCALE;
    }


    @Override
    public Control newEditorOn(Composite parent, final PropertyDescriptor<Double> desc, final PropertySource source,
                               final ValueChangeListener listener, SizeChangeListener sizeListener) {

        final Spinner spinner = newSpinnerFor(parent, source, desc);

        spinner.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent event) {
                Double newValue = spinner.getSelection() / SCALE;
                if (newValue.equals(valueFor(source, desc))) {
                    return;
                }

                source.setProperty(desc, newValue);
                listener.changed(source, desc, newValue);

                adjustRendering(source, desc, spinner);
            }
        });

        return spinner;
    }
}
