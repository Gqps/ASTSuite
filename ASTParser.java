// Last Modified: 26sp
// TODO: Possibly check existence of constructors for OOP assignments

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.net.URL;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.Node.PreOrderIterator;
import com.github.javaparser.StaticJavaParser;

/**
 * Represents a Java program in an Abstract Syntax Tree (AST), utilizing
 * JavaParser's parsing functionality, converting all parts of a Java program
 * to custom objects storing individual classes, methods, statements, expressions, etc.
 * 
 * @author Trey Adams
 */
public class ASTParser {

    private CompilationUnit cu; // Stores the entire program as an AST
    private Map<String, VariableDeclarator> studentFields;
    private Map<String, MethodDeclaration> studentMethods;
    private URLClassLoader constantClassLoader;

    private final String TEMP_FILE_NAME;
    private int classIndex;

    /**
     * Parses given Java file and reads in all fields, methods, and constructors
     * 
     * @param fileName The file name including the file type (should be ".java")
     * @throws FileNotFoundException If the provided file does not exist
     * @throws MalformedURLException If the file is in an unexpected path
     */
    public ASTParser(String fileName) throws FileNotFoundException, MalformedURLException {
        cu = StaticJavaParser.parse(new File(fileName)); // Parses entire program
        studentFields = new HashMap<>();
        studentMethods = new HashMap<>();
        classIndex = 0;
        constantClassLoader = new URLClassLoader(
                new URL[]{new File("./").toURI().toURL()}, null);

        // Read all fields and methods present in the class
        initializeFields();
        initializeMethods();

        // Set temporary file name to a relevant name in case tests are transparent to students
        TEMP_FILE_NAME = fileName.substring(0, fileName.indexOf(".")) + "_";
    }

    /**
     * Determines if any imports are not wildcard imports 
     * (e.g. {@code java.util.Random} or {@code java.util.List})
     * 
     * @return {@code true} if all imports are not wildcard imports, and {@code false} otherwise
     */
    public boolean hasManualImport() {
        for (ImportDeclaration port : cu.findAll(ImportDeclaration.class)) {
            if (!port.isAsterisk()) {
                return true;
            }
        }
        return false;
    }

    // TODO: Find random initializations as well

    /**
     * Determines if there are any Random variable declarations or initializations within a loop.
     * 
     * @return {@code true} if a Random object creation is found inside a loop, and {@code false} otherwise
     */
    public boolean checkRandomCreationInLoop() {
        for (ForStmt loop : cu.findAll(ForStmt.class)) {
            for (ObjectCreationExpr obj : loop.findAll(ObjectCreationExpr.class)) {
                if (obj.getType().getNameAsString().equals("Random")) {
                    return true;
                }
            }
        }
        for (ForEachStmt loop : cu.findAll(ForEachStmt.class)) {
            for (ObjectCreationExpr obj : loop.findAll(ObjectCreationExpr.class)) {
                if (obj.getType().getNameAsString().equals("Random")) {
                    return true;
                }
            }
        }
        for (WhileStmt loop : cu.findAll(WhileStmt.class)) {
            for (ObjectCreationExpr obj : loop.findAll(ObjectCreationExpr.class)) {
                if (obj.getType().getNameAsString().equals("Random")) {
                    return true;
                }
            }
        }
        for (DoStmt loop : cu.findAll(DoStmt.class)) {
            for (ObjectCreationExpr obj : loop.findAll(ObjectCreationExpr.class)) {
                if (obj.getType().getNameAsString().equals("Random")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Tests whether the program contains a field of the exact type and name, and if the field is a class constant, 
     * determines if the field has the correct modifiers.
     * 
     * @param type The declared type of the field
     * @param name The name of the field
     * @param isConstant Whether the field should have all modifiers {@code public static final}; the field is a class constant.
     * @return {@code true} if the field was succesfully found, and if all modifiers are present for class constants. Returns {@code false} otherwise.
     */
    public boolean testFieldExists(String type, String name, boolean isConstant) {
        if (!studentFields.containsKey(name) || studentFields.get(name) == null) return false;

        VariableDeclarator var = studentFields.get(name);
        if (isConstant) { // Checks if the field has modifiers "public" "static" and "void"
            Optional<Node> fieldOpt = var.getParentNode();
            if (fieldOpt.isPresent()) {
                FieldDeclaration field = (FieldDeclaration)fieldOpt.get();
                if (!field.isPublic() || !field.isStatic() || !field.isFinal()) return false;
            }
        }
        return var.getTypeAsString().equals(type);
    }

    /**
     * Tests whether the program contains a method of the provided method signature, including its
     * return type, method name, and list of parameters.
     * 
     * @param returnType The return type of the method
     * @param name The method name
     * @param parameterTypes a String array representing the type of each parameter the method
     *        accepts. Should be ordered in the expected ordering of the parameters
     * @return {@code true} if the method exists, specific to the parameter order given. Returns {@code false} otherwise.
     */
    public boolean testMethodExists(String returnType, String name, String[] parameterTypes) {
        String signature = formatMethodSignature(name, parameterTypes);
        if (studentMethods.containsKey(signature)) {
            return studentMethods.get(signature).getTypeAsString().equals(returnType);
        } 
        return false;
    }

    // Loads a temporary class to perform changes such as rewriting class constant or variable values
    // Returns a compiled Java class that can be invoked
    /**
     * Converts the AST into to a runnable Java class including all changes such as modifying class constants or variables.
     * 
     * @return A loaded class object which can be invoked until the parser's class loader is closed.
     * @throws ClassNotFoundException
     * @throws FileNotFoundException 
     * @throws IOException
     * @throws InterruptedException
     */
    public Class<?> loadClass() throws ClassNotFoundException, FileNotFoundException, 
            IOException, InterruptedException {
        Optional<ClassOrInterfaceDeclaration> clazz = 
        cu.findFirst(ClassOrInterfaceDeclaration.class);
        String fileName = TEMP_FILE_NAME + this.classIndex;
        FileWriter writer = new FileWriter(new File("./" + fileName + ".java"));

        // Rewrites class name in the AST to match file name
        if (clazz.isPresent()) {
            clazz.get().setName(fileName);
            this.classIndex++;
        }

        // Save class to file
        writer.write(cu.toString());
        writer.close();

        // Compile class
        Process processTemp = new ProcessBuilder(
        "javac", fileName + ".java")
                .redirectErrorStream(true)
                .start();
        processTemp.waitFor();

        return constantClassLoader.loadClass(fileName);
    }

    /**
     * Changes field value specifically at its declaration, given the field name and a new initialization as String.
     * <p>
     * The new value passed should follow these guidelines:
     * 
     * <ul>
     *     <li> Primitive type fields may pass in a value simply enclosed by parenthesis. For example:
     *         <blockquote>
     *         {@code public static final int MAX_SIZE = 0;}
     *         </blockquote> 
     * 
     *         Can be initialized to a new value with the following call:
     * 
     *         {@snippet :
     *             changeField("MAX_SIZE", "15");
     *         }
     *     </li>
     *     <li> String type fields should include quotation mark literals enclosing the value. For example:
     *         <blockquote>
     *         {@code public String str;}
     *         </blockquote>
     * 
     *         Can be given an initialization with the following call:
     * 
     *         {@snippet :
     *             changeField("str", "\"Hello!\"");
     *         }
     *     </li>
     *     <li> Array type fields can be initialized to new arrays or references to other objects. 
     *          When initialized to a new array, the {@code new} keyword must used for quick initialization. For example:
     *         <blockquote>
     *         {@code public String[] names = new String[]{"Andrew", "Chloe"};}
     *         </blockquote>
     * 
     *         Can be initialized with the following calls:
     * 
     *         {@snippet :
     *             changeField("names", "new String[]{\"Colton\", \"Parker\"}");
     *             changeField("names", "altNames"); // Assuming altNames exists and can be read
     *         }
     *     </li>
     *     <li> Object type fields can be initialized to new objects or references to other objects. For example:
     *         <blockquote>
     *         {@code public List<Double> list0;}
     *         </blockquote>
     * 
     *         Can be initialized with the following calls:
     * 
     *         {@snippet :
     *             changeField("list0", "List.of(1.5, 2.0, 2.55)");
     *             changeField("list0", "list1"); // Assuming list1 exists and can be read
     *         }
     *     </li>
     * </ul>
     * 
     * @param field The name of the field.
     * @param newValue The String representation for the new initializing expression of the target field.
     * @return {@code true} if the field was succesfully changed, and {@code false} otherwise.
     */
    public boolean changeField(String field, String newValue) {
        if (studentFields.containsKey(field)) {
            Expression newInit = StaticJavaParser.parseExpression(newValue); // Converts newValue to a Javaparser Expression object
            try {
                studentFields.get(field).setInitializer(newInit); // Set field to new value
            } catch (Exception e) {
                return false;
            }
        } else { // Missing constant
            return false;
        }
        return true;
    }

    /**
     * Changes field value specifically at its declaration, given the field name and a new initialization as String.
     * Changes all field values specifically at their declaration, given a mapping from their name to their new value.
     * The provided mapping should contain keys which represent the field names, whose values are
     * String representations of the expressions to initialize each field to.
     * <p>
     * See {@link #changeField(String, String)} for guidelines on how to represent values as a String.
     * 
     * @param fields The mapping from each field's name to its new initializing expression.
     * @return {@code true} if all fields were succesfully changed, and {@code false} otherwise.
     * @see #changeField(String, String)
     */
    public boolean changeField(Map<String, String> fields) {
        for (String field : fields.keySet()) {
            if (studentFields.containsKey(field)) {
                Expression newInit = StaticJavaParser.parseExpression(fields.get(field));
                try {
                    studentFields.get(field).setInitializer(newInit);
                } catch (Exception e) {
                    return false;
                }
            } else { // Missing field
                return false;
            }
        }
        return true; // All fields successfully changed
    }

    /**
     * Given a variable name and the method it is declared in, replaces the value of the variable with a new value.
     * The new value passed in should be represented as a String, following guidelines defined in {@link #changeField(String, String)}.
     * <p>
     * The new value for the variable will be reassigned at the latest <b>write</b> to the variable, prior to the first <b>read</b> within the method.
     * 
     * <blockquote>
     *     For example, for the method:
     * 
     *     {@snippet :
     *         public static void method0() {
     *             String favoriteColor;
     *             favoriteColor = "green";
     *             String randomColor = "red";
     *             randomColor = favoriteColor = "blue";
     *             System.out.println(favoriteColor);
     *         }
     *     }
     * 
     *     We can change the value of {@code favoriteColor} before it is <b>read</b> in the print statement by:
     *
     *     {@snippet :
     *         changeVariable("method0", new String[0], "favoriteColor", "\"purple\"");
     *     }
     * 
     *     Which reassigns {@code favoriteColor} at the latest assignment, which is at the nested assignment:
     *     <br>{@code randomColor = favoriteColor = "blue"}.
     * </blockquote>
     * 
     * @param methodName The name of the method in which the target variable is declared.
     * @param methodParamTypes A {@code String[]} containing the parameters of the method 
     *                         in which the target variable is declared.
     * @param varName The target variable name.
     * @param newValue The String representation for the new initializing expression of the target variable.
     * @return {@code true} if the variable was changed, and {@code false} otherwise.
     * @see #changeField(String, String)
     */
    public boolean changeVariable(String methodName, String[] methodParamTypes, 
            String varName, String newValue) {
        // Get method name and parameters in String format
        String signature = formatMethodSignature(methodName, methodParamTypes);
        // Get method as Javaparser MethodDeclaration object, or set null if not present in student program
        MethodDeclaration studentMethod = studentMethods.getOrDefault(signature, null);

        if (studentMethod != null) {
            // Get all variable declarations in method
            List<VariableDeclarator> declarations = studentMethod.findAll(VariableDeclarator.class);
            for (VariableDeclarator var : declarations) {
                if (var.getNameAsString().equals(varName)) { // variable name matches target variable name
                    Expression newInit = StaticJavaParser.parseExpression(newValue); // Convert to Javaparser Expression object

                    // Read through student code to find the latest assignment to this variable's value before it is read
                    // Note: does not apply for Objects whose value is changed in a different method (through reference semantics)
                    //       But will still be considered read if it is passed as a parameter.
                    //       This is an edge case which is yet to be fully explored.
                    LatestAssignmentVisitor visitor = new LatestAssignmentVisitor(varName);
                    studentMethod.accept(visitor, null);

                    // Get latest Assign Expression (a reassignment) if it exists, 
                    // otherwise the latest read is at the variable's initialization.
                    Optional<AssignExpr> targetOpt = visitor.getLatestAssignment();
                    try {
                        if (targetOpt.isPresent()) {
                            AssignExpr targetExpr = targetOpt.get();
                            targetExpr.setValue(newInit);
                        } else {
                            var.setInitializer(newInit);
                        }
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Removes all temporary files created from {@code loadClass()}.
     * 
     * @throws InterruptedException
     * @throws IOException
     */
    public void close() throws InterruptedException, IOException {
        constantClassLoader.close();

        // Remove all loaded temp files from the directory
        String removingClasses = "";
        for (int i = 0; i < this.classIndex; i++) {
            removingClasses += TEMP_FILE_NAME + i + ".java "
                            +  TEMP_FILE_NAME + i + ".class ";
        }
        ProcessBuilder removeClasses = new ProcessBuilder(new String[] { 
            "rm", "-f", removingClasses});
        removeClasses.start().waitFor();
    }

    // Finds all fields declared in the AST - including local class fields
    private void initializeFields() {
        List<FieldDeclaration> fields = cu.findAll(FieldDeclaration.class);
        for (FieldDeclaration field : fields) {
            NodeList<VariableDeclarator> vars = field.getVariables();
            for (VariableDeclarator var : vars) {
                studentFields.put(var.getNameAsString(), var);
            }
        }
    }

    // Finds all methods declared in the AST - including local class methods
    private void initializeMethods() {
        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
        for (MethodDeclaration method : methods) {
            String declaration = method.getDeclarationAsString(false, false, false);
            studentMethods.put(declaration.substring(declaration.indexOf(" ") + 1), method);
        }
    }

    // Returns a String representation of the method's signature, including its name and list of parameters
    // Parameters:
    //  - name: The name of the method
    //  - parameterTypes: a String array representing the type of each parameter the method
    //                    accepts. Ordered naturally by array indices.
    private String formatMethodSignature(String name, String[] parameterTypes) {
        String signature = name.trim() + "(";
        for (int i = 0; i < parameterTypes.length - 1; i++) {
            signature += parameterTypes[i] + ", ";
        }
        if (parameterTypes.length >= 1) {
            signature += parameterTypes[parameterTypes.length - 1].trim();
        }
        signature += ")";
        return signature;
    }

    // Visitor which find the last assignment of the target variable before the variable value is read
    // A visitor is used by calling .accept() from a Javaparser object that contains a body
    // such as a Method or Class. Specifically for VoidVisitorAdapter, iterates through all
    // nodes and their children by default unless an Object's behavior is overridden.
    private class LatestAssignmentVisitor extends VoidVisitorAdapter<Void> {
        private final String target;

        private AssignExpr latestAssignment = null;
        private boolean stop = false;

        // target - the target variable name to change in the program
        public LatestAssignmentVisitor(String target) {
            this.target = target;
        }

        // Returns an Optional of the latest assignment for the target variable before
        // the target's value is read
        public Optional<AssignExpr> getLatestAssignment() {
            return Optional.ofNullable(latestAssignment);
        }

        // Returns true if the visitor detects a read of the target variable
        public boolean isStopped() {
            return stop;
        }

        // Updates the latest assignment of the target variable if and only if
        // the target variable is the left hand side of the assignment, and if it is not read
        // on the right hand side
        @Override
        public void visit(AssignExpr n, Void arg) {
            if (stop) return;
            
            AssignExpr tempLatest = null;
            if (isTargetAssignment(n)) {
                tempLatest = n;
            }

            // This iterator checks the right hand side of an assignment to check
            // if the target variable is read
            // If the target variable is part of a nested assignment (i.e. x1 = x2 = 10),
            // where x1 or x2 is the target variable, (both 'writes')
            // it will still update the latest assignment as long as the value is not read.
            PreOrderIterator iter = new Node.PreOrderIterator(n.getValue());
            while (!stop && iter.hasNext()) {
                Node next = iter.next();
                if (next instanceof AssignExpr ae) {
                    if (isTargetAssignment(ae)) {
                        tempLatest = ae;
                    }
                } else {
                    if (next instanceof NameExpr ne) {
                        if (!isWrite(ne)) {
                            stop = true;
                        }
                    }
                    if (next instanceof UnaryExpr ue) {
                        if (isTargetName(ue.getExpression())) {
                            stop = true;
                        }
                    }
                }
            }

            if (!stop && tempLatest != null) {
                latestAssignment = tempLatest;
            }

            if (!stop && isTargetName(n.getTarget())) {
                latestAssignment = n;
            }
        }

        @Override
        public void visit(VariableDeclarator n, Void arg) {
            if (stop) return;

            if (n.getNameAsString().equals(target)) {
                latestAssignment = null; // declaration, no AssignExpr before
            }

            super.visit(n, arg);
        }

        @Override
        public void visit(NameExpr n, Void arg) {
            if (stop) return;

            if (!n.getNameAsString().equals(target) || isWrite(n)) {
                return;
            }

            stop = true;
        }

        @Override
        public void visit(UnaryExpr n, Void arg) {
            if (stop) return;

            if (isTargetName(n.getExpression())) {
                stop = true;
            }

            super.visit(n, arg);
        }

        // Returns true if the given expression is the target variable
        private boolean isTargetName(Expression e) {
            return (e instanceof NameExpr ne)
                    && ne.getNameAsString().equals(target);
        }

        // Given an AssignExpr, recurses on any parenthesis that the assignment target might be
        // enclosed by, and returns true if the name of the target matches this String target field
        private boolean isTargetAssignment(AssignExpr n) {
            Expression targetExpr = n.getTarget();
            while (targetExpr instanceof EnclosedExpr ee) {
                targetExpr = ee.getInner();
            }
            return isTargetName(targetExpr);
        }

        // Returns true if the given node is the left hand side of an
        // assignment expression => the given node is a 'write'
        private boolean isWrite(NameExpr n) {
            Optional<Node> parentOpt = n.getParentNode();
            if (parentOpt.isEmpty()) return false;

            Node parent = parentOpt.get();

            // Recurse out until outside of parenthesis (Ex: (((var0))) = 5)
            while (parent instanceof EnclosedExpr) {
                parentOpt = parent.getParentNode();
                if (!parentOpt.isEmpty()) {
                    parent = parentOpt.get();
                }
            }

            // LHS of assignment
            if (parent instanceof AssignExpr ae) {
                Expression targetExpr = ae.getTarget();

                // Recurse inwards until inside of all parenthesis
                while (targetExpr instanceof EnclosedExpr ee) {
                    targetExpr = ee.getInner();
                }
                return targetExpr == n;
            }

            return false;
        }
    }
}
