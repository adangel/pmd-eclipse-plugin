/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

/**
 * A singleton that manages colour resources.
 * 
 * @author Brian Remedios
 */
public final class ColourManager {

    private final Display display;

    private final Map<RGB, Color> coloursByRGB = new HashMap<>();

    private static ColourManager instance;

    private ColourManager(Display theDisplay) {
        display = theDisplay;
    }

    public static synchronized ColourManager managerFor(Display display) {
        if (instance == null) {
            instance = new ColourManager(display);
        }
        return instance;
    }

    public Color colourFor(RGB colourFractions) {

        Color colour = coloursByRGB.get(colourFractions);
        if (colour != null) {
            return colour;
        }

        colour = new Color(display, colourFractions.red, colourFractions.green, colourFractions.blue);
        coloursByRGB.put(colourFractions, colour);
        return colour;
    }

    /**
     * Derive a colour from the input text after slicing it into three sections and taking the log of their hashes and
     * smushing them into RGB values
     * 
     * @param text
     * @return Color
     */
    public Color colourFor(String text) {

        if (StringUtils.isBlank(text)) {
            return display.getSystemColor(SWT.COLOR_WHITE);
        }

        text = text.trim();
        int length = text.length();

        if (length < 3) {
            return display.getSystemColor(SWT.COLOR_WHITE);
        }

        int posA = length / 3;
        int posB = posA * 2;

        int rHash = text.subSequence(0, posA).hashCode();
        int gHash = text.subSequence(posA, posB).hashCode();
        int bHash = text.subSequence(posB, length).hashCode();

        RGB colourFractions = new RGB((int) (Math.log10(rHash) % 1 * 255), (int) (Math.log10(gHash) % 1 * 255),
                (int) (Math.log10(bHash) % 1 * 255));

        return colourFor(colourFractions);
    }

    public void dispose() {
        Iterator<Color> iter = coloursByRGB.values().iterator();
        while (iter.hasNext()) {
            iter.next().dispose();
        }
    }
}
