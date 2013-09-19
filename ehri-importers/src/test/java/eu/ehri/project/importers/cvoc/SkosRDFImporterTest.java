package eu.ehri.project.importers.cvoc;

import static org.junit.Assert.*;

import org.junit.Test;
import org.slf4j.LoggerFactory;

import eu.ehri.project.importers.AbstractImporterTest;

public class SkosRDFImporterTest extends AbstractImporterTest {
	
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SkosRDFImporterTest.class);
    private final String FILENAME = "ehri-skos.rdf";

	@Test
	public void testListConcepts() throws Exception {
		logger.debug("start of test");
		SkosRDFImporter importer = new SkosRDFImporter(FILENAME);
		
		logger.debug("testing subject count");
		assertEquals(881L, importer.getSubjectCount());
		assertEquals(7217L, importer.getStatementCount());
//		importer.listConcepts();
		importer.closeModel();
	}

}
