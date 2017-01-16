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
package jermit.protocol;

import java.util.LinkedList;
import java.util.List;
import jermit.io.LocalFileInterface;

/**
 * SerialFileTransferSession encapsulates all the state used by an upload or
 * download.
 */
public abstract class SerialFileTransferSession {

    // If true, enable some debugging output.
    private static final boolean DEBUG = false;

    /**
     * The possible states for a transfer session.
     */
    public enum State {
        /**
         * Initial state.  SerialFileTransferSession is constructed, but
         * nothing has kicked off.
         */
        INIT,

        /**
         * Waiting for file information to be retrieved from the remote side.
         */
        DOWNLOAD_FILE_INFO,

        /**
         * Transferrring a file.
         */
        TRANSFER,

        /**
         * Completed with a file, maybe waiting for another file.
         */
        FILE_DONE,

        /**
         * Transfer session has aborted.
         */
        ABORT,

        /**
         * Transfer session is complete.
         */
        END
    }

    /**
     * The state of this transfer session.  Note package private access.
     */
    State state = State.INIT;

    /**
     * Get the state of this transfer.
     *
     * @return one of the State enum values
     */
    public State getState() {
        return state;
    }

    /**
     * The overall protocol for this transfer.
     */
    protected Protocol protocol = Protocol.ZMODEM;

    /**
     * Get the protocol.
     *
     * @return the protocol for this transfer
     */
    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Get the protocol name.  Each protocol can have several variants.
     *
     * @return the protocol name for this transfer
     */
    public abstract String getProtocolName();

    /**
     * The directory that contains the file(s) of this transfer.  Note
     * package private access.
     */
    String transferDirectory = "";

    /**
     * Get the upload/download directory name.
     *
     * @return the directory name
     */
    public String getTransferDirectory() {
        return transferDirectory;
    }

    /**
     * A "current status" message string to report on a UI.
     */
    private String currentStatus = "";

    /**
     * Set the current status message.
     *
     * @param message the status message
     */
    public synchronized void setCurrentStatus(final String message) {
        currentStatus = message;
    }

    /**
     * Get the current status message.
     *
     * @return the status message
     */
    public synchronized String getCurrentStatus() {
        return currentStatus;
    }

    /**
     * Get the block size.  Each protocol can have several variants.
     *
     * @return the block size
     */
    public abstract int getBlockSize();

    /**
     * The list of files transferred in this session.  Note that Xmodem only
     * supports one file.  Note package private access.
     */
    LinkedList<FileInfo> files;

    /**
     * If true, this session represents a download.  If false, it represents
     * an upload.  Note package private access.
     */
    boolean download;

    /**
     * Get the download flag.
     *
     * @return If true, this session represents a download.  If false, it
     * represents an upload.
     */
    public boolean isDownload() {
        return download;
    }

    /**
     * Number of bytes transferred in this session.  Note package private
     * access.
     */
    long bytesTransferred = 0;

    /**
     * Number of bytes in total to transfer in this session.  Note package
     * private access.
     */
    long bytesTotal = 0;

    /**
     * Number of blocks transferred in this session.  Note package private
     * access.
     */
    long blocksTransferred = 0;

    /**
     * Number of blocks in total to transfer in this session.  Note package
     * private access.
     */
    long blocksTotal = 0;

    /**
     * Time at which last block was sent or received.  Note package private
     * access.
     */
    long lastBlockMillis = 0;

    /**
     * The list of messages generated by this transfer.
     */
    private LinkedList<SerialFileTransferMessage> messages;

    /**
     * The time at which this session started transferring its first file.
     * Note package private access.
     */
    long startTime = 0;

    /**
     * The time at which this session completed transferring its last file.
     * Note package private access.
     */
    long endTime = 0;

    /**
     * Construct an instance to represent a batch upload.
     *
     * @param uploadFiles list of files to upload
     */
    protected SerialFileTransferSession(List<FileInfo> uploadFiles) {
        messages = new LinkedList<SerialFileTransferMessage>();
        files = new LinkedList<FileInfo>();
        files.addAll(uploadFiles);
        download = false;
    }

    /**
     * Construct an instance to represent a single file upload or download.
     *
     * @param file path to one file on the local filesystem
     * @param download If true, this session represents a download.  If
     * false, it represents an upload.
     */
    protected SerialFileTransferSession(LocalFileInterface file,
        boolean download) {

        this.download = download;
        messages = new LinkedList<SerialFileTransferMessage>();
        files = new LinkedList<FileInfo>();
        files.add(new FileInfo(file));
    }

    /**
     * Add an INFO message to the messages list.  Note package private
     * access.
     *
     * @param message the message text
     */
    synchronized void addInfoMessage(String message) {
        if (DEBUG) {
            System.err.println("addInfoMessage(): " + message);
        }
        messages.add(new SerialFileTransferMessage(message));
        currentStatus = message;
        this.notifyAll();
    }

    /**
     * Add an ERROR message to the messages list.  Note package private
     * access.
     *
     * @param message the message text
     */
    synchronized void addErrorMessage(String message) {
        if (DEBUG) {
            System.err.println("addErrorMessage(): " + message);
        }
        messages.add(new SerialFileTransferMessage(
            SerialFileTransferMessage.ERROR, message));
        currentStatus = message;
        this.notifyAll();
    }

    /**
     * Return the number of errors seen in this transfer session.
     *
     * @return the number of ERROR messages
     */
    synchronized public int errorCount() {
        int count = 0;
        for (SerialFileTransferMessage message: messages) {
            if (message.isError()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Return the number of information messages seen in this transfer session.
     *
     * @return the number of INFO messages
     */
    synchronized public int infoCount() {
        int count = 0;
        for (SerialFileTransferMessage message: messages) {
            if (message.isInfo()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Return the number of messages seen in this transfer session.
     *
     * @return the number of messages
     */
    synchronized public int messageCount() {
        return messages.size();
    }

    /**
     * Get a specific message number in the list of messages.
     *
     * @param index the message number
     * @return the message
     * @throws IndexOutOfBoundsException if index is less than 0 or greater
     * than messageCount()
     */
    synchronized public SerialFileTransferMessage getMessage(final int index) {
        return messages.get(index);
    }

    /**
     * Get the very last message in the list of messages.
     *
     * @return the message, or null if no messages are present.
     */
    synchronized public SerialFileTransferMessage getLastMessage() {
        if (messages.size() > 0) {
            return messages.get(messages.size() - 1);
        }
        return null;
    }

    /**
     * Get the transfer rate (bytes/second) for this transfer session.
     *
     * @return the number of bytes/second transfer, or -1 if the transfer has
     * not yet started
     */
    public double getTransferRate() {
        long millis = 0;
        switch (state) {
        case INIT:
            return -1;
        case DOWNLOAD_FILE_INFO:
        case TRANSFER:
        case FILE_DONE:
            millis = lastBlockMillis - startTime;
            break;
        case ABORT:
        case END:
            millis = endTime - startTime;
            break;
        }

        if (millis > 0) {
            return ((double) bytesTransferred / (double) (millis / 1000.0));
        }
        // getTransferRate() was called so fast that millis is still 0.
        // Return 0 to disambiguate it from the not-started case.
        return 0;
    }

    /**
     * Get the estimated percent completion (0.0 - 100.0) for this total
     * transfer session.  Note that Xmodem downloads will always report 0.0.
     *
     * @return the percentage of completion.  This will be 0.0 if the
     * transfer has not yet started.
     */
    public double getTotalPercentComplete() {
        if (state == State.INIT) {
            return 0.0;
        }
        if (bytesTotal == 0) {
            return 0.0;
        }
        if (bytesTransferred >= bytesTotal) {
            return 100.0;
        }
        return ((double) bytesTransferred / (double) bytesTotal) * 100.0;
    }

    /**
     * Get the estimated percent completion (0.0 - 100.0) for the current
     * file being transferred.  Note that Xmodem downloads will always report
     * 0.0.
     *
     * @return the percentage of completion.  This will be 0.0 if the
     * transfer has not yet started.
     */
    public double getPercentComplete() {
        FileInfo file = getCurrentFile();
        if (file != null) {
            if ((protocol == Protocol.XMODEM) && (download == true)) {
                // Xmodem downloads cannot return % complete.
                return 0.0;
            }
            return file.getPercentComplete();
        }
        return 0.0;
    }

    /**
     * Get the current file being transferred.
     *
     * @return the current file being transferred, or null if the transfer
     * has not started.
     */
    synchronized public FileInfo getCurrentFile() {
        if (state == State.INIT) {
            return null;
        }
        return files.getLast();
    }

    /**
     * Get the list of all files to transfer.
     *
     * @return the list of files to transfer
     */
    synchronized public List<FileInfo> getFiles() {
        return files;
    }

    /**
     * Cancel this entire file transfer.  The session state will become
     * ABORT.
     *
     * @param keepPartial If true, save whatever has been collected if this
     * was a download.  If false, delete the file.
     */
    public abstract void cancelTransfer(boolean keepPartial);

    /**
     * Skip this file and move to the next file in the transfer.
     *
     * <p>Note that this is only guaranteed to work for Kermit, and only if
     * the remote side supports the "Interrupting a File Transfer" option
     * (section 8.4 of the Kermit Protocol specification).</p>
     *
     * <p>Xmodem and Ymodem do not have a skip capability.</p>
     *
     * <p>Zmodem can skip a file at the very beginning of the transfer, and
     * could in theory skip a file during transfer but in practice attempting
     * to skip a file midway will often lead to a stall due to clients not
     * agreeing what should be sent in the ZRPOS/ZACK exchange.</p>
     *
     * @param keepPartial If true, save whatever has been collected if this
     * was a download.  If false, delete the file.
     */
    public abstract void skipFile(boolean keepPartial);

}
