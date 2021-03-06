# Coupling-Mapping
------------------------

This project is inteded to map the dependencies between classes in a Java project, as measured using couplings between classes. In this context, a coupling is considered to be:

 * A call to a method in another class. This includes "new" declarations, as those call a constructor of a class.
 * The use of a data member of another class.

This is a context-insensitive analysis. We do not track the context that a method is called under, and overloaded methods are mostly considered to be a single method (we do not need contextual information for this project).

Use
------------------------

The project can be compiled using the provided ant file (target "ant compile"). First, run target "ant antlr" to generate Java code required for parsing. 

Class CouplingMapper is the main entry point. It generates a CSV file of couplings (Class.method -> Class.variable/method) for all Java classes in a project and a graph of the project. It takes one required argument - a path to the directory where Java files are contained - and one optional argument - a path of a file listing target classes). It recursively searches all subdirectories for Java files.

    java -jar CouplingMapper.jar -l=<path to code> -n=<project name> -t=<list of target classes> -d=<true/false, display graph> -o=<none(default)/random/ga - whether and how to optimize the set of classes to test> -p=<population for optimization, default is 100> -b=<search budget for optimization, default is 120 seconds> -r=<percent of population to retain for GA, default is 0.1> -x=<crossover rate for GA, default is 0.15> -m=<mutation rate for GA, default is 0.15>

The Java class CouplingVisitor generates a CSV file for an individual .Java file of couplings (Class.Method -> Class.variable/method). This is an unfiltered list (layered calls not simplified). CouplingMapper uses this, then filters the results.

Requirements
------------------------

Static analysis is performed using the Antlr framework (http://antlr.org). A version of the Antlr jar is distributed in this repository.

This project uses the Java 1.7 grammar file developed by Terrence Parr and Sam Harwell. This file is used as is without any modification and provided as part of this repository.

Graphs are produced using GraphStream (http://graphstream-project.org). Copies of all requires jars are distributed with this project.

Reporting Faults
------------------------

This utility is under development and has not been extensively tested. If you encounter any issues, please file a report, and I'll try to track it down.

