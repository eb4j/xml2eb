package io.github.eb4j.xml2eb.converter.wdic;

/**
 * Exception when Wdic Data corruption.
 * Created by miurahr on 16/07/16.
 */
public class WdicDataCorruptException extends Exception {

    /**
     * Build EBException from specified message.
     *
     * @param msg message
     */
    public WdicDataCorruptException(final String msg) {
        super(msg);
    }

    /**
     * Build EBException from specified message.
     *
     * @param msg message
     * @param cause cause
     */
    public WdicDataCorruptException(final String msg, final Throwable cause) {
        super(msg, cause);
    }
}
