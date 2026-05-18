// Author: Trey Adams
// Last modified: 26sp
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(EdStemExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PatientPrioritizerTest {
    private ASTParser parser;
    private boolean constantsExist;
    private boolean isWildcardImport;

    @BeforeAll
    public void initializeParser() throws Exception {
        parser = new ASTParser("PatientPrioritizer.java");
        constantsExist = false;
    }

    @AfterAll
    public void cleanup() throws Exception {
        parser.close();
    }

    @Order(0)
    @Test
    @Tag("score:0")
    @DisplayName("[Preliminary Check] Test HOSPITAL_ZIP Exists")
    public void testConstants() throws FileNotFoundException {
        Map<String, String> constantDeclarations = new HashMap<>();
        constantDeclarations.put("HOSPITAL_ZIP", "int");
        boolean result = true;
        for (String constant : constantDeclarations.keySet()) {
            result = result && 
                    parser.testFieldExists(constantDeclarations.get(constant), constant, true);
        }
        if (!result) {
            TestTools.failForConstants();
        }
        constantsExist = result;
    }

    @Order(1)
    @Test
    @Tag("score:0")
    @DisplayName("[Preliminary Check] Check Import Statements")
    public void testImports() throws FileNotFoundException {
        boolean found = parser.hasManualImport();
        if (found) {
            TestTools.failForImports();
        }
        isWildcardImport = !found;
    }

    @Tag("score:0")
    @Tag("IncludeCustomMessage")
    @DisplayName("Constants")
    @MethodSource("ParameterTestValues#params")
    @ParameterizedTest(name="{2}")
    public void testMain(int hospitalZip, String ioFileName, String testName) throws Exception {
        if (constantsExist && isWildcardImport) {
            boolean changedConstants = parser.changeField("HOSPITAL_ZIP", "" + hospitalZip);
            if (changedConstants) {
                Class<?> tempClass = parser.loadClass();
                Method main = tempClass.getDeclaredMethod("main", new Class<?>[]{String[].class});
                String[] args = new String[]{};

                String inputFileName = "tests/" + ioFileName + ".in";
                TestResult result = TestTools.invokeMethodWithRedirect(main, inputFileName, (Object) args);

                String expectedOutput = TestTools.readFile("tests/" + ioFileName + ".out");
                if (result.encounteredRuntimeException()) {
                    assertEquals(expectedOutput, result.getOutput(), result.getErrorMessage(customRuntimeErrorMsg()));
                } else {
                    assertEquals(expectedOutput, result.getOutput());
                }
            } else {
                TestTools.failForConstants();
            }
        } else {
            TestTools.failForPreliminaryChecks();
        }
    }

    ////////////////////////
    // Forbidden Features //
    ////////////////////////
    @Test
    @Tag("score:0")
    @Tag("private")
    @DisplayName("[Forbidden Features]")
    public void superHackyForbiddenFeaturesCheck() {
        String[] files = { "PatientPrioritizer.java" };

        String message = "";

        for (String file : files) {
            message +=
                SelfContainedMoroRunner.runAndReturnMoro(
                    "/course/121_forbidden_features.xml",
                    file
                )
                .replaceAll("Starting audit...\n", "")
                .replaceAll("Audit done.", "")
                .replaceAll("Checkstyle ends with [0-9]+ errors.", "")
                .replaceAll("\n\n", "\n");
        }

        if (!message.isEmpty()) {
            fail(message);
        }
    }

    //////////////////
    // Code Quality //
    //////////////////
    @Test
    @Tag("score:0")
    @Tag("private")
    @DisplayName("[Code Quality]")
    public void superHackyCodeQualityCheck() {
        String[] files = { "PatientPrioritizer.java" };

        String message = "";

        for (String file: files) {
            message +=
                SelfContainedMoroRunner.runAndReturnMoro(
                    "/course/121_code_quality.xml",
                    file
                )
                .replaceAll("Starting audit...\n", "")
                .replaceAll("Audit done.", "")
                .replaceAll("Checkstyle ends with [0-9]+ errors.", "")
                .replaceAll("\n\n", "\n");
        }

        if (!message.isEmpty()) {
            fail(message);
        }
    }

    public static String customRuntimeErrorMsg() {
        return "Ensure that your program:\n" 
                + "  - Calls the appropriate Scanner method when prompting the user "
                + "(i.e., .nextDouble() for a double value)\n"
                + "  - Creates only one Scanner object that is shared across methods\n"
                + "  - Does not mix token-based calls (e.g., .next() or .nextInt()) "
                + "with line-based calls (.nextLine()).\n"
                + "  - Does not make any extra or unnecessary Scanner calls.";
    }
}