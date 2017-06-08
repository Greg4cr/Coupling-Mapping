# Coupling-Mapping
------------------------

This project is inteded to map the dependencies between classes in a Java project, as measured using couplings between classes. In this context, a coupling is considered to be:
 * A call to a method in another class. This includes "new" declarations, as those call a constructor of a class.
 * The use of a data member of another class.

Use
------------------------

The project can be compiled using the provided ant file (target "ant compile"). First, run target "ant antlr" to generate Java code required for parsing. 

The Java class CouplingVisitor generates a CSV file for an individual .Java file of couplings (Class.Method -> Class.variable/method).

Requirements
------------------------

Static analysis is performed using the Antlr framework (http://antlr.org). A version of the Antlr jar is distributed in this repository.

This project uses the Java 1.7 grammar file developed by Terrence Parr and Sam Harwell. This file is used as is without any modification and provided as part of this repository.

Reporting Faults
------------------------

This utility is under development and has not been extensively tested. If you encounter any issues, please file a report, and I'll try to track it down.

