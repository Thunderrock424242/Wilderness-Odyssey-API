package org.apache.logging.log4j;

public interface Logger {
    void error(String msg);
    void error(String msg, Object param1);
    void error(String msg, Object param1, Object param2);
    void error(String msg, Object... params);

    void info(String msg);
    void info(String msg, Object param1);
    void info(String msg, Object param1, Object param2);
    void info(String msg, Object... params);
}
