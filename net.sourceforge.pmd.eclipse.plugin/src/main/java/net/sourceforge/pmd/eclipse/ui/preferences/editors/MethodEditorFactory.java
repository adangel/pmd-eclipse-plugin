/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.preferences.editors;

import java.lang.reflect.Method;
import java.util.Objects;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import net.sourceforge.pmd.eclipse.ui.preferences.br.SizeChangeListener;
import net.sourceforge.pmd.eclipse.ui.preferences.br.ValueChangeListener;
import net.sourceforge.pmd.properties.MethodProperty;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.properties.PropertySource;
import net.sourceforge.pmd.util.ClassUtil;

/**
 * @author Brian Remedios
 * 
 * @deprecated unsupported in PMD
 */
@Deprecated
public final class MethodEditorFactory extends AbstractEditorFactory<Method> {

    public static final MethodEditorFactory INSTANCE = new MethodEditorFactory();
    public static final String[] UNWANTED_PREFIXES = new String[] {
        "java.lang.reflect.",
        "java.lang.",
        "java.util.",
    };

    public static final Method STRING_LENGTH = ClassUtil.methodFor(String.class, "length", ClassUtil.EMPTY_CLASS_ARRAY);


    private MethodEditorFactory() { }

    @Override
    public PropertyDescriptor<Method> createDescriptor(String name, String optionalDescription, Control[] otherData) {
        return new MethodProperty(name, "Method value " + name, STRING_LENGTH, new String[] {"java.lang"}, 0.0f);
    }

    @Override
    protected Method valueFrom(Control valueControl) {

        return ((MethodPicker) valueControl).getMethod();
    }

    @Override
    public Control newEditorOn(Composite parent, final PropertyDescriptor<Method> desc, final PropertySource source,
                               final ValueChangeListener listener, SizeChangeListener sizeListener) {

        final MethodPicker picker = new MethodPicker(parent, SWT.SINGLE | SWT.BORDER, UNWANTED_PREFIXES);
        picker.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        fillWidget(picker, desc, source);

        picker.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                Method newValue = picker.getMethod();
                if (newValue == null) {
                    return;
                }

                Method existingValue = valueFor(source, desc);
                if (Objects.equals(existingValue, newValue)) {
                    return;
                }

                source.setProperty(desc, newValue);
                fillWidget(picker, desc, source);     // redraw
                listener.changed(source, desc, newValue);
            }
        });

        return picker;
    }


    protected void fillWidget(MethodPicker widget, PropertyDescriptor<Method> desc, PropertySource source) {
        Method method = valueFor(source, desc);
        widget.setMethod(method);
        adjustRendering(source, desc, widget);
    }
}
