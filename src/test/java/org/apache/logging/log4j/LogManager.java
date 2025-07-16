package org.apache.logging.log4j;

public class LogManager {
    private static class SimpleLogger implements Logger {
        @Override public void error(String msg) {}
        @Override public void error(String msg, Object param1) {}
        @Override public void error(String msg, Object param1, Object param2) {}
        @Override public void error(String msg, Object... params) {}
        @Override public void info(String msg) {}
        @Override public void info(String msg, Object param1) {}
        @Override public void info(String msg, Object param1, Object param2) {}
        @Override public void info(String msg, Object... params) {}
    }
    public static Logger getLogger(String name) { return new SimpleLogger(); }
}
