package eu.ehri.project.importers.cvoc;

import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.vocabulary.RDF;

/*
 * Reads an RDF file containing a SKOS thesaurus and creates required stuff for ingest in the graph database.
 * @author benc
 */
public class SkosRDFHandler {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SkosRDFHandler.class);
	private Model model;
	private SkosRDFImporter importer;
	
	private final Resource SKOS_CONCEPT = ResourceFactory.createResource("http://www.w3.org/2004/02/skos/core#Concept");
	private final Resource SKOS_CONCEPTSCHEME = ResourceFactory.createResource("http://www.w3.org/2004/02/skos/core#ConceptScheme");
	
	/*
	 * Constructor. Loads an RDF file in the internal model.
	 * @param filename
	 */
	public SkosRDFHandler(String filename) {
		logger.debug("Create an empty Model");
		this.model = ModelFactory.createDefaultModel() ;
		this.model.read(filename);
		
	}
	
	public SkosRDFHandler(String filename, SkosRDFImporter importer) {
		this(filename);
	}
	
	public void closeModel(){
		this.model.close();
	}
	
	public long getStatementCount() {
		return model.size();
	}
	
	/*
	 * Return the number of subjects in the model.
	 */
	public long getSubjectCount() {
		Model nmodel = this.model.query(new SimpleSelector(null, RDF.type, SKOS_CONCEPT));
		logger.debug("Subjects counted: " + nmodel.size());
		return nmodel.size();
	}
	
	/*
	 * Print 10 loaded concepts.
	 */
	public void listConcepts() {
//		logger.debug("start of concept listing");
		ResIterator subjects = model.listSubjects();
		Resource r;
		for(int i=0; i < 10 && subjects.hasNext(); i++) {
			r = subjects.next();
//			logger.debug("s: " + r);
			System.out.println("s: "+r);
		}
//		logger.debug("end of concept listing");
	}
	
	/*
	 * Create the Concepts
	 */
	public void createConcepts() {
		
	}
	
	/*
	 * Create a Vocabulary bundle
	 */
	public void createVocabulary() {
		// Find the ConceptScheme
		ResIterator voc = model.listResourcesWithProperty(RDF.type, SKOS_CONCEPTSCHEME);
		for(Resource res : voc.toList()) {
			// 
		}
	}

	public Model getModel() {
		return model;
	}
}
