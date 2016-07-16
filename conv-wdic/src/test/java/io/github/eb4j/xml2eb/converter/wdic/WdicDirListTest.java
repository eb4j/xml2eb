package io.github.eb4j.xml2eb.converter.wdic;

import org.testng.annotations.Test;

import java.io.File;
import java.net.URL;
import java.util.LinkedHashMap;

import static org.testng.Assert.*;

/**
 * Test of WDicDirList
 * Created by miurahr on 16/07/16.
 */
public class WdicDirListTest {
    @Test
    public void testGetName() throws Exception {
        File file = new File(this.getClass().getResource("/WDIC.WLF").getFile());
        WdicDirList list = new WdicDirList(file);
        assertNotNull(list);
        String key = "CTIF.DV6";
        String expected = "テストデータ";
        assertEquals(list.getName(key), expected);
    }

}