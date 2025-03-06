package com.thunder.wildernessodysseyapi.MainModClass;


import org.apache.logging.log4j.Logger;

/**
 * Extends SLF4J's Logger interface but adds a fatal(...) method.
 */
public interface ExtendedLogger extends Logger {

    /**
     * Introduce a "fatal" method SLF4J doesn't have by default.
     */
    void fatal(String msg);

    void fatal(String msg, Throwable t);

    boolean isFatalEnabled();

    // (You could also repeat or override other logger methods here if you want.)
}