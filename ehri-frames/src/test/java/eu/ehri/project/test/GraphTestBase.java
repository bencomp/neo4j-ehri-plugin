package eu.ehri.project.test;

import com.tinkerpop.blueprints.TransactionalGraph;
import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.frames.FramedGraphFactory;
import com.tinkerpop.frames.modules.javahandler.JavaHandlerModule;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import org.junit.After;
import org.junit.Before;
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 * User: michaelb
 */
public abstract class GraphTestBase {

    private static final FramedGraphFactory graphFactory = new FramedGraphFactory(new JavaHandlerModule());

    protected FramedGraph<? extends TransactionalGraph> graph;
    protected GraphManager manager;

    @Before
    public void setUp() throws Exception {

        graph = graphFactory.create(new Neo4jGraph(
                new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder()
                        .newGraphDatabase()));
        manager = GraphManagerFactory.getInstance(graph);
    }

    @After
    public void tearDown() throws Exception {
        graph.shutdown();
    }
}
