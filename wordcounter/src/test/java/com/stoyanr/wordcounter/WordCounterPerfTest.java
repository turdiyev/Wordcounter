/*
 * $Id: $
 *
 * Copyright 2012 Stoyan Rachev (stoyanr@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stoyanr.wordcounter;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.stoyanr.util.Logger;

@RunWith(Parameterized.class)
public class WordCounterPerfTest {

    private static final String[] VOCABULARY = { "one", "two", "three", "four", "five", "six",
        "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen" };

    private static final String DELIMS = WordCounter.DEFAULT_DELIMITERS;

    private static final String DIR = "words";
    private static final String FILE = "words.txt";

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { 1, 10000000 }, 
            { 100, 100000 }, 
        };
        // @formatter:on
        return asList(data);
    }

    private final int numFiles;
    private final int maxWords;

    private WordCounter counter;
    private Map<String, Integer> counts;
    private File tree;

    public WordCounterPerfTest(int numFiles, int maxWords) {
        this.numFiles = numFiles;
        this.maxWords = maxWords;
    }

    @Before
    public void setUp() throws Exception {
        Logger.level = Logger.Level.INFO;
        counter = new WordCounter();
        counts = new HashMap<>();
        tree = createTree(counts);
    }

    @Test
    public void test() throws Exception {
        testx(false);
        testx(true);
    }

    private void testx(boolean parallel) throws Exception {
        System.out.printf("Processing %d files (parallel: %b) ...\n", numFiles, parallel);
        long time0 = System.currentTimeMillis();
        Map<String, Integer> countsx = counter.countWords(tree, parallel);
        long time1 = System.currentTimeMillis();
        System.out.printf("Processed %d files in %d ms\n", numFiles, (time1 - time0));
        printCounts(countsx);
        TestUtils.assertEqualMaps(counts, countsx);
    }

    private File createTree(Map<String, Integer> counts) throws IOException {
        File dir = new File(DIR);
        TestUtils.deleteDir(dir);
        dir.mkdirs();
        for (int i = 0; i < numFiles; i++) {
            File dirx = new File(DIR + "/" + i);
            dirx.mkdirs();
            FileUtils.writeStringToFile(new File(DIR + "/" + i + "/" + FILE), createText(counts));
        }
        return dir;
    }

    private String createText(Map<String, Integer> counts) {
        int numWords = maxWords;
        StringBuilder sb = new StringBuilder(numWords * 10);
        for (int i = 0; i < numWords; i++) {
            int index = (int) (Math.random() * VOCABULARY.length);
            String word = VOCABULARY[index];
            sb.append(word);
            if (i < numWords - 1) {
                appendDelimiters(sb);
            }
            counts.put(word, (counts.containsKey(word)) ? counts.get(word) + 1 : 1);
        }
        return sb.toString();
    }

    private void appendDelimiters(StringBuilder sb) {
        int numDelims = (int) (Math.random() * 3) + 1;
        for (int i = 0; i < numDelims; i++) {
            int index = (int) (Math.random() * DELIMS.length());
            sb.append(DELIMS.charAt(index));
        }
    }

    private void printCounts(Map<String, Integer> counts) {
        if (Logger.isDebug()) {
            Main.printCounts(counts);
        }
    }

}
