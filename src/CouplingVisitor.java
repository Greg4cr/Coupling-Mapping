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
import java.util.Stack;

public class CouplingVisitor extends JavaBaseListener {

	private boolean canCouple;
	private Stack<String> location;
	private String outerClass;
	private ArrayList<String> classes;
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
		canCouple = true;
		outerClass = "";
		location = new Stack<String>();
		classes = new ArrayList<String>();
		couplings = new HashMap<String, ArrayList<String>>();
		variables = new HashMap<String, HashMap<String, String>>();
		returnTypes = new HashMap<String, String>();
		parents = new HashMap<String, String>();
	}

	// Abstract classes cannot be instantiated, so we mark them as "uncouplable"
	@Override
	public void enterClassOrInterfaceModifier(JavaParser.ClassOrInterfaceModifierContext ctx){
		if(ctx.getText().equals("abstract")){
			canCouple = false;
		}
	}

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
		classes.add(cName);

		// Check whether the class inherits from a parent
		for(int child = 0; child < ctx.getChildCount(); child++){
			if(ctx.getChild(child).getText().equals("extends")){
				String parent = ctx.getChild(child+1).getText();
				if(parent.contains("<")){
					// Strip out specific instantiation. Just need base class name
					parent = parent.substring(0,parent.indexOf("<"));
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
		classes.add(cName);
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
		classes.add(cName);
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
		classes.add(cName);
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
	}

	@Override
	public void exitEnumDeclaration(JavaParser.EnumDeclarationContext ctx){
		location.pop();
		if(outerClass.contains(":")){
			outerClass = outerClass.substring(0,outerClass.lastIndexOf(":"));
		}else{
			outerClass = "";
		}
	}

	@Override
	public void exitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx){
		location.pop();
		if(outerClass.contains(":")){
			outerClass = outerClass.substring(0,outerClass.lastIndexOf(":"));
		}else{
			outerClass = "";
		}
	}

	@Override
	public void exitAnnotationTypeDeclaration(JavaParser.AnnotationTypeDeclarationContext ctx){
		location.pop();
		if(outerClass.contains(":")){
			outerClass = outerClass.substring(0,outerClass.lastIndexOf(":"));
		}else{
			outerClass = "";
		}
	}

	/* Adjusts the current method tracking
	* methodDeclaration :   (typeType|'void') myMethodName formalParameters ('[' ']')*
        * ('throws' qualifiedNameList)? (methodBody |   ';')
	*/
	@Override
	public void enterMethodDeclaration(JavaParser.MethodDeclarationContext ctx){
		location.push(location.peek() + "." + ctx.getChild(1).getText());

		String type = ctx.getChild(0).getText();
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}

		// Is this type an inner class?
		for(String clazz : classes){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		if(returnTypes.containsKey(location.peek())){
			String existing = returnTypes.get(location.peek());
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
				type = type + "," + returnTypes.get(location.peek());
				returnTypes.put(location.peek(), type);
			}
		}else{
			returnTypes.put(location.peek(), type);	
		}
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
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}
	
		// Is this type an inner class?
		for(String clazz : classes){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-1).getText();
		if(name.contains("[")){
			name = name.substring(0, name.indexOf("["));
		}

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
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}

		// Is this type an inner class?
		for(String clazz : classes){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-1).getText();
		if(name.contains("[")){
			name = name.substring(0, name.indexOf("["));
		}
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
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}

		// Is this type an inner class?
		for(String clazz : classes){
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
				if(name.contains("[")){
					name = name.substring(0, name.indexOf("["));
				}
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
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}
		// Is this type an inner class?
		for(String clazz : classes){
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
				if(name.contains("[")){
					name = name.substring(0, name.indexOf("["));
				}
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
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}
		// Is this type an inner class?
		for(String clazz : classes){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}
		// Variable name
		String name = ctx.getChild(ctx.getChildCount()-3).getText();
		if(name.contains("[")){
			name = name.substring(0, name.indexOf("["));
		}
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
		for(String clazz : classes){
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
					// Remove arguments
											
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
			for(String clazz : classes){
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
				for(String clazz : classes){
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
				for(String cl : classes){
					if(cl.contains(":")){
						if(var.equals(cl.substring(cl.lastIndexOf(":")+1,cl.length()))){
							var = cl;	
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
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}
		// Is this type an inner class?
		for(String clazz : classes){
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
			if(!location.isEmpty()){
				outerClass = location.peek();
			}

			String cName = type;
			if(!outerClass.equals("")){
				cName = outerClass + ":" + cName;
			}		
		
			location.push(cName);
			classes.add(cName);
			parents.put(location.peek(), type);
		}
	}
	
	@Override
	public void exitCreator(JavaParser.CreatorContext ctx){
		// Exit inner class
		if(ctx.getChild(ctx.getChildCount()-1).getChildCount() == 2){
			location.pop();
			if(ctx.getChild(ctx.getChildCount()-1).getChildCount() == 2){
				if(outerClass.contains(":")){
					outerClass = outerClass.substring(0,outerClass.lastIndexOf(":"));
				}else{
					outerClass = "";
				}
			}
		}
	}

	@Override
	public void enterInnerCreator(JavaParser.InnerCreatorContext ctx){
		String type = ctx.getChild(0).getText();
		if(type.contains("<")){
			type = type.substring(0, type.indexOf("<"));
		}
		if(type.contains("[")){
			type = type.substring(0, type.indexOf("["));
		}
		// Is this type an inner class?
		for(String clazz : classes){
			if(clazz.contains(":")){
				if(type.equals(clazz.substring(clazz.lastIndexOf(":")+1,clazz.length()))){
					type = clazz;
				}
			}
		}

		// If there is an anonymous class, we update location
		if(ctx.getChild(ctx.getChildCount()-1).getChildCount() == 2){
			if(!location.isEmpty()){
				outerClass = location.peek();
			}

			String cName = type;
			if(!outerClass.equals("")){
				cName = outerClass + ":" + cName;
			}		
		
			location.push(cName);
			classes.add(cName);
			parents.put(location.peek(), type);
		}	
	}
	
	@Override
	public void exitInnerCreator(JavaParser.InnerCreatorContext ctx){
		if(ctx.getChild(ctx.getChildCount()-1).getChildCount() == 2){
			location.pop();
			if(ctx.getChild(ctx.getChildCount()-1).getChildCount() == 2){
				if(outerClass.contains(":")){
					outerClass = outerClass.substring(0,outerClass.lastIndexOf(":"));
				}else{
					outerClass = "";
				}
			}
		}
	}

        // Getters and setters
	public boolean getCanCouple(){
		return canCouple;
	}

 	public Stack<String> getLocation(){
		return location;
	}

	public ArrayList<String> getClasses(){
		return classes;
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
