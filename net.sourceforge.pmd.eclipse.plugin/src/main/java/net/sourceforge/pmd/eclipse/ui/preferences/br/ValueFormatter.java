/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.preferences.br;

import java.lang.reflect.Method;
import java.util.Date;

import net.sourceforge.pmd.RulePriority;
import net.sourceforge.pmd.eclipse.plugin.UISettings;
import net.sourceforge.pmd.eclipse.ui.preferences.editors.MethodEditorFactory;
import net.sourceforge.pmd.eclipse.ui.preferences.editors.MultiTypeEditorFactory;
import net.sourceforge.pmd.eclipse.util.Util;
import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.util.ClassUtil;

/**
 *
 * @author Brian Remedios
 */
public interface ValueFormatter {

    ValueFormatter STRING_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public void format(Object value, StringBuilder target) {
            target.append(value == null ? "" : value);
        }

        @Override
        public String format(Object value) {
            return value == null ? "" : value.toString();
        }
    };

    ValueFormatter MULTI_STRING_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public void format(Object value, StringBuilder target) {
            target.append('[');
            Util.asString((Object[]) value, ", ", target);
            target.append(']');
        }
    };

    ValueFormatter NUMBER_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public void format(Object value, StringBuilder target) {
            target.append(value == null ? "?" : value);
        }
    };

    ValueFormatter BOOLEAN_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public void format(Object value, StringBuilder target) {
            target.append(value == null ? "?" : value);
        }
    };

    ValueFormatter TYPE_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public void format(Object value, StringBuilder target) {
            target.append(value == null ? "" : ClassUtil.asShortestName((Class<?>) value));
        }
    };

    ValueFormatter MULTI_TYPE_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public void format(Object value, StringBuilder target) {
            target.append('[');
            Util.asString(MultiTypeEditorFactory.shortNamesFor((Class<?>[]) value), ", ", target);
            target.append(']');
        }
    };

    ValueFormatter METHOD_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public void format(Object value, StringBuilder target) {
            if (value == null) {
                return;
            }
            target.append(Util.signatureFor((Method) value, MethodEditorFactory.UNWANTED_PREFIXES));
        }
    };

    ValueFormatter MULTI_METHOD_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public void format(Object value, StringBuilder target) {
            target.append('[');
            Object[] methods = (Object[]) value;
            if (methods == null || methods.length == 0) {
                target.append(']');
                return;
            }
            METHOD_FORMATTER.format(methods[0], target);
            for (int i = 1; i < methods.length; i++) {
                target.append(',');
                METHOD_FORMATTER.format(methods[i], target);
            }
            target.append(']');
        }
    };

    ValueFormatter OBJECT_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public void format(Object value, StringBuilder target) {
            target.append(value == null ? "" : value);
        }
    };

    ValueFormatter OBJECT_ARRAY_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public void format(Object value, StringBuilder target) {
            target.append('[');
            Util.asString((Object[]) value, ", ", target);
            target.append(']');
        }
    };

    // =================================================================

    ValueFormatter PRIORITY_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public String format(Object value) {
            return UISettings.labelFor((RulePriority) value);
        }
    };

    ValueFormatter LANGUAGE_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public String format(Object value) {
            return ((Language) value).getName();
        }
    };

    ValueFormatter LANGUAGE_VERSION_FORMATTER = new BasicValueFormatter(null) {
        @Override
        public String format(Object value) {
            return ((LanguageVersion) value).getName();
        }
    };

    ValueFormatter DATE_FROM_LONG_FORMATTER = new BasicValueFormatter("Date") {
        @Override
        public String format(Object value) {
            return new Date((Long) value).toString();
        }
    };

    ValueFormatter TIME_FROM_LONG_FORMATTER = new BasicValueFormatter("Time") {
        @Override
        public String format(Object value) {
            return new Date((Long) value).toString();
        }
    };

    ValueFormatter[] TIME_FORMATTERS = new ValueFormatter[] { DATE_FROM_LONG_FORMATTER };

    String format(Object value);

    void format(Object value, StringBuilder target);

}
