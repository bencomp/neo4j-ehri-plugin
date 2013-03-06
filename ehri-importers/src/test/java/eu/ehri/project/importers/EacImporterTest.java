/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.ehri.project.importers;

import com.tinkerpop.blueprints.Vertex;
import eu.ehri.project.importers.test.AbstractImporterTest;
import eu.ehri.project.models.Authority;
import eu.ehri.project.models.AuthorityDescription;
import eu.ehri.project.models.Agent;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.events.SystemEvent;
import java.io.InputStream;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author linda
 */
public class EacImporterTest extends AbstractImporterTest {

    protected final String SINGLE_EAC = "algemeyner-yidisher-arbeter-bund-in-lite-polyn-un-rusland.xml";
    // Depends on fixtures
    protected final String TEST_REPO = "r1";
    // Depends on hierarchical-ead.xml
    protected final String IMPORTED_ITEM_ID = "159";
    protected final String AUTHORITY_DESC = "159#object";

    @Test
    public void testImportItemsT() throws Exception{
       
         Agent agent = manager.getFrame(TEST_REPO, Agent.class); 
        final String logMessage = "Importing a single EAC";

        int count = getNodeCount(graph);
        System.out.println("count of nodes before importing: " + count);

        InputStream ios = ClassLoader.getSystemResourceAsStream(SINGLE_EAC);
        ImportLog log = new SaxImportManager(graph, agent, validUser, EacImporter.class, EacHandler.class).importFile(ios, logMessage);
        printGraph(graph);
            // How many new nodes will have been created? We should have
            // - 1 more Authority
            // - 1 more AuthorityDescription
            // (- 2 more MaintenanceEvent -- not yet)
            // - 2 more linkEvents (1 for the Authority, 1 for the User)
            // - 1 more SystemEvent        
            assertEquals(count + 5, getNodeCount(graph));

            Iterable<Vertex> docs = graph.getVertices(AccessibleEntity.IDENTIFIER_KEY,
                    IMPORTED_ITEM_ID);
            assertTrue(docs.iterator().hasNext());
            Authority unit = graph.frame(
                    getVertexByIdentifier(graph,IMPORTED_ITEM_ID),
                    Authority.class);

            // check the child items
            AuthorityDescription c1 = graph.frame(
                    getVertexByIdentifier(graph,AUTHORITY_DESC),
                    AuthorityDescription.class);

            // Ensure that c1 is a description of the unit
            for (Description d : unit.getDescriptions()) {
//                assertEquals(d, c1);
                assertEquals(d.getEntity(), unit);
            }

//TODO: find out why the unit and the action are not connected ...
//            Iterable<Action> actions = unit.getHistory();
//            assertEquals(1, toList(actions).size());
            // Check we've only got one action
            assertEquals(1, log.getCreated());
            assertTrue(log.getAction() instanceof SystemEvent);
            assertEquals(logMessage, log.getAction().getLogMessage());

            // Ensure the import action has the right number of subjects.
            List<AccessibleEntity> subjects = toList(log.getAction().getSubjects());
            assertEquals(1, subjects.size());
            assertEquals(log.getSuccessful(), subjects.size());
       

//        System.out.println("created: " + log.getCreated());

    }


}
