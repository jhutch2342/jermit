/*
 * Jermit
 *
 * The MIT License (MIT)
 *
 * Copyright (C) 2017 Kevin Lamonte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * @author Kevin Lamonte [kevin.lamonte@gmail.com]
 * @version 1
 */
package jermit.tests.ymodem;

import java.io.File;
import java.io.IOException;

import jermit.protocol.ymodem.YmodemReceiver;
import jermit.protocol.ymodem.YmodemSession;
import jermit.tests.SerialTransferTest;
import jermit.tests.TestFailedException;

/**
 * Test a basic binary file Ymodem file transfer.
 */
public class Ymodem1 extends SerialTransferTest {

    /**
     * Public constructor.
     */
    public Ymodem1() {
    }

    /**
     * Run the test.
     */
    @Override
    public void doTest() throws IOException, TestFailedException {
        System.out.printf("Ymodem1: one binary file download - VANILLA\n");

        // Process:
        //
        //   1. Extract jermit/tests/data/lady-of-shalott.jpg to
        //      a temp file.
        //   2. Spawn 'sx /path/to/lady-of-shalott.jpg'
        //   3. Spin up YmodemReceiver to download to a temp file.
        //   4. Read both files and compare contents.

        File source = File.createTempFile("send-ymodem", ".jpg");
        saveResourceToFile("jermit/tests/data/lady-of-shalott.jpg", source);
        source.deleteOnExit();

        // Create a directory
        File destinationDirName = File.createTempFile("receive-ymodem", "");
        String destinationPath = destinationDirName.getPath();
        destinationDirName.delete();
        File destinationDir = new File(destinationPath);
        destinationDir.mkdir();
        destinationDir.deleteOnExit();
        File destination = new File(destinationPath, source.getName());
        destination.deleteOnExit();

        ProcessBuilder syb = new ProcessBuilder("sb", source.getPath());
        Process sy = syb.start();

        YmodemReceiver ry = new YmodemReceiver(YmodemSession.YFlavor.VANILLA,
            sy.getInputStream(), sy.getOutputStream(), destinationPath, false);

        ry.run();
        if (!compareFiles(source, destination)) {
            throw new TestFailedException("Files are not the same");
        }

    }

    /**
     * Main entry point.
     *
     * @param args Command line arguments
     */
    public static void main(final String [] args) {
        try {
            Ymodem1 test = new Ymodem1();
            test.doTest();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}