/*
* Gregory Gay (greg@greggay.com)
* Utility that maps couplings between all Java classes that are part 
* of a project.
* 
* Usage: java CouplingMapper <directory where project source is contained>
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class CouplingMapper{
	// List of classes
	private ArrayList<String> classList;
	// List of interfaces and abstract classes
	private ArrayList<String> unusableClasses;
	// Couplings between classes
	private HashMap<String, HashMap<String, ArrayList<String>>> couplings;
	// Return types of class methods
	private HashMap<String, String> returnTypes;
	// Parents of classes
	private HashMap<String, String> parents;
	// Global variables (names/types) for each class
	private HashMap<String, HashMap<String, String>> variables;

	public CouplingMapper(){
		classList = new ArrayList<String>();
		unusableClasses = new ArrayList<String>();
		couplings = new HashMap<String, HashMap<String, ArrayList<String>>>();
		returnTypes = new HashMap<String, String>();
		parents = new HashMap<String, String>();
		variables = new HashMap<String, HashMap<String, String>>();
	}

	public static void main(String[] args) throws IllegalArgumentException{
		if(args.length != 1){
			throw new IllegalArgumentException("Incorrect number of arguments: "+args.length);
		}else{
			CouplingMapper mapper = new CouplingMapper();
			try{
				// Produce list of Java classes.
				mapper.generateClassList(args[0]);
				// Generate couplings for each class
				mapper.generateCouplings();
				// Filter couplings to remove non-project classes and simplify nesting
				mapper.filterCouplings();
	
				// CSV of results
				System.out.println("# Class, Method, Coupling");
				for(String clazz : mapper.getCouplings().keySet()){ 
					HashMap<String, ArrayList<String>> coups = mapper.getCouplings().get(clazz);
					for(String method : coups.keySet()){
						for(String var : coups.get(method)){
							System.out.println(clazz + "," + method + "," + var);
						}
					}
				}

			}catch(IOException e){
				e.printStackTrace();
			}
		}	
	}

	// Generates a list of Java files from a directory
	public void generateClassList(String directory) throws IOException{
		File dir = new File(directory);

		// Does the path exist?
		if(dir.exists()){
			// Is it a directory?
			if(dir.isDirectory()){
				ArrayList<String> listing = generateJavaFileList(dir, new ArrayList<String>());
				for(String file: listing){
					couplings.put(file, new HashMap<String, ArrayList<String>>());
				}
			}else{
				throw new IOException("The provided path " + directory + " is not a directory.");
			}
		}else{
			throw new IOException("Directory " + directory + " does not exist.");
		}	
	}

	/* Helper function for {@link #generateClassList(String)}.
	 * Recursively builds a list of Java files from a directory listing.
	 */
	public ArrayList<String> generateJavaFileList(File directory, ArrayList<String> list){
		File[] fList = directory.listFiles();
		for(File file: fList){
			if(file.toString().contains(".java")){
				list.add(file.toString());
			}else if(file.isDirectory()){
				list = generateJavaFileList(file, list);
			}
		}	
		return list;
	}

	// Gather couplings for each class. 
	public void generateCouplings(){
		for(String file : couplings.keySet()){
			try{
				ANTLRInputStream input = new ANTLRInputStream(new FileInputStream(file));
				JavaLexer lexer = new JavaLexer(input);
				CommonTokenStream tokens = new CommonTokenStream(lexer);
				JavaParser parser = new JavaParser(tokens);
				ParseTree tree = parser.compilationUnit(); 
				ParseTreeWalker walker = new ParseTreeWalker();		
				CouplingVisitor visitor = new CouplingVisitor(); 
				walker.walk(visitor, tree);

				HashMap<String, Boolean> classes = visitor.getClasses();
				HashMap<String, ArrayList<String>> coups = visitor.getCouplings();
				HashMap<String, String> rTypes = visitor.getReturnTypes();
				HashMap<String, String> parentList = visitor.getParents();
				HashMap<String, HashMap<String, String>> allVars = visitor.getVariables();

				for(String clazz : classes.keySet()){
					if(classes.get(clazz)){	
						if(classList.contains(clazz)){
							System.out.println("Warning: Multiple Class Definitions: " + clazz);
						}else{
							classList.add(clazz);
						}
					}else{
						if(unusableClasses.contains(clazz)){
							System.out.println("Warning: Multiple Class Definitions: " + clazz);
						}else{
							unusableClasses.add(clazz);
						}
					}
				}

				HashMap<String, ArrayList<String>> coupsToAdd = new HashMap<String, ArrayList<String>>();
				for(String clazz : coups.keySet()){
					String cl = "";
					if(clazz.contains(".")){
						cl = clazz.substring(0, clazz.indexOf("."));
					}else{
						cl = clazz;
					}

					if(classList.contains(cl)){
						coupsToAdd.put(clazz, coups.get(clazz));
					}
				}

				couplings.put(file, coupsToAdd);


				for(String key : rTypes.keySet()){
					String clazz = key.substring(0,key.indexOf("."));

					if(classList.contains(clazz)){
						if(returnTypes.containsKey(key)){
							if(!returnTypes.get(key).equals(rTypes.get(key))){
								System.out.println("Warning: Multiple Method Definitions: " + key + " = {" + returnTypes.get(key) + ", " + rTypes.get(key) + "}");
							}
						}
						returnTypes.put(key, rTypes.get(key));
					}
				}

				for(String key: parentList.keySet()){
					System.out.println("+" + key + "," + parentList.get(key));
					if(classList.contains(key) || unusableClasses.contains(key)){
						if(parents.containsKey(key)){
							if(!parents.get(key).equals(parentList.get(key))){
								System.out.println("Warning: Multiple Class Definitions: " + key + ", Conflicting Parents = {" + parents.get(key) + ", " + parentList.get(key) + "}");
							}
						}
						parents.put(key, parentList.get(key));	
					}
				}

				for(String key: allVars.keySet()){
					String clazz = "";
					if(key.contains(".")){
						clazz = key.substring(0, key.indexOf("."));
					}else{
						clazz = key;
					}
					if(classList.contains(clazz)){
						if(!key.contains(".")){
							// Looking only for global variables
							HashMap<String, String> gVars = allVars.get(key);
				
							if(variables.containsKey(key)){
								HashMap<String, String> eVars = variables.get(key);
								for(String gv: gVars.keySet()){
									if(eVars.containsKey(gv)){
										if(!eVars.get(gv).equals(gVars.get(gv))){
											System.out.println("Warning: Multiple Class Definitions: " + key + ", Conflicting Variable: " + gv + " = {" + eVars.get(gv) + ", " + gVars.get(gv) + "}");
										}
									}
									eVars.put(gv, gVars.get(gv));
								}	
								variables.put(key, eVars);
							}else{
								variables.put(key, gVars);
							}
							//System.out.println(key + ":" + variables.get(key));
						}
					}
				}
				
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}

	/* Filter couplings to simplify nested couplings
	 * For example, X.y.z is filtered for the return type of X.y, 
	 * to become A.z.
	 * In this process, non-project classes are also removed.
	 */
	public void filterCouplings(){

		for(String clazz : couplings.keySet()){
			HashMap<String, ArrayList<String>> coups = couplings.get(clazz);
			for(String method : coups.keySet()){
				ArrayList<String> mCoups = coups.get(method);
				ArrayList<String> filteredCoups = new ArrayList<String>(); 
				for(int index = 0; index < mCoups.size(); index++){
					System.out.println("-----------------------------:" + clazz);
					// Go over each coupling. These are indexed by class/method.
					String coupling = mCoups.get(index);
					String coupled = "";

					System.out.println(coupling);

					// If there are 2+ "." characters, we want to simplify
					if(coupling.contains(".")){
						String[] parts = coupling.split("\\.");
						coupled = parts[0];
						// References to methods of a parent might get through.
						if(!classList.contains(coupled) && Character.isLowerCase(coupled.charAt(0))){
							String cName = "";
							if(method.contains(".")){
								cName = method.substring(0, method.indexOf("."));
							}else{
								cName = method;
							}
							if(parents.containsKey(cName)){
								coupling = parents.get(cName) + "." + coupled;
								// Replace coupling with return type
								for(int word = 1; word < parts.length; word++){
									coupling = coupling + "." + parts[word];
								}
								
								parts = coupling.split("\\.");
							}
						}

						while(parts.length > 2){
							System.out.println(coupling);
							// Get initial object
							coupled = parts[0];	
							// Is this part of the project?
							if(classList.contains(coupled)){
								// Get method return type
								if(returnTypes.containsKey(coupled + "." + parts[1])){
									String rType = returnTypes.get(coupled + "." + parts[1]);
									coupling = rType;
									// Replace coupling with return type
									for(int word = 2; word < parts.length; word++){
										coupling = coupling + "." + parts[word];
									}
								}else if(variables.containsKey(coupled) && variables.get(coupled).containsKey(parts[1])){
									// If it isn't a method, it may be a local variable
									String rType = variables.get(coupled).get(parts[1]);
									coupling = rType;
									// Replace coupling with return type
									for(int word = 2; word < parts.length; word++){
										coupling = coupling + "." + parts[word];
									}
								}else if(parts[1].equals("this")){
									// References to "this" that get through must be filtered.
									coupling = coupled;
									for(int word = 2; word < parts.length; word++){
										coupling = coupling + "." + parts[word];
									}
								}else if(parts[1].equals("class") || parts[1].equals("getClass") || parts[1].equals("getName") || parts[1].equals("getType")){
									// Filter out .class references
									coupling = "Class";
									for(int word = 2; word < parts.length; word++){
										coupling = coupling + "." + parts[word];
									}
								}else if(parts[1].equals("getObject") || parts[1].equals("clone")){
									coupling = "Object";
									for(int word = 2; word < parts.length; word++){
										coupling = coupling + "." + parts[word];
									}
								}else if(parts[1].equals("equals") || parts[1].equals("finalize") || parts[1].equals("hashCode") || parts[1].equals("notify") || parts[1].equals("notifyAll") || parts[1].equals("toString") || parts[1].equals("wait")){
									coupling = "primitive";
									for(int word = 2; word < parts.length; word++){
										coupling = coupling + "." + parts[word];
									}
								}else if(parents.containsKey(coupled)){
									// If we lack the return type and it's a project class,
									// and this is not a reference to a class variable
									// it is likely inherited from a parent class
									String rType = parents.get(coupled);
									coupling = rType;
									// Replace coupling with return type
									for(int word = 1; word < parts.length; word++){
										coupling = coupling + "." + parts[word];
									}
								}else{
									System.out.println("Not Found: " + coupling);
									break;
								}
							}else{
								// If not, go ahead and break out.
								break;
							}
							parts = coupling.split("\\.");
						}

						coupled = coupling.substring(0, coupling.indexOf("."));
						System.out.println(coupling);
					}else{
						coupled = coupling;
					}

					if(classList.contains(coupled)){
						// Make sure the method or variable exists.
						if(coupling.contains(".")){
							String mName = coupling.substring(coupling.indexOf(".") + 1, coupling.length());
							boolean found = false;

							if(returnTypes.containsKey(coupling)){
								// Do we have a return type?
								filteredCoups.add(coupling);
								found = true;
							}else if(variables.containsKey(coupled) && variables.get(coupled).containsKey(mName)){
								// Is it a variable?
								filteredCoups.add(coupling);
								found = true;
							}else if(mName.equals("this")){
								// Do we have a "this"?
								filteredCoups.add(coupled);
								found = true;
							}
						
							// Is it a variable or method inherited from a parent?
							if(!found){
								String pName = coupled;
								while(parents.containsKey(pName) && !found){
									System.out.println("--" + parents.get(pName));	
									if(!classList.contains(parents.get(pName))){
										if(unusableClasses.contains(parents.get(pName))){
											System.out.println("Coupled to abstract class or interface: " + coupling);
										}else{
											System.out.println("Coupled to non-project parent: " + coupling);
										}
										found = true;
										break;

									}else{
										pName = parents.get(pName);	
										String newCoupling = pName + "." + mName;
										System.out.println("--" + newCoupling);


										if(returnTypes.containsKey(newCoupling)){
											filteredCoups.add(newCoupling);
											System.out.println(newCoupling);
											found = true;
										}else if(variables.containsKey(pName) && variables.get(pName).containsKey(mName)){
											filteredCoups.add(newCoupling);
											found = true;
											System.out.println(newCoupling);
										}

										if(parents.containsKey(pName) && parents.get(pName).equals(pName)){
											break;
										}
									}						
								}
							}

							// Is it a method automatically derived from Object?
							if(!found){
								if(mName.equals("equals") || mName.equals("finalize") || mName.equals("hashCode") || mName.equals("notify") || mName.equals("notifyAll") || mName.equals("toString") || mName.equals("wait") || mName.equals("getClass") || mName.equals("getObject") || mName.equals("clone") || mName.equals("length")){
									// Do nothing
									found = true;
								}
							}

							if(!found){
								System.out.println("Not Found: " + coupling);
							}
						}else{
							filteredCoups.add(coupling);
						}
					}else if(unusableClasses.contains(coupled)){
						System.out.println("Coupled to abstract class or interface: " + coupling);
					}else{
						System.out.println("Coupled to non-project class: " + coupling);
					}
				}
				coups.put(method, filteredCoups);
				couplings.put(clazz, coups);
			}
		}
	}

	// Getters and setters
	public ArrayList<String> getClassList(){
		return classList;
	}

	public ArrayList<String> getUnusableClasses(){
		return unusableClasses;
	}

	public HashMap<String, HashMap<String, ArrayList<String>>> getCouplings(){
		return couplings;
	}

	public HashMap<String, String> getReturnTypes(){
		return returnTypes;
	}

	public HashMap<String, String> getParents(){
		return parents;
	}
	
	public HashMap<String, HashMap<String, String>> getVariables(){
		return variables;
	}
}
