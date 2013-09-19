package eu.ehri.project.importers.cvoc;

import static org.junit.Assert.*;

import org.junit.Test;
import org.slf4j.LoggerFactory;

import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.importers.AbstractImporterTest;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.ConceptDescription;
import eu.ehri.project.models.cvoc.Vocabulary;

/*
 * Test loading SKOS vocabulary/-ies using an RDF library.
 * @author benc
 */
public class SkosRDFImporterTest extends AbstractImporterTest {
	
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SkosRDFImporterTest.class);
    private final String FILENAME = "ehri-skos.rdf";
    
    private final String CONCEPT_URI_1 = "http://ehri01.dans.knaw.nl/tematres/vocab/?tema=511";
    private final String CONCEPT_URI_2 = "http://ehri01.dans.knaw.nl/tematres/vocab/?tema=512";
    private final String CONCEPTSCHEME_URI = "http://ehri01.dans.knaw.nl/tematres/vocab/";

	@Test
	public void testReadConcepts() throws Exception {
		logger.debug("start read concepts test");
		SkosRDFHandler handler = new SkosRDFHandler(FILENAME);
		
		logger.debug("testing subject count");
		assertEquals(881L, handler.getSubjectCount());
		assertEquals(7217L, handler.getStatementCount());
		handler.closeModel();
	}
	
	/*
	 * Load statements and check that the concepts were loaded into Neo4J correctly, and linked correctly.
	 */
	@Test
	public void testConceptLinks() throws Exception {
        final String logMessage = "Importing a SKOS file";
		logger.debug("start concept links test");
		
		int beforeNodeCount = getNodeCount(graph);
        
		SkosRDFHandler handler = new SkosRDFHandler(FILENAME);
		// LOAD HERE
		
		int afterNodeCount = getNodeCount(graph);
		/**
         * We should have afterwards:
         *  - 881 new concepts
         *  - 1386 new descriptions (1 per prefLabel)
         *  - 1 new Event
         *  - 882 new event links (1 for user, 1 per concept)
         */
        assertEquals(beforeNodeCount + 881 + 1386 + 1 + 882, afterNodeCount);

		
		
		Vocabulary scheme = (Vocabulary) graph.getVertex(CONCEPTSCHEME_URI);
		
		// Does Concept with URI <http://ehri01.dans.knaw.nl/tematres/vocab/?tema=511>
		// 	have a description with English prefLabel "Prisoners of war"?
		Concept con = (Concept) graph.getVertex(CONCEPT_URI_1);
		
		for(Description des : con.getDescriptions()) {
			if (des.getLanguageOfDescription().equals("en")) {
				assertEquals(des.asVertex().getProperty(Ontology.NAME_KEY), "Prisoners of war");
				assertEquals(des.asVertex().getProperty(Ontology.CONCEPT_ALTLABEL), "POWs");
			} else if (des.getLanguageOfDescription().equals("de")) {
				assertEquals(des.asVertex().getProperty(Ontology.NAME_KEY), "Kriegsgefangener");
				assertEquals(des.asVertex().getProperty(Ontology.CONCEPT_ALTLABEL), "Kriegsgefangene");
			}
		}
		
		for(Concept narrower : con.getNarrowerConcepts()) {
			assertEquals(narrower, (Concept) graph.getVertex(CONCEPT_URI_2));
		}
		
		// Does `con` have a link to the `scheme` Vocabulary? 
		assertEquals(scheme, con.getVocabulary());
		
	}

}
