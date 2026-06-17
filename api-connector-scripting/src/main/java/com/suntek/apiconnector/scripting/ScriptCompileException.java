package com.suntek.apiconnector.scripting;

/**
 * Raised when Groovy script compilation fails.
 */
public final class ScriptCompileException extends RuntimeException {

    private final String label;
    private final Integer line;

    public ScriptCompileException(String label, Integer line, String message, Throwable cause) {
        super(formatMessage(label, line, message), cause);
        this.label = label;
        this.line = line;
    }

    public String label() {
        return label;
    }

    public Integer line() {
        return line;
    }

    private static String formatMessage(String label, Integer line, String message) {
        if (line != null) {
            return "Script compile failed for '" + label + "' at line " + line + ": " + message;
        }
        return "Script compile failed for '" + label + "': " + message;
    }
}
