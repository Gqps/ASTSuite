public class TestResult {
    private Object returnValue;
    private String output;
    private boolean hadRuntimeException;
    private String runtimeException;

    public TestResult(Object returnValue, String output, boolean hadRuntimeException) {
        this.returnValue = returnValue;
        this.output = output;
        this.hadRuntimeException = hadRuntimeException;
        this.runtimeException = null;
    }

    public TestResult(Object returnValue, String output, 
            boolean hadRuntimeException, String runtimeException) {
        this(returnValue, output, hadRuntimeException);
        this.runtimeException = runtimeException;
    }

    public Object getReturn() {
        return returnValue;
    }

    public String getOutput() {
        return output;
    }

    public boolean encounteredRuntimeException() {
        return hadRuntimeException;
    }

    public String getErrorMessage() {
        return this.runtimeException + "Below is your program's output prior to the Exception:";
    }

    public String getErrorMessage(String customMessage) {
        String errorMsg = this.runtimeException + "\n\n" + customMessage
                + "\n\nBelow is your program's output prior to the Exception:";
        return errorMsg;
    }
}