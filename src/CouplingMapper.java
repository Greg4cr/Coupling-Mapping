/*
* Gregory Gay (greg@greggay.com)
* Utility that maps couplings between all Java classes that are part 
* of a project.
* 
* Usage: java CouplingMapper 
* -l=<directory where project source is contained> 
* -n=<project name> 
* -t=<file containing list of targets> 
* -d=<true/false, display the graph>
* -o=<optimization mode, default is none. Options: random, none>
* -b=<search budget, default is 120 seconds>
* -p=<solution population, default is 100>
* -r=<percent of population to retain in GA, default is 0.1>
* -x=<crossover rate for GA, default is 0.15>
* -m=<mutation rate for GA, default is 0.15>
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;
import org.graphstream.algorithm.Dijkstra;
import static org.graphstream.algorithm.Toolkit.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
	// Project name
	private String project;
	// Graph of couplings
	private Graph graph;

	public CouplingMapper(){
		classList = new ArrayList<String>();
		unusableClasses = new ArrayList<String>();
		couplings = new HashMap<String, HashMap<String, ArrayList<String>>>();
		returnTypes = new HashMap<String, String>();
		parents = new HashMap<String, String>();
		variables = new HashMap<String, HashMap<String, String>>();
		project = "results";
		graph = new MultiGraph("couplings");
	}

	public static void main(String[] args) throws IllegalArgumentException{
		CouplingMapper mapper = new CouplingMapper();
		try{
			String path = "";
			Boolean display = true;	
			ArrayList<String> targets = new ArrayList<String>();
			int population = 100;
			int budget = 120;
			String mode = "none";
			double retention = 0.1;
			double crossover = 0.15;
			double mutation = 0.15;

			for(int arg = 0; arg < args.length; arg++){
				String[] words = args[arg].split("=");
				if(words[0].equals("-l")){
					path = words[1];
				}else if(words[0].equals("-n")){
					mapper.setProject(words[1]);
				}else if(words[0].equals("-t")){
					BufferedReader reader = new BufferedReader(new FileReader(words[1]));	
					String current = "";
					while((current = reader.readLine()) != null){
						// Strip out path information
						if(current.indexOf('.') >= 0){
							targets.add(current.substring(current.lastIndexOf('.') + 1, current.length()));
						}else{
							targets.add(current);
						}
					}
				}else if(words[0].equals("-d")){
					if(words[1].equals("true")){
						display=true;
					}else{
						display=false;
					}
				}else if(words[0].equals("-o")){
					mode = words[1];	
				}else if(words[0].equals("-b")){
					budget = Integer.parseInt(words[1]);
				}else if(words[0].equals("-p")){
					population = Integer.parseInt(words[1]);
				}else if(words[0].equals("-r")){
					retention = Double.parseDouble(words[1]);
				}else if(words[0].equals("-m")){
					mutation = Double.parseDouble(words[1]);
				}else if(words[0].equals("-x")){
					crossover = Double.parseDouble(words[1]);
				}else{
					throw new Exception("Incorrect Argument: " + words[0]);
				}
			}

			if(!path.equals("")){	
				// Produce list of Java classes.
				mapper.generateClassList(path);
				// Generate couplings for each class
				mapper.generateCouplings();
				// Filter couplings to remove non-project classes and simplify nesting
				mapper.filterCouplings();
				// Generate CSV of results 
				mapper.generateCSV();
				// Generate graph
				mapper.generateGraph(targets);
				// Generate set of classes to generate tests for.
				if(!mode.equals("none")){
					mapper.optimizeGenSet(path, targets, mode, population, budget, retention, mutation, crossover);
				}
				if(display){
					mapper.displayGraph();
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}	

	// Generate CSV of results
	public void generateCSV() throws IOException{
		BufferedWriter writer = new BufferedWriter(new FileWriter(project + ".csv"));	
		writer.write("# Class, Method, Coupling\n");
		for(String clazz : couplings.keySet()){ 
			HashMap<String, ArrayList<String>> coups = couplings.get(clazz);
			for(String method : coups.keySet()){
				for(String var : coups.get(method)){
					writer.write(clazz + "," + method + "," + var + "\n");
				}
			}
		}
		writer.close();
	}

	// Display the graph
	public void displayGraph(){
		graph.display();	
		//graph.addAttribute("ui.screenshot", project + ".png");	
	}

	// Generate graph
	public void generateGraph(ArrayList<String> targets){
		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");

		// Define custom coloring for graph
		graph.addAttribute("ui.stylesheet", "graph { fill-color: white; }" +
			"node { fill-color: black; text-background-mode: rounded-box; text-alignment: under; text-offset: 0, 5;}" +
			"node.target { fill-color: red; }" +
			"node.selected { fill-color: green; }" +
			"edge { shape: line; fill-color: #222; arrow-size: 5px, 4px;}" +
			"edge.high { fill-color: red; }" +
			"edge.medium { fill-color: orange; }" +
			"edge.low { fill-color: blue; }"
		);
		graph.addAttribute("ui.quality");
		graph.addAttribute("ui.antialias");

		// Add all classes as nodes
		for(String clazz: classList){
			graph.addNode(clazz);
			graph.getNode(clazz).addAttribute("ui.label", clazz);
			if(targets.contains(clazz)){
				graph.getNode(clazz).addAttribute("ui.class", "target");
			}
		}

		for(String clazz : couplings.keySet()){ 
			HashMap<String, ArrayList<String>> coups = couplings.get(clazz);
			for(String method : coups.keySet()){
				for(String var : coups.get(method)){
					// Get class that is coupled to another
					String source = "";
					if(method.contains(".")){
						source = method.substring(0, method.indexOf("."));
					}else{
						source = method;
					}

					// Get class that is the target
					String sink = "";
					if(var.contains(".")){
						sink = var.substring(0, var.indexOf("."));
					}else{
						sink = var;
					}
			
					// Do not add self-edges (to keep graph clean)
					if(!source.equals(sink)){
						// Edge name
						String eName = source + "-" + sink;
	
						// Add edge if it does not exist
						if(graph.getEdge(eName) == null){
							graph.addEdge(eName, source, sink, true);
							// Set weight
							graph.getEdge(eName).setAttribute("weight", 1.0);
						}else{
							graph.getEdge(eName).setAttribute("weight", graph.getEdge(eName).getNumber("weight") + 1);
						}
					}
				}
			}
		}
		/*
		// Color edges by degree of coupling
		for(Edge edge: graph.getEachEdge()){
			double weight = edge.getNumber("weight");
			if(weight <= 5){
				edge.addAttribute("ui.class", "low");
			}else if(weight <= 10){
				edge.addAttribute("ui.class", "medium");
			}else{
				edge.addAttribute("ui.class", "high");
			}
		}*/
	}

	// Optimizes a set of classes for test generation
	public void optimizeGenSet(String path, ArrayList<String> targets, String mode, int population, int budget, double retention, double mutation, double crossover) throws Exception{
		// Pre-compute and cache all shortest path distances and coverage
		// These are calculated per target class
		HashMap<String, ArrayList<Double>> pathLengths = new HashMap<String, ArrayList<Double>>();
		HashMap<String, ArrayList<ArrayList<Node>>> coverage = new HashMap<String, ArrayList<ArrayList<Node>>>();
		ArrayList<Double> maxLength = new ArrayList<Double>();
		ArrayList<HashSet<String>> maxCoverage = new ArrayList<HashSet<String>>();

		for(String target: targets){
			maxLength.add(0.0);
			maxCoverage.add(new HashSet<String>());
		}
		Dijkstra dijkstra = new Dijkstra(Dijkstra.Element.NODE, null, null);
		dijkstra.init(graph);
		for(String clazz: classList){
			if(!targets.contains(clazz)){
				// Shortest path to each target
				dijkstra.setSource(graph.getNode(clazz));
				dijkstra.compute();
				ArrayList<Double> lengths = new ArrayList<Double>();
				ArrayList<ArrayList<Node>> pathClasses = new ArrayList<ArrayList<Node>>();
				for (int currentTarget = 0; currentTarget < targets.size(); currentTarget++){
					String target = targets.get(currentTarget);
					Node node = graph.getNode(target);
					double length = dijkstra.getPathLength(node);
					lengths.add(length);
					if(length != Double.POSITIVE_INFINITY && length > maxLength.get(currentTarget)){
						maxLength.set(currentTarget, length);
					}
					ArrayList<Node> classes = new ArrayList(dijkstra.getPath(node).getNodePath());
					pathClasses.add(classes);
					HashSet<String> maxCoveredClasses = maxCoverage.get(currentTarget);
					for(Node n: classes){
						maxCoveredClasses.add(n.getId());
					}
					maxCoverage.set(currentTarget, maxCoveredClasses);
				}
			
				// Only add it to the cache if the node is on at least one path to a target
				int covSize = 0;
				for(int currentTarget = 0; currentTarget < targets.size(); currentTarget++){
					covSize += pathClasses.get(currentTarget).size();
				}

				if(covSize > 0){
					pathLengths.put(clazz, lengths);
					coverage.put(clazz, pathClasses);
					//System.out.println("------\n" + clazz);
					//System.out.println(lengths.toString());
					//System.out.println(pathClasses.toString());
				}
				dijkstra.clear();
			}
		}
		// Generate solutions
		ArrayList<String> solution = new ArrayList<String>();
		if(mode.equals("random")){
			solution = randomSearch(population, budget, targets, pathLengths, coverage, maxLength, maxCoverage);
		}else if(mode.equals("ga")){
			solution = geneticSearch(population, budget, targets, pathLengths, coverage, maxLength, maxCoverage, retention, mutation, crossover);
		}else{
			throw new Exception("Invalid search mode: " + mode);
		}

		for(String clazz: classList){
			if(solution.contains(clazz)){
				graph.getNode(clazz).addAttribute("ui.class", "selected");
			}
		}

		// Output list to a file
		for(String target: targets){
			solution.add(target);
		}
		System.out.println("Size: " + solution.size() + " / " + classList.size());

		BufferedWriter writer = new BufferedWriter(new FileWriter(project + ".src"));
		// Need to get the path name
		for(String clazz: solution){
			for(String fileName: couplings.keySet()){
				if(path.indexOf('/') == 0){
					if(fileName.indexOf('/') != 0){
						fileName = "/" + fileName;
					}
				}
				fileName = fileName.replace(path,"");
				fileName = fileName.replace(".java","");
				fileName = fileName.replace("/",".");
				if(clazz.contains("$")){
					String superClazz = clazz.substring(0, clazz.indexOf("$"));
					String subClazz = clazz.substring(clazz.indexOf("$"), clazz.length());
					if(superClazz.equals(fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length()))){
						clazz = fileName + subClazz;
						break;
					}
				}else{
					if(clazz.equals(fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length()))){
						clazz = fileName;
						break;
					}
				}	
			}
			writer.write(clazz + "\n");
		}
		writer.close();
	}

	// Simple random search. Generates populations of solutions, tracks the best,
	// and continues until the budget is exhausted 
	public ArrayList<String> randomSearch(int population, int budget, ArrayList<String> targets, 
			HashMap<String, ArrayList<Double>> pathLengths, HashMap<String, ArrayList<ArrayList<Node>>> coverage,
			ArrayList<Double> maxLength, ArrayList<HashSet<String>> maxCoverage){

		// Track the best solution seen		
		ArrayList<String> bestSolution = new ArrayList<String>();
		double bestScore = 100000;

		boolean timeRemaining = true;
		long startingTime = System.currentTimeMillis();
		long elapsedTime = (System.currentTimeMillis() - startingTime) / 1000;

		int generations = 0;

		while(elapsedTime <= budget){
			// If time remains in the budget, generate a population of solutions
			ArrayList<ArrayList<String>> solutions = new ArrayList<ArrayList<String>>();
			ArrayList<Double> scores = new ArrayList<Double>();
			generations++;
			ArrayList<String> classes = new ArrayList<String>(pathLengths.keySet());

			for(int member = 0; member < population; member++){
				HashSet<String> solutionSet = new HashSet<String>();

				for(int choice = 0; choice < ThreadLocalRandom.current().nextInt(1, classes.size()); choice++){
					solutionSet.add(classes.get(ThreadLocalRandom.current().nextInt(0, classes.size())));
				}
				solutions.add(new ArrayList<String>(solutionSet));
				// Score solution
				scores.add(scoreSolution(solutions.get(member), targets, pathLengths, coverage, maxLength, maxCoverage));
				// If the score is better, mark this as the "best" to date
				if(scores.get(member) < bestScore){
					bestScore = scores.get(member);
					bestSolution = solutions.get(member);
				}
			}

			// How much time has elapsed?
			elapsedTime = (System.currentTimeMillis() - startingTime) / 1000;
			//System.out.println(generations + " : " + elapsedTime);
		}

		System.out.println("-----\n" + bestScore + " : " + bestSolution.toString());	
		return bestSolution;
	}

	// Simple genetic algorithm. Generates populations of solutions, tracks the best,
	// formulates new population using retention, mutation, crossover, and replacement.
	// Continues until the budget is exhausted 
	public ArrayList<String> geneticSearch(int population, int budget, ArrayList<String> targets, 
			HashMap<String, ArrayList<Double>> pathLengths, HashMap<String, ArrayList<ArrayList<Node>>> coverage,
			ArrayList<Double> maxLength, ArrayList<HashSet<String>> maxCoverage, double retention, double mutation, double crossover){

		ArrayList<String> classes = new ArrayList<String>(pathLengths.keySet());
		// Track the best solution seen		
		ArrayList<String> bestSolution = new ArrayList<String>();
		double bestScore = 100000;

		int generations = 0;
		ArrayList<ArrayList<String>> solutions = new ArrayList<ArrayList<String>>();
		ArrayList<Double> scores = new ArrayList<Double>();

		// Form initial population completely at random
		for(int member = 0; member < population; member++){
			HashSet<String> solutionSet = new HashSet<String>();

			for(int choice = 0; choice < ThreadLocalRandom.current().nextInt(1, classes.size()); choice++){
				solutionSet.add(classes.get(ThreadLocalRandom.current().nextInt(0, classes.size())));
			}
			solutions.add(new ArrayList<String>(solutionSet));
			// Score solution
			scores.add(scoreSolution(solutions.get(member), targets, pathLengths, coverage, maxLength, maxCoverage));
			// If the score is better, mark this as the "best" to date
			if(scores.get(member) < bestScore){
				bestScore = scores.get(member);
				bestSolution = solutions.get(member);
			}
		}

		boolean timeRemaining = true;
		long startingTime = System.currentTimeMillis();
		long elapsedTime = (System.currentTimeMillis() - startingTime) / 1000;

		while(elapsedTime <= budget){
			// If time remains in the budget, generate a population of solutions
			generations++;

			// Form new population
			ArrayList<ArrayList<String>> newSolutions = new ArrayList<ArrayList<String>>();
			ArrayList<Double> newScores = new ArrayList<Double>();

			// Retain the top-scoring population members
			for(int choice = 0; choice < ((int) (retention * population)); choice++){
				double topScore = 100000;
				int topPosition = -1;
				for(int score = 0; score < scores.size(); score++){
					if(scores.get(score) < topScore){
						topScore = scores.get(score);
						topPosition = score;
					}
				}
				if(topPosition >= 0){
					newSolutions.add(solutions.get(topPosition));
					newScores.add(topScore);
					solutions.remove(topPosition);
					scores.remove(topScore);
				}
			}

			// Mutate some of these members
			ArrayList<ArrayList<String>> mutatedSols = new ArrayList<ArrayList<String>>();
			for(int choice = 0; choice < ((int) (mutation * population)); choice++){
				if(newSolutions.size() > 0){
					ArrayList<String> chosen = newSolutions.get(ThreadLocalRandom.current().nextInt(0, newSolutions.size()));
					ArrayList<String> mutated = mutateSolution(chosen, classes);
					mutatedSols.add(mutated);
				}
			}
			
			// Perform crossover to create new members
			for(int choice = 0; choice < ((int) (crossover * population)); choice += 2){
				if(newSolutions.size() > 0){
					int first = 0;
					int second = 0;
					while(first == second){
						// Which solutions should be the parents?
						first = ThreadLocalRandom.current().nextInt(0, newSolutions.size());
						second = ThreadLocalRandom.current().nextInt(0, newSolutions.size());
					}

					ArrayList<ArrayList<String>> children = crossoverSolutions(newSolutions.get(first), newSolutions.get(second));
					mutatedSols.add(children.get(0));
					mutatedSols.add(children.get(1));
				}
			}

			// Add mutated and crossover solutions to set
			for(ArrayList<String> mutated: mutatedSols){	
				newSolutions.add(mutated);
				newScores.add(scoreSolution(mutated, targets, pathLengths, coverage, maxLength, maxCoverage));
			}
	
			// Fill the rest randomly
			int start = newSolutions.size() - 1;
			if(start < 0){
				start = 0;
			}
			for(int member = start; member < population; member++){
				HashSet<String> solutionSet = new HashSet<String>();

				for(int choice = 0; choice < ThreadLocalRandom.current().nextInt(1, classes.size()); choice++){
					solutionSet.add(classes.get(ThreadLocalRandom.current().nextInt(0, classes.size())));
				}
				newSolutions.add(new ArrayList<String>(solutionSet));
				// Score solution
				newScores.add(scoreSolution(newSolutions.get(member), targets, pathLengths, coverage, maxLength, maxCoverage));
				// If the score is better, mark this as the "best" to date
				if(newScores.get(member) < bestScore){
					bestScore = newScores.get(member);
					bestSolution = newSolutions.get(member);
				}
			}

			// Set new population as population
			solutions = newSolutions;
			scores = newScores;

			// How much time has elapsed?
			elapsedTime = (System.currentTimeMillis() - startingTime) / 1000;
			//System.out.println(generations + " : " + elapsedTime);
		}

		System.out.println("-----\n" + bestScore + " : " + bestSolution.toString());
		return bestSolution;
	}

	// Mutate a solution by adding, deleting, or changing one of the classes
	public ArrayList<String> mutateSolution(ArrayList<String> solution, ArrayList<String> classes){
		// First, choose whether you are adding, deleting, or changing a reference.
		HashSet<String> mutated = new HashSet<String>(solution);

		int choice = ThreadLocalRandom.current().nextInt(1, 4);

		// If all classes in set, delete one
		if(choice == 1 && mutated.size() == classes.size()){
			choice = 2;
		// If no classes in set, add one
		}else if(choice >= 2 && mutated.size() == 0){
			choice = 1;
		}

		if(choice == 1){
			// Add a class
			int initSize = mutated.size();
			while(initSize == mutated.size()){
				mutated.add(classes.get(ThreadLocalRandom.current().nextInt(0, classes.size())));
			}
		}else if(choice == 2){
			// Delete a class
			mutated.remove(solution.get(ThreadLocalRandom.current().nextInt(0, solution.size())));
		}else{
			// Change one class to another. Really a delete and an add (make sure they aren't the same
			// The class to delete.
			String toRemove = solution.get(ThreadLocalRandom.current().nextInt(0, solution.size()));
			mutated.remove(toRemove);
			// The class to add
			int initSize = mutated.size();
			while(initSize == mutated.size()){
				String toAdd = classes.get(ThreadLocalRandom.current().nextInt(0, classes.size()));
				if(!toAdd.equals(toRemove)){
					mutated.add(toAdd);
				}
			}
		}

		return new ArrayList<String>(mutated);
	}	

	// Create two children by performing crossover between two parents
	// Implements discrete recombination
	public ArrayList<ArrayList<String>> crossoverSolutions(ArrayList<String> firstParent, ArrayList<String> secondParent){
		ArrayList<ArrayList<String>> children = new ArrayList<ArrayList<String>>();
		HashSet<String> firstChild = new HashSet<String>();
		HashSet<String> secondChild = new HashSet<String>();
		int maxPos = Math.max(firstParent.size(), secondParent.size());

		// First child
		// For each position in a parent solution
		for(int position = 0; position < maxPos; position++){
			// Flip a coin
			if(ThreadLocalRandom.current().nextInt(1, 3) == 1){
				// Take from first parent
				if(position < firstParent.size()){
					firstChild.add(firstParent.get(position));
				}
			}else{
				// Take from second parent
				if(position < secondParent.size()){
					firstChild.add(secondParent.get(position));
				}
			}
		}
		// Second child
		// For each position in a parent solution
		for(int position = 0; position < maxPos; position++){
			// Flip a coin
			if(ThreadLocalRandom.current().nextInt(1, 3) == 1){
				// Take from first parent
				if(position < firstParent.size()){
					secondChild.add(firstParent.get(position));
				}
			}else{
				// Take from second parent
				if(position < secondParent.size()){
					secondChild.add(secondParent.get(position));
				}
			}
		}
		children.add(new ArrayList<String>(firstChild));
		children.add(new ArrayList<String>(secondChild));

		return children;
	}

	/* Calculate a score for a set of classes
	 * Score is the Euclidean distance to a sweet spot of min distance, 
	 * min size, and max coverage.
	 * Distance = average distance from target for each node
	 * Max distance is calculated from longest "shortest" path
	 * Min distance = 2.0 (source, target)
	 * Coverage = number of unique classes covered by the set, per target
	 * Max coverage is all classes on a path to one of the targets
	 * Min coverage = 2.0 (source, target)
	 * score = root(norm(distance)^2 + norm(size)^2 + (norm(coverage) - 1)^2)
	 * Calculated for each target, then summed.
	 */
	public double scoreSolution(ArrayList<String> solution, ArrayList<String> targets, 
			HashMap<String, ArrayList<Double>> pathLengths, HashMap<String, ArrayList<ArrayList<Node>>> coverage,
			ArrayList<Double> maxLength, ArrayList<HashSet<String>> maxCoverage){
		ArrayList<Double> scores = new ArrayList<Double>();
		for(int currentTarget = 0; currentTarget < targets.size(); currentTarget++){
			double avgDistance = 0.0;
			HashSet<String> coveredClasses = new HashSet<String>();
			int omitted = 0;
			for(int currentNode = 0; currentNode < solution.size(); currentNode++){
				String node = solution.get(currentNode);
				double toAdd = pathLengths.get(node).get(currentTarget);
				if(toAdd != Double.POSITIVE_INFINITY){
					avgDistance += toAdd;
				}else{
					omitted++;
				}

				for(Node cov : coverage.get(node).get(currentTarget)){
					coveredClasses.add(cov.getId());
				}	
			}
			if(solution.size() - omitted == 0){
				avgDistance = maxLength.get(currentTarget);
				// No path to target in chosen set.
			}else{
				avgDistance = avgDistance / (solution.size() - omitted);
			}

			// Normalize the distance
			if(maxLength.get(currentTarget) > 2.0){
				avgDistance = (avgDistance - 2.0) / (maxLength.get(currentTarget) - 2.0);
			}else{
				avgDistance = 0.0;
			}	

			// Calculate the set size
			double size = (solution.size() - 1.0) / (pathLengths.keySet().size() - 1.0);

			// Calculate coverage
			double coverageScore = coveredClasses.size();
			coverageScore = (coverageScore - 2.0) / (maxCoverage.get(currentTarget).size() - 2.0);
			coverageScore = coverageScore - 1; // Convert to minimization

			// Combine
			double score = Math.sqrt(Math.pow(avgDistance, 2) + Math.pow(size, 2) + Math.pow(coverageScore, 2));
			//System.out.println("---" + avgDistance + "," + size + "," + coverageScore);

			scores.add(score);
		}

		//System.out.println(scores.toString());

		double finalScore = 0.0;
		for(double score: scores){
			finalScore += score;
		}
		return finalScore;
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
	public void generateCouplings() throws IOException{
		BufferedWriter writer = new BufferedWriter(new FileWriter(project + ".log"));
		for(String file : couplings.keySet()){
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
						writer.write("Warning: Multiple Class Definitions: " + clazz + "\n");
					}else{
						classList.add(clazz);
					}
				}else{
					if(unusableClasses.contains(clazz)){
						writer.write("Warning: Multiple Class Definitions: " + clazz + "\n");
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
							writer.write("Warning: Multiple Method Definitions: " + key + " = {" + returnTypes.get(key) + ", " + rTypes.get(key) + "}\n");
						}
					}
					returnTypes.put(key, rTypes.get(key));
				}
			}

			for(String key: parentList.keySet()){
				if(classList.contains(key) || unusableClasses.contains(key)){
					if(parents.containsKey(key)){
						if(!parents.get(key).equals(parentList.get(key))){
							writer.write("Warning: Multiple Class Definitions: " + key + ", Conflicting Parents = {" + parents.get(key) + ", " + parentList.get(key) + "}\n");
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
										writer.write("Warning: Multiple Class Definitions: " + key + ", Conflicting Variable: " + gv + " = {" + eVars.get(gv) + ", " + gVars.get(gv) + "}\n");
									}
								}
								eVars.put(gv, gVars.get(gv));
							}	
							variables.put(key, eVars);
						}else{
							variables.put(key, gVars);
						}
						//System.out.println("+" + key + "-" + variables.get(key));
					}
				}
			}
		}
		writer.close();
	}

	/* Filter couplings to simplify nested couplings
	 * For example, X.y.z is filtered for the return type of X.y, 
	 * to become A.z.
	 * In this process, non-project classes are also removed.
	 */
	public void filterCouplings() throws IOException{
		BufferedWriter writer = new BufferedWriter(new FileWriter(project + ".log"));
		for(String clazz : couplings.keySet()){
			HashMap<String, ArrayList<String>> coups = couplings.get(clazz);
			for(String method : coups.keySet()){
				ArrayList<String> mCoups = coups.get(method);
				ArrayList<String> filteredCoups = new ArrayList<String>(); 
				for(int index = 0; index < mCoups.size(); index++){
					//System.out.println("-----------------------------:" + clazz);
					// Go over each coupling. These are indexed by class/method.
					String coupling = mCoups.get(index);
					String coupled = "";

					//System.out.println(coupling);

					// If there are 2+ "." characters, we want to simplify
					if(coupling.contains(".")){
						String[] parts = coupling.split("\\.");
						coupled = parts[0];
						// References to methods of a parent and package names might get through.
						if(!classList.contains(coupled) && !unusableClasses.contains(coupled)){
							if(!coupled.equals("int") && !coupled.equals("short") && !coupled.equals("long")
								&& !coupled.equals("char") && !coupled.equals("byte") && !coupled.equals("float")
								&& !coupled.equals("double") && !coupled.equals("boolean") && !coupled.equals("primitive")){
	
								boolean found = false;
								String cName = "";
								if(method.contains(".")){
									cName = method.substring(0, method.indexOf("."));
								}else{
									cName = method;
								}
								// Is this a package name?
								
								if(Character.isLowerCase(coupled.charAt(0))){
									String rest = "";
									boolean print = false;
									for(int word = 1; word < parts.length; word++){
										if(print){
											rest = rest + "." + parts[word];
										}else{
											if(parts[word].length() > 0 && Character.isUpperCase(parts[word].charAt(0))){
												print = true;
												rest = parts[word];
											}
										}
									}
									if(!rest.equals("")){
										found = true;
										coupling = rest;
										parts = coupling.split("\\.");
										coupled = parts[0];
									}
								}

								// Could this be inherited?
								if(!found){	
									if(parents.containsKey(cName) && classList.contains(parents.get(cName))){
										String potential = parents.get(cName) + "." + coupled;
										if(returnTypes.containsKey(potential) || 
											(variables.containsKey(parents.get(cName)) && variables.get(parents.get(cName)).containsKey(coupled)) ||
											Character.isLowerCase(coupled.charAt(0))){
											// If this is a variable or method of the parent
											coupling = potential;
											// Replace coupling with return type
											for(int word = 1; word < parts.length; word++){
												coupling = coupling + "." + parts[word];
											}
									
											parts = coupling.split("\\.");
											found = true;
										}
									}
								}
								
								// One last try - could be inherited from an abstract parent
								if(!found){
									if(parents.containsKey(cName) && unusableClasses.contains(parents.get(cName))){
										// If this is a variable or method of the parent
										coupling = parents.get(cName) + "." + coupled;
										// Replace coupling with return type
										for(int word = 1; word < parts.length; word++){
											coupling = coupling + "." + parts[word];
										}
								
										parts = coupling.split("\\.");
									}
								}
							}
						}

						while(parts.length > 2){
							//System.out.println(coupling);
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
									writer.write("Not Found: " + coupling + "\n");
									break;
								}	
							}else{
								// If not, go ahead and break out.
								break;
							}
							parts = coupling.split("\\.");
						}

						if(coupling.contains(".")){
							coupled = coupling.substring(0, coupling.indexOf("."));
						}else{
							coupled = coupling;
						}
						//System.out.println(coupling);
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
									//System.out.println("--" + parents.get(pName));	
									if(!classList.contains(parents.get(pName))){
										if(unusableClasses.contains(parents.get(pName))){
											writer.write("Coupled to interface or abstract class: " + coupling + "\n");
										}else{
											writer.write("Coupled to non-project parent: " + coupling + "\n");
										}
										found = true;
										break;

									}else{
										pName = parents.get(pName);	
										String newCoupling = pName + "." + mName;
										//System.out.println("--" + newCoupling);


										if(returnTypes.containsKey(newCoupling)){
											filteredCoups.add(newCoupling);
											//System.out.println(newCoupling);
											found = true;
										}else if(variables.containsKey(pName) && variables.get(pName).containsKey(mName)){
											filteredCoups.add(newCoupling);
											found = true;
											//System.out.println(newCoupling);
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
								writer.write("Not Found: " + coupling + "\n");
							}
						}else{
							filteredCoups.add(coupling);
						}
					}else if(unusableClasses.contains(coupled)){
						writer.write("Coupled to abstract class or interface: " + coupling + "\n");
					}else{
						writer.write("Coupled to non-project class: " + coupling + "\n");
					}
				}
				coups.put(method, filteredCoups);
				couplings.put(clazz, coups);
			}
		}
		writer.close();
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

	public String getProject(){
		return project;
	}

	public void setProject(String project){
		this.project = project;
	}

	public Graph getGraph(){
		return graph;
	}
	
	public void setGraph(Graph graph){
		this.graph = graph;
	}
}
