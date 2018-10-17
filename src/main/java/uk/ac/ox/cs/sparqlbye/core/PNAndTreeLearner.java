package uk.ac.ox.cs.sparqlbye.core;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.sparql.core.Var;

import org.apache.log4j.Logger;
import uk.ac.ox.cs.sparqlbye.core.UtilsLearner.UTripleProducer;

public final class PNAndTreeLearner extends LearnerBase {

    private static final Logger log = Logger.getLogger(PNAndTreeLearner.class);

    PNAndTreeLearner(
            Set<QuerySolution> positiveSolutions,
            Set<QuerySolution> negativeSolutions,
            List<String> badUris,
            Function<Query, QueryExecution> queryToQueryExecution) {
        super(positiveSolutions, negativeSolutions, badUris, queryToQueryExecution);
    }

    @Override
    public Optional<AOTree> learn() {
        log.info("PNAndTreeLearner.learn()");
        log.info("PNAndTreeLearner.learn(): pSols = " + positiveSolutions);

        learnedTree = UtilsLearner.buildTemplateTree(positiveSolutions);
        _learnNode(learnedTree);

        // Done. Now check:
        ULearnedQueryChecker checker
                = new ULearnedQueryChecker(learnedTree, positiveSolutions, negativeSolutions, queryToQueryExecution);
        boolean checkPasses = checker.checkLearnedQuery();

        log.info("PNAndTreeLearner.learn(): checkPasses = " + checkPasses);

        return checkPasses ? Optional.of(learnedTree) : Optional.empty();
    }

    private void _learnNode(AOTree node) {
        // Prepare signatures (domains):
        Set<Var> scopedVars = node.getDesiredScopedVars();
        Set<Var> newVars = node.getDesiredTopVars();

        Set<QuerySolution> killableSolutions = new HashSet<>(negativeSolutions);

        System.out.println("Learner> node: scopedVars = " + scopedVars + ",  newVars = " + newVars);

        // Prepare new node:
        Set<Triple> triples = new HashSet<>();
        Set<Var> mentionedVars = new HashSet<>();

        boolean nodeReady = false;

        if (!nodeReady) {
            // Iterate over (v,v,v) type triples:
            //			System.out.println("Learner: start (v,v,c)");
            outer:
            for (Var s : scopedVars) {
                for (Var p : scopedVars) {
                    for (Var o : scopedVars) {
                        UTripleProducer tripleProducer
                                = new UTripleProducer(s, p, o, scopedVars, positiveSolutions, badUris, queryToQueryExecution);

                        while (tripleProducer.hasNext()) {
                            Triple nextTriple = tripleProducer.next(); // this triple works. does it kill?
                            if (_processTriplev3(nextTriple, killableSolutions, mentionedVars, triples, newVars)) {
                                System.out.println("Learner: Triples = " + triples);
                                System.out.println("Learner: Proceed to next node.");
                                nodeReady = true;
                                break outer;
                            }
                        }
                        tripleProducer.close();
                    }
                }
            }
            //			System.out.println("Learner: finished (v,v,v)");
        }

        if (!nodeReady) {
            // Iterate over (v,v,c) type triples:
            //			System.out.println("Learner: start (v,v,c)");
            outer:
            for (Var s : scopedVars) {
                for (Var p : scopedVars) {
                    UTripleProducer tripleProducer = new UTripleProducer(s, p, null, scopedVars, positiveSolutions, badUris, queryToQueryExecution);

                    while (tripleProducer.hasNext()) {
                        Triple nextTriple = tripleProducer.next(); // this triple works. does it kill?
                        if (_processTriplev3(nextTriple, killableSolutions, mentionedVars, triples, newVars)) {
                            //							System.out.println("Learner: killable is empty and all vars were mentioned.");
                            System.out.println("Learner: Triples = " + triples);
                            System.out.println("Learner: Proceed to next node.");
                            nodeReady = true;
                            break outer;
                        }
                    }
                    tripleProducer.close();
                }
            }
            //			System.out.println("Learner: finished (v,v,c)");
        }

        if (!nodeReady) {
            // Iterate over (v,c,v) type triples:
            System.out.println("Learner: start (v,c,v)");
            outer:
            for (Var s : scopedVars) {
                for (Var o : scopedVars) {
                    UTripleProducer tripleProducer = new UTripleProducer(s, null, o, scopedVars, positiveSolutions, badUris, queryToQueryExecution);

                    while (tripleProducer.hasNext()) {
                        Triple nextTriple = tripleProducer.next(); // this triple works. does it kill?
                        if (_processTriplev3(nextTriple, killableSolutions, mentionedVars, triples, newVars)) {
                            //							System.out.println("Learner: killable is empty and all vars were mentioned.");
                            System.out.println("Learner: Triples = " + triples);
                            System.out.println("Learner: Proceed to next node.");
                            nodeReady = true;
                            break outer;
                        }
                    }
                    tripleProducer.close();
                }
            }
            //			System.out.println("Learner: finished (v,c,v)");
        }

        if (!nodeReady) {
            // Iterate over (c,v,v) type triples:
            //			System.out.println("Learner: start (c,v,v)");
            outer:
            for (Var p : scopedVars) {
                for (Var o : scopedVars) {
                    UTripleProducer tripleProducer = new UTripleProducer(null, p, o, scopedVars, positiveSolutions, badUris, queryToQueryExecution);

                    while (tripleProducer.hasNext()) {
                        Triple nextTriple = tripleProducer.next(); // this triple works. does it kill?
                        if (_processTriplev3(nextTriple, killableSolutions, mentionedVars, triples, newVars)) {
                            //							System.out.println("Learner: killable is empty and all vars were mentioned.");
                            System.out.println("Learner: Triples = " + triples);
                            System.out.println("Learner: Proceed to next node.");
                            nodeReady = true;
                            break outer;
                        }
                    }
                    tripleProducer.close();
                }
            }
            //			System.out.println("Learner: finished (c,v,v)");
        }

        if (!nodeReady) {
            // Iterate over (v,c,c) type triples:
            System.out.println("Learner: start (v,c,c)");
            outer:
            for (Var s : scopedVars) {
                UTripleProducer tripleProducer = new UTripleProducer(s, null, null, scopedVars, positiveSolutions, badUris, queryToQueryExecution);

                while (tripleProducer.hasNext()) {
                    Triple nextTriple = tripleProducer.next(); // this triple works. does it kill?
                    if (_processTriplev3(nextTriple, killableSolutions, mentionedVars, triples, newVars)) {
                        //							System.out.println("Learner: killable is empty and all vars were mentioned.");
                        System.out.println("Learner: Triples = " + triples);
                        System.out.println("Learner: Proceed to next node.");
                        nodeReady = true;
                        break outer;
                    }
                }
                tripleProducer.close();
            }
            //			System.out.println("Learner: finished (v,c,c)");
        }

        if (!nodeReady) {
            // Iterate over (c,v,c) type triples:
            //			System.out.println("Learner: start (c,v,c)");
            outer:
            for (Var p : scopedVars) {
                UTripleProducer tripleProducer = new UTripleProducer(null, p, null, scopedVars, positiveSolutions, badUris, queryToQueryExecution);

                while (tripleProducer.hasNext()) {
                    Triple nextTriple = tripleProducer.next(); // this triple works. does it kill?
                    if (_processTriplev3(nextTriple, killableSolutions, mentionedVars, triples, newVars)) {
                        //							System.out.println("Learner: killable is empty and all vars were mentioned.");
                        System.out.println("Learner: Triples = " + triples);
                        System.out.println("Learner: Proceed to next node.");
                        nodeReady = true;
                        break outer;
                    }
                }
                tripleProducer.close();
            }
            //			System.out.println("Learner: finished (c,v,c)");
        }

        if (!nodeReady) {
            // Iterate over (c,c,v) type triples:
            //			System.out.println("Learner: start (c,c,v)");
            outer:
            for (Var o : scopedVars) {
                UTripleProducer tripleProducer = new UTripleProducer(null, null, o, scopedVars, positiveSolutions, badUris, queryToQueryExecution);

                while (tripleProducer.hasNext()) {
                    Triple nextTriple = tripleProducer.next(); // this triple works. does it kill?
                    if (_processTriplev3(nextTriple, killableSolutions, mentionedVars, triples, newVars)) {
                        //							System.out.println("Learner: killable is empty and all vars were mentioned.");
                        System.out.println("Learner: Triples = " + triples);
                        System.out.println("Learner: Proceed to next node.");
                        nodeReady = true;
                        break outer;
                    }
                }
                tripleProducer.close();
            }
            //			System.out.println("Learner: finished (c,c,v)");
        }

        if (nodeReady) {
            for (Triple t : triples) {
                node.addTriple(t);
            }
        } else {
            System.out.println("Malformed node!");
        }
    }

    private boolean _processTriplev3(Triple triple, Set<QuerySolution> killable,
            Set<Var> mentionedVars, Set<Triple> triples, Set<Var> newVars) {
        Set<Var> tDom = UtilsJena.dom(triple);

        Set<QuerySolution> killed
                = UtilsLearner.killedByTriples(triple, triples, killable, queryToQueryExecution);
        killable.removeAll(killed);

        if (!killed.isEmpty() || !mentionedVars.containsAll(tDom)) {
            System.out.println("Learner: Adding triple pattern: " + triple);
            triples.add(triple);
            mentionedVars.addAll(tDom);
        }
//		else {
        //	System.out.println("Learner: Adding triple pattern (although not needed?): " + triple);
        //	triples.add(triple);
        //	mentionedVars.addAll(tDom);
//		}

        return killable.isEmpty() && mentionedVars.containsAll(newVars);
    }

}
