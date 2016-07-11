package io.github.eb4j.xml2eb.converter.wdic;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.CharUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.eb4j.xml2eb.util.FontUtil;
import io.github.eb4j.xml2eb.util.UnicodeUtil;

/**
 * ユーティリティクラス。
 *
 * @author Hisaya FUKUMOTO
 */
public final class WdicUtil {

    /** ログ */
    private static final Logger LOGGER = LoggerFactory.getLogger(WdicUtil.class);
    /** 文字参照マップ */
    private static final Map<String, String> CHAR_MAP = new HashMap<>();

    /** フォントマップ (Unicodeブロック別) */
    private static final Map<Character.UnicodeBlock, Font[]> UNICODE_BLOCK_HASH_MAP = new HashMap<>();
    /** フォントマップ (Unicodeコードポイント別) */
    private static final Map<Integer, Font> FONT_HASH_MAP = new HashMap<>();

    /** デフォルトフォント */
    private static Font[] DEFAULT_FONTS = null;
    /** Unicodeブロック未定義フォント */
    private static Font[] UNKNOWN_FONTS = null;
    /** 論理フォント */
    private static final Font[] LOGICAL_FONTS = {
        new Font(Font.SANS_SERIF, Font.PLAIN, 1),
        new Font(Font.SERIF, Font.PLAIN, 1),
        new Font(Font.MONOSPACED, Font.PLAIN, 1),
        new Font(Font.DIALOG, Font.PLAIN, 1),
        new Font(Font.DIALOG_INPUT, Font.PLAIN, 1)
    };

    private static final int TOP = 0;
    private static final int MIDDLE = 1;
    private static final int BOTTOM = 2;

    static {
        Map<String, Font> fontMap = new HashMap<>();
        int len = LOGICAL_FONTS.length;
        for (int i=0; i<len; i++) {
            fontMap.put(LOGICAL_FONTS[i].getFamily(Locale.ENGLISH), LOGICAL_FONTS[i]);
        }
        String ttfdirStr = System.getProperty("ttf.dir");
        if (StringUtils.isNotBlank(ttfdirStr)) {
            File ttfdir = new File(ttfdirStr);
            if (ttfdir.exists() && ttfdir.isDirectory()) {
                File[] files = ttfdir.listFiles();
                int n = ArrayUtils.getLength(files);
                for (int i = 0; i < n; i++) {
                    try {
                        Font font = Font.createFont(Font.TRUETYPE_FONT, files[i]);
                        LOGGER.info("load font: " + font.getName()
                                     + " (family:" + font.getFamily(Locale.ENGLISH) + ")");
                        fontMap.put(font.getFamily(Locale.ENGLISH), font);
                    } catch (FontFormatException ffe) {
                        LOGGER.info(ffe.getMessage());
                        LOGGER.info("Can not load font: " + files[i].getAbsolutePath());
                    } catch (IOException ioex) {
                        LOGGER.info(ioex.getMessage());
                        LOGGER.info("Can not load font: " + files[i].getAbsolutePath());
                    }
                }
            }
        }

        String propFile = System.getProperty("wdic-fonts.properties");
        if (StringUtils.isBlank(propFile)) {
          URL url = WdicUtil.class.getClassLoader().getResource("wdic-fonts.properties");
          propFile = url.getPath();
        }
        if (StringUtils.isNotBlank(propFile)) {
            File file = new File(propFile);
            FileInputStream fis = null;
            ExtendedProperties prop = new ExtendedProperties();
            try {
                fis = new FileInputStream(file);
                prop.load(fis, "UTF-8");
            } catch (IOException e) {
                LOGGER.warn(e.getMessage(), e);
            } finally {
                IOUtils.closeQuietly(fis);
            }
            ArrayList<Font> fontList = new ArrayList<Font>();
            Iterator<?> it = prop.getKeys();
            while (it.hasNext()) {
                String key = (String)it.next();
                String[] family = prop.getStringArray(key);
                int n = ArrayUtils.getLength(family);
                if (n == 0) {
                    continue;
                }
                fontList.clear();
                for (int i = 0; i < n; i++) {
                    Font font = fontMap.get(family[i]);
                    if (font == null) {
                        font = new Font(family[i], Font.PLAIN, 1);
                        if (font.getFamily(Locale.ENGLISH).equals(family[i])) {
                            fontMap.put(family[i], font);
                            fontList.add(font);
                        } else {
                            LOGGER.error("unknown font name: " + family[i]);
                        }
                    } else {
                        fontList.add(font);
                    }
                }
                Font[] fonts = fontList.toArray(new Font[fontList.size()]);
                if ("default".equals(key)) {
                    DEFAULT_FONTS = fonts;
                }else if ("UNKNOWN_UNICODE_BLOCK".equals(key)) {
                    UNKNOWN_FONTS = fonts;
                } else {
                    try {
                        Integer codePoint = Integer.decode(key);
                        FONT_HASH_MAP.put(codePoint, fonts[0]);
                    } catch (NumberFormatException e1) {
                        try {
                            Character.UnicodeBlock unicodeBlock =
                                Character.UnicodeBlock.forName(key);
                            UNICODE_BLOCK_HASH_MAP.put(unicodeBlock, fonts);
                        } catch (IllegalArgumentException e2) {
                            LOGGER.error("unknown UnicodeBlock: " + key);
                        }
                    }
                }
            }
        }

        propFile = System.getProperty("wdic-chars.properties");
        if (StringUtils.isBlank(propFile)) {
          URL url = WdicUtil.class.getClassLoader().getResource("ewdic-chars.properties");
          propFile = url.getPath();
        }
        if (StringUtils.isNotBlank(propFile)) {
            File file = new File(propFile);
            FileInputStream fis = null;
            ExtendedProperties prop = new ExtendedProperties();
            try {
                fis = new FileInputStream(file);
                prop.load(fis, "UTF-8");
            } catch (IOException e) {
                LOGGER.warn(e.getMessage(), e);
            } finally {
                IOUtils.closeQuietly(fis);
            }
            Iterator<?> it = prop.getKeys();
            while (it.hasNext()) {
                String key = (String)it.next();
                String value = prop.getString(key);
                if (StringUtils.isBlank(value)) {
                    continue;
                }
                try {
                    Integer codePoint = Integer.decode(value);
                    String str = String.valueOf(Character.toChars(codePoint));
                    CHAR_MAP.put(key, str);
                } catch (NumberFormatException e1) {
                    CHAR_MAP.put(key, value);
                }
            }
        }
    }


    /**
     * コンストラクタ。
     *
     */
    private WdicUtil() {
        super();
    }


    /**
     * 先頭のタブ数を返します。
     *
     * @param str 文字列
     * @return タブ数
     */
    public static int getTabCount(final String str) {
        int tab = 0;
        int len = str.length();
        for (int i = 0; i < len; i++) {
            if (str.charAt(i) != '\t') {
                break;
            }
            tab++;
        }
        return tab;
    }

    /**
     * 先頭のタブを削除します。
     *
     * @param str 文字列
     * @return 削除後の文字列
     */
    public static String deleteTab(final String str) {
        if (str == null) {
            return "";
        }
        return str.substring(getTabCount(str));
    }

    /**
     * 不要な文字を削除し、ユニコードを矯正します。
     *
     * @param str 文字列
     * @return 変換後の文字列
     */
    public static String sanitize(final String str) {
        String target = str;
        if (str != null && str.length() > 0) {
            switch (str.charAt(0)) {
                case '\ufeff': // ZWNBSP
                case '\u001a': // ^Z
                    target = str.substring(1);
                    break;
                default:
                    break;
            }
        }
        return UnicodeUtil.sanitizeUnicode(target);
    }

    /**
     * 指定された文字列が指定位置から最初に出現する位置を返します。
     *
     * @param str 文字列
     * @param searchStr 検索文字列
     * @param offset 検索開始位置
     * @return 文字位置
     */
    public static int indexOf(final String str, final String searchStr, final int offset) {
        int len = str.length();
        int off = offset;
        while (off < len) {
            int idx1 = str.indexOf(searchStr, off);
            if (idx1 <= off) {
                return idx1;
            }
            if (str.charAt(idx1 - 1) != '\\') {
                return idx1;
            } else {
                int cnt = 1;
                int idx2 = idx1 - 2;
                while (idx2 >= off) {
                    if (str.charAt(idx2) != '\\') {
                        break;
                    }
                    cnt++;
                    idx2--;
                }
                if ((cnt % 2) == 0) {
                    // バックスラッシュはエスケープされている
                    return idx1;
                }
            }
            off = idx1 + 1;
        }
        return -1;
    }


    /**
     * エスケープ文字を展開します。(見出し用)
     *
     * @param str 文字列
     * @return 展開後の文字列
     */
    public static String unescape(final String str) {
        StringBuilder buf = new StringBuilder();
        int len = str.length();
        for (int i = 0; i < len; i++) {
            int codePoint = str.codePointAt(i);
            if (Character.isSupplementaryCodePoint(codePoint)) {
                buf.appendCodePoint(codePoint);
                i = i + Character.charCount(codePoint) - 1;
                continue;
            }

            char ch = (char)codePoint;
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
                LOGGER.error("unexpected format: " + str);
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
            String name = null;
            ArrayList<String> param = new ArrayList<String>();
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
                int idx2 = -1;
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
                String[] arg = ref.substring(sep2+1).split(":");
                int n = arg.length;
                for (int j=0; j<n; j++) {
                    param.add(arg[j]);
                }
            }

            if ("x".equals(name)) {
                String code = param.get(0);
                try {
                    codePoint = Integer.parseInt(code, 16);
                    buf.appendCodePoint(codePoint);
                } catch (NumberFormatException e) {
                    LOGGER.error("unknown character code: " + code);
                }
            } else {
                if (!"sup".equals(name) && !"sub".equals(name)) {
                    LOGGER.error("unknown function name: " + name);
                }
                buf.append(unescape(param.get(0)));
            }
        }
        return buf.toString();
    }

    /**
     * 文字実体参照を返します。
     *
     * @param name 参照名称
     * @return 文字
     */
    public static String getCharacter(final String name) {
        String ch = CHAR_MAP.get(name);
        if (ch != null) {
            return ch;
        }
        LOGGER.error("unknown character reference: " + name);
        return name;
    }

    /**
     * カタカナ表音拡張文字をカタカナに変換します。
     *
     * @param codePoint カタカナ表音拡張文字
     * @return カタカナ
     */
    public static int toLargeKatakana(final int codePoint) {
        int code = codePoint;
        switch (code) {
            case '\u31f0': // KU
                code = '\u30af';
                break;
            case '\u31f1': // SI
                code = '\u30b7';
                break;
            case '\u31f2': // SU
                code = '\u30b9';
                break;
            case '\u31f3': // TO
                code = '\u30c8';
                break;
            case '\u31f4': // NU
                code = '\u30cc';
                break;
            case '\u31f5': // HA
                code = '\u30cf';
                break;
            case '\u31f6': // HI
                code = '\u30d2';
                break;
            case '\u31f7': // HU
                code = '\u30d5';
                break;
            case '\u31f8': // HE
                code = '\u30d8';
                break;
            case '\u31f9': // HO
                code = '\u30db';
                break;
            case '\u31fa': // MU
                code = '\u30e0';
                break;
            case '\u31fb': // RA
                code = '\u30e9';
                break;
            case '\u31fc': // RI
                code = '\u30ea';
                break;
            case '\u31fd': // RU
                code = '\u30eb';
                break;
            case '\u31fe': // RE
                code = '\u30ec';
                break;
            case '\u31ff': // RO
                code = '\u30ed';
                break;
            default:
                break;
        }
        return code;
    }

    /**
     * 指定された文字列をイメージに変換します。
     *
     * @param str 文字列
     * @return イメージ
     */
    public static BufferedImage toImage(final String str) {
        Font font = getFont(str.codePointAt(0));
        return FontUtil.stringToImage(str, 16, font);
    }

    /**
     * 指定された文字をイメージに変換します。
     *
     * @param codePoint Unicodeコードポイント
     * @return イメージ
     */
    public static BufferedImage toImage(final int codePoint) {
        int cp = codePoint;
        String type = FontUtil.getFontType(cp);
        Font font = getFont(cp);
        BufferedImage img = null;
        if (font.canDisplay(cp)) {
            if ("narrow".equals(type)) {
                img = FontUtil.charToImage(cp, 8, 16, font);
            } else {
                img = FontUtil.charToImage(cp, 16, 16, font);
            }
        } else {
            Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.of(cp);
            if (Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS.equals(unicodeBlock)) {
                // 通常のカタカナを小さくして描画
                cp = toLargeKatakana(cp);
                font = getFont(cp);
                img = FontUtil.smallCharToImage(cp, 8, 16, font);
            } else {
                // 表示できない文字は'?'を描画
                String code = "U+" + toHexString(cp);
                if (unicodeBlock == null) {
                    LOGGER.warn("unavailable display font: [" + code + "]"
                                 + " UNKNOWN_UNICODE_BLOCK");
                } else {
                    LOGGER.warn("unavailable display font: [" + code + "]"
                                 + " " + unicodeBlock.toString());
                }
                cp = '?';
                font = getFont(cp);
                if ("narrow".equals(type)) {
                    img = FontUtil.charToImage(cp, 8, 16, font);
                } else {
                    img = FontUtil.charToImage(cp, 16, 16, font);
                }
            }
        }
        return img;
    }

    /**
     * 指定された文字を上線付きでイメージに変換します。
     *
     * @param codePoint Unicodeコードポイント
     * @return イメージ
     */
    public static BufferedImage toOverLineImage(final int codePoint) {
        return toLineImage(codePoint, TOP);
    }

    /**
     * 指定された文字を下線付きでイメージに変換します。
     *
     * @param codePoint Unicodeコードポイント
     * @return イメージ
     */
    public static BufferedImage toUnderLineImage(final int codePoint) {
        return toLineImage(codePoint, BOTTOM);
    }

    /**
     * 指定された文字を打ち消し線付きでイメージに変換します。
     *
     * @param codePoint Unicodeコードポイント
     * @return イメージ
     */
    public static BufferedImage toLineThroughImage(final int codePoint) {
        return toLineImage(codePoint, MIDDLE);
    }

    /**
     * 指定された文字を線付きでイメージに変換します。
     *
     * @param codePoint Unicodeコードポイント
     * @param pos 線の位置
     * @return イメージ
     */
    private static BufferedImage toLineImage(final int codePoint, final int pos) {
        BufferedImage img = toImage(codePoint);
        Graphics2D g2 = img.createGraphics();
        int w = img.getWidth();
        int h = img.getHeight();
        g2.setColor(Color.BLACK);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        switch (pos) {
            case TOP:
                g2.drawLine(0, 1, w, 1);
                break;
            case MIDDLE:
                g2.drawLine(0, h / 2, w, h / 2);
                break;
            case BOTTOM:
                g2.drawLine(0, h - 2, w, h - 2);
                break;
            default:
                break;
        }
        g2.dispose();
        return img;
    }

    /**
     * フォントを返します。
     *
     * @param codePoint Unicodeコードポイント
     * @return フォント
     */
    public static Font getFont(final int codePoint) {
        // コード指定フォント
        Font font = FONT_HASH_MAP.get(codePoint);
        if (font != null && font.canDisplay(codePoint)) {
            return font;
        }

        // ブロック指定フォント
        Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.of(codePoint);
        Font[] fonts = null;
        if (unicodeBlock == null) {
            // Unicodeブロック未定義フォントから検索
            fonts = UNKNOWN_FONTS;
        } else {
            fonts = UNICODE_BLOCK_HASH_MAP.get(unicodeBlock);
        }
        int len = ArrayUtils.getLength(fonts);
        for (int i = 0; i < len; i++) {
            if (fonts[i].canDisplay(codePoint)) {
                return fonts[i];
            }
        }

        String code = "U+" + toHexString(codePoint);
        if (unicodeBlock == null) {
            LOGGER.info("undefined font: [" + code + "]"
                         + " UNKNOWN_UNICODE_BLOCK");
        } else {
            LOGGER.info("undefined font: [" + code + "]"
                         + " " + unicodeBlock.toString());
        }

        // デフォルトフォントから検索
        len = ArrayUtils.getLength(DEFAULT_FONTS);
        for (int i = 0; i < len; i++) {
            if (DEFAULT_FONTS[i].canDisplay(codePoint)) {
                return DEFAULT_FONTS[i];
            }
        }
        // 論理フォントから検索
        len = LOGICAL_FONTS.length;
        for (int i = 0; i < len; i++) {
            if (LOGICAL_FONTS[i].canDisplay(codePoint)) {
                return LOGICAL_FONTS[i];
            }
        }
        return LOGICAL_FONTS[0];
    }

    protected static String toHexString(final int x) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(x);
        return Hex.encodeHexString(buffer.array());
    }
}

// end of WdicUtil.java
