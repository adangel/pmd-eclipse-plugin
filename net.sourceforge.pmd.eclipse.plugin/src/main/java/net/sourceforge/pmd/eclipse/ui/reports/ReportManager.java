/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.reports;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.preferences.IPreferences;
import net.sourceforge.pmd.properties.PropertyDescriptor;
import net.sourceforge.pmd.renderers.Renderer;
import net.sourceforge.pmd.renderers.RendererFactory;

/**
 *
 * @author Brian Remedios
 */
public final class ReportManager {

    private final Renderer[] allRenderers;

    public static final ReportManager INSTANCE = new ReportManager();

    public static final String DEFAULT_REPORT_PROPERTY_FILENAME = "reportProperties.xml";

    private ReportManager() {
        allRenderers = availableRenderers2();
    }

    public Renderer[] allRenderers() {
        return allRenderers;
    }

    // private Renderer[] knownRenderers() {
    //
    // Properties props = new Properties();
    //
    // return new Renderer[] {
    // new HTMLRenderer(props),
    // new SummaryHTMLRenderer(props),
    // new CSVRenderer(props),
    // new XMLRenderer(props),
    // new TextRenderer(props),
    // new VBHTMLRenderer(props)
    // };
    // }

    public Renderer[] availableRenderers2() {
        List<Renderer> renderers = new ArrayList<>();

        for (String reportName : RendererFactory.REPORT_FORMAT_TO_RENDERER.keySet()) {
            renderers.add(RendererFactory.createRenderer(reportName, new Properties()));
        }

        return renderers.toArray(new Renderer[0]);
    }

    public List<Renderer> activeRenderers() {
        List<Renderer> actives = new ArrayList<>();
        IPreferences prefs = PMDPlugin.getDefault().loadPreferences();

        for (Renderer renderer : allRenderers) {
            if (prefs.isActiveRenderer(renderer.getName())) {
                actives.add(renderer);
            }
        }

        return actives;
    }

    public static String asString(Map<PropertyDescriptor<?>, Object> propertyDefinitions) {
        if (propertyDefinitions.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<PropertyDescriptor<?>, Object> entry : propertyDefinitions.entrySet()) {
            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey().name()).append(": ").append(String.valueOf(entry.getValue()));
        }

        return sb.toString();
    }

    /**
     * Derive a map key for the renderer, descriptor pair.
     * 
     * @param renderer
     *            Renderer
     * @param desc
     *            PropertyDescriptor<?>
     * @return String
     */
    private static String keyOf(Renderer renderer, PropertyDescriptor<?> desc) {
        return renderer.getName() + "__" + desc.name();
    }

    public static void loadReportProperties() {
        loadReportProperties(DEFAULT_REPORT_PROPERTY_FILENAME);
    }

    public static void saveReportProperties() {
        saveReportProperties(DEFAULT_REPORT_PROPERTY_FILENAME);
    }

    /**
     * Load the properties for all renderers from the specified filename. Return whether we succeeded or not.
     * 
     * @param propertyFilename
     *            String
     * @return boolean
     */
    private static boolean loadReportProperties(String propertyFilename) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(new File(propertyFilename).toPath())) {
            props.loadFromXML(in);
        } catch (Exception e) {
            return false;
        }

        for (Renderer renderer : ReportManager.INSTANCE.allRenderers()) {
            for (PropertyDescriptor pDesc : renderer.getPropertyDescriptors()) {
                String key = keyOf(renderer, pDesc);
                if (props.containsKey(key)) {
                    Object value = pDesc.valueFrom((String) props.get(key));
                    renderer.setProperty(pDesc, value);
                }
            }
        }

        return true;
    }

    /**
     * Save the properties of all renderers to the specified filename.
     * 
     * @param propertyFilename
     *            String
     */
    private static void saveReportProperties(String propertyFilename) {
        Properties props = new Properties();

        for (Renderer renderer : ReportManager.INSTANCE.allRenderers()) {
            Map<PropertyDescriptor<?>, Object> valuesByProp = renderer.getPropertiesByPropertyDescriptor();
            for (Map.Entry<PropertyDescriptor<?>, Object> entry : valuesByProp.entrySet()) {
                PropertyDescriptor desc = entry.getKey();
                props.put(keyOf(renderer, desc), desc.asDelimitedString(entry.getValue()));

            }
        }

        try (OutputStream out = Files.newOutputStream(new File(propertyFilename).toPath())) {
            props.storeToXML(out, "asdf");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
