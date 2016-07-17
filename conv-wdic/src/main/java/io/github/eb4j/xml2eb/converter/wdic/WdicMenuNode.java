package io.github.eb4j.xml2eb.converter.wdic;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Menu Node creation class.
 *
 * Created by miurahr on 16/07/17.
 */
public class WdicMenuNode {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private WdicNode wdicNode;
    private Map<String, Set<WdicItem>> pluginMap;
    private WdicMan manual;
    private WdicGroupList groupList;
    private WdicDirList dirList = null;

    /**
     * Genrate menu node.
     * @param node to add menu as sub node.
     * @param pmap pluginMap.
     * @param manual manual file.
     * @param list group list.
     * @param dir directory list.
     */
    public WdicMenuNode(final WdicNode node, final Map<String, Set<WdicItem>> pmap,
                        final WdicMan manual, final WdicGroupList list, final WdicDirList dir) {
        wdicNode = node;
        this.pluginMap = pmap;
        this.manual = manual;
        this.groupList = list;
        this.dirList = dir;
    }

    /**
     * メニューノードを作成します。
     *
     * @param menu メニューノード
     */
    public void makeMenuNode(final Element menu) {
        Element layerElem = _appendLayer(menu, "MENU:top");

        Element refElem = wdicNode.appendIdReference(layerElem, "MENU:manual");
        String title = groupList.getName() + " " + groupList.getEdition();
        wdicNode.appendRawText(refElem, title);
        wdicNode.appendNewLine(layerElem);

        refElem = wdicNode.appendIdReference(layerElem, "MENU:bib");
        wdicNode.appendRawText(refElem, "基礎文献");
        wdicNode.appendNewLine(layerElem);

        refElem = wdicNode.appendIdReference(layerElem, "DIR:/");
        wdicNode.appendRawText(refElem, "分類別収録語一覧");
        wdicNode.appendNewLine(layerElem);

        refElem = wdicNode.appendIdReference(layerElem, "MENU:group");
        wdicNode.appendRawText(refElem, "グループ別収録語一覧");
        wdicNode.appendNewLine(layerElem);

        refElem = wdicNode.appendIdReference(layerElem, "MENU:plugin");
        wdicNode.appendRawText(refElem, "グループ別プラグイン一覧");
        wdicNode.appendNewLine(layerElem);

        refElem = wdicNode.appendIdReference(layerElem, "MENU:image");
        wdicNode.appendRawText(refElem, "画像プラグイン一覧");
        wdicNode.appendNewLine(layerElem);

        refElem = wdicNode.appendIdReference(layerElem, "MENU:sound");
        wdicNode.appendRawText(refElem, "音声プラグイン一覧");
        wdicNode.appendNewLine(layerElem);

        refElem = wdicNode.appendIdReference(layerElem, "MENU:text");
        wdicNode.appendRawText(refElem, "文書プラグイン一覧");
        wdicNode.appendNewLine(layerElem);

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
            Element refElem = wdicNode.appendIdReference(layerElem, "MENU:manual:" + sec[i]);
            wdicNode.appendRawText(refElem, sec[i]);
            wdicNode.appendNewLine(layerElem);
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
        Element keyElem = wdicNode.appendElement(layerElem, "key");
        wdicNode.appendRawText(keyElem, sec);

        Element indent1Elem = wdicNode.appendElement(layerElem, "indent");
        Element indent2Elem = null;
        Element indent3Elem = null;
        Element indentElem = indent1Elem;
        for (String str : manual.getContents(sec)) {
            if (str.length() > 0) {
                if (str.startsWith("\t")) {
                    if (indent2Elem == null) {
                        indent2Elem = wdicNode.appendElement(indent1Elem, "indent");
                    }
                    if (str.startsWith("\t\t")) {
                        if (indent3Elem == null) {
                            indent3Elem = wdicNode.appendElement(indent2Elem, "indent");
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
                wdicNode.appendRawText(indentElem, str);
            }
            wdicNode.appendNewLine(indentElem);
        }

        wdicNode.appendNewLine(layerElem);

        if (prev != null) {
            wdicNode.appendRawText(layerElem, "\u2190 ");
            Element refElem = wdicNode.appendIdReference(layerElem, "MENU:manual:" + prev);
            wdicNode.appendRawText(refElem, prev);
            wdicNode.appendRawText(layerElem, " | ");
        }
        if (next != null) {
            wdicNode.appendRawText(layerElem, "\u2192 ");
            Element refElem = wdicNode.appendIdReference(layerElem, "MENU:manual:" + next);
            wdicNode.appendRawText(refElem, next);
            wdicNode.appendRawText(layerElem, " | ");
        }
        wdicNode.appendRawText(layerElem, "\u2191 ");
        String title = groupList.getName() + " " + groupList.getEdition();
        Element refElem = wdicNode.appendIdReference(layerElem, "MENU:manual");
        wdicNode.appendRawText(refElem, title);
        wdicNode.appendNewLine(layerElem);
    }

    /**
     * 基礎文献メニュー階層を作成します。
     *
     * @param menu メニューノード
     */
    private void _createBibliographyLayer(final Element menu) {
        Element layerElem = _appendLayer(menu, "MENU:bib");
        Collection<WdicGroup> groups = groupList.getGroups();
        for (WdicGroup group : groups) {
            Element refElem = wdicNode.appendIdReference(layerElem, "MENU:bib:"
                    + group.getGroupId());
            wdicNode.appendRawText(refElem, group.getGroupName() + "用語の基礎知識");
            wdicNode.appendNewLine(layerElem);
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
        Element keyElem = wdicNode.appendElement(layerElem, "key");
        wdicNode.appendRawText(keyElem, group.getGroupName() + "用語の基礎知識");
        Element indentElem = wdicNode.appendElement(layerElem, "indent");
        for (String str: group.getWdicBib().getBibliography()) {
            if (str.length() > 0) {
                wdicNode.appendRawText(indentElem, str);
            }
            wdicNode.appendNewLine(indentElem);
        }
    }

    /**
     * 分類一覧メニュー階層を作成します。
     *
     * @param menu メニューノード
     */
    private void _createDirectoryLayer(final Element menu) {
        Element layerElem = _appendLayer(menu, "DIR:/");
        for (String dir: dirList.getChildren("/")) {
            wdicNode.appendRawText(layerElem, "\u21d2 ");
            Element refElem = wdicNode.appendIdReference(layerElem, "DIR:" + dir);
            wdicNode.appendRawText(refElem, dirList.getName(dir));
            wdicNode.appendNewLine(layerElem);
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
        Element refElem = wdicNode.appendIdReference(layerElem, "DIR:/");
        wdicNode.appendRawText(refElem, "分類");
        String[] dirs;
        if (dir.startsWith("/")) {
            dirs = dir.substring(1).split("/");
        } else {
            dirs = dir.split("/");
        }
        String key = "";
        int len = dirs.length;
        for (int i = 0; i < len - 1; i++) {
            wdicNode.appendRawText(layerElem, " > ");
            key += "/" + dirs[i];
            refElem = wdicNode.appendIdReference(layerElem, "DIR:" + key);
            wdicNode.appendRawText(refElem, dirList.getName(key));
        }
        wdicNode.appendRawText(layerElem, " > " + dirList.getName(dir));
        wdicNode.appendNewLine(layerElem);

        List<String> children = dirList.getChildren(dir);
        len = children.size();
        int cnt = len;
        for (int i = 0; i < len; i++) {
            String child = children.get(i);
            wdicNode.appendRawText(layerElem, "\u21d2 ");
            if (dirList.hasAlias(child)) {
                String alias = dirList.getAlias(child);
                refElem = wdicNode.appendIdReference(layerElem, "DIR:" + alias);
                wdicNode.appendRawText(refElem, dirList.getName(child) + "@");
                wdicNode.appendNewLine(layerElem);
            } else {
                refElem = wdicNode.appendIdReference(layerElem, "DIR:" + child);
                wdicNode.appendRawText(refElem, dirList.getName(child));
                wdicNode.appendNewLine(layerElem);
                _createDirectoryLayer(menu, child);
            }
        }

        List<WdicItem> items = groupList.getWdicItem(dir);
        len = items.size();
        cnt += len;
        for (int i = 0; i < len; i++) {
            WdicItem item = items.get(i);
            wdicNode.appendRawText(layerElem, "\u2192 ");
            String head = item.getHead();
            String grpId = item.getWdic().getGroupId();
            String id = "WDIC:" + grpId + ":" + head;
            refElem = wdicNode.appendIdReference(layerElem, id);
            String gname = item.getWdic().getGroupName();
            String part = item.getWdic().getPartName();
            String title = head + " 《" + gname + "：" + part + "》";
            wdicNode.appendRawText(refElem, title);
            wdicNode.appendNewLine(layerElem);
        }

        if (cnt == 0) {
            wdicNode.appendRawText(layerElem, "(該当単語なし)");
            wdicNode.appendNewLine(layerElem);
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
        for (WdicGroup group : groupList.getGroups()) {
            Element refElem = wdicNode.appendIdReference(layerElem, id + ":" + group.getGroupId());
            wdicNode.appendRawText(refElem, MessageFormat.format("{0}用語の基礎知識",
                    group.getGroupName()));
            wdicNode.appendNewLine(layerElem);
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
        Element refElem = wdicNode.appendIdReference(layerElem, "MENU:group");
        wdicNode.appendRawText(refElem, "グループ");
        wdicNode.appendRawText(layerElem, " > " + group.getGroupName());
        wdicNode.appendNewLine(layerElem);
        for (Wdic wdic : group.getWdics()) {
            String name = wdic.getPartName() + "編";
            refElem = wdicNode.appendIdReference(layerElem, id + ":" + wdic.getPartId());
            wdicNode.appendRawText(refElem, name);
            wdicNode.appendNewLine(layerElem);
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
        Element refElem = wdicNode.appendIdReference(layerElem, "MENU:group");
        wdicNode.appendRawText(refElem, "グループ");
        wdicNode.appendRawText(layerElem, " > ");
        refElem = wdicNode.appendIdReference(layerElem, "MENU:group:" + grpId);
        wdicNode.appendRawText(refElem, wdic.getGroupName());
        wdicNode.appendRawText(layerElem, " > " + wdic.getPartName() + "編");
        wdicNode.appendNewLine(layerElem);
        for (WdicItem item : wdic.getWdicItems()) {
            if (item.isAlias()) {
                String name = item.getHead();
                wdicNode.appendRawText(layerElem, name);
                List<String> yomiList = item.getYomi();
                if (yomiList.isEmpty()) {
                    logger.info("yomi not defined: " + grpId + ":" + partId + ":" + item.getHead());
                } else {
                    String yomi = yomiList.get(0);
                    wdicNode.appendText(item, layerElem, " [" + yomi + "]");
                }
                wdicNode.appendRawText(layerElem, " \u21d2 ");
                name = item.getRealName();
                String refid = WdicUtil.unescape(name);
                id = "WDIC:" + grpId + ":" + refid;
                refElem = wdicNode.appendIdReference(layerElem, id);
                wdicNode.appendText(item, refElem, name);
                wdicNode.appendNewLine(layerElem);
            } else {
                String name = item.getHead();
                id = "WDIC:" + grpId + ":" + name;
                refElem = wdicNode.appendIdReference(layerElem, id);
                wdicNode.appendRawText(refElem, name);
                List<String> yomiList = item.getYomi();
                if (yomiList.isEmpty()) {
                    logger.info("yomi not defined: " + grpId + ":" + partId + ":" + item.getHead());
                } else {
                    String yomi = yomiList.get(0);
                    wdicNode.appendText(item, layerElem, " [" + yomi + "]");
                }
                wdicNode.appendNewLine(layerElem);
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
        for (WdicGroup group: groupList.getGroups()) {
            Element refElem = wdicNode.appendIdReference(layerElem, id + ":" + group.getGroupId());
            wdicNode.appendRawText(refElem, group.getGroupName() + "用語の基礎知識");
            wdicNode.appendNewLine(layerElem);
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
        Element refElem = wdicNode.appendIdReference(layerElem, "MENU:plugin");
        wdicNode.appendRawText(refElem, "グループ");
        wdicNode.appendRawText(layerElem, " > " + group.getGroupName());
        wdicNode.appendNewLine(layerElem);

        pluginMap.entrySet().stream()
            .filter(entry -> entry.getValue().stream()
                    .anyMatch(v -> group.equals(v.getWdic().getGroup())))
            .forEach(entry -> {
                String name = entry.getKey();
                wdicNode.appendRawText(wdicNode.appendIdReference(layerElem, "PLUGIN:" + name),
                        name);
                wdicNode.appendNewLine(layerElem);
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
                    Element refElem = wdicNode.appendIdReference(layerElem, "PLUGIN:" + name);
                    wdicNode.appendRawText(refElem, name);
                    wdicNode.appendNewLine(layerElem);
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
                    Element refElem = wdicNode.appendIdReference(layerElem, "PLUGIN:" + name);
                    wdicNode.appendRawText(refElem, name);
                    wdicNode.appendNewLine(layerElem);
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
                    Element refElem = wdicNode.appendIdReference(layerElem, "PLUGIN:" + name);
                    wdicNode.appendRawText(refElem, name);
                    wdicNode.appendNewLine(layerElem);
                });
    }

    /**
     * レイヤ要素を追加します。
     *
     * @param node レイヤ要素を追加するノード
     * @param id ID属性値
     * @return 追加されたレイヤ要素
     */
    private Element _appendLayer(final Node node, final String id) {
        Element elem = wdicNode.appendElement(node, "layer");
        elem.setAttribute("id", id);
        return elem;
    }

}
