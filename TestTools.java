import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;

public class TestTools {
    
    // Helper method with invocation logic for a given method - Does not redirect input
    // Returns invoked method return value and output. If return type is void, returns null
    public static <T> TestResult invokeMethod(Method method, T... params) 
            throws IOException, IllegalAccessException, InvocationTargetException {
        ByteArrayOutputStream bufferStudentOutput = new ByteArrayOutputStream();
        Object returnValue = null;
        PrintStream originalOut = System.out;

        try {
            // Redirect System.out
            System.setOut(new PrintStream(bufferStudentOutput));
            // Invoke method
            returnValue = method.invoke(null, params);

            // Restore System IO
            System.setOut(originalOut);

            // Convert student output to String
            String studentOutput = convertStudentOutput(bufferStudentOutput);

            return new TestResult(returnValue, studentOutput, false);
        } catch (Exception e) {
            // Cleanup
            System.setOut(originalOut);

            String studentOutput = convertStudentOutput(bufferStudentOutput);
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                return new TestResult(returnValue, studentOutput, true, re.toString());
            } else {
                throw e;
            }
        }
    }

    // Helper method with invocation logic for a given method - Redirects input to a given file
    // Returns invoked method return value and output. If return type is void, returns null
    public static <T> TestResult invokeMethodWithRedirect(Method method, String inputFileName, T... params) 
            throws IOException, IllegalAccessException, InvocationTargetException {
        
        ByteArrayOutputStream bufferStudentOutput = new ByteArrayOutputStream();
        Object returnValue = null;
        PrintStream originalOut = System.out;
        InputStream originalIn = System.in;
        
        try {
            // Redirect System IO
            System.setOut(new PrintStream(bufferStudentOutput));
            FileInputStream fis = new FileInputStream(inputFileName);
            System.setIn(fis);

            // Invoke method    
            returnValue = method.invoke(null, params);

            // Restore System IO
            System.setOut(originalOut);
            System.setIn(originalIn);

            // Convert student output to String
            String studentOutput = convertStudentOutput(bufferStudentOutput);

            return new TestResult(returnValue, studentOutput, false);
        } catch (Exception e) {
            // Cleanup
            System.setOut(originalOut);
            System.setIn(originalIn);

            String studentOutput = convertStudentOutput(bufferStudentOutput);
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                return new TestResult(returnValue, studentOutput, true, re.toString());
            } else {
                throw e;
            }
        }
    }
    
    // Returns the given file in a String, formatted with line breaks for each new line
    public static String readFile(String fileName) throws IOException {
        BufferedReader fileReader = new BufferedReader(new FileReader(fileName));
        StringBuilder result = new StringBuilder();

        // Start reading
        String fileCurLine = fileReader.readLine();

        while (fileCurLine != null) {
            result.append(fileCurLine);
            result.append("\n");

            fileCurLine = fileReader.readLine();
        }

        // Cleanup
        fileReader.close();

        return result.toString();
    }

    // Converts the student output (redirected to the given parameter) into a String
    private static String convertStudentOutput(
            ByteArrayOutputStream bufferStudentOutput) throws IOException {
        // Convert output to a String
        StringBuilder output = new StringBuilder();
        BufferedReader studentReader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bufferStudentOutput.toByteArray())));
        String currLine;
        while ((currLine = studentReader.readLine()) != null) {
            output.append(currLine).append("\n");
        }
        return output.toString();
    }

    //////////////////////////////////////////////// - [] X
    ///////////////////////////////////////////////////////
    // Helper methods for tests containing general       //
    // error messages. Useful for boolean conditions     //
    // dependent on preliminary checks passing.          // isnt this box so cool
    // Each method below throws an AssertionFailedError. //
    ///////////////////////////////////////////////////////

    // Fail message for student missing one of the required class constants
    // Can also fail if not declared as public static final
    public static void failForConstants() {
        fail("Ensure your class constants are declared as:\n" 
                + "  public static final\nwith the EXACT names from the spec. "
                + "Also make sure they have the correct data types.");
    }

    // Fail message for student using manual impor statements
    public static void failForImports() {
        fail("This test ensures that your program does not have manual import statements such as:"
                + "\n  - import java.util.Random\n  - import java.util.Scanner");
    }

    // Fail message for Random object creation inside a loop (mainly for P1)
    public static void failForRandomInLoop() {
        fail("Make sure you only create and use one Random variable, "
                + "and that it is initialized before any loops.\n"
                + "If you created a Random variable inside a loop "
                + "try moving it outside of the loop :)");
    }

    // Fail message for preliminary checks not passing
    // Note: Could be paired with global boolean variables in the test file, ensuring that all preliminary checks
    //       pass before running the main tests
    public static void failForPreliminaryChecks() {
        fail("Make sure that all tests for Preliminary Checks PASS for these tests to run");
    }
}