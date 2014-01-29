package eu.ehri.project.importers;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;

import eu.ehri.project.models.DocumentaryUnit;

/**
 * Test stub for importing two EAD files describing the same documentary unit in different languages.
 * @author ben
 *
 */
public class MultilingualEadTest extends AbstractImporterTest {

	protected final String TEST_REPO = "r1";
    protected final String XMLFILENL = "archief-8224-nl.xml";
    protected final String XMLFILEFR = "archief-8224-fr.xml";
    protected final String ARCHDESC = "AA 1593",
            C01 = "cegesomaID1",
            C02_01 = "AA 1134 / 32",
            C02_02 = "AA 1134 / 34";
    DocumentaryUnit archdesc, c1, c2_1, c2_2;
    int origCount=0;
	
	/**
	 * Import a Dutch archival description and then a French archival description
	 * of the same Cegesoma fonds and check that two description nodes were
	 * created and linked to the same documentary unit
	 */
	@Ignore
	@Test
	public void cegesomaTwoLanguageTest() {
		
		// check conditions before imports
		
		
		// import Dutch description
		
		
		// check conditions between imports
		// - new nodes:
		//   - 1 DocumentaryUnit
		//   - 1 DocumentDescription
		//   - x other nodes
		printGraph(graph);
		
		// import French description
		
		
		// check conditions after imports
		// - new nodes:
		//   - 0 DocumentaryUnit
		//   - 1 DocumentDescription
		//   - x other nodes (ideally translations of subject terms 
		//          are descriptions of existing subject nodes)
		fail("Not yet implemented");
	}

}
