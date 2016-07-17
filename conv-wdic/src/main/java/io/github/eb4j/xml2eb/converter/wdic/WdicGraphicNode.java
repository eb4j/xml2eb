package io.github.eb4j.xml2eb.converter.wdic;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by miurahr on 16/07/17.
 */
public class WdicGraphicNode {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private static final String WDIC_GLYPH_DIR = "glyph";
    private static final String WDIC_PLUGIN_DIR = "plugin";
    private static final String WDIC_TABLE_DIR = "table";

    private WdicNode wdicNode;
    private File plugin;
    private File glyph;
    private File table;

    /**
     * グリフリスト
     */
    private List<String> glyphList = new ArrayList<>();
    /**
     * 表リスト
     */
    private List<String> tableList = new ArrayList<>();


    public WdicGraphicNode(final WdicNode wdicNode, final File basedir) {
        this.wdicNode = wdicNode;
        plugin = new File(basedir, WDIC_PLUGIN_DIR);
        glyph = new File(basedir, WDIC_GLYPH_DIR);
        table = new File(basedir, WDIC_TABLE_DIR);
    }

    void addTableItem(final String item) {
        if (!tableList.contains(item)) {
            tableList.add(item);
        }
    }
    void addGlyphItem(final String item) {
        if (!glyphList.contains(item)) {
            glyphList.add(item);
        }
    }

    /**
     * 画像データノードを作成します。
     *
     * @param subbook subbookノード
     */
    void makeGraphicNode(final Element subbook) {
        Element graphic = wdicNode.appendElement(subbook, "graphic");
        for (String name : wdicNode.getPluginMapKeySet()) {
            if (name.endsWith(".jpg")) {
                File jpg = new File(plugin, name);
                if (!jpg.exists()) {
                    logger.error("file not found: " + jpg.getPath());
                }
                String path = FilenameUtils.concat(WDIC_PLUGIN_DIR, name);
                wdicNode._appendData(graphic, name, path, "jpg");
            } else if (name.endsWith(".png")) {
                String bmpName = name + ".bmp";
                File bmp = new File(plugin, bmpName);
                if (!bmp.exists()) {
                    logger.error("file not found: " + bmp.getPath());
                }
                String path = FilenameUtils.concat(WDIC_PLUGIN_DIR, bmpName);
                wdicNode._appendData(graphic, name, path, "bmp");
            }
        }

        for (String name : glyphList) {
            String bmpName = name + ".50px.png.bmp";
            File bmp = new File(glyph, bmpName);
            if (!bmp.exists()) {
                logger.error("file not found: " + bmp.getPath());
            }
            String path = FilenameUtils.concat(WDIC_GLYPH_DIR, bmpName);
            wdicNode._appendData(graphic, "glyph-" + name, path, "bmp");
        }

        for (String name : tableList) {
            name += ".bmp";
            File bmp = new File(table, name);
            if (!bmp.exists()) {
                logger.error("file not found: " + bmp.getPath());
            }
            String path = FilenameUtils.concat(WDIC_TABLE_DIR, name);
            wdicNode._appendData(graphic, name, path, "bmp");
        }
    }
}
