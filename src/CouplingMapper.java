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

public class CouplingMapper{

	private HashMap<String, HashMap<String, ArrayList<String>>> couplings;
	private HashMap<String, String> returnTypes;

	public CouplingMapper(){
		couplings = new HashMap<String, HashMap<String, ArrayList<String>>>();
		returnTypes = new HashMap<String, String>();
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
				// Filter couplings to remove non-project classes
				mapper.filterCouplings();
				// Simplify multi-level couplings
				// Run one more filtering pass to remove non-project classes
	
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
				JavaParser.CompilationUnitContext tree = parser.compilationUnit(); 
		
				CouplingVisitor visitor = new CouplingVisitor(); 
				visitor.visit(tree);
				HashMap<String, ArrayList<String>> coups = visitor.getCouplings();
				HashMap<String, String> rTypes = visitor.getReturnTypes();
				couplings.put(file, coups);

				for(String key : rTypes.keySet()){
					System.out.println(key + ":" + rTypes.get(key));
					returnTypes.put(key, rTypes.get(key));
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
		Set<String> pathList = couplings.keySet();
		ArrayList<String> classList = new ArrayList<String>();
		// Classlist stored as paths. Filter for just the class name
		for(String path : pathList){
			String[] parts = path.split("/");
			String cName = parts[parts.length - 1];
			cName = cName.substring(0, cName.indexOf("."));
			classList.add(cName);
		}

		for(String clazz : pathList){
			HashMap<String, ArrayList<String>> coups = couplings.get(clazz);
			for(String method : coups.keySet()){
				ArrayList<String> mCoups = coups.get(method);
				ArrayList<String> filteredCoups = new ArrayList<String>(); 
				for(int index = 0; index < mCoups.size(); index++){
					// Go over each coupling. These are indexed by class/method.
					String coupling = mCoups.get(index);
					String coupled = "";

					// If there are 2+ "." characters, we want to simplify
					if(coupling.contains(".")){
						String[] parts = coupling.split("\\.");
						while(parts.length > 2){
							System.out.println(coupling);
							// Get initial object
							coupled = parts[0];
							// Is this part of the project?
							if(classList.contains(coupled)){
								// Get method return type
								System.out.println("---"+coupled+"."+parts[1]);
								String rType = returnTypes.get(coupled + "." + parts[1]);
								// Replace coupling with return type
								coupling = rType;
								for(int word = 2; word < parts.length; word++){
									coupling = coupling + "." + parts[word];
								}
							}else{
								// If not, go ahead and break out.
								break;
							}
							parts = coupling.split("\\.");
						}
						coupled = coupling.substring(0, coupling.indexOf("."));
						System.out.println(coupling);
						System.out.println("-----------------------------");
					}else{
						coupled = coupling;
					}

					if(classList.contains(coupled)){
						filteredCoups.add(coupling);
					}
				}
				coups.put(method, filteredCoups);
				couplings.put(clazz, coups);
			}
		}
	}

	// Getters and setters
	public HashMap<String, HashMap<String, ArrayList<String>>> getCouplings(){
		return couplings;
	}

	public HashMap<String, String> getReturnTypes(){
		return returnTypes;
	}
}
