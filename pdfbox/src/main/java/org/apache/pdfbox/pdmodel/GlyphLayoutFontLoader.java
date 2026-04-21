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

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides glyph positioning e.g. for accented Latin letters.
 *
 * @author Volker Kunert
 */
public class GlyphLayoutFontLoader {

    private final Map<PDType0Font, java.awt.Font> awtFontMap = new ConcurrentHashMap<>();

    /**
     * Loads the AWT font needed for layout
     *
     * @param pdDocument  document
     * @param inputStream of the font
     * @return pdType0Font PDFBox font
     * @throws RuntimeException if font can not be loaded
     */
    public PDType0Font loadFont(PDDocument pdDocument, InputStream inputStream, boolean embedSubset) {
        PDType0Font pdType0Font = null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int bytes_read = -1;
            while ((bytes_read = inputStream.read(buffer)) > 0) {
                baos.write(buffer, 0, bytes_read);
            }
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

            pdType0Font = PDType0Font.load(pdDocument, bais, embedSubset);
            bais.reset();
            loadAwtFont(pdType0Font, bais);

        } catch (Exception e) {
            throw new RuntimeException("Font creation failed.", e);
        }
        return pdType0Font;
    }

    /**
     * Loads the AWT font needed for layout
     *
     * @param pdDocument  document
     * @param inputStream of the font
     * @return pdType0Font PDFBox font
     * @throws RuntimeException if font can not be loaded
     */
    public PDType0Font loadFont(PDDocument pdDocument, InputStream inputStream) {
        return loadFont(pdDocument, inputStream, true);
    }

    /**
     * Loads the AWT font needed for layout
     *
     * @param pdType0Font        OpenPdf base font
     * @param inputStream of the font file
     * @throws RuntimeException if font can not be loaded
     */
    public void loadAwtFont(PDType0Font pdType0Font, InputStream inputStream) {
        Font awtFont = null;
        try {
            if (!awtFontMap.containsKey(pdType0Font)) {
                awtFont = Font.createFont(java.awt.Font.TRUETYPE_FONT, inputStream);
                if (awtFont == null) {
                    throw new RuntimeException("Font is null");
                }
                awtFontMap.put(pdType0Font, awtFont);
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("AWT Font creation failed for %s.", pdType0Font.getName()), e);
        } finally {
            try {
                inputStream.close();
            } catch (Exception e) {
                //ignore
            }
        }
    }

    /**
     * Determines if glyph layout is supported for this font
     *
     * @param font PDFBox font
     * @return true if glyph layout is supported for this font
     */
    public boolean supportsFont(PDFont font) {
        boolean supports = font instanceof PDType0Font &&
                awtFontMap.containsKey((PDType0Font)font);
        return supports;
    }

    /**
     * Gets the corresponding AWT-font for the given PDFBox-font
     *
     * @param font PDFBox font
     * @return AWT font if available
     */
    public Font getAwtFont(PDType0Font font) {
        return awtFontMap.get(font);
    }
}
