/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2020-- Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020-- Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * Tai-e is only for educational and academic purposes,
 * and any form of commercial use is disallowed.
 * Distribution of Tai-e is disallowed without the approval.
 */

package pascal.taie.analysis.dataflow.analysis;

import pascal.taie.analysis.dataflow.fact.SetFact;
import pascal.taie.analysis.graph.cfg.CFG;
import pascal.taie.analysis.graph.cfg.CFGNodeMapper;
import pascal.taie.config.AnalysisConfig;
import pascal.taie.ir.IR;
import pascal.taie.ir.LocalVarMapper;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Stmt;
import pascal.taie.util.ObjectIdMapper;
import pascal.taie.util.collection.IndexMap;
import pascal.taie.util.collection.MapperBitSet;

import java.util.Map;

public class ReachingDefinition extends AnalysisDriver<Stmt, SetFact<Stmt>> {

    public static final String ID = "reachdef";

    public ReachingDefinition(AnalysisConfig config) {
        super(config);
    }

    @Override
    protected Analysis makeAnalysis(CFG<Stmt> cfg) {
        return new Analysis(cfg);
    }

    private static class Analysis extends AbstractDataflowAnalysis<Stmt, SetFact<Stmt>> {

        /**
         * Mapper for Stmts (nodes) in the CFG.
         */
        private final ObjectIdMapper<Stmt> stmtMapper;

        /**
         * Maps a variable to all statements that define it.
         * This information can accelerate kill operation of live variable analysis.
         */
        private final Map<Var, SetFact<Stmt>> defs;

        private static final SetFact<Stmt> EMPTY_DEFS = new SetFact<>();

        private Analysis(CFG<Stmt> cfg) {
            super(cfg);
            stmtMapper = new CFGNodeMapper<>(cfg);
            defs = computeDefs(cfg.getIR());
        }

        /**
         * Pre-computes all definitions of all variables in given ir.
         */
        private  Map<Var, SetFact<Stmt>> computeDefs(IR ir) {
            Map<Var, SetFact<Stmt>> defs = new IndexMap<>(
                    new LocalVarMapper(ir),
                    ir.getVars().size());
            for (Stmt stmt : ir) {
                stmt.getDef().ifPresent(def -> {
                    if (def instanceof Var defVar) {
                        defs.computeIfAbsent(defVar, unused -> newInitialFact())
                                .add(stmt);
                    }
                });
            }
            return defs;
        }

        @Override
        public boolean isForward() {
            return true;
        }

        @Override
        public SetFact<Stmt> newBoundaryFact() {
            return newInitialFact();
        }

        @Override
        public SetFact<Stmt> newInitialFact() {
            return new SetFact<>(new MapperBitSet<>(stmtMapper));
        }

        @Override
        public void meetInto(SetFact<Stmt> fact, SetFact<Stmt> target) {
            target.union(fact);
        }

        @Override
        public boolean transferNode(Stmt stmt, SetFact<Stmt> in, SetFact<Stmt> out) {
            SetFact<Stmt> oldOut = out.copy();
            out.set(in);
            stmt.getDef().ifPresent(def -> {
                if (def instanceof Var defVar) {
                    // kill previous definitions of defVar
                    out.removeAll(defs.getOrDefault(defVar, EMPTY_DEFS));
                    // generate definition of defVar
                    out.add(stmt);
                }
            });
            return !out.equals(oldOut);
        }
    }
}
