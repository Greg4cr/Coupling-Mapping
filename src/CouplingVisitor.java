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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class CouplingVisitor extends JavaBaseVisitor<Void> {

        private String currentClass;
	private String currentMethod;
	private HashMap<String, ArrayList<String>> couplings;
	private HashMap<String, HashMap<String, String>> variables;
	private HashMap<String, String> returnTypes;
	private HashMap<String, String> parents;

	public static void main(String[] args){
		try{
			ANTLRInputStream input = new ANTLRInputStream(new FileInputStream(args[0]));
			JavaLexer lexer = new JavaLexer(input);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			JavaParser parser = new JavaParser(tokens);
			JavaParser.CompilationUnitContext tree = parser.compilationUnit(); 
		
			CouplingVisitor visitor = new CouplingVisitor(); 
			visitor.visit(tree);
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
		currentClass = "";
		currentMethod = "";
		couplings = new HashMap<String, ArrayList<String>>();
		variables = new HashMap<String, HashMap<String, String>>();
		returnTypes = new HashMap<String, String>();
		parents = new HashMap<String, String>();
	}

	/* Gets the class name and whether it inherits from any parents
	* classDeclaration :   'class' className typeParameters? ('extends' typeType)?
        * ('implements' typeList)? classBody
	*/
        @Override
        public Void visitClassDeclaration(JavaParser.ClassDeclarationContext ctx){
		currentMethod="";

		// The second child is the class name. 
		currentClass = ctx.getChild(1).getText();

		// Check whether the class inherits from a parent
		for(int child = 0; child < ctx.getChildCount(); child++){
			if(ctx.getChild(child).getText().equals("extends")){
				String parent = ctx.getChild(child+1).getText();
				if(parent.contains("<")){
					// Strip out specific instantiation. Just need base class name
					parent = parent.substring(0,parent.indexOf("<"));
				}
				parents.put(currentClass,parent);
				break;
			}
		} 

		return super.visitChildren(ctx);
	}

	@Override
        public Void visitEnumDeclaration(JavaParser.EnumDeclarationContext ctx){
		currentMethod="";

		// The second child is the class name. 
		currentClass = ctx.getChild(1).getText();

		return super.visitChildren(ctx);
	}

	/* Adjusts the current method tracking
	* methodDeclaration :   (typeType|'void') myMethodName formalParameters ('[' ']')*
        * ('throws' qualifiedNameList)? (methodBody |   ';')
	*/
	@Override
	public Void visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx){
		currentMethod = ctx.getChild(1).getText();
		String type = ctx.getChild(0).getText();
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}

		if(returnTypes.containsKey(currentMethod)){
			String existing = returnTypes.get(currentMethod);
			boolean exists = false;
			if(existing.contains(",")){
				String[] types = existing.split(",");
				for(String eType : types){
					if(eType.equals(type)){
						exists = true;
					}
				}	
			}else{
				if(existing.equals(type)){
					exists = true;
				}
			}
			if(!exists){
				type = type + "," + returnTypes.get(currentMethod);
				returnTypes.put(currentClass + "." + currentMethod, type);
			}
		}else{
			returnTypes.put(currentClass + "." + currentMethod, type);	
		}
		// Update couplings to resolve references to static methods.
		updateCouplings();
		return super.visitChildren(ctx);
	}

	/* Adjusts current method to the proper constructor
	* constructorDeclaration:   Identifier formalParameters ('throws' qualifiedNameList)? constructorBody
	*/
	@Override 
	public Void visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx){
		currentMethod = "constructor";
		return super.visitChildren(ctx);

	}

	/* Adds parameters to variable list
	* formalParameter :   variableModifier* typeType variableDeclaratorId
	*/
	@Override
	public Void visitFormalParameter(JavaParser.FormalParameterContext ctx){
		HashMap<String, String> localVars;
		if(variables.containsKey(currentClass+"."+currentMethod)){
			localVars = variables.get(currentClass+"."+currentMethod);
		}else{
			localVars = new HashMap<String, String>();
		}
		// Last two children are type and name
		// Variable type
		String type = ctx.getChild(ctx.getChildCount()-2).getText();
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}
		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-1).getText();
		if(name.contains("[")){
			name = name.substring(0, name.indexOf("["));
		}

		localVars.put(name, type);
		variables.put(currentClass+"."+currentMethod, localVars);

		return super.visitChildren(ctx);
	}

	/* Adds parameters to variable list
	* lastFormalParameter :   variableModifier* typeType '...' variableDeclaratorId
	*/
	@Override
	public Void visitLastFormalParameter(JavaParser.LastFormalParameterContext ctx){
		HashMap<String, String> localVars;
		if(variables.containsKey(currentClass+"."+currentMethod)){
			localVars = variables.get(currentClass+"."+currentMethod);
		}else{
			localVars = new HashMap<String, String>();
		}

		// Variable type
		String type = ctx.getChild(ctx.getChildCount()-3).getText();
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}

		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-1).getText();
		if(name.contains("[")){
			name = name.substring(0, name.indexOf("["));
		}
		localVars.put(name, type);
		variables.put(currentClass+"."+currentMethod, localVars);

		return super.visitChildren(ctx);
	}

	/* Captures global variables for variable list.
	*	fieldDeclaration :   typeType variableDeclarators ';'
	*/
	@Override
	public Void visitFieldDeclaration(JavaParser.FieldDeclarationContext ctx){
		HashMap<String, String> globalVars;
		if(variables.containsKey(currentClass)){
			globalVars = variables.get(currentClass);
		}else{
			globalVars = new HashMap<String, String>();
		}
		// Variable type
		String type = ctx.getChild(0).getText();
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}

		// Variable name
		for(int child = 0; child < ctx.getChild(1).getChildCount(); child++){
			if(!ctx.getChild(1).getChild(child).getText().equals(",")){
				String name = ctx.getChild(1).getChild(child).getChild(0).getText();
				if(name.contains("[")){
					name = name.substring(0, name.indexOf("["));
				}
				globalVars.put(name, type);
				variables.put(currentClass, globalVars);
			}
		}

		return super.visitChildren(ctx);
	}

	/* Captures local variables for variable list.
	*	localVariableDeclaration :   variableModifier* typeType variableDeclarators
	*/
	@Override
	public Void visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx){
		HashMap<String, String> localVars;
		if(variables.containsKey(currentClass+"."+currentMethod)){
			localVars = variables.get(currentClass+"."+currentMethod);
		}else{
			localVars = new HashMap<String, String>();
		}

		// Last two children are type and declarators (which gives us names)
		// Variable type
		String type = ctx.getChild(ctx.getChildCount()-2).getText();
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}

		// Variable name
		for(int child = 0; child < ctx.getChild(ctx.getChildCount()-1).getChildCount(); child++){
			if(!ctx.getChild(ctx.getChildCount()-1).getChild(child).getText().equals(",")){
				String name = ctx.getChild(ctx.getChildCount()-1).getChild(child).getChild(0).getText();
				if(name.contains("[")){
					name = name.substring(0, name.indexOf("["));
				}
				localVars.put(name, type);
				variables.put(currentClass+"."+currentMethod, localVars);
			}
		}

		return super.visitChildren(ctx);	
	}

	/* Adds variables declared in enhanced for loops to the variable list.
	 * enhancedForControl:   variableModifier* typeType variableDeclaratorId ':' expression
	 */
	@Override
	public Void visitEnhancedForControl(JavaParser.EnhancedForControlContext ctx){
		HashMap<String, String> localVars;
		if(variables.containsKey(currentClass+"."+currentMethod)){
			localVars = variables.get(currentClass+"."+currentMethod);
		}else{
			localVars = new HashMap<String, String>();
		}

		// Type and name are two children away from last child
		// Variable type
		String type = ctx.getChild(ctx.getChildCount()-4).getText();
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}

		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-3).getText();
		if(name.contains("[")){
			name = name.substring(0, name.indexOf("["));
		}
		localVars.put(name, type);
		variables.put(currentClass+"."+currentMethod, localVars);

		return super.visitChildren(ctx);
	}

	/* Adds exceptions from catch blocks to variable list
	* catchClause :   'catch' '(' variableModifier* catchType Identifier ')' block
	*/
	@Override
	public Void visitCatchClause(JavaParser.CatchClauseContext ctx){
		HashMap<String, String> localVars;
		if(variables.containsKey(currentClass+"."+currentMethod)){
			localVars = variables.get(currentClass+"."+currentMethod);
		}else{
			localVars = new HashMap<String, String>();
		}

		// Type and name are two children away from last child
		// Variable type
		String type = ctx.getChild(ctx.getChildCount()-4).getText();
		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-3).getText();
		localVars.put(name, type);
		variables.put(currentClass+"."+currentMethod, localVars);

		return super.visitChildren(ctx);
	}

	// Capture coupling from expression
	@Override
	public Void visitExpression(JavaParser.ExpressionContext ctx){
		// Child 2 should be a "." to count
		if(ctx.getChildCount() > 2){
			if(ctx.getChild(1).getText().equals(".")){
				ArrayList<String> deps;
				if(!currentMethod.equals("")){
					if(couplings.containsKey(currentClass + "." + currentMethod)){
						deps = couplings.get(currentClass + "." + currentMethod);
					}else{	
						deps = new ArrayList<String>();
					}
				}else{
					if(couplings.containsKey(currentClass)){
						deps = couplings.get(currentClass);
					}else{	
						deps = new ArrayList<String>();
					}
				}
				String expr = ctx.getText();
				// Remove generics
				expr = expr.replaceAll("<.*?>","");
				// Remove array references
				expr = expr.replaceAll("\\[.*?\\]","");
								
				if(expr.contains("(")){
					// Remove arguments
					expr = expr.replaceAll("\\\".*?\\\"","String");
					expr = expr.replaceAll("\\\'.*?\\\'","char");
						
					// First portion might contain a cast. 
					// Need to distinguish cast from arguments.
					// If first character is "(", then cast.
					if(expr.indexOf("(")==0){
						String first = expr.substring(0, expr.indexOf("."));
						String rest = expr.substring(expr.indexOf("."), expr.length());
						String castType = "";
						String[] parts = first.split("[(]");
						for(int word = 0; word < parts.length; word++){
							if(!parts[word].equals("")){
								castType = parts[word].substring(0,parts[word].indexOf(")"));
								break;
							}
						}
						first = castType;
						expr = first + rest;
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
					if(parents.containsKey(currentClass)){
						var = parents.get(currentClass);
					}
					found = true;
				}

				// Check for keyword "this"
				if(!found){ 
					if(var.equals("this")){
						var = currentClass;
						found = true;
					}
					//Check globals
					if(!found){
						if(variables.containsKey(currentClass)){
							vars = variables.get(currentClass);
							for(String current : vars.keySet()){
								if(current.equals(var)){
									found = true;
									var = vars.get(current);
									break;
								}
							}
						}
						// Then check locals
						if(!found){
							if(variables.containsKey(currentClass + "." + currentMethod)){
								vars = variables.get(currentClass + "." + currentMethod);
								for(String current : vars.keySet()){
									if(current.equals(var)){
										var = vars.get(current);
										found = true;
										break;
									}
								}
							}
							// It could also be a static method
							if(!found){
								if(returnTypes.containsKey(currentClass + "." + var)){
									var = returnTypes.get(currentClass + "." + var);
									found = true; 
								}
							
								// Finally, if it is actually a "new" declaration
								if(!found && var.contains("new")){
									if(var.indexOf("new") == 0){
										var = var.substring(3,var.length());
										found = true;
									}
								}
							}
						}
					}
				}
				
				// Now put the type in
				if(found){
					if(expr.contains(".")){
						expr = var + expr.substring(expr.indexOf("."),expr.length());
					}else{
						expr = var;
					}
				}
				deps.add(expr);
				if(!currentMethod.equals("")){
					couplings.put(currentClass + "." + currentMethod, deps);
				}else{
					couplings.put(currentClass, deps);
				}
			}
		}
		return super.visitChildren(ctx);
	}

	/* Update couplings list to replace references to static methods 
	 * with the return type of that method.
	 */
	public void updateCouplings(){
		for(String method : couplings.keySet()){
			ArrayList<String> deps = couplings.get(method);

			for(int expr=0; expr < deps.size(); expr++){
				// Find type of referenced variable
				String var = deps.get(expr);
				if(var.contains(".")){
					var = var.substring(0,var.indexOf("."));
				}
				boolean found = false;
				
				if(returnTypes.containsKey(currentClass + "." + var)){
					var = returnTypes.get(currentClass + "." + var);
					found = true; 
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
	public Void visitCreator(JavaParser.CreatorContext ctx){
		String type = ctx.getChild(ctx.getChildCount()-2).getText();
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}

		ArrayList<String> deps;
		if(!currentMethod.equals("")){
			if(couplings.containsKey(currentClass + "." + currentMethod)){
				deps = couplings.get(currentClass + "." + currentMethod);
			}else{	
				deps = new ArrayList<String>();
			}
		}else{
			if(couplings.containsKey(currentClass)){
				deps = couplings.get(currentClass);
			}else{	
				deps = new ArrayList<String>();
			}
		}

		deps.add(type);
		if(!currentMethod.equals("")){
			couplings.put(currentClass + "." + currentMethod, deps);
		}else{
			couplings.put(currentClass, deps);
		}

		return super.visitChildren(ctx);
	}

        // Getters and setters
        public String getCurrentClass(){
		return currentClass;
	}

	public String getCurrentMethod(){
		return currentMethod;
	}

	public HashMap<String,ArrayList<String>> getCouplings(){
		return couplings;
	}

	public HashMap<String, HashMap<String, String>> getVariables(){
		return variables;
	}

	public HashMap<String, String> getReturnTypes(){
		return returnTypes;
	}
	
	public HashMap<String, String> getParents(){
		return parents;
	}
}
