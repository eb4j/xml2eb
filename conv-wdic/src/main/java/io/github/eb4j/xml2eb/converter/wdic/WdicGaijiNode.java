package io.github.eb4j.xml2eb.converter.wdic;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.eb4j.xml2eb.util.FontUtil;
import io.github.eb4j.xml2eb.util.HexUtil;
import io.github.eb4j.xml2eb.util.WordUtil;


/**
 * Gaiji handler class.
 * Created by miurahr on 16/07/17.
 */
class WdicGaijiNode {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private static final String WDIC_GAIJI_DIR = "gaiji";

    private File gaijidir;
    private Map<String, String> gaijiMap;

    WdicGaijiNode(File gaijidir, Map<String, String> gaijiMap) {
        this.gaijidir = gaijidir;
        this.gaijiMap = gaijiMap;
    }

   /**
     * 外字データノードを作成します。
     *
     * @param subbook subbookノード
     */
    void makeFontNode(final Element subbook) {
        Element font = appendElement(subbook, "font");
        for (Map.Entry<String, String> entry : gaijiMap.entrySet()) {
            String name = entry.getKey();
            String type = entry.getValue();
            File file = new File(gaijidir, name + ".xbm");
            if (!file.exists()) {
                logger.error("file not found: " + file.getPath());
            }
            String path = FilenameUtils.concat(WDIC_GAIJI_DIR, file.getName());
            Element charElem = appendElement(font, "char");
            charElem.setAttribute("name", name);
            charElem.setAttribute("type", type);
            Element dataElem = appendElement(charElem, "data");
            dataElem.setAttribute("size", "16");
            dataElem.setAttribute("src", path);
        }
    }

    private enum GaijiStyle { underLine, overLine, lineThrough }

    void addOverLineGaijiFont(final Node node, final String pstr) {
        addGaijiFont2(node, pstr, GaijiStyle.overLine);
    }

    void addUnderLineGaijiFont(final Node node, final String pstr) {
        addGaijiFont2(node, pstr, GaijiStyle.underLine);
    }

    void addLineThroughGaijiFont(final Node node, final String pstr) {
        addGaijiFont2(node, pstr, GaijiStyle.lineThrough);
    }

    /**
     * 使用されている文字が有効かどうかを確認します。
     *
     * @param node ノード
     */
    void checkCharacter(final Node node) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            checkTextNodeGaiji(node);
        }
        if (node.hasChildNodes()) {
            NodeList nlist = node.getChildNodes();
            int len = nlist.getLength();
            for (int i = 0; i < len; i++) {
                Node child = nlist.item(i);
                checkCharacter(child);
                int n = nlist.getLength();
                if (len < n) {
                    i += n - len;
                    len = n;
                }
            }
        }
    }

    private void addGaijiFont2(final Node node, final String pstr, final GaijiStyle style) {
        int n = pstr.length();
        for (int j = 0; j < n; j++) {
            int codePoint = pstr.codePointAt(j);
            String hex = HexUtil.toHexString(codePoint, 6);
            String fontname = "U" + hex + "-LT";
            if (!gaijidir.exists() && !gaijidir.mkdirs()) {
                logger.error("failed to create directories: " + gaijidir.getPath());
            }
            File file = new File(gaijidir, fontname + ".xbm");
            if (!file.exists()) {
                BufferedImage img;
                switch (style) {
                    case overLine:
                        img = WdicUtil.toOverLineImage(codePoint);
                        break;
                    case underLine:
                        img = WdicUtil.toUnderLineImage(codePoint);
                        break;
                    case lineThrough:
                        img = WdicUtil.toLineThroughImage(codePoint);
                        break;
                    default:
                        // don't come here
                        // FIXME dummy
                        img = WdicUtil.toLineThroughImage(codePoint);
                        break;
                }
                try {
                    FontUtil.writeXbm(img, file);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    if (file.exists() && !file.delete()) {
                        logger.error("failed to delete file: " + file.getPath());
                    }
                } finally {
                    if (img != null) {
                        img.flush();
                    }
                }
            }
            String type = gaijiMap.get(fontname);
            if (type == null) {
                type = FontUtil.getFontType(codePoint);
                gaijiMap.put(fontname, type);
            }
            _appendCharReference(node, fontname, type);
        }
    }

    private void checkTextNodeGaiji(final Node node) {
            Text text = (Text)node;
            String str = text.getNodeValue();
            int len = str.length();
            int idx = 0;
            while (idx < len) {
                int codePoint = str.codePointAt(idx);
                int cnt = Character.charCount(codePoint);
                if (WordUtil.isValidChar(codePoint)) {
                    idx += cnt;
                    continue;
                }
                int end = idx + cnt;
                Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.of(codePoint);
                if (Character.UnicodeBlock.HEBREW.equals(unicodeBlock)
                    || Character.UnicodeBlock.ARABIC.equals(unicodeBlock)
                    || Character.UnicodeBlock.DEVANAGARI.equals(unicodeBlock)) {
                    while (end < len) {
                        int cp = str.codePointAt(end);
                        if (!unicodeBlock.equals(Character.UnicodeBlock.of(cp))) {
                            if (cp == ' ' && (end + 1) < len) {
                                cp = str.codePointAt(end + 1);
                                if (unicodeBlock.equals(Character.UnicodeBlock.of(cp))) {
                                    end += 1 + Character.charCount(cp);
                                    continue;
                                }
                            }
                            break;
                        }
                        end += Character.charCount(cp);
                    }
                }
                if (end > idx + cnt) {
                    String s = str.substring(idx, end);
                    Node parent = text.getParentNode();
                    text = text.splitText(idx);
                    text.deleteData(0, end - idx);
                    Element nobr = text.getOwnerDocument().createElement("nobr");
                    parent.insertBefore(nobr, text);

                    int n = s.length();
                    StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < n; i++) {
                        if (i > 0) {
                            buf.append("_");
                        }
                        int cp = s.codePointAt(i);
                        buf.append("U").append(HexUtil.toHexString(cp, 6));
                        i = i + Character.charCount(cp) - 1;
                    }
                    String name = buf.toString() + "-N";
                    String[] files =
                        gaijidir.list(FileFilterUtils.andFileFilter(
                                     FileFilterUtils.prefixFileFilter(name),
                                     FileFilterUtils.suffixFileFilter(".xbm")));
                    if (ArrayUtils.isEmpty(files)) {
                        if (unicodeBlock == null) {
                            logger.info("unsupported characters:"
                                         + " '" + s + "'"
                                         + " UNKNOWN_UNICODE_BLOCK");
                        } else {
                            logger.info("unsupported characters:"
                                         + " '" + s + "'"
                                         + " " + unicodeBlock.toString());
                        }
                        BufferedImage img = WdicUtil.toImage(s);
                        int height = img.getHeight();
                        int width = 8;
                        byte[][] b = FontUtil.split(img, width);
                        img.flush();
                        n = b.length;
                        for (int i = 0; i < n; i++) {
                            File file = new File(gaijidir, name + i + ".xbm");
                            try {
                                FontUtil.writeXbm(b[i], width, height, file);
                            } catch (IOException e) {
                                logger.error(e.getMessage(), e);
                                if (file.exists() && !file.delete()) {
                                    logger.error("failed to delete file: " + file.getPath());
                                }
                            }
                        }
                    } else {
                        n = files.length;
                    }
                    for (int i = 0; i < n; i++) {
                        String name0 = name + i;
                        if (!gaijiMap.containsKey(name0)) {
                            gaijiMap.put(name0, "narrow");
                        }
                        _appendCharReference(nobr, name0, "narrow");
                    }
                } else {
                    String hex = HexUtil.toHexString(codePoint, 6);
                    String name = "U" + hex;
                    File file = new File(gaijidir, name + ".xbm");
                    if (!file.exists()) {
                        String s = String.valueOf(Character.toChars(codePoint));
                        if (unicodeBlock == null) {
                            logger.info("unsupported characters:"
                                         + " '" + s + "'"
                                         + " UNKNOWN_UNICODE_BLOCK");
                        } else {
                            logger.info("unsupported character:"
                                         + " [U+" + hex + "]"
                                         + " '" + s + "'"
                                         + " " + unicodeBlock.toString());
                        }
                        BufferedImage img = WdicUtil.toImage(codePoint);
                        try {
                            FontUtil.writeXbm(img, file);
                        } catch (IOException e) {
                            logger.error(e.getMessage(), e);
                            if (file.exists() && !file.delete()) {
                                logger.error("failed to delete file: " + file.getPath());
                            }
                        } finally {
                            if (img != null) {
                                img.flush();
                            }
                        }
                    }
                    String type = gaijiMap.get(name);
                    if (type == null) {
                        type = FontUtil.getFontType(codePoint);
                        gaijiMap.put(name, type);
                    }
                    Node parent = text.getParentNode();
                    text = text.splitText(idx);
                    text.deleteData(0, cnt);
                    Element elem = text.getOwnerDocument().createElement("char");
                    elem.setAttribute("name", name);
                    elem.setAttribute("type", type);
                    parent.insertBefore(elem, text);
                }
                str = text.getNodeValue();
                len = str.length();
                idx = 0;
            }
    }

    /**
     * 要素を追加します。
     *
     * @param node 要素を追加するノード
     * @param tag  要素のタグ名称
     * @return 追加された要素
     */
    private Element appendElement(final Node node, final String tag) {
        Element elem = node.getOwnerDocument().createElement(tag);
        return (Element) node.appendChild(elem);
    }

    /**
     * 外字参照要素を追加します。
     *
     * @param node 外字参照要素を追加するノード
     * @param name name属性値
     * @param type type属性値
     * @return 追加された参照要素
     */
    private Element _appendCharReference(final Node node, final String name, final String type) {
        Element elem = appendElement(node, "char");
        elem.setAttribute("name", name);
        elem.setAttribute("type", type);
        return elem;
    }
}
