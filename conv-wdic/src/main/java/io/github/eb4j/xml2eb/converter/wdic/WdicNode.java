package io.github.eb4j.xml2eb.converter.wdic;


import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.CharUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.eb4j.xml2eb.util.BmpUtil;
import io.github.eb4j.xml2eb.util.FontUtil;
import io.github.eb4j.xml2eb.util.HexUtil;
import io.github.eb4j.xml2eb.util.WordUtil;


/**
 * Node generation class.
 * Created by miurahr on 16/07/17.
 */
public class WdicNode {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private static final String ENCODING = "UTF-8";
    private static final String WDIC_PLUGIN_DIR = "plugin";
    private static final String WDIC_GAIJI_DIR = "gaiji";
    private static final String WDIC_TABLE_DIR = "table";

    /**
     * ベースディレクトリ
     */
    private File basedir = null;

    /**
     * WDICグループリスト
     */
    private WdicGroupList groupList = null;
    /**
     * WDIC分類リスト
     */
    private WdicDirList dirList = null;
    /**
     * WDICマニュアル
     */
    private WdicMan manual = null;
    /**
     * プラグインマップ
     */
    private Map<String, Set<WdicItem>> pluginMap = null;
    /**
     * 外字マップ
     */
    private Map<String, String> gaijiMap = null;

    private WdicMenuNode wdicMenuNode;
    private WdicGraphicNode wdicGraphicNode;
    private WdicSoundNode wdicSoundNode;
    private WdicGaijiNode wdicGaijiNode;


    public WdicNode(File basedir) {
        this.basedir = basedir;
        File file = new File(basedir, "FILE.GL");
        this.groupList = new WdicGroupList(file);
        file = new File(basedir, "DIR.LST");
        this.dirList = new WdicDirList(file);
        file = new File(basedir, "WDICALL.MAN");
        this.manual = new WdicMan(file);
        this.pluginMap = groupList.getPluginMap();
        this.gaijiMap = new TreeMap<>();

        wdicMenuNode = new WdicMenuNode(this, pluginMap, manual, groupList, dirList);
        wdicGraphicNode = new WdicGraphicNode(this, basedir);
        wdicSoundNode = new WdicSoundNode(this, basedir);

        File dir = new File(basedir, WDIC_GAIJI_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            logger.error("failed to create directories: " + dir.getPath());
        }
        this.wdicGaijiNode = new WdicGaijiNode(dir, gaijiMap);
    }

    Set<String> getPluginMapKeySet() {
        return pluginMap.keySet();
    }

    void makeNodes(final Element subbook) {
        logger.info("create content node...");
        makeContentNode(subbook);
        logger.info("create graphic node...");
        wdicGraphicNode.makeGraphicNode(subbook);
        logger.info("create sound node...");
        wdicSoundNode.makeSoundNode(subbook);
        logger.info("create font node...");
        wdicGaijiNode.makeFontNode(subbook);
    }

    /**
     * メニューノードを作成します。
     *
     * @param content コンテントノード
     */
    public void makeMenuNode(final Element content) {
        Element menu = appendElement(content, "menu");
        wdicMenuNode.makeMenuNode(menu);
    }

    /**
     * 辞書データノードを作成します。
     *
     * @param subbook subbookノード
     */
    void makeContentNode(final Element subbook) {
        Element content = appendElement(subbook, "content");

        logger.info("create item node...");
        for (WdicGroup group : groupList.getGroups()) {
            for (Wdic dic : group.getWdics()) {
                for (WdicItem item : dic.getWdicItems()) {
                    makeItemNode(content, item);
                }
            }
        }
        makeItemNode(content);

        logger.info("create menu node...");
        makeMenuNode(content);

        logger.info("create copyright node...");
        _makeCopyrightNode(content);
    }

    /**
     * レイヤ要素を追加します。
     *
     * @param node レイヤ要素を追加するノード
     * @param id ID属性値
     * @return 追加されたレイヤ要素
     */
    Element _appendLayer(final Node node, final String id) {
        Element elem = appendElement(node, "layer");
        elem.setAttribute("id", id);
        return elem;
    }


    /**
     * 辞書項目ノードを作成します。
     *
     * @param content コンテントノード
     * @param item    辞書項目
     */
    void makeItemNode(final Element content, final WdicItem item) {
        String grpId = item.getWdic().getGroupId();
        String grpName = item.getWdic().getGroupName();
        String partName = item.getWdic().getPartName();
        String partId = item.getWdic().getPartId();

        String head = item.getHead();
        logger.debug("  [" + grpId + ":" + partId + "] " + head);
        Element itemElem = _appendItem(content, "WDIC:" + grpId + ":" + head);
        Element headElem = appendElement(itemElem, "head");
        _appendRawText(headElem, head + " 【" + grpName + "：" + partName + "】");
        boolean wordAvail = false;
        if (WordUtil.isValidWord(head)) {
            Element wordElem = appendElement(itemElem, "word");
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
                Element wordElem = appendElement(itemElem, "word");
                _appendRawText(wordElem, word);
                wordAvail = true;
            }
        }
        // 英字表記を検索語として登録
        Map<String, String> spellMap = item.getSpell();
        for (Map.Entry<String, String> entry : spellMap.entrySet()) {
            String str = WdicUtil.unescape(entry.getValue());
            if (StringUtils.isAsciiPrintable(str)) {
                int idx = str.indexOf(": ");
                if (idx > 0) {
                    // 略語
                    String ss = str.substring(0, idx).trim();
                    if (!head.equals(ss) && WordUtil.isValidWord(ss)) {
                        Element wordElem = appendElement(itemElem, "word");
                        _appendRawText(wordElem, ss);
                        wordAvail = true;
                    }
                    // 元の語形
                    str = str.substring(idx + 2).trim();
                }
                if (!head.equals(str) && WordUtil.isValidWord(str)) {
                    Element wordElem = appendElement(itemElem, "word");
                    _appendRawText(wordElem, str);
                    wordAvail = true;
                }
            }
        }

        if (!wordAvail) {
            logger.warn("word not defined: " + grpId + ":" + partId + ":" + head);
        }

        // 本文の登録
        Element bodyElem = appendElement(itemElem, "body");
        Element keyElem = appendElement(bodyElem, "key");
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
            Iterator<Map.Entry<String, String>> spellIt = spellMap.entrySet().iterator();
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
            StringBuilder buf = new StringBuilder();
            for (String s : speechList) {
                if (buf.length() == 0) {
                    buf.append("品詞：");
                } else {
                    buf.append(",");
                }
                buf.append(s);
            }
            _appendRawText(bodyElem, buf.toString());
            _appendNewLine(bodyElem);
        }

        // 内容
        _appendNewLine(bodyElem);
        Stack<Element> indentStack = new Stack<>();
        int curIndent = 0;
        int ignoreTabs = 0;
        int section = 0;
        int tableNum = 0;
        Element indentElem = appendElement(bodyElem, "indent");
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
                    indentElem = appendElement(indentElem, "indent");
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
                            Element nobr = appendElement(indentElem, "nobr");
                            _appendText(item, nobr, block.substring(3));
                        }
                        _appendNewLine(indentElem);
                    }
                    _appendNewLine(indentElem);
                } else if (block.startsWith(">>")) {
                    // 引用
                    Element indentElem2 = appendElement(indentElem, "indent");
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
                            Element indentElem2 = appendElement(indentElem, "indent");
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
                                indentElem2 = appendElement(indentElem, "indent");
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
                    wdicGraphicNode.addTableItem(name);
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
                        if (img != null) {
                            try {
                                BmpUtil.write(img, file);
                            } catch (IOException e) {
                                logger.error(e.getMessage(), e);
                                if (file.exists() && !file.delete()) {
                                    logger.error("failed to delete file: " + file.getPath());
                                }
                            } finally {
                                img.flush();
                            }
                            wdicGraphicNode.addTableItem(name);
                            Element elem = _appendDataReference(indentElem, file.getName(), "graphic");
                            _appendRawText(elem, "[表]");
                            _appendNewLine(indentElem);
                        }
                    }
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
     * @param item   辞書項目
     * @param elem   追加対象の要素
     * @param block  追加する内容
     * @param numMap インデント数と箇条書き数とのマップ
     * @param prefix プレフィックス
     */
    void _appendItemBodyBlock(final WdicItem item, final Element elem, final String block,
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
     * @param item  辞書項目
     * @param elem  追加対象の要素
     * @param block 追加する内容
     */
    void _appendItemLinkBlock(final WdicItem item, final Element elem, final String block) {
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
    void makeItemNode(final Element content) {
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
                    Element headElem = appendElement(itemElem, "head");
                    _appendRawText(headElem, name + " 【プラグイン】");
                    // ファイル名をキーワードとして登録
                    Element keywordElem = appendElement(itemElem, "keyword");
                    _appendRawText(keywordElem, name);

                    Element bodyElem = appendElement(itemElem, "body");
                    Element keyElem = appendElement(bodyElem, "key");
                    _appendRawText(keyElem, name);
                    _appendNewLine(bodyElem);
                    Element refElem = _appendDataReference(bodyElem, name, "graphic");
                    _appendRawText(refElem, "[図版]");
                    _appendNewLine(bodyElem);

                    // プラグインを参照している項目を列挙
                    for (WdicItem item : entry.getValue()) {
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
                    Element headElem = appendElement(itemElem, "head");
                    _appendRawText(headElem, name + " 【プラグイン】");
                    // ファイル名をキーワードとして登録
                    Element keywordElem = appendElement(itemElem, "keyword");
                    _appendRawText(keywordElem, name);

                    Element bodyElem = appendElement(itemElem, "body");
                    Element keyElem = appendElement(bodyElem, "key");
                    _appendRawText(keyElem, name);
                    _appendNewLine(bodyElem);
                    Element refElem = _appendDataReference(bodyElem, name, "sound");
                    _appendRawText(refElem, "[音声]");
                    _appendNewLine(bodyElem);

                    // プラグインを参照している項目を列挙
                    for (WdicItem item : entry.getValue()) {
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
                Element headElem = appendElement(itemElem, "head");
                _appendRawText(headElem, name + " 【プラグイン】");
                // ファイル名をキーワードとして登録
                Element keywordElem = appendElement(itemElem, "keyword");
                _appendRawText(keywordElem, name);

                Element bodyElem = appendElement(itemElem, "body");
                Element keyElem = appendElement(bodyElem, "key");
                _appendRawText(keyElem, name);
                _appendNewLine(bodyElem);

                // プラグインを参照している項目を列挙
                for (WdicItem item : entry.getValue()) {
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
                Element indentElem = appendElement(bodyElem, "indent");
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
        Element copyright = appendElement(content, "copyright");
        String[] line = manual.getCopyright();
        int len = line.length;
        for (int i = 0; i < len; i++) {
            _appendRawText(copyright, line[i]);
            _appendNewLine(copyright);
        }
    }

    /**
     * テキストノードを追加します。
     *
     * @param node テキストを追加するノード
     * @param str  文字列
     */
    void _appendRawText(final Node node, final String str) {
        if (str != null && str.trim().length() > 0) {
            String tmp = str.replace((char) 0x3099, (char) 0x309b)
                    .replace((char) 0x309a, (char) 0x309c);
            if (node != null) {
                Text text = node.getOwnerDocument().createTextNode(tmp);
                node.appendChild(text);
                wdicGaijiNode.checkCharacter(text);
            }
        }
    }

    /**
     * 要素を追加します。
     *
     * @param node 要素を追加するノード
     * @param tag  要素のタグ名称
     * @return 追加された要素
     */
    Element appendElement(final Node node, final String tag) {
        Element elem = node.getOwnerDocument().createElement(tag);
        return (Element) node.appendChild(elem);
    }

    /**
     * 改行要素を追加します。
     *
     * @param node 改行を追加するノード
     * @return 追加された改行要素
     */
    Element _appendNewLine(final Node node) {
        return appendElement(node, "br");
    }

    /**
     * 項目要素を追加します。
     *
     * @param node 項目要素を追加するノード
     * @param id   ID属性値
     * @return 追加された項目要素
     */
    private Element _appendItem(final Node node, final String id) {
        Element elem = appendElement(node, "item");
        elem.setAttribute("id", id);
        return elem;
    }

    /**
     * 参照要素を追加します。
     *
     * @param node 参照要素を追加するノード
     * @param id   ID属性値
     * @return 追加された参照要素
     */
    Element _appendIdReference(final Node node, final String id) {
        Element elem = appendElement(node, "ref");
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
        Element elem = appendElement(node, "ref");
        elem.setAttribute("data", data);
        elem.setAttribute("type", type);
        return elem;
    }

    /**
     * データ要素を追加します。
     *
     * @param node   データ要素を追加するノード
     * @param name   name属性値
     * @param src    src属性値
     * @param format format属性値
     * @return 追加されたデータ要素
     */
    Element _appendData(final Node node, final String name, final String src,
                        final String format) {
        Element elem = appendElement(node, "data");
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
     * @param str  文字列
     */
    void _appendText(final WdicItem item, final Node node, final String str) {
        _appendText(item, node, str, false);
    }

    /**
     * テキストノードを追加します。
     *
     * @param item      辞書項目
     * @param node      テキストを追加するノード
     * @param str       文字列
     * @param linkBlock リンク部の場合はtrue
     */
    void _appendText(final WdicItem item, final Node node, final String str,
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
                        Element elem = appendElement(node, "em");
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
                Element elem = appendElement(node, name);
                _appendText(item, elem, param.get(0), linkBlock);
            } else if ("ruby".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                _appendText(item, node, param.get(0), linkBlock);
                if (param.size() > 1) {
                    Element elem = appendElement(node, "sub");
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
                wdicGraphicNode.addGlyphItem(glyph);
                Element elem = _appendDataReference(node, "glyph-" + glyph, "inlineGraphic");
                _appendRawText(elem, "[グリフ:" + glyph + "]");
            } else if ("oline".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                String pstr = param.get(0);
                wdicGaijiNode.addOverLineGaijiFont(node, pstr);
           } else if ("uline".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                String pstr = param.get(0);
                wdicGaijiNode.addUnderLineGaijiFont(node, pstr);
            } else if ("sout".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                String pstr = param.get(0);
                wdicGaijiNode.addLineThroughGaijiFont(node, pstr);
            } else if ("date".equals(name) || "dt".equals(name)) {
                _appendRawText(node, buf.toString());
                buf.delete(0, buf.length());
                buf.append(param.get(0));
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

}
