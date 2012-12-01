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

import static com.stoyanr.wordcounter.WordUtils.countWords;
import static com.stoyanr.wordcounter.WordUtils.getEndWordIndex;
import static com.stoyanr.util.FileUtils.readFileToString;
import static com.stoyanr.util.FileUtils.readFileAsync;

import com.stoyanr.util.CharPredicate;
import com.stoyanr.util.FileUtils;
import com.stoyanr.util.ProducerConsumerExecutor;
import com.stoyanr.util.ProducerConsumerExecutor.Putter;
import com.stoyanr.util.ProducerConsumerExecutor.Taker;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class WordCounter {

    private static final int MAX_ST_SIZE = 1024 * 1024;
    private static final EnumSet<FileVisitOption> OPTS = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
    
    private final Path path;
    private final CharPredicate isWordChar;
    private final boolean par;
    private final int parLevel;
    
    public WordCounter(Path path, CharPredicate isWordChar, boolean par) {
        this(path, isWordChar, par, ProducerConsumerExecutor.DEFAULT_PAR_LEVEL);
    }

    public WordCounter(Path path, CharPredicate isWordChar, boolean par, int parLevel) {
        if (path == null || !Files.exists(path)) {
            throw new IllegalArgumentException("Path is null or doesn't exist.");
        }
        if (isWordChar == null) {
            throw new IllegalArgumentException("Predicate is null.");
        }
        this.path = path;
        this.isWordChar = isWordChar;
        this.par = par;
        this.parLevel = parLevel;
    }

    public WordCounts count() throws IOException {
        return (par) ? countPar() : countSer();
    }

    private WordCounts countSer() throws IOException {
        final WordCounts wc;
        if (Files.isDirectory(path)) {
            wc = new WordCounts();
            FileVisitor<Path> visitor = new WordCounterFileVisitor(
                (file) -> wc.add(countWords(readFileToString(file), isWordChar)));
            Files.walkFileTree(path, OPTS, Integer.MAX_VALUE, visitor);
        } else {
            wc = countWords(readFileToString(path), isWordChar);
        }
        return wc;
    }

    private WordCounts countPar() {
        WordCounts wc = new WordCounts(parLevel);
        ProducerConsumerExecutor<Path, String> executor = new ProducerConsumerExecutor<Path, String>(
            (putter) -> collect(putter), 
            (taker, putter) -> read(taker, putter),
            (taker) -> count(wc, taker), parLevel);
        executor.execute();
        executor.finish();
        return wc;
    }

    private void collect(Putter<Path> putter) {
        try {
            if (Files.isDirectory(path)) {
                FileVisitor<Path> visitor = new WordCounterFileVisitor(
                    (file) -> collect(file, putter));
                Files.walkFileTree(path, OPTS, Integer.MAX_VALUE, visitor);
            } else {
                collect(path, putter);
            }
        } catch (IOException e) {
            throw new WordCounterException(String.format("Can't walk directory tree %s: %s", 
                path.toString(), e.getMessage()), e);
        }
    }
    
    private void collect(Path file, Putter<Path> putter) {
        try {
            putter.put(file);
        } catch (InterruptedException e) {
        }
    }

    private void read(Taker<Path> taker, Putter<String> putter) {
        boolean finished = false;
        while (!finished) {
            Path file = null;
            try {
                file = taker.take();
                if (file != null) {
                    readFile(file, putter);
                } else {
                    finished = true;
                }
            } catch (InterruptedException e) {
                finished = true;
            } catch (IOException e) {
                throw new WordCounterException(String.format("Can't read file %s: %s", 
                    file.toString(), e.getMessage()), e);
            }
        }
    }

    private void readFile(Path file, Putter<String> putter) throws IOException {
        try {
            if (Files.size(file) <= MAX_ST_SIZE) {
                String text = FileUtils.readFileToString(file);
                putter.put(text);
            } else {
                readFileAsync(file, (String text, String state) -> { 
                    return putText(text, state, putter); 
                });
            }
        } catch (InterruptedException e) {
        }
    }

    private String putText(String text, String state, Putter<String> putter) throws InterruptedException {
        int ei = getEndWordIndex(text, isWordChar);
        String rem = (state != null) ? state : "";
        String textx = rem + text.substring(0, ei);
        rem = text.substring(ei);
        putter.put(textx);
        return rem;
    }
    
    private void count(WordCounts counts, Taker<String> taker) {
        boolean finished = false;
        while (!finished) {
            try {
                String text = taker.take();
                if (text != null) {
                    counts.add(countWords(text, isWordChar));
                } else {
                    finished = true;
                }
            } catch (InterruptedException e) {
                finished = true;
            }
        }
    }
    
    final static class WordCounterFileVisitor implements FileVisitor<Path> {
    
        interface FileProcessor {
            void process(Path file) throws IOException;
        }

        private final FileProcessor fp;

        public WordCounterFileVisitor(FileProcessor fp) {
            this.fp = fp;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
            throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            fp.process(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }
    }

}
