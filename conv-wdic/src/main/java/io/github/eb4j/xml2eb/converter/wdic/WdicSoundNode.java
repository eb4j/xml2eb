package io.github.eb4j.xml2eb.converter.wdic;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import org.apache.commons.io.FilenameUtils;


/**
 * Wdic sound node class.
 *
 * Created by miurahr on 16/07/17.
 */
class WdicSoundNode {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private static final String WDIC_PLUGIN_DIR = "plugin";

    private WdicNode wdicNode;
    private File plugin;

    WdicSoundNode(final WdicNode wdicNode, final File basedir) {
        this.wdicNode = wdicNode;
        plugin = new File(basedir, WDIC_PLUGIN_DIR);
    }

    /**
     * 音声データノードを作成します。
     *
     * @param subbook subbookノード
     */
    void makeSoundNode(final Element subbook) {
        Element sound = wdicNode.appendElement(subbook, "sound");
        for (String name : wdicNode.getPluginMapKeySet()) {
            if (name.endsWith(".mp3") || name.endsWith(".ogg")) {
                String wavName = name + ".wav";
                File wav = new File(plugin, wavName);
                if (!wav.exists()) {
                    logger.error("file not found: " + wav.getPath());
                }
                String path = FilenameUtils.concat(WDIC_PLUGIN_DIR, wavName);
                wdicNode.appendData(sound, name, path, "wav");
            } else if (name.endsWith(".mid")) {
                File midi = new File(plugin, name);
                if (!midi.exists()) {
                    logger.error("file not found: " + midi.getPath());
                }
                String path = FilenameUtils.concat(WDIC_PLUGIN_DIR, name);
                wdicNode.appendData(sound, name, path, "mid");
            }
        }
    }
}
