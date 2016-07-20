package io.github.eb4j.xml2eb.converter.wdic;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.eb4j.xml2eb.CatalogInfo;
import io.github.eb4j.xml2eb.util.HexUtil;
import io.github.eb4j.xml2eb.util.XmlUtil;

/**
 * WDIC→XML変換クラス。
 *
 * @author Hisaya FUKUMOTO
 */
public class Wdic2Xml {

    /**
     * プロブラム名
     */
    private static final String PROGRAM = Wdic2Xml.class.getName();

    private static final String BOOK_XML = "book.xml";

    private static final String BOOK_TITLE = "通信用語の基礎知識";
    private static final String BOOK_DIR = "wdic";
    private static final String BOOK_TYPE =
            "0x" + HexUtil.toHexString(CatalogInfo.TYPE_GENERAL, 2);

    /**
     * ログ
     */
    private Logger logger = null;
    /**
     * ベースディレクトリ
     */
    private File basedir = null;

    /**
     * メインメソッド。
     *
     * @param args command line arguments.
     */
    public static void main(final String[] args) {
        if (args.length == 0) {
            System.out.println("java " + PROGRAM + " [wdic-directory]");
        } else {
            try {
                new Wdic2Xml(args[0]).convert();
            } catch (ParserConfigurationException | IOException e) {
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
     * @param basedir ベースディレクトリ
     */
    public Wdic2Xml(final File basedir) {
        super();
        logger = LoggerFactory.getLogger(getClass());
        this.basedir = basedir;
    }

    /**
     * 変換します。
     *
     * @throws ParserConfigurationException DocumentBuilderを生成できない場合
     * @throws IOException                  入出力エラーが発生した場合
     */
    public void convert() throws ParserConfigurationException, IOException {
        WdicNode wdicNode = new WdicNode(basedir);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();
        Element book = doc.createElement("book");
        doc.appendChild(book);
        Element subbook = wdicNode.appendElement(book, "subbook");
        subbook.setAttribute("title", BOOK_TITLE);
        subbook.setAttribute("dir", BOOK_DIR);
        subbook.setAttribute("type", BOOK_TYPE);
        wdicNode.makeNodes(subbook);

        File file = new File(basedir, BOOK_XML);
        logger.info("write file: " + file.getPath());
        XmlUtil.write(doc, file);
    }
}
// end of Wdic2Xml.java
