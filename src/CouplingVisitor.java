/*
* Gregory Gay (greg@greggay.com)
* Visitor that extracts couplings from Java files.
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Stack;

public class CouplingVisitor extends JavaBaseListener {

	// Tracks current program location
	private Stack<String> location;
	private String outerClass;
	private int anonymousCounter;

	// List of classes, and whether they can couple (false = interface)
	private HashMap<String, Boolean> classes;
	boolean canCouple;

	// List of couplings. Indexed by location
	private HashMap<String, ArrayList<String>> couplings;

	// List of program variables, method return types, and class parents
	private HashMap<String, HashMap<String, String>> variables;
	private HashMap<String, String> returnTypes;
	private HashMap<String, String> parents;
	private ArrayList<String> importedSubclasses;

	public static void main(String[] args){
		try{
			ANTLRInputStream input = new ANTLRInputStream(new FileInputStream(args[0]));
			JavaLexer lexer = new JavaLexer(input);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			JavaParser parser = new JavaParser(tokens);
			ParseTree tree = parser.compilationUnit();
			ParseTreeWalker walker = new ParseTreeWalker();
			CouplingVisitor visitor = new CouplingVisitor(); 
			walker.walk(visitor, tree);
			HashMap<String, ArrayList<String>> coups = visitor.getCouplings();

			System.out.println("# Method, Coupling");
			for(String method : coups.keySet()){
				for(String var : coups.get(method)){
					System.out.println(method + "," + var);
				}
			}

		}catch(IOException e){
			e.printStackTrace();
		}	
	}

	public CouplingVisitor(){
		outerClass = "";
		canCouple = true;
		location = new Stack<String>();
		classes = new HashMap<String, Boolean>();
		couplings = new HashMap<String, ArrayList<String>>();
		variables = new HashMap<String, HashMap<String, String>>();
		returnTypes = new HashMap<String, String>();
		parents = new HashMap<String, String>();
		importedSubclasses = new ArrayList<String>();
		anonymousCounter = 0;
	}

	/* Import statements can help qualify subclasses imported from within the project.
	 * importDeclaration :   'import' 'static'? qualifiedName ('.' '*')? ';'
	 */
	@Override
	public void enterImportDeclaration(JavaParser.ImportDeclarationContext ctx){
		// The import name will be either child 2 or 3
		String iName = "";
		if(ctx.getChild(1).getText().equals("static")){
			iName = ctx.getChild(2).getText();
		}else{
			iName = ctx.getChild(1).getText();
		}

		String iClass = "";
		String[] parts = iName.split("\\.");
		for(int part = 0; part < parts.length; part++){
			// Is the first letter uppercase?
			if(Character.isUpperCase(parts[part].charAt(0))){
				iClass = iClass + parts[part] + ":";
			}
		}
		if(iClass.contains(":")){
			iClass = iClass.substring(0, iClass.length() - 1);
			if(iClass.contains(":")){
				importedSubclasses.add(iClass);
			}
		}
	}


	
	// Abstract classes cannot be instantiated, so we mark them as "uncouplable"
	/*
	@Override
	public void enterClassOrInterfaceModifier(JavaParser.ClassOrInterfaceModifierContext ctx){
		if(ctx.getText().equals("abstract")){
			canCouple = false;
		}
	}*/

	/* Gets the class name and whether it inherits from any parents
	* classDeclaration :   'class' className typeParameters? ('extends' typeType)?
        * ('implements' typeList)? classBody
	*/
        @Override
        public void enterClassDeclaration(JavaParser.ClassDeclarationContext ctx){
		// The second child is the class name. 
		if(!location.isEmpty()){
			outerClass = location.peek();
		}

		String cName = ctx.getChild(1).getText();
		if(!outerClass.equals("")){
			cName = outerClass + ":" + cName;
		}		
		
		location.push(cName);
		classes.put(cName, canCouple);
		// If there is an outer class, add this to the variables list
		// To capture references such as X.Class.value
		if(!outerClass.equals("")){
			HashMap<String, String> localVars;
			if(variables.containsKey(outerClass)){
				localVars = variables.get(outerClass);
			}else{
				localVars = new HashMap<String, String>();
			}
			localVars.put(ctx.getChild(1).getText(), cName);
			variables.put(outerClass, localVars);
		}

		// Check whether the class inherits from a parent
		for(int child = 0; child < ctx.getChildCount(); child++){
			if(ctx.getChild(child).getText().equals("extends")){
				String parent = ctx.getChild(child+1).getText();
				// Remove generics
				parent = parent.replaceAll("<.*?>","");

				// Is this type an inner class?
				for(String clazz : classes.keySet()){
					if(clazz.contains(":")){
						if(parent.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
							parent = clazz;
						}
					}
				}
				// Is this type an imported subclass?
				for(String clazz : importedSubclasses){
					if(clazz.contains(":")){
						if(parent.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
							parent = clazz;
						}
					}
				}

				parents.put(location.peek(),parent);
				break;
			}
		} 

	}

	@Override
        public void enterEnumDeclaration(JavaParser.EnumDeclarationContext ctx){
		// The second child is the class name. 
		if(!location.isEmpty()){
			outerClass = location.peek();
		}

		String cName = ctx.getChild(1).getText();
		if(!outerClass.equals("")){
			cName = outerClass + ":" + cName;
		}		
		
		location.push(cName);
		classes.put(cName, canCouple);
		// If there is an outer class, add this to the variables list
		// To capture references such as X.Class.value
		if(!outerClass.equals("")){
			HashMap<String, String> localVars;
			if(variables.containsKey(outerClass)){
				localVars = variables.get(outerClass);
			}else{
				localVars = new HashMap<String, String>();
			}
			localVars.put(ctx.getChild(1).getText(), cName);
			variables.put(outerClass, localVars);
		}	
	}

	@Override
        public void enterInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx){
		canCouple = false;
		// The second child is the class name. 
		if(!location.isEmpty()){
			outerClass = location.peek();
		}

		String cName = ctx.getChild(1).getText();
		if(!outerClass.equals("")){
			cName = outerClass + ":" + cName;
		}		
		
		location.push(cName);
		classes.put(cName, canCouple);
		// If there is an outer class, add this to the variables list
		// To capture references such as X.Class.value
		if(!outerClass.equals("")){
			HashMap<String, String> localVars;
			if(variables.containsKey(outerClass)){
				localVars = variables.get(outerClass);
			}else{
				localVars = new HashMap<String, String>();
			}
			localVars.put(ctx.getChild(1).getText(), cName);
			variables.put(outerClass, localVars);
		}
	}

	@Override
        public void enterAnnotationTypeDeclaration(JavaParser.AnnotationTypeDeclarationContext ctx){
		canCouple = false;
		// The third child is the class name. 
		if(!location.isEmpty()){
			outerClass = location.peek();
		}

		String cName = ctx.getChild(2).getText();
		if(!outerClass.equals("")){
			cName = outerClass + ":" + cName;
		}		
		
		location.push(cName);
		classes.put(cName, canCouple);
		// If there is an outer class, add this to the variables list
		// To capture references such as X.Class.value
		if(!outerClass.equals("")){
			HashMap<String, String> localVars;
			if(variables.containsKey(outerClass)){
				localVars = variables.get(outerClass);
			}else{
				localVars = new HashMap<String, String>();
			}
			localVars.put(ctx.getChild(1).getText(), cName);
			variables.put(outerClass, localVars);
		}
	}

	// Reset class name on exit.
	@Override
	public void exitClassDeclaration(JavaParser.ClassDeclarationContext ctx){
		location.pop();
		if(outerClass.contains(":")){
			outerClass = outerClass.substring(0,outerClass.lastIndexOf(":"));
		}else{
			outerClass = "";
		}
		canCouple = true;
	}

	@Override
	public void exitEnumDeclaration(JavaParser.EnumDeclarationContext ctx){
		location.pop();
		if(outerClass.contains(":")){
			outerClass = outerClass.substring(0,outerClass.lastIndexOf(":"));
		}else{
			outerClass = "";
		}

		canCouple = true;
	}

	@Override
	public void exitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx){
		location.pop();
		if(outerClass.contains(":")){
			outerClass = outerClass.substring(0,outerClass.lastIndexOf(":"));
		}else{
			outerClass = "";
		}
		
		canCouple = true;
	}

	@Override
	public void exitAnnotationTypeDeclaration(JavaParser.AnnotationTypeDeclarationContext ctx){
		location.pop();
		if(outerClass.contains(":")){
			outerClass = outerClass.substring(0,outerClass.lastIndexOf(":"));
		}else{
			outerClass = "";
		}

		canCouple = true;
	}

	/* Adjusts the current method tracking
	* methodDeclaration :   (typeType|'void') myMethodName formalParameters ('[' ']')*
        * ('throws' qualifiedNameList)? (methodBody |   ';')
	*/
	@Override
	public void enterMethodDeclaration(JavaParser.MethodDeclarationContext ctx){
		location.push(location.peek() + "." + ctx.getChild(1).getText());

		String type = ctx.getChild(0).getText();
		if(type.contains(".")){
			String newType = "";
			String[] parts = type.split("[.]");
			for(int part = 0; part < parts.length; part++){
				if(parts[part].contains("<")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("<"));
				}else if(parts[part].contains(">")){
					parts[part] = parts[part].substring(parts[part].indexOf(">") + 1, parts[part].length());
				}
				if(parts[part].contains("[")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("["));
				}
				if(parts[part].length() > 0 && !Character.isLowerCase(parts[part].charAt(0))){
					// Gets rid of package names as part of the type name
					newType = newType + parts[part] + ".";
				}	
			}
			type = newType.substring(0, newType.length() -1);
		}else{
			if(type.contains("<")){
				type = type.substring(0, type.indexOf("<"));
			}
			if(type.contains("[")){
				type = type.substring(0, type.indexOf("["));
			}
		}

		// Is this type an inner class?
		for(String clazz : classes.keySet()){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}
		// Is this type an imported subclass?
		for(String clazz : importedSubclasses){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		returnTypes.put(location.peek(), type);	
		
		// Update couplings to resolve references to static methods.
		updateCouplings();
	}

	/* Adjusts current method to the proper constructor
	* constructorDeclaration:   Identifier formalParameters ('throws' qualifiedNameList)? constructorBody
	*/
	@Override 
	public void enterConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx){
		location.push(location.peek() + ".constructor");
	}

	// Reset current method name on exit from a method
	@Override
	public void exitMethodDeclaration(JavaParser.MethodDeclarationContext ctx){
		location.pop();
	}

	@Override
	public void exitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx){
		location.pop();
	}

	/* Adds enum values to variable list
	* enumConstant:   annotation* Identifier arguments? classBody?
	*/
	@Override
	public void enterEnumConstant(JavaParser.EnumConstantContext ctx){
		for(int child = 0; child < ctx.getChildCount(); child ++){
			if(ctx.getChild(child) instanceof TerminalNode){
				HashMap<String, String> localVars;
				if(variables.containsKey(location.peek())){
					localVars = variables.get(location.peek());
				}else{
					localVars = new HashMap<String, String>();
				}
				// Variable type
				String type = location.peek();
	
				// Variable name
				String name = ctx.getChild(child).getText();
				if(name.contains("(")){
					name = name.substring(0, name.indexOf("("));
				}
				localVars.put(name, type);
				variables.put(location.peek(), localVars);
			}
		}
	}

	/* Adds parameters to variable list
	* formalParameter :   variableModifier* typeType variableDeclaratorId
	*/
	@Override
	public void enterFormalParameter(JavaParser.FormalParameterContext ctx){
		HashMap<String, String> localVars;
		if(variables.containsKey(location.peek())){
			localVars = variables.get(location.peek());
		}else{
			localVars = new HashMap<String, String>();
		}
		// Last two children are type and name
		// Variable type
		String type = ctx.getChild(ctx.getChildCount()-2).getText();
		if(type.contains(".")){
			String newType = "";
			String[] parts = type.split("[.]");
			for(int part = 0; part < parts.length; part++){
				if(parts[part].contains("<")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("<"));
				}else if(parts[part].contains(">")){
					parts[part] = parts[part].substring(parts[part].indexOf(">") + 1, parts[part].length());
				}
				if(parts[part].contains("[")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("["));
				}
				if(parts[part].length() > 0 && !Character.isLowerCase(parts[part].charAt(0))){
					// Gets rid of package names as part of the type name
					newType = newType + parts[part] + ".";
				}
			}
			type = newType.substring(0, newType.length() -1);
		}else{
			if(type.contains("<")){
				type = type.substring(0, type.indexOf("<"));
			}
			if(type.contains("[")){
				type = type.substring(0, type.indexOf("["));
			}
		}
	
		// Is this type an inner class?
		for(String clazz : classes.keySet()){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}
		// Is this type an imported subclass?
		for(String clazz : importedSubclasses){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-1).getText();
		name = name.replaceAll("\\[.*?\\]","");

		localVars.put(name, type);
		variables.put(location.peek(), localVars);
	}

	/* Adds parameters to variable list
	* lastFormalParameter :   variableModifier* typeType '...' variableDeclaratorId
	*/
	@Override
	public void enterLastFormalParameter(JavaParser.LastFormalParameterContext ctx){
		HashMap<String, String> localVars;
		if(variables.containsKey(location.peek())){
			localVars = variables.get(location.peek());
		}else{
			localVars = new HashMap<String, String>();
		}

		// Variable type
		String type = ctx.getChild(ctx.getChildCount()-3).getText();
		if(type.contains(".")){
			String newType = "";
			String[] parts = type.split("[.]");
			for(int part = 0; part < parts.length; part++){
				if(parts[part].contains("<")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("<"));
				}else if(parts[part].contains(">")){
					parts[part] = parts[part].substring(parts[part].indexOf(">") + 1, parts[part].length());
				}
				if(parts[part].contains("[")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("["));
				}
				if(parts[part].length() > 0 && !Character.isLowerCase(parts[part].charAt(0))){
					// Gets rid of package names as part of the type name
					newType = newType + parts[part] + ".";
				}
			}
			type = newType.substring(0, newType.length() -1);
		}else{
			if(type.contains("<")){
				type = type.substring(0, type.indexOf("<"));
			}
			if(type.contains("[")){
				type = type.substring(0, type.indexOf("["));
			}
		}

		// Is this type an inner class?
		for(String clazz : classes.keySet()){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}
		// Is this type an imported subclass?
		for(String clazz : importedSubclasses){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-1).getText();
		name = name.replaceAll("\\[.*?\\]","");

		localVars.put(name, type);
		variables.put(location.peek(), localVars);
	}

	/* Captures global variables for variable list.
	*	fieldDeclaration :   typeType variableDeclarators ';'
	*/
	@Override
	public void enterFieldDeclaration(JavaParser.FieldDeclarationContext ctx){
		HashMap<String, String> globalVars;
		if(variables.containsKey(location.peek())){
			globalVars = variables.get(location.peek());
		}else{
			globalVars = new HashMap<String, String>();
		}
		// Variable type
		String type = ctx.getChild(0).getText();
		if(type.contains(".")){
			String newType = "";
			String[] parts = type.split("[.]");
			for(int part = 0; part < parts.length; part++){
				if(parts[part].contains("<")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("<"));
				}else if(parts[part].contains(">")){
					parts[part] = parts[part].substring(parts[part].indexOf(">") + 1, parts[part].length());
				}
				if(parts[part].contains("[")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("["));
				}
				if(parts[part].length() > 0 && !Character.isLowerCase(parts[part].charAt(0))){
					// Gets rid of package names as part of the type name
					newType = newType + parts[part] + ".";
				}
			}
			type = newType.substring(0, newType.length() -1);
		}else{
			if(type.contains("<")){
				type = type.substring(0, type.indexOf("<"));
			}
			if(type.contains("[")){
				type = type.substring(0, type.indexOf("["));
			}
		}

		// Is this type an inner class?
		for(String clazz : classes.keySet()){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}
		// Is this type an imported subclass?
		for(String clazz : importedSubclasses){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		// Variable name
		for(int child = 0; child < ctx.getChild(1).getChildCount(); child++){
			if(!ctx.getChild(1).getChild(child).getText().equals(",")){
				String name = ctx.getChild(1).getChild(child).getChild(0).getText();
				name = name.replaceAll("\\[.*?\\]","");

				globalVars.put(name, type);
				variables.put(location.peek(), globalVars);
			}
		}
	}

	/* Captures local variables for variable list.
	*	localVariableDeclaration :   variableModifier* typeType variableDeclarators
	*/
	@Override
	public void enterLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx){
		HashMap<String, String> localVars;
		if(variables.containsKey(location.peek())){
			localVars = variables.get(location.peek());
		}else{
			localVars = new HashMap<String, String>();
		}

		// Last two children are type and declarators (which gives us names)
		// Variable type
		String type = ctx.getChild(ctx.getChildCount()-2).getText();
		if(type.contains(".")){
			String newType = "";
			String[] parts = type.split("[.]");
			for(int part = 0; part < parts.length; part++){
				if(parts[part].contains("<")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("<"));
				}else if(parts[part].contains(">")){
					parts[part] = parts[part].substring(parts[part].indexOf(">") + 1, parts[part].length());
				}
				if(parts[part].contains("[")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("["));
				}
				if(parts[part].length() > 0 && !Character.isLowerCase(parts[part].charAt(0))){
					// Gets rid of package names as part of the type name
					newType = newType + parts[part] + ".";
				}
			}
			type = newType.substring(0, newType.length() -1);
		}else{
			if(type.contains("<")){
				type = type.substring(0, type.indexOf("<"));
			}
			if(type.contains("[")){
				type = type.substring(0, type.indexOf("["));
			}
		}

		// Is this type an inner class?
		for(String clazz : classes.keySet()){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}
		// Is this type an imported subclass?
		for(String clazz : importedSubclasses){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		// Variable name
		for(int child = 0; child < ctx.getChild(ctx.getChildCount()-1).getChildCount(); child++){
			if(!ctx.getChild(ctx.getChildCount()-1).getChild(child).getText().equals(",")){
				String name = ctx.getChild(ctx.getChildCount()-1).getChild(child).getChild(0).getText();
				name = name.replaceAll("\\[.*?\\]","");

				localVars.put(name, type);
				variables.put(location.peek(), localVars);
			}
		}
	}

	/* Adds variables declared in enhanced for loops to the variable list.
	 * enhancedForControl:   variableModifier* typeType variableDeclaratorId ':' expression
	 */
	@Override
	public void enterEnhancedForControl(JavaParser.EnhancedForControlContext ctx){
		HashMap<String, String> localVars;
		if(variables.containsKey(location.peek())){
			localVars = variables.get(location.peek());
		}else{
			localVars = new HashMap<String, String>();
		}

		// Type and name are two children away from last child
		// Variable type
		String type = ctx.getChild(ctx.getChildCount()-4).getText();
		if(type.contains(".")){
			String newType = "";
			String[] parts = type.split("[.]");
			for(int part = 0; part < parts.length; part++){
				if(parts[part].contains("<")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("<"));
				}else if(parts[part].contains(">")){
					parts[part] = parts[part].substring(parts[part].indexOf(">") + 1, parts[part].length());
				}
				if(parts[part].contains("[")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("["));
				}
				if(parts[part].length() > 0 && !Character.isLowerCase(parts[part].charAt(0))){
					// Gets rid of package names as part of the type name
					newType = newType + parts[part] + ".";
				}
			}
			type = newType.substring(0, newType.length() -1);
		}else{
			if(type.contains("<")){
				type = type.substring(0, type.indexOf("<"));
			}
			if(type.contains("[")){
				type = type.substring(0, type.indexOf("["));
			}
		}

		// Is this type an inner class?
		for(String clazz : classes.keySet()){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}
		// Is this type an imported subclass?
		for(String clazz : importedSubclasses){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-3).getText();
		name = name.replaceAll("\\[.*?\\]","");

		localVars.put(name, type);
		variables.put(location.peek(), localVars);
	}

	/* Adds exceptions from catch blocks to variable list
	* catchClause :   'catch' '(' variableModifier* catchType Identifier ')' block
	*/
	@Override
	public void enterCatchClause(JavaParser.CatchClauseContext ctx){
		HashMap<String, String> localVars;
		if(variables.containsKey(location.peek())){
			localVars = variables.get(location.peek());
		}else{
			localVars = new HashMap<String, String>();
		}

		// Type and name are two children away from last child
		// Variable type
		String type = ctx.getChild(ctx.getChildCount()-4).getText();
		// Is this type an inner class?
		for(String clazz : classes.keySet()){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}
		// Is this type an imported subclass?
		for(String clazz : importedSubclasses){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-3).getText();
		localVars.put(name, type);
		variables.put(location.peek(), localVars);
	}

	// Capture coupling from expression
	@Override
	public void enterExpression(JavaParser.ExpressionContext ctx){
		// Child 2 should be a "." to count
		if(ctx.getChildCount() > 2){
			if(ctx.getChild(1).getText().equals(".")){
				//System.out.println("-------" + location.peek() + "\n--" + ctx.getText());
				ArrayList<String> deps;
				if(couplings.containsKey(location.peek())){
					deps = couplings.get(location.peek());
				}else{	
					deps = new ArrayList<String>();
				}
				String expr = ctx.getText();
				// Remove generics
				expr = expr.replaceAll("<.*?>","");
				// Remove array references
				expr = expr.replaceAll("\\[.*?\\]","");
				// Replace strings with generic filler.
				expr = expr.replaceAll("\\\".*?\\\"","String");
				expr = expr.replaceAll("\\\'.*?\\\'","char");
			
				if(expr.contains("(")){
					/* We want to remove arguments
					 * Parentheses are part of arguments, casts, and subexpressions
					 * Given our restrictions on the types of expressions considered 
 					 * at this stage, parentheses can only come through in certain forms.
					 */
						
					if(expr.indexOf("(")==0){
						String[] parts = expr.split("[.]"); 
						String first = "";
						String rest = "";
						boolean balanced = false;
						int parens = 0;
						for(int part = 0; part < parts.length; part++){
							if(balanced){
								if(part < parts.length - 1){
									rest = rest + parts[part] + ".";
								}else{
									rest = rest + parts[part];
								}
							}else{
								for(char letter: parts[part].toCharArray()){
									if(letter == '('){
										parens++;
									}else if(letter == ')'){
										parens--;
									}
								}

								if(parens == 0){
									balanced = true;
								}
								first = first + parts[part] + ".";						
							}
						}
						first = first.substring(0, first.length() - 1);

						// Now drop parens if this is a grouping
						if(first.charAt(first.length()-1) == ')'){
							first = first.substring(1, first.length() - 1);
						}
						expr = first + "." + rest;
						//System.out.println(expr);

						while(expr.charAt(1) == '('){
							// Split into substrings based on "."
							parts = expr.split("[.]");
							first = "";
							rest = "";
							balanced = false;
							parens = 0;

							for(int part = 0; part < parts.length; part++){
								if(balanced){
									if(part < parts.length - 1){
										rest = rest + parts[part] + ".";
									}else{
										rest = rest + parts[part];
									}
								}else{
									for(char letter: parts[part].toCharArray()){
										if(letter == '('){
											parens++;
										}else if(letter == ')'){
											parens--;
										}
									}

									if(parens == 0){
										balanced = true;
									}
									first = first + parts[part] + ".";						
								}
							}
							first = first.substring(0, first.length() - 1);

							// Now drop parens if this is a grouping
							if(first.charAt(first.length()-1) == ')'){
								first = first.substring(1, first.length() - 1);
							}
							expr = first + "." + rest;
							//System.out.println(expr);
						}
	
						// Now, if this is a cast, replace with type
						if(first.charAt(0) == '('){
							String castType = "";
							parts = first.split("[(]");
							for(int word = 0; word < parts.length; word++){
								if(!parts[word].equals("")){
									castType = parts[word].substring(0,parts[word].indexOf(")"));
									break;
								}
							}
							first = castType;
						}
						expr = first + "." + rest;
					}
					
					int include = 0;
					String newExpr = "";
					for(char letter : expr.toCharArray()){
						if(letter == '('){
							include++;
						}else if(letter == ')'){
							include--;
						}else if(include == 0){
							newExpr = newExpr + letter;
						}
					}	
					expr = newExpr;
				}	

				// Find type of referenced variable
				String var;
				if(expr.contains(".")){
					var = expr.substring(0,expr.indexOf("."));
				}else{
					var = expr;
				}
				boolean found = false;
				HashMap<String, String> vars;
				
				// If the "variable" is super, fill in parent class
				if(var.equals("super")){
					String pName = "";
					if(location.peek().contains(".")){
						pName = location.peek().substring(0, location.peek().indexOf("."));
					}else{
						pName = location.peek();
					}
					if(parents.containsKey(pName)){
						var = parents.get(pName);
						found = true;
					}else{
						// If no parent, all classes descend from Object
						var = "Object";
						found = true;
					}
				}

				// Check for keyword "this"
				if(!found){ 
					if(var.equals("this")){
						if(location.peek().contains(".")){
							var = location.peek().substring(0,location.peek().indexOf("."));
						}else{
							var = location.peek();
						}
						found = true;
					}
				}
				
				// Check for Java globally available methods
				if(!found){
					if(var.equals("getType") || var.equals("getClass")){
						var = "Class";
						found = true;
					}
				}
				//Check globals
				if(!found){
					if(variables.containsKey(location.peek())){
						vars = variables.get(location.peek());
						for(String current : vars.keySet()){
							if(current.equals(var)){
								found = true;
								var = vars.get(current);
								break;
							}
						}
					}
				}
				// Then check locals
				if(!found){
					if(variables.containsKey(location.peek())){
						vars = variables.get(location.peek());
						for(String current : vars.keySet()){
							if(current.equals(var)){
								var = vars.get(current);
								found = true;
								break;
							}
						}
					}
				}
				// It could also be a static method
				if(!found){
					Stack<String> nesting = (Stack<String>) location.clone();
					String currentClass = "";
					while(!nesting.isEmpty()){
						currentClass = nesting.pop();
						if(!nesting.contains(".")){
							break;
						}
					}
					if(returnTypes.containsKey(currentClass + "." + var)){
						var = returnTypes.get(currentClass + "." + var);
						found = true; 
					}
				}

				// It can also be a global variable or method from the outer class
				
				if(!found){
					Stack<String> nesting = (Stack<String>) location.clone();
					while(!nesting.isEmpty()){
						String level = nesting.pop();
						if(!level.contains(".")){
							if(variables.containsKey(level)){
								vars = variables.get(level);
								for(String current : vars.keySet()){
									if(current.equals(var)){
										found = true;
										var = vars.get(current);
										break;
									}
								}
							}		
							if(!found){
								if(returnTypes.containsKey(level + "." + var)){
									var = returnTypes.get(level + "." + var);
									found = true; 
								}
							}
						}
						if(found){
							break;
						}
					}
				} 
							
				// It could actually a "new" declaration
				if(!found && var.contains("new")){
					if(var.indexOf("new") == 0){
						var = var.substring(3,var.length());
						found = true;
					}
				}
				
				// If this is a subexpression, it is a primitive
				if(var.contains("+") || var.contains("-") || var.contains("*") || var.contains("/") || var.contains("&") || var.contains("&") || var.contains("|")){
					var = "primitive";

					found = true;
				}
				
				// Now put the type in
				if(found){
					if(expr.contains(".")){
						expr = var + expr.substring(expr.indexOf("."),expr.length());
					}else{
						expr = var;
					}
				}
				//System.out.println(expr);
				deps.add(expr);
				couplings.put(location.peek(), deps);
			}
		}
	}

	/* Update couplings list to replace references to static methods 
	 * with the return type of that method.
	 */
	public void updateCouplings(){
		// Update return types
		for(String method: returnTypes.keySet()){
			String type = returnTypes.get(method);
			// Is this type an inner class?
			for(String clazz : classes.keySet()){
				if(clazz.contains(":")){
					if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
						returnTypes.put(method, clazz);
					}
				}
			}
		}
		// Update variable types
		for(String method: variables.keySet()){
			HashMap<String, String> vars = variables.get(method);
			for(String variable: vars.keySet()){
				String type = vars.get(variable);
				// Is this type an inner class?
				for(String clazz : classes.keySet()){
					if(clazz.contains(":")){
						if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
							vars.put(variable, clazz);
						}
					}
				}
			}
			variables.put(method, vars);
		}

		// Update couplings
		for(String method : couplings.keySet()){
			String clazz = "";
			if(method.contains(".")){
				clazz = method.substring(0,method.indexOf("."));
			}else{
				clazz = method;
			}
			ArrayList<String> deps = couplings.get(method);

			for(int expr=0; expr < deps.size(); expr++){
				// Find type of referenced variable
				String var = deps.get(expr);

				if(var.contains(".")){
					var = var.substring(0,var.indexOf("."));
				}
				boolean found = false;
				
				if(returnTypes.containsKey(clazz + "." + var)){
					var = returnTypes.get(clazz + "." + var);
					found = true; 
				}

				// Is this type an inner class?
				for(String cl : classes.keySet()){
					if(cl.contains(":")){
						if(var.equals(cl.substring(cl.lastIndexOf(":")+1,cl.length()))){
							var = cl;	
							found = true;
						}
					}
				}

				// Is this actually an imported subclass?
				for(String cl: importedSubclasses){
					if(cl.contains(":")){
						if(var.equals(cl.substring(cl.lastIndexOf(":")+1,cl.length()))){
							var = cl;	
							found = true;
						}
					}				
				}

				// Now put the type in
				if(found){
					if(deps.get(expr).contains(".")){
						var = var + deps.get(expr).substring(deps.get(expr).indexOf("."),deps.get(expr).length());
					}
					
					deps.set(expr,var);
				}
			}
			couplings.put(method, deps);
		}

	}

	/* Captures coupling to constructors (new declarations)
	* creator:   nonWildcardTypeArguments createdName classCreatorRest 
	* |   createdName (arrayCreatorRest | classCreatorRest)
	*/
	@Override
	public void enterCreator(JavaParser.CreatorContext ctx){
		String type = ctx.getChild(ctx.getChildCount()-2).getText();
		if(type.contains(".")){
			String newType = "";
			String[] parts = type.split("[.]");
			for(int part = 0; part < parts.length; part++){
				if(parts[part].contains("<")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("<"));
				}else if(parts[part].contains(">")){
					parts[part] = parts[part].substring(parts[part].indexOf(">") + 1, parts[part].length());
				}
				if(parts[part].contains("[")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("["));
				}
				if(parts[part].length() > 0 && !Character.isLowerCase(parts[part].charAt(0))){
					// Gets rid of package names as part of the type name
					newType = newType + parts[part] + ".";
				}
			}
		}else{
			if(type.contains("<")){
				type = type.substring(0, type.indexOf("<"));
			}
			if(type.contains("[")){
				type = type.substring(0, type.indexOf("["));
			}
		}

		// Is this type an inner class?
		for(String clazz : classes.keySet()){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}
		// Is this type an imported subclass?
		for(String clazz : importedSubclasses){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		ArrayList<String> deps;
		if(couplings.containsKey(location.peek())){
			deps = couplings.get(location.peek());
		}else{	
			deps = new ArrayList<String>();
		}

		deps.add(type);
		couplings.put(location.peek(), deps);
		// If there is an anonymous class, we update location
		if(ctx.getChild(ctx.getChildCount()-1).getChildCount() == 2){
			String currentClass = location.peek();
			if(currentClass.contains(".")){
				currentClass = currentClass.substring(0, currentClass.indexOf("."));
			}
			anonymousCounter++;
			String cName = currentClass + ":" + anonymousCounter;
				
			location.push(cName);
			classes.put(cName, canCouple);
			parents.put(location.peek(), type);
		}
	}
	
	@Override
	public void exitCreator(JavaParser.CreatorContext ctx){
		// Exit inner class
		if(ctx.getChild(ctx.getChildCount()-1).getChildCount() == 2){
			location.pop();
		}
	}

	@Override
	public void enterInnerCreator(JavaParser.InnerCreatorContext ctx){
		String type = ctx.getChild(0).getText();
		if(type.contains(".")){
			String newType = "";
			String[] parts = type.split("[.]");
			for(int part = 0; part < parts.length; part++){
				if(parts[part].contains("<")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("<"));
				}else if(parts[part].contains(">")){
					parts[part] = parts[part].substring(parts[part].indexOf(">") + 1, parts[part].length());
				}
				if(parts[part].contains("[")){
					parts[part] = parts[part].substring(0, parts[part].indexOf("["));
				}
				if(parts[part].length() > 0 && !Character.isLowerCase(parts[part].charAt(0))){
					// Gets rid of package names as part of the type name
					newType = newType + parts[part] + ".";
				}
			}
			type = newType.substring(0, newType.length() -1);
		}else{
			if(type.contains("<")){
				type = type.substring(0, type.indexOf("<"));
			}
			if(type.contains("[")){
				type = type.substring(0, type.indexOf("["));
			}
		}

		// Is this type an inner class?
		for(String clazz : classes.keySet()){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}
		// Is this type an imported subclass?
		for(String clazz : importedSubclasses){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		// If there is an anonymous class, we update location
		if(ctx.getChild(ctx.getChildCount()-1).getChildCount() == 2){
			String currentClass = location.peek();
			if(currentClass.contains(".")){
				currentClass = currentClass.substring(0, currentClass.indexOf("."));
			}
			anonymousCounter++;
			String cName = currentClass + ":" + anonymousCounter;
				
			location.push(cName);
			classes.put(cName, canCouple);
			parents.put(location.peek(), type);
		}	
	}
	
	@Override
	public void exitInnerCreator(JavaParser.InnerCreatorContext ctx){
		if(ctx.getChild(ctx.getChildCount()-1).getChildCount() == 2){
			location.pop();
		}
	}

        // Getters and setters
	public boolean getCanCouple(){
		return canCouple;
	}

 	public Stack<String> getLocation(){
		return location;
	}

	public HashMap<String, Boolean> getClasses(){
		return classes;
	}

	public HashMap<String,ArrayList<String>> getCouplings(){
		updateCouplings();
		return couplings;
	}

	public HashMap<String, HashMap<String, String>> getVariables(){
		updateCouplings();
		return variables;
	}

	public HashMap<String, String> getReturnTypes(){
		updateCouplings();
		return returnTypes;
	}
	
	public HashMap<String, String> getParents(){
		return parents;
	}

	public ArrayList<String> getImportedSubclasses(){
		return importedSubclasses;
	}
}
