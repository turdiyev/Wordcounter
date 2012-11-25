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

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.stoyanr.util.Logger;
import com.stoyanr.wordcounter.WordCountAnalyzer;

@RunWith(Parameterized.class)
public class WordCountAnalyzerPerfTest {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_";
    
    private static final int MAX_LENGTH = 12;

    @Parameters
    public static Collection<Object[]> data() {
        // @formatter:off
        Object[][] data = new Object[][] { 
            { 2000000, 1000000000, 10 }, 
        };
        // @formatter:on
        return asList(data);
    }

    private final int numWords;
    private final int maxCount;
    private final int number;

    private WordCountAnalyzer analyzer;
    private Map<String, Integer> counts;
    private SortedMap<Integer, Set<String>> sorted;

    public WordCountAnalyzerPerfTest(int numWords, int maxCount, int number) {
        this.numWords = numWords;
        this.maxCount = maxCount;
        this.number = number;
    }

    @Before
    public void setUp() {
        Logger.level = Logger.Level.INFO;
        analyzer = new WordCountAnalyzer();
        counts = createCounts();
        sorted = getSorted(counts);
    }

    @Test
    public void test() throws Exception {
        testx(false);
        testx(true);
    }

    private void testx(boolean parallel) throws Exception {
        System.out.printf("Processing %d words (parallel: %b) ...\n", counts.size(), parallel);
        long time0 = System.currentTimeMillis();
        SortedMap<Integer, Set<String>> sortedx = analyzer.findTop(counts, number, true, parallel);
        long time1 = System.currentTimeMillis();
        System.out.printf("Analyzed %d words in %d ms\n", counts.size(), (time1 - time0));
        printSorted(sortedx);
        TestUtils.assertEqualSortedMaps(sorted, sortedx);
    }
    
    private Map<String, Integer> createCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < numWords; i++) {
            counts.put(getRandomWord(), getRandomCount());
        }
        return counts;
    }

    private String getRandomWord() {
        StringBuilder sb = new StringBuilder();
        int length = (int) (Math.random() * MAX_LENGTH) + 1;
        for (int j = 0; j < length; j++) {
            int index = (int) (Math.random() * ALPHABET.length());
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    private int getRandomCount() {
        return (int) (Math.random() * maxCount);
    }

    private SortedMap<Integer, Set<String>> getSorted(Map<String, Integer> counts) {
        SortedMap<Integer, Set<String>> sorted = new TreeMap<>(comparator());
        for (Entry<String, Integer> e : counts.entrySet()) {
            String word = e.getKey();
            int count = e.getValue();
            if (sorted.containsKey(count)) {
                sorted.get(count).add(word);
            } else {
                Set<String> set = new HashSet<>();
                set.add(word);
                sorted.put(count, set);
            }
        }
        return TestUtils.getHead(sorted, number);
    }

    private void printSorted(SortedMap<Integer, Set<String>> sorted) {
        if (Logger.isDebug()) {
            Main.printSorted(sorted, number, true);
        }
    }

    private static Comparator<Integer> comparator() {
        return (x, y) -> (y - x);
    }
}
