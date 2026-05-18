// Created by Sam Korostov in Wi 26 as a way to provide students clean diff output on ed.
// Updated by William Baird Sp 26 to gather output more cleanly and allow toggling the diffs off/adding custom messages.
import org.junit.jupiter.api.extension.*;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.ValueWrapper;
import java.util.*;
import java.io.*;

/**
 * JUnit extension that captures test results and outputs them in JSON format
 * for EdStem custom test mode.
 * Allows for diff visualization on AssertEquals string comparisons
 *  as well as adjusts test output to Ed's testcase format while maintaining standard JUnit testing
 * 
 * Usage: 
 *  Add @ExtendWith(EdStemExtension.class) to your test class
 *  Set Ed test mode to Custom
 *  Mark command: J=/usr/share/java/junit-platform-console-standalone.jar && javac -cp .:$J *.java && java -cp .:$J EdStemExtension <TEST_CLASS_NAME>
 *  Annotate method with @Tag("DiffOff") to conditionally toggle displaying diffs (default = on)
 *  If you'd like diffs *and* you want a custom message to be displayed upon a failure preceding the diff display
 *      annotate methods with @Tag("IncludeCustomMessage") and when you make an assertEquals call on strings 
 *      add the custom failure message you want before the diff as a 3rd parameter.
 * 

 */

public class EdStemExtension implements TestWatcher, AfterAllCallback {
    private static final String TEST_CASE_FORMAT = 
        "  {\"name\":\"%s\",\"ok\":%s,\"passed\":%s,\"hidden\":%s,\"private\":%s,\"feedback\":\"%s\"%s}";

    private static final List<Map<String, Object>> testcases = new ArrayList<>();
    private static PrintStream originalOut;
    
    // Runs tests programmatically to avoid ConsoleLauncher's formatted output
    // and ensure clean JSON is outputted for Ed's parser.
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java EdStemExtension <TestClassName>");
            System.exit(1);
        }
        originalOut = System.out;
        // set system.out to a garbage stream till results are output.
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        String testClassName = args[0];
        Class<?> testClass = Class.forName(testClassName);
        
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectClass(testClass))
            .build();
        
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request);
    }
    
    // Called when a test passes. Records the success, simply outputs 'Test passed'.
    //     - Potential future addition: Could provided the expected output upon a test passing to give students more information.
    @Override
    public void testSuccessful(ExtensionContext context) {
        addResult(context, true, "Test passed", null);
    }
    
    // Called when a test fails. Records the failure using the provided error message (if one exists) 
    // If the failure is caused by an assertEquals statement between two strings, displays a diff view of the expected and actual values.
    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        // To get full stacktrace need to print the throwable out, otherwise we just get either the failure message or just the cause, no details on stacktrace.
        StringWriter stackTraceStringWriter = new StringWriter();
        PrintWriter stackTracePrintWriter = new PrintWriter(stackTraceStringWriter);
        cause.printStackTrace(stackTracePrintWriter);
        String feedback = stackTraceStringWriter.toString();
        if (feedback.contains("jdk.internal.reflect") || feedback.contains("org.junit")) {
            if (feedback.indexOf("jdk.internal.reflect") == -1) {
                feedback = feedback.substring(0, feedback.indexOf("org.junit") - 1) + "...";
            } else if (feedback.indexOf("org.junit") == -1) {
                feedback = feedback.substring(0, feedback.indexOf("jdk.internal.reflect") - 1) + "...";
            } else {
                feedback = feedback.substring(0, Math.min(feedback.indexOf("jdk.internal.reflect"), feedback.indexOf("org.junit")) - 1) + "...";
            }
        }
        if (feedback.startsWith("org.opentest4j.AssertionFailedError: ")) {
            feedback = feedback.substring("org.opentest4j.AssertionFailedError: ".length());
        }
        addResult(context, false, feedback, cause);
    }
    
    // Adds test result to collection, extracting expected/observed values for diff if available.
    private void addResult(ExtensionContext context, boolean passed, String feedback, Throwable cause) {
        Map<String, Object> result = new HashMap<>();
        result.put("name", context.getDisplayName());
        result.put("ok", true);
        result.put("passed", passed);
        
        Set<String> tags = context.getTags();
        result.put("hidden", tags.contains("hidden"));
        result.put("private", tags.contains("private"));
        
        // Checks that the error is an assertionfailederror (could be caused by assertTrue or assertequals)
        if (!passed && cause != null && feedback != null && cause instanceof AssertionFailedError afe && !tags.contains("DiffOff")) {
            // get the compared objects
            Object expectedObj = afe.getExpected() != null ? afe.getExpected().getValue() : null;
            Object observedObj = afe.getActual() != null ? afe.getActual().getValue() : null;
            
            // Only show diff when comparing Strings with assertequals.
            if (expectedObj != null && observedObj != null && 
                (expectedObj instanceof String expectedStr && observedObj instanceof String observedStr)) {
                // put the expected and observed into result.
                result.put("expected", expectedStr);
                result.put("observed", observedStr);
                // If they don't specify to include a custom message, don't
                if (!tags.contains("IncludeCustomMessage")) {
                    result.put("feedback", "Output does not match expected. See diff below.");
                } else if (feedback.contains("==> expected: <")) {
                    // Otherwise, grab whatever the message was and add it.
                    String customMessage;
                    customMessage = feedback.substring(0, feedback.indexOf("==> expected: <"));
                    if (customMessage.strip().equals("")) {
                        customMessage = "Output does not match expected. See diff below.";
                    }
                    result.put("feedback", customMessage);
                } else {
                    result.put("feedback", "Output does not match expected. See diff below.");
                }
                testcases.add(result);
                return;
            }
        } else if (tags.contains("DiffOff") && feedback.contains("==> expected: <")) {
            // remove junit expected output because it's gross and they said no diff.
            feedback = feedback.substring(0, feedback.indexOf("==> expected: <"));
            if (feedback.strip().equals("")) {
                feedback = "Test Failed.";
            }
        }
        
        result.put("feedback", feedback);
        testcases.add(result);
    }
    
    // Called after all tests finish. Outputs collected results as JSON in Ed's
    // specified formatting
    @Override
    public void afterAll(ExtensionContext context) {
        System.setOut(originalOut);
        System.out.println("{\"testcases\": [");
        for (int i = 0; i < testcases.size(); i++) {
            Map<String, Object> tc = testcases.get(i);
            
            String diffFields = "";
            if (tc.containsKey("expected")) {
                diffFields = String.format(",\"expected\":\"%s\",\"observed\":\"%s\"",
                    escape((String)tc.get("expected")),
                    escape((String)tc.get("observed")));
            }
            
            String testCase = String.format(TEST_CASE_FORMAT,
                escape((String)tc.get("name")),
                tc.get("ok"),
                tc.get("passed"),
                tc.get("hidden"),
                tc.get("private"),
                escape((String)tc.get("feedback")),
                diffFields);
            
            System.out.print(testCase);
            if (i < testcases.size() - 1) System.out.println(",");
        }
        System.out.println("\n]}");
    }
    
    // Escapes special characters for valid JSON output.
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}

