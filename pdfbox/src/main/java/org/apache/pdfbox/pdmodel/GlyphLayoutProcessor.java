/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pdfbox.pdmodel;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedString;
import java.text.Bidi;

/**
 * Worker class for glyph positioning
 *
 * @author Volker Kunert
 */
public class GlyphLayoutProcessor {
    private final GlyphLayoutFontLoader glyphLayoutFontLoader;
    private PDType0Font font;
    private float fontSize;
    private PDAbstractContentStream contentStream;

    /**
     * Constructs a GlyphLayoutWorker
     *
     */
    public GlyphLayoutProcessor() {
        this.glyphLayoutFontLoader = new GlyphLayoutFontLoader();
    }

    void setContentStream(PDAbstractContentStream contentStream) {
        this.contentStream = contentStream;
    }


    /**
     *
     * @param font to be checked
     * @return true if font is supported
     */
    public boolean supportsFont(PDFont font) {
        return glyphLayoutFontLoader.supportsFont(font);
    }


    /**
     * Loads the AWT font needed for layout
     *
     * @param pdDocument document
     * @param inputStream of the font
     * @param embedSubset must be false for PDF forms
     * @throws RuntimeException if font can not be loaded
     */
    public PDType0Font loadFont(PDDocument pdDocument, InputStream inputStream, boolean embedSubset) {
        return glyphLayoutFontLoader.loadFont(pdDocument, inputStream, embedSubset);
    }


    /**
     * Loads the AWT font needed for layout
     *
     * @param pdDocument document
     * @param inputStream of the font
     * @throws RuntimeException if font can not be loaded
     */
    public PDType0Font loadFont(PDDocument pdDocument, InputStream inputStream) {
        return glyphLayoutFontLoader.loadFont(pdDocument, inputStream, true);
    }


    /**
     * Checks if the glyphVector contains adjustments
     * that make advanced layout necessary
     *
     * @param glyphVector glyph vector containing the positions
     * @return true if the glyphVector contains adjustments
     */
    private static boolean hasAdjustments(GlyphVector glyphVector) {
        boolean retVal = false;
        float lastX = 0f;
        float lastY = 0f;

        for (int i = 0; i < glyphVector.getNumGlyphs(); i++) {
            Point2D p = glyphVector.getGlyphPosition(i);
            float dx = (float) p.getX() - lastX;
            float dy = (float) p.getY() - lastY;

            float ax = (i == 0) ? 0.0f : glyphVector.getGlyphMetrics(i - 1).getAdvanceX();
            float ay = (i == 0) ? 0.0f : glyphVector.getGlyphMetrics(i - 1).getAdvanceY();

            if (dx != ax || dy != ay) {
                retVal = true;
                break;
            }
            lastX = (float) p.getX();
            lastY = (float) p.getY();
        }
        return retVal;
    }

    /**
     * Computes glyph positioning
     *
     * @param text input text
     * @return glyph vector containing reordered text, width and positioning info
     */
    private GlyphVector computeGlyphVector(String text) {
        char[] chars = text.toCharArray();

        FontRenderContext fontRenderContext = new FontRenderContext(new AffineTransform(), false, true);
        // use fractional metrics
        AttributedString as = new AttributedString(text);
        Bidi bidi = new Bidi(as.getIterator());
        int localFlags = bidi.isLeftToRight() ? java.awt.Font.LAYOUT_LEFT_TO_RIGHT : java.awt.Font.LAYOUT_RIGHT_TO_LEFT;

        java.awt.Font awtFont = glyphLayoutFontLoader.getAwtFont(font).deriveFont(fontSize);
        GlyphVector glyphVector = awtFont.layoutGlyphVector(fontRenderContext, chars, 0, chars.length, localFlags);

        return glyphVector;
    }


    /**
     * Sets the font and fontSize
     *
     * @param font to be set
     * @param fontSize font size
     */
    void setFontAndSize(PDType0Font font, float fontSize) {
        this.font = font;
        this.fontSize = fontSize;
    }


    /**
     * Shows a text using glyph positioning (if needed)
     *
     * @param text text to show
     * @throws IOException
     */
    void showText(String text) throws IOException {
        GlyphVector glyphVector = computeGlyphVector(text);
        if (!hasAdjustments(glyphVector)) {
            contentStream.showTextPDType0Font(glyphVector, 0, glyphVector.getNumGlyphs());
            return;
        }

        final float delta = 1e-5f;
        final float factorX = 1000f / fontSize;
        float lastX = 0f;

        GlyphsAndPositions ga = new GlyphsAndPositions();

        for (int i = 0; i < glyphVector.getNumGlyphs(); i++) {
            Point2D p = glyphVector.getGlyphPosition(i);
            float ax = (i == 0) ? 0.0f : glyphVector.getGlyphMetrics(i - 1).getAdvanceX();
            float dx = (float) p.getX() - lastX - ax;
            float py = (float) p.getY();

            if (Math.abs(py) >= delta) {
                if (!ga.isEmpty()) {
                    contentStream.showGlyphsWithPositioning(ga);
                    ga.clear();
                }
                contentStream.setTextRise(-py);
            }
            if (Math.abs(dx) >= delta) {
                ga.add(-dx * factorX);
            }
            ga.add(glyphVector.getGlyphCode(i));
            if (Math.abs(py) >= delta) {
                contentStream.showGlyphsWithPositioning(ga);
                ga.clear();
                contentStream.setTextRise(0.0f);
            }
            lastX = (float) p.getX();
        }
        // adjust the end position
        Point2D p = glyphVector.getGlyphPosition(glyphVector.getNumGlyphs());
        float ax = (glyphVector.getNumGlyphs() == 0) ? 0.0f
                : glyphVector.getGlyphMetrics(glyphVector.getNumGlyphs() - 1).getAdvanceX();
        float dx = (float) p.getX() - lastX - ax;
        if (Math.abs(dx) >= delta) {
            ga.add(-dx * factorX);
        }
        contentStream.showGlyphsWithPositioning(ga);
        ga.clear();
    }
}
