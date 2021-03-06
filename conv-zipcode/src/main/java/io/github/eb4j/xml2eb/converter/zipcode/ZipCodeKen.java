package io.github.eb4j.xml2eb.converter.zipcode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedHashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.eb4j.xml2eb.util.UnicodeUtil;

/**
 * 全国一括郵便番号クラス。
 *
 * @author Hisaya FUKUMOTO
 */
class ZipCodeKen {

    private static final String ENCODING = "MS932";

    /** ログ */
    private Logger _logger = null;
    /** ファイル */
    private File _file = null;
    /** 項目リスト */
    private List<Item> _itemList = new ArrayList<Item>();


    /**
     * コンストラクタ。
     *
     * @param file 全国一括郵便番号ファイル
     */
    ZipCodeKen(final File file) {
        super();
        _logger = LoggerFactory.getLogger(getClass());
        _file = file;
        _load();
    }

    /**
     * 最終更新日を返します。
     *
     * @return 最終更新日
     */
    Date getDate() {
        long time = _file.lastModified();
        return new Date(time);
    }

    /**
     * 郵便番号別項目マップを返します。
     *
     * @return 郵便番号をキーとする項目マップ
     */
    Map<String, List<Item>> getZipcodeMap() {
        Map<String, List<Item>> map = new TreeMap<>();
        for (Item item : _itemList) {
            String key = item.getZipcode();
            List<Item> list = map.get(key);
            if (list == null) {
                list = new ArrayList<>();
                map.put(key, list);
            }
            list.add(item);
        }
        return map;
    }

    /**
     * 住所別項目マップを返します。
     *
     * @return 住所をキーとする項目マップ
     */
    Map<String, Map<String, List<Item>>> getAddressMap() {
        Map<String, Map<String, List<Item>>> map = new LinkedHashMap<>();
        for (Item item : _itemList) {
            // 都道府県別
            String key1 = item.getPrefecture();
            Map<String, List<Item>> map1 = map.get(key1);
            if (map1 == null) {
                map1 = new LinkedHashMap<>();
                map.put(key1, map1);
            }
            // 市区町村別
            String key2 = item.getCity();
            List<Item> list = map1.get(key2);
            if (list == null) {
                list = new ArrayList<>();
                map1.put(key2, list);
            }
            list.add(item);
        }
        return map;
    }

    /**
     * ファイルを読み込みます。
     *
     */
    private void _load() {
        _logger.info("load file: " + _file.getPath());
        Charset cs = Charset.forName(ENCODING);
        try (LineNumberReader lnr = new LineNumberReader(new BufferedReader(new InputStreamReader(
                            new FileInputStream(_file), cs)))) {
            String line;
            Item item = null;
            while ((line = lnr.readLine()) != null) {
                line = UnicodeUtil.sanitizeUnicode(line);
                StrTokenizer st = StrTokenizer.getCSVInstance(line);
                int n = st.size();
                if (n != 15) {
                    _logger.error("unexpected token count: "
                                  + _file.getName()
                                  + "[" + lnr.getLineNumber() + "] " + n);
                    continue;
                }
                String[] tokens = st.getTokenArray();
                int empty = -1;
                for (int i = 0; i < n; i++) {
                    if (StringUtils.isBlank(tokens[i])) {
                        empty = i;
                        break;
                    }
                }
                if (empty != -1) {
                    _logger.warn("empty token found: "
                                 + _file.getName()
                                 + "[" + lnr.getLineNumber() + "] " + empty);
                    continue;
                }
                if (item != null && !item.isClosed()) {
                    item.appendArea(tokens[5], tokens[8]);
                } else {
                    item = new Item(tokens[0], tokens[2],
                                    tokens[3], tokens[4], tokens[5],
                                    tokens[6], tokens[7], tokens[8]);
                    _itemList.add(item);
                }
            }
            _logger.info("loaded " + _itemList.size() + " items");
        } catch (IOException e) {
            _logger.error(e.getMessage(), e);
        }
    }

    /**
     * 項目クラス。
     *
     */
    public static class Item {

        /** 全国地方公共団体コード */
        private String _code = null;
        /** 郵便番号 */
        private String _zipcode = null;
        /** 都道府県名 (カタカナ表記) */
        private String _kanaPrefecture = null;
        /** 都道府県名 */
        private String _prefecture = null;
        /** 市区町村名 (カタカナ表記) */
        private String _kanaCity = null;
        /** 市区町村名 */
        private String _city = null;
        /** 町域名 (カタカナ表記) */
        private String _kanaTown = null;
        /** 町域名 */
        private String _town = null;
        /** 小字名、丁目、番地等 (カタカナ表記) */
        private String _kanaArea = null;
        /** 小字名、丁目、番地等 */
        private String _area = null;
        /** 記載がない場合フラグ */
        private boolean _exception = false;
        /** データが完結しているかどうか */
        private boolean _closed = true;


        /**
         * コンストラクタ。
         *
         * @param code 全国地方公共団体コード
         * @param zipcode 郵便番号
         * @param kanaPrefecture 都道府県名 (カタカナ表記)
         * @param kanaCity 市区町村名 (カタカナ表記)
         * @param kanaTown 町域名 (カタカナ表記)
         * @param prefecture 都道府県名
         * @param city 市区町村名
         * @param town 町域名
         */
        Item(final String code, final String zipcode,
             final String kanaPrefecture, final String kanaCity, final String kanaTown,
             final String prefecture, final String city, final String town) {
            super();
            _code = code;
            _zipcode = zipcode.substring(0, 3) + "-" + zipcode.substring(3);

            _kanaPrefecture = ZipCodeUtil.toFullwidth(kanaPrefecture, prefecture);
            _prefecture = prefecture;

            _kanaCity = ZipCodeUtil.toFullwidth(kanaCity, city);
            _city = city;

            if ("以下に掲載がない場合".equals(town)) {
                _exception = true;
            } else if (town.endsWith("の次に番地がくる場合")) {
                _exception = true;
                _town = town;
            } else if (town.length() > 3
                       && (town.endsWith("市一円")
                           || town.endsWith("町一円")
                           || town.endsWith("村一円"))) {
                _exception = true;
                _town = town;
            } else {
                String kana = ZipCodeUtil.toFullwidth(kanaTown, town);
                int idx1 = kana.indexOf("\uff08");
                if (idx1 == -1) {
                    _kanaTown = kana;
                } else {
                    _kanaTown = kana.substring(0, idx1);
                    int idx2 = kana.indexOf("\uff09", idx1 + 1);
                    if (idx2 == -1) {
                        _closed = false;
                        _kanaArea = kana.substring(idx1 + 1);
                    } else {
                        _kanaArea = kana.substring(idx1 + 1, idx2);
                    }
                }

                idx1 = town.indexOf("\uff08");
                if (idx1 == -1) {
                    _town = town;
                } else {
                    _town = town.substring(0, idx1);
                    int idx2 = town.indexOf("\uff09", idx1 + 1);
                    if (idx2 == -1) {
                        _closed = false;
                        _area = town.substring(idx1 + 1);
                    } else {
                        _area = town.substring(idx1 + 1, idx2);
                    }
                }
            }
        }


        /**
         * 全国地方公共団体コードを返します。
         *
         * @return 全国地方公共団体コード
         */
        public String getCode() {
            return _code;
        }

        /**
         * 郵便番号を返します。
         *
         * @return 郵便番号
         */
        String getZipcode() {
            return _zipcode;
        }

        /**
         * カタカナ表記の都道府県名を返します。
         *
         * @return 都道府県名
         */
        String getKanaPrefecture() {
            return _kanaPrefecture;
        }

        /**
         * カタカナ表記の市区町村名を返します。
         *
         * @return 市区町村名
         */
        String getKanaCity() {
            return _kanaCity;
        }

        /**
         * カタカナ表記の町域名を返します。
         *
         * @return 町域名
         */
        String getKanaTown() {
            return _kanaTown;
        }

        /**
         * カタカナ表記の小字名、丁目、番地等を返します。
         *
         * @return 小字名、丁目、番地等
         */
        String getKanaArea() {
            return _kanaArea;
        }

        /**
         * 都道府県名を返します。
         *
         * @return 都道府県名
         */
        String getPrefecture() {
            return _prefecture;
        }

        /**
         * 市区町村名を返します。
         *
         * @return 市区町村名
         */
        String getCity() {
            return _city;
        }

        /**
         * 町域名を返します。
         *
         * @return 町域名
         */
        String getTown() {
            return _town;
        }

        /**
         * 小字名、丁目、番地等を返します。
         *
         * @return 小字名、丁目、番地等
         */
        String getArea() {
            return _area;
        }

        /**
         * 記載がない場合かどうかを返します。
         *
         * @return 記載がない場合はtrue、そうでない場合はfalse
         */
        boolean isException() {
            return _exception;
        }

        /**
         * データが完結しているかどうかを返します。
         *
         * @return 完結している場合はtrue、そうでない場合はfalse
         */
        boolean isClosed() {
            return _closed;
        }

        /**
         * 小字名、丁目、番地等を追加します。
         *
         * @param kanaStr 小字名、丁目、番地等 (カタカナ表記)
         * @param str 小字名、丁目、番地等
         */
        void appendArea(final String kanaStr, final String str) {
            String kana = ZipCodeUtil.toFullwidth(kanaStr, str);
            int idx = kana.indexOf("\uff09");
            if (idx == -1) {
                _kanaArea += kana;
            } else {
                _closed = true;
                _kanaArea += kana.substring(0, idx);
            }
            idx = str.indexOf("\uff09");
            if (idx == -1) {
                _area += str;
            } else {
                _closed = true;
                _area += str.substring(0, idx);
            }
        }
    }
}

// end of ZipCodeKen.java
