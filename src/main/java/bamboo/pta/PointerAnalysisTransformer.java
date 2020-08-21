/*
 * Bamboo - A Program Analysis Framework for Java
 *
 * Copyright (C) 2020 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020 Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * This software is designed for the "Static Program Analysis" course at
 * Nanjing University, and it supports a subset of Java features.
 * Bamboo is only for educational and academic purposes, and any form of
 * commercial use is disallowed.
 */

package bamboo.pta;

import bamboo.pta.core.cs.ArrayIndex;
import bamboo.pta.core.cs.CSMethod;
import bamboo.pta.core.cs.CSVariable;
import bamboo.pta.core.cs.InstanceField;
import bamboo.pta.core.cs.Pointer;
import bamboo.pta.core.cs.StaticField;
import bamboo.pta.core.solver.PointerAnalysis;
import bamboo.pta.core.solver.PointerAnalysisBuilder;
import bamboo.pta.jimple.JimplePointerAnalysis;
import bamboo.pta.options.Options;
import bamboo.util.Pair;
import bamboo.util.Timer;
import soot.SceneTransformer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

import static bamboo.util.StringUtils.streamToString;

public class PointerAnalysisTransformer extends SceneTransformer {

    private static final PointerAnalysisTransformer INSTANCE =
            new PointerAnalysisTransformer();
    /**
     * The print stream used to output key analysis results, i.e., the results
     * that are checked during testing.
     */
    private PrintStream out = System.out;

    private final DecimalFormat formatter = new DecimalFormat("#,####");

    private PointerAnalysisTransformer() {
    }

    public static PointerAnalysisTransformer v() {
        return INSTANCE;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        PointerAnalysis pta = new PointerAnalysisBuilder()
                .build(Options.get());
        Timer ptaTimer = new Timer("Pointer analysis");
        ptaTimer.start();
        pta.analyze();
        ptaTimer.stop();
        JimplePointerAnalysis.v().setPointerAnalysis(pta);
        printResults(pta, ptaTimer);
        if (ResultChecker.isAvailable()) {
            ResultChecker.get().compare(pta);
        }
    }

    private void printResults(PointerAnalysis pta, Timer ptaTimer) {
        if (Options.get().isTestMode()) {
            printPointers(pta);
        } else if (Options.get().isOutputResults()) {
            File output = Options.get().getOutputFile();
            if (output != null) {
                try {
                    setOut(new PrintStream(new FileOutputStream(output)));
                } catch (FileNotFoundException e) {
                    System.err.println("Failed to write output, caused by " + e);
                }
            }
            out.println("---------- Reachable methods: ----------");
            pta.getCallGraph().getReachableMethods()
                    .stream()
                    .sorted(Comparator.comparing(CSMethod::toString))
                    .forEach(out::println);
            out.println("---------- Call graph edges: ----------");
            pta.getCallGraph().getAllEdges().forEach(out::println);
            printPointers(pta);
            out.println("----------------------------------------");
        }
        printStatistics(pta, ptaTimer);
    }

    private void printPointers(PointerAnalysis pta) {
        printVariables(pta.getVariables());
        printInstanceFields(pta.getInstanceFields());
        printArrayIndexes(pta.getArrayIndexes());
        printStaticFields(pta.getStaticFields());
    }

    private void printVariables(Stream<CSVariable> vars) {
        out.println("---------- Points-to sets of all variables: ----------");
        vars.sorted(Comparator.comparing(CSVariable::toString))
                .forEach(this::printPointsToSet);
    }

    private void printInstanceFields(Stream<InstanceField> fields) {
        out.println("---------- Points-to sets of all instance fields: ----------");
        fields.sorted(Comparator.comparing(InstanceField::toString))
                .forEach(this::printPointsToSet);
    }

    private void printArrayIndexes(Stream<ArrayIndex> arrays) {
        out.println("---------- Points-to sets of all array indexes: ----------");
        arrays.sorted(Comparator.comparing(ArrayIndex::toString))
                .forEach(this::printPointsToSet);
    }

    private void printStaticFields(Stream<StaticField> fields) {
        out.println("---------- Points-to sets of all static fields: ----------");
        fields.sorted(Comparator.comparing(StaticField::toString))
                .forEach(this::printPointsToSet);
    }

    private void printPointsToSet(Pointer pointer) {
        out.println(pointer + " -> "
//                + "\t" + pointer.getPointsToSet().size() + "\t"
                + streamToString(pointer.getPointsToSet().stream()));
    }

    private void printStatistics(PointerAnalysis pta, Timer ptaTimer) {
        int varInsens = (int) pta.getVariables()
                .map(CSVariable::getVariable)
                .distinct()
                .count();
        int varSens = (int) pta.getVariables().count();
        int vptSizeSens = pta.getVariables()
                .mapToInt(v -> v.getPointsToSet().size())
                .sum();
        int ifptSizeSens = pta.getInstanceFields()
                .mapToInt(f -> f.getPointsToSet().size())
                .sum();
        int aptSizeSens = pta.getArrayIndexes()
                .mapToInt(a -> a.getPointsToSet().size())
                .sum();
        int sfptSizeSens = pta.getStaticFields()
                .mapToInt(f -> f.getPointsToSet().size())
                .sum();
        int reachableInsens = (int) pta.getCallGraph()
                .getReachableMethods()
                .stream()
                .map(CSMethod::getMethod)
                .distinct()
                .count();
        int reachableSens = pta.getCallGraph()
                .getReachableMethods()
                .size();
        int callEdgeInsens = (int) pta.getCallGraph()
                .getAllEdges()
                .map(e -> new Pair<>(e.getCallSite().getCallSite(),
                        e.getCallee().getMethod()))
                .distinct()
                .count();
        int callEdgeSens = (int) pta.getCallGraph()
                .getAllEdges()
                .count();
        System.out.println("-------------- Pointer analysis statistics: --------------");
        System.out.println(ptaTimer);
        System.out.printf("%-30s%s (insens) / %s (sens)\n", "#var pointers:",
                format(varInsens), format(varSens));
        System.out.printf("%-30s%s (sens)\n", "#var points-to:",
                format(vptSizeSens));
        System.out.printf("%-30s%s (sens)\n", "#instance field points-to:",
                format(ifptSizeSens));
        System.out.printf("%-30s%s (sens)\n", "#array points-to:",
                format(aptSizeSens));
        System.out.printf("%-30s%s (sens)\n", "#static field points-to:",
                format(sfptSizeSens));
        System.out.printf("%-30s%s (insens) / %s (sens)\n", "#reachable methods:",
                format(reachableInsens), format(reachableSens));
        System.out.printf("%-30s%s (insens) / %s (sens)\n", "#call graph edges:",
                format(callEdgeInsens), format(callEdgeSens));
        System.out.println("----------------------------------------");
    }

    private String format(int i) {
        return formatter.format(i);
    }
}
