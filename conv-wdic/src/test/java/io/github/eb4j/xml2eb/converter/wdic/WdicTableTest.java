package io.github.eb4j.xml2eb.converter.wdic;

import io.github.eb4j.xml2eb.util.BmpUtil;
import org.testng.annotations.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Test for table crator.
 * Created by miurahr on 16/07/16.
 */
public class WdicTableTest {

    WdicTable table;

    @Test(groups = "init")
    public void testCreateTable() throws Exception {
        File manFile = new File(this.getClass().getResource("/WDIC.MAN").getFile());
        WdicGroupList groupList = new WdicGroupList(manFile);
        String id= "WDIC";
        File file = new File(this.getClass().getResource("/WDIC.WLF").getFile());
        WdicGroup group = new WdicGroup(groupList, id, file);
        String part = "概要編";
        Wdic wdic = new Wdic(group, part, file);
        String head = "head";
        int idx = 0;
        WdicItem item = new WdicItem(wdic, head, idx);
        table = new WdicTable(item);
    }


    @Test(groups = "case1", dependsOnMethods = {"testCreateTable"})
    public void testAdd() throws Exception {
        List<String> src = new ArrayList<>();
        src.add("|= 名称       |解像度|<     |画素数\\br;(百万)|縦横比|画素数比率|<|<|<|<|<");
        src.add("|= ~          |横    |縦    |~   |~         |VGA  |QVGA  |QVGA+ |WQVGA |WQVGA+|HVGA");
        src.add("|| =<<[[QVGA]]|>>320 |>>240 |0.08|4\\ratio;3 |>>25%|=><‐ |>>93% |>>80% |>>75% |>>50%");
        src.add("|| =<<QVGA+   |>>345 |>>240 |0.08|          |>>27%|>>108%|=><‐ |>>86% |>>81% |>>54%");
        src.add("|| =<<WQVGA   |>>400 |>>240 |0.10|5\\ratio;3 |>>31%|>>125%|>>116%|=><‐ |>>94% |>>63%");
        src.add("|| =<<WQVGA+  |>>427 |>>240 |0.10|16\\ratio;9|>>33%|>>133%|>>124%|>>107%|=><‐ |>>67%");
        src.add("|| =<<[[HVGA]]|>>480 |>>320 |0.15|3\\ratio;2 |>>50%|>>200%|>>186%|>>160%|>>150%|=><‐");
        src.add("|| ~          |>>640 |>>240 |0.15|           |~    |~     |~     |~     |~     |~ ");
        for (String block: src) {
            table.add(block);
        }
    }

    @Test(groups = "case1", dependsOnMethods = {"testAdd"})
    public void testGetImage() throws Exception {
        File dir = Files.createTempDirectory("wdic").toFile();
        File file = new File(dir, "test.bmp");
        BufferedImage img = table.getImage();
        assertNotNull(img);
        if (img  != null) {
            try {
                BmpUtil.write(img, file);
            } catch (IOException e) {
                throw e;
            } finally {
                if (img != null) {
                    img.flush();
                }
            }
        }
    }
}