package eu.ehri.project.importers.cvoc;

import org.apache.jena.riot.RDFDataMgr;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.vocabulary.RDF;

/*
 * Reads an RDF file containing a SKOS thesaurus and creates required stuff for ingest in the graph database.
 * @author benc
 */
public class SkosRDFImporter {
	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SkosRDFImporter.class);
//	private RDF fm;
	private Model model;
	
	/*
	 * Constructor. Loads an RDF file in the internal model.
	 * @param filename
	 */
	public SkosRDFImporter(String filename) {
		logger.debug("start of constructor");
//		this.fm = FileManager.get();
//		this.model = RDFDataMgr.loadModel(filename);
		this.model = ModelFactory.createDefaultModel() ;
		this.model.read(filename);
		logger.debug("end of constructor");
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
		logger.debug("start of subject count");
		Model nmodel = this.model.query(new SimpleSelector(null, RDF.type, ResourceFactory.createResource("http://www.w3.org/2004/02/skos/core#Concept")));
		logger.debug("end of subject count");
		return nmodel.size();
	}
	
	/*
	 * Print 10 loaded concepts.
	 */
	public void listConcepts() {
		logger.debug("start of concept listing");
		ResIterator subjects = model.listSubjects();
		Resource r;
		for(int i=0; i < 10 && subjects.hasNext(); i++) {
			r = subjects.next();
//			logger.debug("s: " + r);
			System.out.println("s: "+r);
		}
//		logger.debug("listing subjects");
		logger.debug("end of concept listing");
	}
}
