package io.github.eb4j.xml2eb.converter.wdic;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.CharUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.eb4j.xml2eb.CatalogInfo;
import io.github.eb4j.xml2eb.util.BmpUtil;
import io.github.eb4j.xml2eb.util.FontUtil;
import io.github.eb4j.xml2eb.util.HexUtil;
import io.github.eb4j.xml2eb.util.WordUtil;
import io.github.eb4j.xml2eb.util.XmlUtil;

/**
 * WDIC→XML変換クラス。
 *
 * @author Hisaya FUKUMOTO
 */
public class Wdic2Xml {

    /** プロブラム名 */
    private static final String PROGRAM = Wdic2Xml.class.getName();

    private static final String ENCODING = "UTF-8";
    private static final String WDIC_GLYPH_DIR = "glyph";
    private static final String WDIC_PLUGIN_DIR = "plugin";
    private static final String WDIC_GAIJI_DIR = "gaiji";
    private static final String WDIC_TABLE_DIR = "table";
    private static final String BOOK_XML = "book.xml";

    private static final String BOOK_TITLE = "通信用語の基礎知識";
    private static final String BOOK_DIR = "wdic";
    private static final String BOOK_TYPE =
        "0x" + HexUtil.toHexString(CatalogInfo.TYPE_GENERAL, 2);

    /** ログ */
    private Logger logger = null;
    /** ベースディレクトリ */
    private File basedir = null;
    /** WDICグループリスト */
    private WdicGroupList groupList = null;
    /** WDIC分類リスト */
    private WdicDirList dirList = null;
    /** WDICマニュアル */
    private WdicMan manual = null;
    /** プラグインマップ */
    private Map<String, Set<WdicItem>> pluginMap = null;
    /** 外字マップ */
    private Map<String, String> gaijiMap = null;
    /** グリフリスト */
    private List<String> glyphList = null;
    /** 表リスト */
    private List<String> tableList = null;


    /**
     * メインメソッド。
     *
     * @param args command line arguments.
     * @throws IOException when fails to convert or write file.
     * @throws ParserConfigurationException when fail to parse input file.
     */
    public static void main(final String[] args) {
        if (args.length == 0) {
            System.out.println("java " + PROGRAM + " [wdic-directory]");
        } else {
            try {
                new Wdic2Xml(args[0]).convert();
            } catch (ParserConfigurationException e) {
                System.exit(1);
            } catch (IOException e) {
                System.exit(1);
            }
        }
    }


    /**
     * コンストラクタ。
     *
     * @param path ベースパス
     */
    public Wdic2Xml(final String path) {
        this(new File(path));
    }

    /**
     * コンストラクタ。
     *
     * @param dir ベースディレクトリ
     */
    public Wdic2Xml(final File dir) {
        super();
        logger = LoggerFactory.getLogger(getClass());
        basedir = dir;
    }


    /**
     * 変換します。
     *
     * @exception ParserConfigurationException DocumentBuilderを生成できない場合
     * @exception IOException 入出力エラーが発生した場合
     */
    public void convert() throws ParserConfigurationException, IOException {
        File file = new File(basedir, "FILE.GL");
        groupList = new WdicGroupList(file);
        file = new File(basedir, "DIR.LST");
        dirList = new WdicDirList(file);
        file = new File(basedir, "WDICALL.MAN");
        manual = new WdicMan(file);
        pluginMap = groupList.getPluginMap();
        gaijiMap = new TreeMap<>();
        glyphList = new ArrayList<>();
        tableList = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        Element book = doc.createElement("book");
        doc.appendChild(book);
        Element subbook = _appendElement(book, "subbook");
        subbook.setAttribute("title", BOOK_TITLE);
        subbook.setAttribute("dir", BOOK_DIR);
        subbook.setAttribute("type", BOOK_TYPE);

        logger.info("create content node...");
        _makeContentNode(subbook);
        logger.info("create graphic node...");
        _makeGraphicNode(subbook);
        logger.info("create sound node...");
        _makeSoundNode(subbook);
        logger.info("create font node...");
        _makeFontNode(subbook);

        file = new File(basedir, BOOK_XML);
        logger.info("write file: " + file.getPath());
        XmlUtil.write(doc, file);
    }

    /**
     * 画像データノードを作成します。
     *
     * @param subbook subbookノード
     */
    private void _makeGraphicNode(final Element subbook) {
        Element graphic = _appendElement(subbook, "graphic");
        File plugin = new File(basedir, WDIC_PLUGIN_DIR);
        Iterator<String> it = pluginMap.keySet().iterator();
        while (it.hasNext()) {
            String name = it.next();
            if (name.endsWith(".jpg")) {
                File jpg = new File(plugin, name);
                if (!jpg.exists()) {
                    logger.error("file not found: " + jpg.getPath());
                }
                String path = FilenameUtils.concat(WDIC_PLUGIN_DIR, name);
                _appendData(graphic, name, path, "jpg");
            } else if (name.endsWith(".png")) {
                String bmpName = name + ".bmp";
                File bmp = new File(plugin, bmpName);
                if (!bmp.exists()) {
                    logger.error("file not found: " + bmp.getPath());
                }
                String path = FilenameUtils.concat(WDIC_PLUGIN_DIR, bmpName);
                _appendData(graphic, name, path, "bmp");
            }
        }

        File glyph = new File(basedir, WDIC_GLYPH_DIR);
        int len = glyphList.size();
        for (int i = 0; i < len; i++) {
            String name = glyphList.get(i);
            String bmpName = name + ".50px.png.bmp";
            File bmp = new File(glyph, bmpName);
            if (!bmp.exists()) {
                logger.error("file not found: " + bmp.getPath());
            }
            String path = FilenameUtils.concat(WDIC_GLYPH_DIR, bmpName);
            _appendData(graphic, "glyph-" + name, path, "bmp");
        }

        File table = new File(basedir, WDIC_TABLE_DIR);
        len = tableList.size();
        for (int i=0; i<len; i++) {
            String name = tableList.get(i) + ".bmp";
            File bmp = new File(table, name);
            if (!bmp.exists()) {
                logger.error("file not found: " + bmp.getPath());
            }
            String path = FilenameUtils.concat(WDIC_TABLE_DIR, name);
            _appendData(graphic, name, path, "bmp");
        }
    }

    /**
     * 音声データノードを作成します。
     *
     * @param subbook subbookノード
     */
    private void _makeSoundNode(final Element subbook) {
        Element sound = _appendElement(subbook, "sound");
        File plugin = new File(basedir, WDIC_PLUGIN_DIR);
        Iterator<String> it = pluginMap.keySet().iterator();
        while (it.hasNext()) {
            String name = it.next();
            if (name.endsWith(".mp3") || name.endsWith(".ogg")) {
                String wavName = name + ".wav";
                File wav = new File(plugin, wavName);
                if (!wav.exists()) {
                    logger.error("file not found: " + wav.getPath());
                }
                String path = FilenameUtils.concat(WDIC_PLUGIN_DIR, wavName);
                _appendData(sound, name, path, "wav");
            } else if (name.endsWith(".mid")) {
                File midi = new File(plugin, name);
                if (!midi.exists()) {
                    logger.error("file not found: " + midi.getPath());
                }
                String path = FilenameUtils.concat(WDIC_PLUGIN_DIR, name);
                _appendData(sound, name, path, "mid");
            }
        }
    }

    /**
     * 外字データノードを作成します。
     *
     * @param subbook subbookノード
     */
    private void _makeFontNode(final Element subbook) {
        Element font = _appendElement(subbook, "font");
        File gaiji = new File(basedir, WDIC_GAIJI_DIR);

        Iterator<Map.Entry<String, String>> it = gaijiMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            String name = entry.getKey();
            String type = entry.getValue();
            File file = new File(gaiji, name + ".xbm");
            if (!file.exists()) {
                logger.error("file not found: " + file.getPath());
            }
            String path = FilenameUtils.concat(WDIC_GAIJI_DIR, file.getName());
            Element charElem = _appendElement(font, "char");
            charElem.setAttribute("name", name);
            charElem.setAttribute("type", type);
            Element dataElem = _appendElement(charElem, "data");
            dataElem.setAttribute("size", "16");
            dataElem.setAttribute("src", path);
        }
    }

    /**
     * 辞書データノードを作成します。
     *
     * @param subbook subbookノード
     */
    private void _makeContentNode(final Element subbook) {
        Element content = _appendElement(subbook, "content");

        logger.info("create item node...");
        Collection<WdicGroup> groups = groupList.getGroups();
        Iterator<WdicGroup> it = groups.iterator();
        while (it.hasNext()) {
            WdicGroup group = it.next();
            List<Wdic> dics = group.getWdics();
            int len1 = dics.size();
            for (int i = 0; i < len1; i++) {
                Wdic dic = dics.get(i);
                List<WdicItem> items = dic.getWdicItems();
                int len2 = items.size();
                for (int j = 0; j < len2; j++) {
                    WdicItem item = items.get(j);
                    _makeItemNode(content, item);
                }
            }
        }
        _makeItemNode(content);

        logger.info("create menu node...");
        _makeMenuNode(content);

        logger.info("create copyright node...");
        _makeCopyrightNode(content);
    }

    /**
     * 辞書項目ノードを作成します。
     *
     * @param content コンテントノード
     * @param item 辞書項目
     */
    private void _makeItemNode(final Element content, final WdicItem item) {
        String grpId = item.getWdic().getGroupId();
        String grpName = item.getWdic().getGroupName();
        String partName = item.getWdic().getPartName();
        String partId = item.getWdic().getPartId();

        String head = item.getHead();
        logger.debug("  [" + grpId + ":" + partId + "] " + head);
        Element itemElem = _appendItem(content, "WDIC:" + grpId + ":" + head);
        Element headElem = _appendElement(itemElem, "head");
        _appendRawText(headElem, head + " 【" + grpName + "：" + partName + "】");
        boolean wordAvail = false;
        if (WordUtil.isValidWord(head)) {
            Element wordElem = _appendElement(itemElem, "word");
            _appendRawText(wordElem, head);
            wordAvail = true;
        }

        // 読みを検索語として登録
        List<String> yomiList = item.getYomi();
        int n = yomiList.size();
        for (int i = 0; i < n; i++) {
            String yomi = yomiList.get(i);
            String word = WdicUtil.unescape(yomi);
            if (!"???".equals(yomi) && !head.equals(word) && WordUtil.isValidWord(word)) {
                Element wordElem = _appendElement(itemElem, "word");
                _appendRawText(wordElem, word);
                wordAvail = true;
            }
        }
        // 英字表記を検索語として登録
        Map<String, String> spellMap = item.getSpell();
        Iterator<Map.Entry<String, String>> spellIt = spellMap.entrySet().iterator();
        while (spellIt.hasNext()) {
            Map.Entry<String, String> entry = spellIt.next();
            String str = WdicUtil.unescape(entry.getValue());
            if (StringUtils.isAsciiPrintable(str)) {
                int idx = str.indexOf(": ");
                if (idx > 0) {
                    // 略語
                    String ss = str.substring(0, idx).trim();
                    if (!head.equals(ss) && WordUtil.isValidWord(ss)) {
                        Element wordElem = _appendElement(itemElem, "word");
                        _appendRawText(wordElem, ss);
                        wordAvail = true;
                    }
                    // 元の語形
                    str = str.substring(idx + 2).trim();
                }
                if (!head.equals(str) && WordUtil.isValidWord(str)) {
                    Element wordElem = _appendElement(itemElem, "word");
                    _appendRawText(wordElem, str);
                    wordAvail = true;
                }
            }
        }

        if (!wordAvail) {
            logger.warn("word not defined: " + grpId + ":" + partId + ":" + head);
        }

        // 本文の登録
        Element bodyElem = _appendElement(itemElem, "body");
        Element keyElem = _appendElement(bodyElem, "key");
        _appendRawText(keyElem, head);
        _appendRawText(bodyElem, " 【");
        Element refElem = _appendIdReference(bodyElem, "MENU:group:" + grpId);
        _appendRawText(refElem, grpName);
        _appendRawText(bodyElem, "：");
        refElem = _appendIdReference(bodyElem, "MENU:group:" + grpId + ":" + partId);
        _appendRawText(refElem, partName + "編");
        _appendRawText(bodyElem, "】");
        _appendNewLine(bodyElem);

        // 分類
        List<String> dirs = item.getDir();
        n = dirs.size();
        for (int i = 0; i < n; i++) {
            refElem = _appendIdReference(bodyElem, "DIR:/");
            _appendRawText(refElem, "分類");
            _appendRawText(bodyElem, "：");
            String str = dirs.get(i);
            if (str.startsWith("/")) {
                str = str.substring(1);
            }
            String[] ss = str.split("/");
            String key = "";
            int m = ss.length;
            for (int j = 0; j < m; j++) {
                if (j != 0) {
                    _appendRawText(bodyElem, " > ");
                }
                key += "/" + ss[j];
                refElem = _appendIdReference(bodyElem, "DIR:" + key);
                _appendRawText(refElem, dirList.getName(key));
            }
            _appendNewLine(bodyElem);
        }

        // 読み
        if (!yomiList.isEmpty()) {
            n = yomiList.size();
            for (int i = 0; i < n; i++) {
                String str = "読み：" + yomiList.get(i);
                _appendText(item, bodyElem, str);
                _appendNewLine(bodyElem);
            }
        }

        // 外語
        if (!spellMap.isEmpty()) {
            spellIt = spellMap.entrySet().iterator();
            while (spellIt.hasNext()) {
                Map.Entry<String, String> entry = spellIt.next();
                String str = "外語：[" + entry.getKey() + "] " + entry.getValue();
                _appendText(item, bodyElem, str);
                _appendNewLine(bodyElem);
            }
        }

        // 発音
        Map<String, String> pronMap = item.getPronounce();
        if (!pronMap.isEmpty()) {
            Iterator<Map.Entry<String, String>> pronIt = pronMap.entrySet().iterator();
            while (pronIt.hasNext()) {
                Map.Entry<String, String> entry = pronIt.next();
                String str = "発音：[" + entry.getKey() + "] " + entry.getValue();
                _appendRawText(bodyElem, str);
                _appendNewLine(bodyElem);
            }
        }

        // 品詞
        List<String> speechList = item.getSpeech();
        if (!speechList.isEmpty()) {
            StringBuilder buf = new StringBuilder("品詞：");
            n = speechList.size();
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    buf.append(",");
                }
                buf.append(speechList.get(i));
            }
            _appendRawText(bodyElem, buf.toString());
            _appendNewLine(bodyElem);
        }

        // 内容
        _appendNewLine(bodyElem);
        Stack<Element> indentStack = new Stack<Element>();
        int curIndent = 0;
        int ignoreTabs = 0;
        int section = 0;
        int tableNum = 0;
        Element indentElem = _appendElement(bodyElem, "indent");
        Map<Integer, Integer> numMap = new HashMap<>();
        boolean linkBlock = false;
        List<String> bodyList = item.getBody();
        n = bodyList.size();
        for (int i = 0; i < n; i++) {
            String body = bodyList.get(i);
            String block = WdicUtil.deleteTab(body);

            if ("//LINK".equals(block)) {
                while (!indentStack.isEmpty()) {
                    indentElem = indentStack.pop();
                }
                _appendNewLine(indentElem);
                linkBlock = true;
                curIndent = 0;
                // リンク部では常に1段インデントを無視する ("//LINK"部のタブ分)
                ignoreTabs = 1;
                continue;
            }

            int indent = WdicUtil.getTabCount(body);
            if (block.startsWith("= ")) {
                // 無視するタブ数を変更
                section = indent;
                ignoreTabs = indent + 1;
                indent = 0;
                curIndent = 0;
                while (!indentStack.isEmpty()) {
                    indentElem = indentStack.pop();
                }
            } else {
                indent -= ignoreTabs;
                indent = Math.max(indent, 0);
                if (block.startsWith("+ ")) {
                    // キー"-1"にこのブロックのインデント数を設定する
                    numMap.put(-1, indent);
                    if (numMap.size() > 1) {
                        // 次段以降はインデントを下げない
                        indent = curIndent;
                    }
                } else {
                    // 数字あり箇条書きでない場合はマップをクリアする
                    numMap.clear();
                }
            }
            if (curIndent < indent) {
                while (curIndent != indent) {
                    indentStack.push(indentElem);
                    indentElem = _appendElement(indentElem, "indent");
                    curIndent++;
                }
            } else if (curIndent > indent) {
                while (curIndent != indent) {
                    indentElem = indentStack.pop();
                    curIndent--;
                }
            }

            if (linkBlock) {
                // リンク部
                _appendItemLinkBlock(item, indentElem, block);
                _appendNewLine(indentElem);
            } else {
                // 本文部
                if (block.startsWith("))")) {
                    // 整形済み
                    _appendNewLine(indentElem);
                    for (; i < n; i++) {
                        body = bodyList.get(i);
                        block = WdicUtil.deleteTab(body);
                        if (!block.startsWith("))")) {
                            i--;
                            break;
                        }
                        if (block.startsWith(")) ")) {
                            Element nobr = _appendElement(indentElem, "nobr");
                            _appendText(item, nobr, block.substring(3));
                        }
                        _appendNewLine(indentElem);
                    }
                    _appendNewLine(indentElem);
                } else if (block.startsWith(">>")) {
                    // 引用
                    Element indentElem2 = _appendElement(indentElem, "indent");
                    for (; i < n; i++) {
                        body = bodyList.get(i);
                        block = WdicUtil.deleteTab(body);
                        if (!block.startsWith(">>")) {
                            i--;
                            break;
                        }
                        if (block.startsWith(">> ")) {
                            _appendText(item, indentElem2, block.substring(3));
                        }
                        _appendNewLine(indentElem2);
                    }
                } else if (block.startsWith(":: ")) {
                    // 定義語 (簡易形式)
                    for (; i < n; i++) {
                        body = bodyList.get(i);
                        block = WdicUtil.deleteTab(body);
                        if (!block.startsWith(":: ")) {
                            i--;
                            break;
                        }
                        int idx = WdicUtil.indexOf(block, "|", 3);
                        if (idx >= 0) { // return minus when not found
                            String dt = block.substring(3, idx).trim();
                            String dd = block.substring(idx + 1).trim();
                            _appendText(item, indentElem, "\u30fb " + dt);
                            _appendNewLine(indentElem);
                            Element indentElem2 = _appendElement(indentElem, "indent");
                            _appendText(item, indentElem2, dd);
                            _appendNewLine(indentElem2);
                        }
                    }
                } else if (block.startsWith(": ")) {
                    // 定義語 (完全形式)
                    int tab = curIndent;
                    boolean term = false;
                    Element indentElem2 = null;
                    for (i = i + 1; i < n; i++) {
                        body = bodyList.get(i);
                        int t = WdicUtil.getTabCount(body) - ignoreTabs;
                        if (t <= tab) {
                            i--;
                            break;
                        }
                        block = WdicUtil.deleteTab(body);
                        if (block.startsWith("+ ")) {
                            // キー"-1"にこのブロックのインデント数を設定する
                            numMap.put(-1, t);
                        } else {
                            // 数字あり箇条書きでない場合はマップをクリアする
                            numMap.clear();
                        }
                        if (block.startsWith(":>")) {
                            if (indentElem2 != null) {
                                indentElem2 = null;
                            }
                            term = true;
                            String dt = block.substring(2).trim();
                            if (StringUtils.isNotBlank(dt)) {
                                _appendText(item, indentElem, "\u30fb " + dt);
                                _appendNewLine(indentElem);
                            }
                        } else if (block.startsWith(":<")) {
                            if (indentElem2 == null) {
                                indentElem2 = _appendElement(indentElem, "indent");
                            }
                            term = false;
                            String dd = block.substring(2).trim();
                            if (StringUtils.isNotBlank(dd)) {
                                _appendText(item, indentElem2, dd);
                                _appendNewLine(indentElem2);
                            }
                        } else {
                            if (term) {
                                _appendItemBodyBlock(item, indentElem, block, numMap, "\u30fb ");
                                _appendNewLine(indentElem);
                            } else {
                                if (indentElem2 != null) {
                                    _appendItemBodyBlock(item, indentElem2, block, numMap, null);
                                    _appendNewLine(indentElem2);
                                }
                            }
                        }
                    }
                } else if (block.startsWith("| ")) {
                    // 表 (完全形式)
                    tableNum++;
                    WdicTable table = new WdicTable(item);
                    table.add(block);
                    int tab = curIndent;
                    for (i = i + 1; i < n; i++) {
                        body = bodyList.get(i);
                        int t = WdicUtil.getTabCount(body) - ignoreTabs;
                        if (t <= tab) {
                            i--;
                            break;
                        }
                        block = WdicUtil.deleteTab(body);
                        table.add(block);
                    }
                    File dir = new File(basedir, WDIC_TABLE_DIR);
                    if (!dir.exists() && !dir.mkdirs()) {
                        logger.error("failed to create directories: " + dir.getPath());
                    }
                    String name = grpId + "_" + partId + "_" + item.getIndex() + "-" + tableNum;
                    File file = new File(dir, name + ".bmp");
                    if (!file.exists()) {
                        BufferedImage img = table.getImage();
                        try {
                            BmpUtil.write(img, file);
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
                    if (!tableList.contains(name)) {
                        tableList.add(name);
                    }
                    Element elem = _appendDataReference(indentElem, file.getName(), "graphic");
                    _appendRawText(elem, "[表]");
                    _appendNewLine(indentElem);
                } else if (block.startsWith("|| ") || block.startsWith("|= ")) {
                    // 表 (簡易形式)
                    tableNum++;
                    WdicTable table = new WdicTable(item);
                    table.add(block);
                    for (i = i + 1; i < n; i++) {
                        body = bodyList.get(i);
                        block = WdicUtil.deleteTab(body);
                        if (!block.startsWith("|| ") && !block.startsWith("|= ")) {
                            i--;
                            break;
                        }
                        table.add(block);
                    }
                    File dir = new File(basedir, WDIC_TABLE_DIR);
                    if (!dir.exists() && !dir.mkdirs()) {
                        logger.error("failed to create directories: " + dir.getPath());
                    }
                    String name = grpId + "_" + partId + "_" + item.getIndex() + "-" + tableNum;
                    File file = new File(dir, name + ".bmp");
                    if (!file.exists()) {
                        BufferedImage img = table.getImage();
                        try {
                            BmpUtil.write(img, file);
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
                    if (!tableList.contains(name)) {
                        tableList.add(name);
                    }
                    Element elem = _appendDataReference(indentElem, file.getName(), "graphic");
                    _appendRawText(elem, "[表]");
                    _appendNewLine(indentElem);
                } else if (block.startsWith("= ")) {
                    // 章見出し
                    if (i > 0) {
                        String prev = WdicUtil.deleteTab(bodyList.get(i - 1));
                        if (!prev.startsWith("= ")) {
                            _appendNewLine(indentElem);
                        }
                    }
                    // U+25A0: Black Square
                    // U+25A1: White Square
                    StringBuilder buf = new StringBuilder();
                    int black = 5 - section;
                    int white = section;
                    for (int j = 0; j < white; j++) {
                        buf.append('\u25a1');
                    }
                    for (int j = 0; j < black; j++) {
                        buf.append('\u25a0');
                    }
                    buf.append(" ");
                    buf.append(block.substring(2));
                    buf.append(" ");
                    for (int j = 0; j < black; j++) {
                        buf.append('\u25a0');
                    }
                    for (int j = 0; j < white; j++) {
                        buf.append('\u25a1');
                    }
                    _appendText(item, indentElem, buf.toString());
                    _appendNewLine(indentElem);
                } else {
                    // その他
                    _appendItemBodyBlock(item, indentElem, block, numMap, null);
                    _appendNewLine(indentElem);
                }
            }
        }
    }

    /**
     * 辞書項目内容を追加します。
     *
     * @param item 辞書項目
     * @param elem 追加対象の要素
     * @param block 追加する内容
     * @param numMap インデント数と箇条書き数とのマップ
     * @param prefix プレフィックス
     */
    private void _appendItemBodyBlock(final WdicItem item, final Element elem, final String block,
                                      final Map<Integer, Integer> numMap, final String prefix) {
        String target = block;
        if (target.startsWith("* ")) {
            // 文章
            // U+25c6: Black Diamond
            target = "\u25c6 " + target.substring(2);
        } else if (target.startsWith("- ")) {
            // 数字なし箇条書き
            // U+30FB: Katakana Middle Dot
            target = "\u30fb " + target.substring(2);
        } else if (target.startsWith("+ ")) {
            // 数字あり箇条書き
            // キー"-1"にこのブロックのインデント数が設定されている
            int indent = numMap.get(-1);
            // 現在の階層の数値
            int val = 1;
            if (numMap.containsKey(indent)) {
                val = numMap.get(indent) + 1;
            }
            String num = Integer.toString(val);
            numMap.put(indent, val);
            // 下位階層をクリア
            int lower = indent + 1;
            while (true) {
                if (!numMap.containsKey(lower)) {
                    break;
                }
                numMap.remove(lower);
                lower++;
            }
            // 上位階層の数値を追加
            int upper = indent - 1;
            while (upper >= 0) {
                if (!numMap.containsKey(upper)) {
                    break;
                }
                num = numMap.get(upper) + "." + num;
                upper--;
            }
            target = num + ") " + target.substring(2);
        } else if (target.startsWith("=> ")) {
            // 参照
            // U+21D2: Rightwards Double Arrow
            target = "\u21d2 " + target.substring(3);
        }
        if (prefix != null) {
            target = prefix + target;
        }
        _appendText(item, elem, target);
    }

    /**
     * 辞書項目のリンク部を追加します。
     *
     * @param item 辞書項目
     * @param elem 追加対象の要素
     * @param block 追加する内容
     */
    private void _appendItemLinkBlock(final WdicItem item, final Element elem, final String block) {
        String target = block;
        if (target.startsWith("= ")) {
            // グループ見出し
            _appendNewLine(elem);
            // U+25BC: Black Down-Pointing Triangle
            target = "\u25bc " + target.substring(2);
        } else if (target.startsWith("- ")) {
            // 関連語、外部リンク
            // U+21D2: Rightwards Double Arrow
            target = "\u21d2 " + target.substring(2);
        } else if (target.startsWith("-! ")) {
            // 反対語
            // U+21D4: Left Right Double Arrow
            target = "\u21d4 " + target.substring(3);
        }
        _appendText(item, elem, target, true);
    }

    /**
     * 辞書項目ノードを作成します。
     *
     * @param content コンテントノード
     */
    private void _makeItemNode(final Element content) {
        Iterator<Map.Entry<String, Set<WdicItem>>> it;
        Map.Entry<String, Set<WdicItem>> entry;

        // 画像グラグイン
        logger.debug("  graphic plugin");
        String[] ext = {".jpg", ".png"};
        int len = ext.length;
        for (int i = 0; i < len; i++) {
            it = pluginMap.entrySet().iterator();
            while (it.hasNext()) {
                entry = it.next();
                String name = entry.getKey();
                if (name.endsWith(ext[i])) {
                    Element itemElem = _appendItem(content, "PLUGIN:" + name);
                    Element headElem = _appendElement(itemElem, "head");
                    _appendRawText(headElem, name + " 【プラグイン】");
                    // ファイル名をキーワードとして登録
                    Element keywordElem = _appendElement(itemElem, "keyword");
                    _appendRawText(keywordElem, name);

                    Element bodyElem = _appendElement(itemElem, "body");
                    Element keyElem = _appendElement(bodyElem, "key");
                    _appendRawText(keyElem, name);
                    _appendNewLine(bodyElem);
                    Element refElem = _appendDataReference(bodyElem, name, "graphic");
                    _appendRawText(refElem, "[図版]");
                    _appendNewLine(bodyElem);

                    // プラグインを参照している項目を列挙
                    Set<WdicItem> set = entry.getValue();
                    Iterator<WdicItem> setIt = set.iterator();
                    while (setIt.hasNext()) {
                        WdicItem item = setIt.next();
                        _appendRawText(bodyElem, "\u2192 ");
                        String head = item.getHead();
                        String grpId = item.getWdic().getGroupId();
                        String id = "WDIC:" + grpId + ":" + head;
                        refElem = _appendIdReference(bodyElem, id);
                        String gname = item.getWdic().getGroupName();
                        String part = item.getWdic().getPartName();
                        String title = head + " 《" + gname + "：" + part + "》";
                        _appendRawText(refElem, title);
                        _appendNewLine(bodyElem);
                    }
                }
            }
        }

        // 音声プラグイン
        logger.debug("  sound plugin");
        ext = new String[]{".mp3", ".ogg", ".mid"};
        len = ext.length;
        for (int i = 0; i < len; i++) {
            it = pluginMap.entrySet().iterator();
            while (it.hasNext()) {
                entry = it.next();
                String name = entry.getKey();
                if (name.endsWith(ext[i])) {
                    Element itemElem = _appendItem(content, "PLUGIN:" + name);
                    Element headElem = _appendElement(itemElem, "head");
                    _appendRawText(headElem, name + " 【プラグイン】");
                    // ファイル名をキーワードとして登録
                    Element keywordElem = _appendElement(itemElem, "keyword");
                    _appendRawText(keywordElem, name);

                    Element bodyElem = _appendElement(itemElem, "body");
                    Element keyElem = _appendElement(bodyElem, "key");
                    _appendRawText(keyElem, name);
                    _appendNewLine(bodyElem);
                    Element refElem = _appendDataReference(bodyElem, name, "sound");
                    _appendRawText(refElem, "[音声]");
                    _appendNewLine(bodyElem);

                    // プラグインを参照している項目を列挙
                    Set<WdicItem> set = entry.getValue();
                    Iterator<WdicItem> setIt = set.iterator();
                    while (setIt.hasNext()) {
                        WdicItem item = setIt.next();
                        _appendRawText(bodyElem, "\u2192 ");
                        String head = item.getHead();
                        String grpId = item.getWdic().getGroupId();
                        String id = "WDIC:" + grpId + ":" + head;
                        refElem = _appendIdReference(bodyElem, id);
                        String gname = item.getWdic().getGroupName();
                        String part = item.getWdic().getPartName();
                        String title = head + " 《" + gname + "：" + part + "》";
                        _appendRawText(refElem, title);
                        _appendNewLine(bodyElem);
                    }
                }
            }
        }

        // その他のプラグイン
        logger.debug("  document plugin");
        File plugin = new File(basedir, WDIC_PLUGIN_DIR);
        ext = new String[]{".jpg", ".png", ".mp3", ".ogg", ".mid"};
        len = ext.length;
        it = pluginMap.entrySet().iterator();
        while (it.hasNext()) {
            entry = it.next();
            String name = entry.getKey();
            boolean add = true;
            for (int i = 0; i < len; i++) {
                if (name.endsWith(ext[i])) {
                    add = false;
                    break;
                }
            }
            if (add) {
                File file = new File(plugin, name);
                if (!file.exists()) {
                    logger.error("file not found: " + file.getPath());
                    continue;
                }
                Element itemElem = _appendItem(content, "PLUGIN:" + name);
                Element headElem = _appendElement(itemElem, "head");
                _appendRawText(headElem, name + " 【プラグイン】");
                // ファイル名をキーワードとして登録
                Element keywordElem = _appendElement(itemElem, "keyword");
                _appendRawText(keywordElem, name);

                Element bodyElem = _appendElement(itemElem, "body");
                Element keyElem = _appendElement(bodyElem, "key");
                _appendRawText(keyElem, name);
                _appendNewLine(bodyElem);

                // プラグインを参照している項目を列挙
                Set<WdicItem> set = entry.getValue();
                Iterator<WdicItem> setIt = set.iterator();
                while (setIt.hasNext()) {
                    WdicItem item = setIt.next();
                    _appendRawText(bodyElem, "\u2192 ");
                    String head = item.getHead();
                    String grpId = item.getWdic().getGroupId();
                    String id = "WDIC:" + grpId + ":" + head;
                    Element refElem = _appendIdReference(bodyElem, id);
                    String gname = item.getWdic().getGroupName();
                    String part = item.getWdic().getPartName();
                    String title = head + " 《" + gname + "：" + part + "》";
                    _appendRawText(refElem, title);
                    _appendNewLine(bodyElem);
                }

                // プラグインの内容
                Element indentElem = _appendElement(bodyElem, "indent");
                try {
                    LineIterator lineIt = FileUtils.lineIterator(file, ENCODING);
                    while (lineIt.hasNext()) {
                        String line = WdicUtil.sanitize(lineIt.nextLine());
                        _appendRawText(indentElem, line);
                        _appendNewLine(indentElem);
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 著作権ノードを作成します。
     *
     * @param content コンテントノード
     */
    private void _makeCopyrightNode(final Element content) {
        Element copyright = _appendElement(content, "copyright");
        String[] line = manual.getCopyright();
        int len = line.length;
        for (int i = 0; i < len; i++) {
            _appendRawText(copyright, line[i]);
            _appendNewLine(copyright);
        }
    }

    /**
     * メニューノードを作成します。
     *
     * @param content コンテントノード
     */
    private void _makeMenuNode(final Element content) {
        Element menu = _appendElement(content, "menu");
        Element layerElem = _appendLayer(menu, "MENU:top");

        Element refElem = _appendIdReference(layerElem, "MENU:manual");
        String title = groupList.getName() + " " + groupList.getEdition();
        _appendRawText(refElem, title);
        _appendNewLine(layerElem);

        refElem = _appendIdReference(layerElem, "MENU:bib");
        _appendRawText(refElem, "基礎文献");
        _appendNewLine(layerElem);

        refElem = _appendIdReference(layerElem, "DIR:/");
        _appendRawText(refElem, "分類別収録語一覧");
        _appendNewLine(layerElem);

        refElem = _appendIdReference(layerElem, "MENU:group");
        _appendRawText(refElem, "グループ別収録語一覧");
        _appendNewLine(layerElem);

        refElem = _appendIdReference(layerElem, "MENU:plugin");
        _appendRawText(refElem, "グループ別プラグイン一覧");
        _appendNewLine(layerElem);

        refElem = _appendIdReference(layerElem, "MENU:image");
        _appendRawText(refElem, "画像プラグイン一覧");
        _appendNewLine(layerElem);

        refElem = _appendIdReference(layerElem, "MENU:sound");
        _appendRawText(refElem, "音声プラグイン一覧");
        _appendNewLine(layerElem);

        refElem = _appendIdReference(layerElem, "MENU:text");
        _appendRawText(refElem, "文書プラグイン一覧");
        _appendNewLine(layerElem);

        logger.debug("  manual");
        _createManualLayer(menu);
        logger.debug("  bibliography");
        _createBibliographyLayer(menu);
        logger.debug("  directory");
        _createDirectoryLayer(menu);
        logger.debug("  group");
        _createGroupLayer(menu);
        logger.debug("  plugin list");
        _createPluginLayer(menu);
        logger.debug("  graphic list");
        _createImageLayer(menu);
        logger.debug("  sound list");
        _createSoundLayer(menu);
        logger.debug("  document list");
        _createTextLayer(menu);
    }

    /**
     * マニュアルメニュー階層を作成します。
     *
     * @param menu メニューノード
     */
    private void _createManualLayer(final Element menu) {
        Element layerElem = _appendLayer(menu, "MENU:manual");
        String[] sec = manual.getSections();
        int len = sec.length;
        for (int i = 0; i < len; i++) {
            Element refElem = _appendIdReference(layerElem, "MENU:manual:" + sec[i]);
            _appendRawText(refElem, sec[i]);
            _appendNewLine(layerElem);
            String prev = null;
            String next = null;
            if (i > 0) {
                prev = sec[i - 1];
            }
            if (i < (len - 1)) {
                next = sec[i + 1];
            }
            _createManualLayer(menu, sec[i], prev, next);
        }
    }

    /**
     * マニュアルメニュー階層を作成します。
     *
     * @param menu メニューノード
     * @param sec セクション
     * @para prev 前セクション
     * @para next 次セクション
     */
    private void _createManualLayer(final Element menu, final String sec, final String prev,
                                    final String next) {
        Element layerElem = _appendLayer(menu, "MENU:manual:" + sec);
        Element keyElem = _appendElement(layerElem, "key");
        _appendRawText(keyElem, sec);

        Element indent1Elem = _appendElement(layerElem, "indent");
        Element indent2Elem = null;
        Element indent3Elem = null;
        Element indentElem = indent1Elem;
        String[] contents = manual.getContents(sec);
        int len = contents.length;
        for (int i=0; i<len; i++) {
            String str = contents[i];
            if (str.length() > 0) {
                if (str.startsWith("\t")) {
                    if (indent2Elem == null) {
                        indent2Elem = _appendElement(indent1Elem, "indent");
                    }
                    if (str.startsWith("\t\t")) {
                        if (indent3Elem == null) {
                            indent3Elem = _appendElement(indent2Elem, "indent");
                        }
                        str = str.substring(2);
                        indentElem = indent3Elem;
                    } else {
                        str = str.substring(1);
                        indentElem = indent2Elem;
                        indent3Elem = null;
                    }
                } else {
                    indentElem = indent1Elem;
                    indent2Elem = null;
                    indent3Elem = null;
                }
                _appendRawText(indentElem, str);
            }
            _appendNewLine(indentElem);
        }

        _appendNewLine(layerElem);

        if (prev != null) {
            _appendRawText(layerElem, "\u2190 ");
            Element refElem = _appendIdReference(layerElem, "MENU:manual:" + prev);
            _appendRawText(refElem, prev);
            _appendRawText(layerElem, " | ");
        }
        if (next != null) {
            _appendRawText(layerElem, "\u2192 ");
            Element refElem = _appendIdReference(layerElem, "MENU:manual:" + next);
            _appendRawText(refElem, next);
            _appendRawText(layerElem, " | ");
        }
        _appendRawText(layerElem, "\u2191 ");
        String title = groupList.getName() + " " + groupList.getEdition();
        Element refElem = _appendIdReference(layerElem, "MENU:manual");
        _appendRawText(refElem, title);
        _appendNewLine(layerElem);
    }

    /**
     * 基礎文献メニュー階層を作成します。
     *
     * @param menu メニューノード
     */
    private void _createBibliographyLayer(final Element menu) {
        Element layerElem = _appendLayer(menu, "MENU:bib");
        Collection<WdicGroup> groups = groupList.getGroups();
        Iterator<WdicGroup> it = groups.iterator();
        while (it.hasNext()) {
            WdicGroup group = it.next();
            Element refElem = _appendIdReference(layerElem, "MENU:bib:" + group.getGroupId());
            _appendRawText(refElem, group.getGroupName() + "用語の基礎知識");
            _appendNewLine(layerElem);
            _createBibliographyLayer(menu, group);
        }
    }

    /**
     * 基礎文献メニュー階層を作成します。
     *
     * @param menu メニューノード
     * @param group 辞書グループ
     */
    private void _createBibliographyLayer(final Element menu, final WdicGroup group) {
        Element layerElem = _appendLayer(menu, "MENU:bib:" + group.getGroupId());
        Element keyElem = _appendElement(layerElem, "key");
        _appendRawText(keyElem, group.getGroupName() + "用語の基礎知識");
        Element indentElem = _appendElement(layerElem, "indent");
        String[] contents = group.getWdicBib().getBibliography();
        int len = contents.length;
        for (int i = 0; i < len; i++) {
            String str = contents[i];
            if (str.length() > 0) {
                _appendRawText(indentElem, str);
            }
            _appendNewLine(indentElem);
        }
    }

    /**
     * 分類一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     */
    private void _createDirectoryLayer(final Element menu) {
        Element layerElem = _appendLayer(menu, "DIR:/");
        List<String> dirs = dirList.getChildren("/");
        int len = dirs.size();
        for (int i = 0; i < len; i++) {
            String dir = dirs.get(i);
            _appendRawText(layerElem, "\u21d2 ");
            Element refElem = _appendIdReference(layerElem, "DIR:" + dir);
            _appendRawText(refElem, dirList.getName(dir));
            _appendNewLine(layerElem);
            _createDirectoryLayer(menu, dir);
        }
    }

    /**
     * 分類一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     * @param dir 分類
     */
    private void _createDirectoryLayer(final Element menu, final String dir) {
        Element layerElem = _appendLayer(menu, "DIR:" + dir);
        Element refElem = _appendIdReference(layerElem, "DIR:/");
        _appendRawText(refElem, "分類");
        String[] dirs;
        if (dir.startsWith("/")) {
            dirs = dir.substring(1).split("/");
        } else {
            dirs = dir.split("/");
        }
        String key = "";
        int len = dirs.length;
        for (int i = 0; i < len - 1; i++) {
            _appendRawText(layerElem, " > ");
            key += "/" + dirs[i];
            refElem = _appendIdReference(layerElem, "DIR:" + key);
            _appendRawText(refElem, dirList.getName(key));
        }
        _appendRawText(layerElem, " > " + dirList.getName(dir));
        _appendNewLine(layerElem);

        List<String> children = dirList.getChildren(dir);
        len = children.size();
        int cnt = len;
        for (int i = 0; i < len; i++) {
            String child = children.get(i);
            _appendRawText(layerElem, "\u21d2 ");
            if (dirList.hasAlias(child)) {
                String alias = dirList.getAlias(child);
                refElem = _appendIdReference(layerElem, "DIR:" + alias);
                _appendRawText(refElem, dirList.getName(child) + "@");
                _appendNewLine(layerElem);
            } else {
                refElem = _appendIdReference(layerElem, "DIR:" + child);
                _appendRawText(refElem, dirList.getName(child));
                _appendNewLine(layerElem);
                _createDirectoryLayer(menu, child);
            }
        }

        List<WdicItem> items = groupList.getWdicItem(dir);
        len = items.size();
        cnt += len;
        for (int i = 0; i < len; i++) {
            WdicItem item = items.get(i);
            _appendRawText(layerElem, "\u2192 ");
            String head = item.getHead();
            String grpId = item.getWdic().getGroupId();
            String id = "WDIC:" + grpId + ":" + head;
            refElem = _appendIdReference(layerElem, id);
            String gname = item.getWdic().getGroupName();
            String part = item.getWdic().getPartName();
            String title = head + " 《" + gname + "：" + part + "》";
            _appendRawText(refElem, title);
            _appendNewLine(layerElem);
        }

        if (cnt == 0) {
            _appendRawText(layerElem, "(該当単語なし)");
            _appendNewLine(layerElem);
        }
    }

    /**
     * グループ一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     */
    private void _createGroupLayer(final Element menu) {
        String id = "MENU:group";
        Element layerElem = _appendLayer(menu, id);
        Collection<WdicGroup> groups = groupList.getGroups();
        Iterator<WdicGroup> it = groups.iterator();
        while (it.hasNext()) {
            WdicGroup group = it.next();
            Element refElem = _appendIdReference(layerElem, id + ":" + group.getGroupId());
            _appendRawText(refElem, group.getGroupName() + "用語の基礎知識");
            _appendNewLine(layerElem);
            _createGroupLayer(menu, group);
        }
    }

    /**
     * グループ一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     * @param group 辞書グループ
     */
    private void _createGroupLayer(final Element menu, final WdicGroup group) {
        String id = "MENU:group:" + group.getGroupId();
        Element layerElem = _appendLayer(menu, id);
        Element refElem = _appendIdReference(layerElem, "MENU:group");
        _appendRawText(refElem, "グループ");
        _appendRawText(layerElem, " > " + group.getGroupName());
        _appendNewLine(layerElem);
        List<Wdic> dics = group.getWdics();
        int len = dics.size();
        for (int i = 0; i < len; i++) {
            Wdic wdic = dics.get(i);
            String name = wdic.getPartName() + "編";
            refElem = _appendIdReference(layerElem, id + ":" + wdic.getPartId());
            _appendRawText(refElem, name);
            _appendNewLine(layerElem);
            _createGroupLayer(menu, wdic);
        }
    }

    /**
     * グループ一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     * @param wdic 辞書
     */
    private void _createGroupLayer(final Element menu, final Wdic wdic) {
        String grpId = wdic.getGroupId();
        String partId = wdic.getPartId();
        String id = "MENU:group:" + grpId + ":" + partId;
        Element layerElem = _appendLayer(menu, id);
        Element refElem = _appendIdReference(layerElem, "MENU:group");
        _appendRawText(refElem, "グループ");
        _appendRawText(layerElem, " > ");
        refElem = _appendIdReference(layerElem, "MENU:group:" + grpId);
        _appendRawText(refElem, wdic.getGroupName());
        _appendRawText(layerElem, " > " + wdic.getPartName() + "編");
        _appendNewLine(layerElem);
        List<WdicItem> items = wdic.getWdicItems();
        int len = items.size();
        for (int i = 0; i < len; i++) {
            WdicItem item = items.get(i);
            if (item.isAlias()) {
                String name = item.getHead();
                _appendRawText(layerElem, name);
                List<String> yomiList = item.getYomi();
                if (yomiList.isEmpty()) {
                    logger.info("yomi not defined: " + grpId + ":" + partId + ":" + item.getHead());
                } else {
                    String yomi = yomiList.get(0);
                    _appendText(item, layerElem, " [" + yomi + "]");
                }
                _appendRawText(layerElem, " \u21d2 ");
                name = item.getRealName();
                String refid = WdicUtil.unescape(name);
                id = "WDIC:" + grpId + ":" + refid;
                refElem = _appendIdReference(layerElem, id);
                _appendText(item, refElem, name);
                _appendNewLine(layerElem);
            } else {
                String name = item.getHead();
                id = "WDIC:" + grpId + ":" + name;
                refElem = _appendIdReference(layerElem, id);
                _appendRawText(refElem, name);
                List<String> yomiList = item.getYomi();
                if (yomiList.isEmpty()) {
                    logger.info("yomi not defined: " + grpId + ":" + partId + ":" + item.getHead());
                } else {
                    String yomi = yomiList.get(0);
                    _appendText(item, layerElem, " [" + yomi + "]");
                }
                _appendNewLine(layerElem);
            }
        }
    }

    /**
     * プラグイン一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     */
    private void _createPluginLayer(final Element menu) {
        String id = "MENU:plugin";
        Element layerElem = _appendLayer(menu, id);
        Collection<WdicGroup> groups = groupList.getGroups();
        Iterator<WdicGroup> it = groups.iterator();
        while (it.hasNext()) {
            WdicGroup group = it.next();
            Element refElem = _appendIdReference(layerElem, id + ":" + group.getGroupId());
            _appendRawText(refElem, group.getGroupName() + "用語の基礎知識");
            _appendNewLine(layerElem);
            _createPluginLayer(menu, group);
        }
    }

    /**
     * プラグイン一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     * @param group 辞書グループ
     */
    private void _createPluginLayer(final Element menu, final WdicGroup group) {
        String id = "MENU:plugin:" + group.getGroupId();
        Element layerElem = _appendLayer(menu, id);
        Element refElem = _appendIdReference(layerElem, "MENU:plugin");
        _appendRawText(refElem, "グループ");
        _appendRawText(layerElem, " > " + group.getGroupName());
        _appendNewLine(layerElem);

        pluginMap.entrySet().stream()
            .filter(entry -> entry.getValue().stream()
                    .anyMatch(v -> group.equals(v.getWdic().getGroup())))
            .forEach(entry -> {
                String name = entry.getKey();
                _appendRawText(_appendIdReference(layerElem, "PLUGIN:" + name), name);
                _appendNewLine(layerElem);
            });
    }

    /**
     * 画像一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     */
    private void _createImageLayer(final Element menu) {
        Element layerElem = _appendLayer(menu, "MENU:image");
        List<String> ext = Arrays.asList(".jpg", ".png");
        pluginMap.keySet().stream()
                .filter(v -> ext.stream().anyMatch(s -> s.endsWith(v)))
                .forEach(name -> {
                    Element refElem = _appendIdReference(layerElem, "PLUGIN:" + name);
                    _appendRawText(refElem, name);
                    _appendNewLine(layerElem);
                });
    }

    /**
     * 音声一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     */
    private void _createSoundLayer(final Element menu) {
        Element layerElem = _appendLayer(menu, "MENU:sound");
        List<String> ext = Arrays.asList(".mp3", ".ogg", ".mid");
        pluginMap.keySet().stream()
                .filter(v -> ext.stream().anyMatch(s -> s.endsWith(v)))
                .forEach(name -> {
                    Element refElem = _appendIdReference(layerElem, "PLUGIN:" + name);
                    _appendRawText(refElem, name);
                    _appendNewLine(layerElem);
                });
    }

    /**
     * 文書一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     */
    private void _createTextLayer(final Element menu) {
        Element layerElem = _appendLayer(menu, "MENU:text");
        List<String> ext = Arrays.asList(".jpg", ".png", ".mp3", ".ogg", ".mid");
        pluginMap.keySet().stream()
                .filter(v -> ext.stream().noneMatch(s -> s.endsWith(v)))
                .forEach(name -> {
                    Element refElem = _appendIdReference(layerElem, "PLUGIN:" + name);
                    _appendRawText(refElem, name);
                    _appendNewLine(layerElem);
                });
    }

    /**
     * テキストノードを追加します。
     *
     * @param node テキストを追加するノード
     * @param str 文字列
     */
    private void _appendRawText(final Node node, final String str) {
        if (str != null && str.trim().length() > 0) {
            String tmp = str.replace((char)0x3099, (char)0x309b)
                            .replace((char)0x309a, (char)0x309c);
            if (node != null) {
                Text text = node.getOwnerDocument().createTextNode(tmp);
                node.appendChild(text);
                _checkCharacter(text);
            }
        }
    }

    /**
     * 要素を追加します。
     *
     * @param node 要素を追加するノード
     * @param tag 要素のタグ名称
     * @return 追加された要素
     */
    private Element _appendElement(final Node node, final String tag) {
        Element elem = node.getOwnerDocument().createElement(tag);
        return (Element)node.appendChild(elem);
    }

    /**
     * 改行要素を追加します。
     *
     * @param node 改行を追加するノード
     * @return 追加された改行要素
     */
    private Element _appendNewLine(final Node node) {
        return _appendElement(node, "br");
    }

    /**
     * 項目要素を追加します。
     *
     * @param node 項目要素を追加するノード
     * @param id ID属性値
     * @return 追加された項目要素
     */
    private Element _appendItem(final Node node, final String id) {
        Element elem = _appendElement(node, "item");
        elem.setAttribute("id", id);
        return elem;
    }

    /**
     * 参照要素を追加します。
     *
     * @param node 参照要素を追加するノード
     * @param id ID属性値
     * @return 追加された参照要素
     */
    private Element _appendIdReference(final Node node, final String id) {
        Element elem = _appendElement(node, "ref");
        elem.setAttribute("id", id);
        return elem;
    }

    /**
     * 参照要素を追加します。
     *
     * @param node 参照要素を追加するノード
     * @param data data属性値
     * @param type type属性値
     * @return 追加された参照要素
     */
    private Element _appendDataReference(final Node node, final String data, final String type) {
        Element elem = _appendElement(node, "ref");
        elem.setAttribute("data", data);
        elem.setAttribute("type", type);
        return elem;
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
        Element elem = _appendElement(node, "char");
        elem.setAttribute("name", name);
        elem.setAttribute("type", type);
        return elem;
    }

    /**
     * レイヤ要素を追加します。
     *
     * @param node レイヤ要素を追加するノード
     * @param id ID属性値
     * @return 追加されたレイヤ要素
     */
    private Element _appendLayer(final Node node, final String id) {
        Element elem = _appendElement(node, "layer");
        elem.setAttribute("id", id);
        return elem;
    }

    /**
     * データ要素を追加します。
     *
     * @param node データ要素を追加するノード
     * @param name name属性値
     * @param src src属性値
     * @param format format属性値
     * @return 追加されたデータ要素
     */
    private Element _appendData(final Node node, final String name, final String src,
                                final String format) {
        Element elem = _appendElement(node, "data");
        elem.setAttribute("name", name);
        elem.setAttribute("src", src);
        elem.setAttribute("format", format);
        return elem;
    }

    /**
     * テキストノードを追加します。
     *
     * @param item 辞書項目
     * @param node テキストを追加するノード
     * @param str 文字列
     */
    private void _appendText(final WdicItem item, final Node node, final String str) {
        _appendText(item, node, str, false);
    }

    /**
     * テキストノードを追加します。
     *
     * @param item 辞書項目
     * @param node テキストを追加するノード
     * @param str 文字列
     * @param linkBlock リンク部の場合はtrue
     */
    private void _appendText(final WdicItem item, final Node node, final String str,
                             final boolean linkBlock) {
        String grpId = item.getWdic().getGroupId();
        String partId = item.getWdic().getPartId();
        String itemId = grpId + ":" + partId + ":" + item.getHead();
        StringBuilder buf = new StringBuilder();
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char ch = str.charAt(i);
            if (Character.isHighSurrogate(ch)
                || Character.isLowSurrogate(ch)) {
                buf.append(ch);
                continue;
            }

            if (ch == '\'') {
                StringBuilder bracket = new StringBuilder("'");
                int idx1 = i + 1;
                for (; idx1 < len; idx1++) {
                    if (str.charAt(idx1) != '\'') {
                        break;
                    }
                    bracket.append("'");
                }
                if (bracket.length() > 1) {
                    // 2個以上は強調表示
                    int idx2 = WdicUtil.indexOf(str, bracket.toString(), idx1);
                    if (idx2 != -1) {
                        // 強調
                        _appendRawText(node, buf.toString());
                        buf.delete(0, buf.length());
                        Element elem = _appendElement(node, "em");
                        _appendText(item, elem, str.substring(idx1, idx2), linkBlock);
                        i = idx2 + bracket.length() - 1;
                    } else {
                        // 閉じられていないのでそのまま追加する
                        buf.append(bracket);
                        i = idx1 - 1;
                    }
                    continue;
                }
            } else if (ch == '[') {
                if (i + 1 < len && str.charAt(i + 1) == '[') {
                    int idx1 = i + 1;
                    int idx2 = WdicUtil.indexOf(str, "]]", idx1 + 1);
                    if (idx2 != -1) {
                        // リンク
                        _appendRawText(node, buf.toString());
                        buf.delete(0, buf.length());
                        String ref = str.substring(idx1 + 1, idx2);
                        String name = null;
                        if (ref.startsWith("<")) {
                            // 表示内容
                            int idx3 = WdicUtil.indexOf(ref, ">", 1);
                            if (idx3 != -1) {
                                name = ref.substring(1, idx3);
                                ref = ref.substring(idx3 + 1);
                            }
                        }
                        if (ref.startsWith("http:")
                            || ref.startsWith("https:")
                            || ref.startsWith("ftp:")
                            || ref.startsWith("news:")
                            || ref.startsWith("gopher:")
                            || ref.startsWith("mailto:")
                            || ref.startsWith("phone:")
                            || ref.startsWith("urn:")
                            || ref.startsWith("x-geo:")) {
                            // URI
                            if (StringUtils.isNotBlank(name)) {
                                if (linkBlock) {
                                    ref = name + " <" + ref + ">";
                                } else {
                                    ref = name + "<" + ref + ">";
                                }
                            }
                            _appendText(item, node, ref, linkBlock);
                        } else if (ref.startsWith("//")) {
                            // プラグイン
                            int idx3 = ref.indexOf("|");
                            if (idx3 > 0) {
                                // delete option
                                ref = ref.substring(0, idx3);
                            }
                            String gid = null;
                            String file = null;
                            idx3 = ref.indexOf("/", 2);
                            if (idx3 != -1) {
                                gid = ref.substring(2, idx3);
                                file = ref.substring(idx3 + 1);
                            } else {
                                gid = grpId;
                                file = ref.substring(2);
                            }
                            Element refElem = null;
                            if (file.endsWith(".jpg") || file.endsWith(".png")) {
                                refElem = _appendDataReference(node, file, "graphic");
                            } else if (file.endsWith(".mp3") || file.endsWith(".ogg")
                                       || file.endsWith(".mid")) {
                                refElem = _appendDataReference(node, file, "sound");
                            } else {
                                refElem = _appendIdReference(node, "PLUGIN:" + file);
                            }
                            if (StringUtils.isBlank(name)) {
                                name = file;
                            }
                            if (linkBlock) {
                                WdicGroup group = groupList.getGroup(gid);
                                if (group != null) {
                                    String gname = group.getGroupName();
                                    name = name + " 《" + gname + "》";
                                }
                            }
                            _appendRawText(refElem, name);
                        } else {
                            if (ref.startsWith("x-wdic:")) {
                                // x-wdic:/グループ名/単語
                                ref = ref.substring("x-wdic:".length());
                            }
                            String gid = null;
                            String head = null;
                            if (ref.startsWith("/")) {
                                // グループ名/単語
                                int idx3 = WdicUtil.indexOf(ref, "/", 1);
                                if (idx3 != -1) {
                                    gid = ref.substring(1, idx3);
                                    head = ref.substring(idx3 + 1);
                                } else {
                                    head = ref.substring(1);
                                }
                            } else {
                                // 単語
                                head = ref;
                            }
                            String refid = WdicUtil.unescape(head);
                            if (StringUtils.isBlank(name)) {
                                name = head;
                            }
                            if (StringUtils.isBlank(gid)) {
                                // 同一グループ内
                                gid = grpId;
                            }
                            WdicGroup group = groupList.getGroup(gid);
                            if (group != null) {
                                String gname = group.getGroupName();
                                Wdic wdic = group.getWdic(refid);
                                if (wdic != null) {
                                    String id = "WDIC:" + gid + ":" + refid;
                                    Element refElem = _appendIdReference(node, id);
                                    if (linkBlock) {
                                        name = name + " 《" + gname + "：" + wdic.getPartName() + "》";
                                    }
                                    _appendText(item, refElem, name, linkBlock);
                                } else {
                                    logger.error("undefined word: " + gid + "/" + refid);
                                    if (linkBlock) {
                                        name = name + " 《" + gname + "》";
                                    }
                                    _appendText(item, node, name, linkBlock);
                                }
                            } else {
                                logger.error("undefined group: " + gid);
                                _appendText(item, node, name, linkBlock);
                            }
                        }
                        i = idx2 + 1;
                    } else {
                        // 閉じられていないのでそのまま追加する
                        buf.append("[[");
                        i = idx1;
                    }
                    continue;
                }
            }

            if (ch != '\\') {
                // バックスラッシュ以外はそのまま追加
                buf.append(ch);
                continue;
            }
            if (i + 1 >= len) {
               // バックスラッシュに続く文字がないのでそのまま追加
                buf.append(ch);
                continue;
            }

            char ch1 = str.charAt(i + 1);
            if (ch1 >= 0x21 && ch1 <= 0x7e) {
                if (!CharUtils.isAsciiAlphanumeric(ch1)) {
                    // 1文字エスケープ (英数字以外の記号)
                    i++;
                    buf.append(ch1);
                    continue;
                }
            }

            int idx = WdicUtil.indexOf(str, ";", i + 1);
            if (idx < 0) {
                logger.error("unexpected format: " + str);
                buf.append(ch);
                continue;
            }
            String ref = str.substring(i + 1, idx);
            i = idx;
            int sep1 = WdicUtil.indexOf(ref, "{", 0);
            int sep2 = WdicUtil.indexOf(ref, ":", 0);
            if (sep1 == -1 && sep2 == -1) {
                // 実体参照
                buf.append(WdicUtil.getCharacter(ref));
                continue;
            }

            // 特殊機能
            String name;
            ArrayList<String> param = new ArrayList<>();
            if (sep1 != -1 && sep2 != -1) {
                if (sep2 < sep1) {
                    sep1 = -1;
                } else {
                    sep2 = -1;
                }
            }
            if (sep1 != -1) {
                // 引数は{}で括られている
                name = ref.substring(0, sep1);
                int idx1 = sep1;
                int idx2;
                while (idx1 != -1) {
                    idx2 = ref.indexOf('}', idx1 + 1);
                    if (idx2 == -1) {
                        idx2 = ref.length();
                    }
                    param.add(ref.substring(idx1 + 1, idx2));
                    idx1 = ref.indexOf('{', idx2 + 1);
                }
            } else {
                // 引数は:で区切られている
                name = ref.substring(0, sep2);
                Collections.addAll(param, ref.substring(sep2 + 1).split(":"));
            }

            if ("x".equals(name)) {
                String code = param.get(0);
                try {
                    int codePoint = Integer.parseInt(code, 16);
                    buf.appendCodePoint(codePoint);
                } catch (Exception e) {
                    logger.error("unknown character code: " + code);
                }
            } else if ("sup".equals(name) || "sub".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                Element elem = _appendElement(node, name);
                _appendText(item, elem, param.get(0), linkBlock);
            } else if ("ruby".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                _appendText(item, node, param.get(0), linkBlock);
                if (param.size() > 1) {
                    Element elem = _appendElement(node, "sub");
                    _appendText(item, elem, "(" + param.get(1) + ")", linkBlock);
                }
            } else if ("asin".equals(name)) {
                String asin = param.get(0);
                String url;
                switch (asin.charAt(0)) {
                    case '4':
                        url = "http://www.amazon.co.jp/exec/obidos/ASIN/";
                        break;
                    case '3':
                        url = "http://www.amazon.de/exec/obidos/ASIN/";
                        break;
                    case '2':
                        url = "http://www.amazon.fr/exec/obidos/ASIN/";
                        break;
                    case '1':
                        url = "http://www.amazon.co.uk/exec/obidos/ASIN/";
                        break;
                    case '0':
                    default:
                        url = "http://www.amazon.com/exec/obidos/ASIN/";
                        break;
                }
                buf.append(url + asin);
            } else if ("flag".equals(name)) {
                // ignore
            } else if ("mex".equals(name)) {
                buf.append("[" + param.get(0) + "]");
            } else if ("glyph".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                String glyph = param.get(0);
                if (!glyphList.contains(glyph)) {
                    glyphList.add(glyph);
                }
                Element elem = _appendDataReference(node, "glyph-" + glyph, "inlineGraphic");
                _appendRawText(elem, "[グリフ:" + glyph + "]");
            } else if ("oline".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                String pstr = param.get(0);
                int n = pstr.length();
                for (int j = 0; j < n; j++) {
                    int codePoint = pstr.codePointAt(j);
                    String hex = HexUtil.toHexString(codePoint, 6);
                    String fontname = "U" + hex + "-OL";
                    File dir = new File(basedir, WDIC_GAIJI_DIR);
                    if (!dir.exists() && !dir.mkdirs()) {
                        logger.error("failed to create directories: " + dir.getPath());
                    }
                    File file = new File(dir, fontname + ".xbm");
                    if (!file.exists()) {
                        BufferedImage img = WdicUtil.toOverLineImage(codePoint);
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
            } else if ("uline".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                String pstr = param.get(0);
                int n = pstr.length();
                for (int j = 0; j < n; j++) {
                    int codePoint = pstr.codePointAt(j);
                    String hex = HexUtil.toHexString(codePoint, 6);
                    String fontname = "U" + hex + "-UL";
                    File dir = new File(basedir, WDIC_GAIJI_DIR);
                    if (!dir.exists() && !dir.mkdirs()) {
                        logger.error("failed to create directories: " + dir.getPath());
                    }
                    File file = new File(dir, fontname + ".xbm");
                    if (!file.exists()) {
                        BufferedImage img = WdicUtil.toUnderLineImage(codePoint);
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
            } else if ("sout".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                String pstr = param.get(0);
                int n = pstr.length();
                for (int j = 0; j < n; j++) {
                    int codePoint = pstr.codePointAt(j);
                    String hex = HexUtil.toHexString(codePoint, 6);
                    String fontname = "U" + hex + "-LT";
                    File dir = new File(basedir, WDIC_GAIJI_DIR);
                    if (!dir.exists() && !dir.mkdirs()) {
                        logger.error("failed to create directories: " + dir.getPath());
                    }
                    File file = new File(dir, fontname + ".xbm");
                    if (!file.exists()) {
                        BufferedImage img = WdicUtil.toLineThroughImage(codePoint);
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
            } else if ("date".equals(name) || "dt".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                buf.append(param.get(0));
                // if (param.size() > 1) {
                //     String type = param.get(1);
                //     if ("JC".equals(type)) {
                //         buf.append("[ユリウス歴]");
                //     } else if ("GC".equals(type)) {
                //         buf.append("[グレゴリオ歴]");
                //     } else if ("LC".equals(type)) {
                //         buf.append("[太陰太陽歴]");
                //     } else {
                //         logger.error("unknown function parameter: " + itemId +
                //                       " [" + name + ":" + type + "]");
                //     }
                // }
                _appendText(item, node, buf.toString(), linkBlock);
                buf.delete(0, buf.length());
            } else {
                if (!"unit".equals(name)) {
                    logger.error("unknown function name: " + itemId + " [" + name + "]");
                }
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                _appendText(item, node, param.get(0), linkBlock);
            }
        }
        _appendRawText(node, buf.toString());
    }

    /**
     * 使用されている文字が有効かどうかを確認します。
     *
     * @param node ノード
     */
    private void _checkCharacter(final Node node) {
        if (node.getNodeType() == Node.TEXT_NODE) {
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
                    File dir = new File(basedir, WDIC_GAIJI_DIR);
                    if (!dir.exists() && !dir.mkdirs()) {
                        logger.error("failed to create directories: " + dir.getPath());
                    }
                    String[] files =
                        dir.list(FileFilterUtils.andFileFilter(
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
                            File file = new File(dir, name + i + ".xbm");
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
                    File dir = new File(basedir, WDIC_GAIJI_DIR);
                    if (!dir.exists() && !dir.mkdirs()) {
                        logger.error("failed to create directories: " + dir.getPath());
                    }
                    File file = new File(dir, name + ".xbm");
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
        if (node.hasChildNodes()) {
            NodeList nlist = node.getChildNodes();
            int len = nlist.getLength();
            for (int i = 0; i < len; i++) {
                Node child = nlist.item(i);
                _checkCharacter(child);
                int n = nlist.getLength();
                if (len < n) {
                    i += n - len;
                    len = n;
                }
            }
        }
    }
}

// end of Wdic2Xml.java
