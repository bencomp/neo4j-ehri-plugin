/**
 * 
 */
package eu.ehri.project.importers.cvoc;



import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import com.google.common.base.Optional;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.vocabulary.DC;
import com.hp.hpl.jena.vocabulary.RDF;
import com.tinkerpop.blueprints.TransactionalGraph;
import eu.ehri.project.definitions.EventTypes;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.base.*;
import eu.ehri.project.persistance.*;
import eu.ehri.project.utils.TxCheckedNeo4jGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.tinkerpop.frames.FramedGraph;

import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.importers.ImportLog;
import eu.ehri.project.importers.exceptions.InputParseError;
import eu.ehri.project.importers.exceptions.InvalidXmlDocument;
import eu.ehri.project.importers.exceptions.InvalidInputFormatError;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.cvoc.Concept;
import eu.ehri.project.models.cvoc.Vocabulary;
import eu.ehri.project.persistance.ActionManager.EventContext;
/**
 * 
 * @author ben
 * @author paulboon original code
 */
public class SkosRDFImporter {
	private static final Logger logger = LoggerFactory.getLogger(SkosRDFImporter.class);
	protected final FramedGraph<? extends TransactionalGraph> framedGraph;
	protected final Actioner actioner;
	protected Boolean tolerant = false;
	protected final Vocabulary vocabulary;

	private static final String CONCEPT_URL = "url";
	
	private final Resource SKOS_CONCEPT = ResourceFactory.createResource("http://www.w3.org/2004/02/skos/core#Concept");
	private final Resource SKOS_CONCEPTSCHEME = ResourceFactory.createResource("http://www.w3.org/2004/02/skos/core#ConceptScheme");
	
	private final Property SKOS_PREFLABEL = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#prefLabel");
	private final Property SKOS_ALTLABEL = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#altLabel");
	private final Property SKOS_NOTATION = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#notation");
	private final Property SKOS_DEFINITION = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#definition");
	private final Property SKOS_SCOPENOTE = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#scopeNote");
	private final Property SKOS_BROADER = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#broader");
	private final Property SKOS_NARROWER = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#narrower");
	private final Property SKOS_RELATED = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#related");
	private final Property SKOS_INSCHEME = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#inScheme");

	// map from the internal SKOS identifier to the placeholder
	protected Map<String, ConceptPlaceholder> conceptLookup = new HashMap<String, ConceptPlaceholder>();

	private Optional<String> getLogMessage(String msg) {
		return msg.trim().isEmpty() ? Optional.<String>absent() : Optional.of(msg);
	}

	/**
	 * Constructor.
	 * 
	 * @param framedGraph
	 * @param actioner
	 * @param vocabulary
	 */
	public SkosRDFImporter(FramedGraph<? extends TransactionalGraph> framedGraph,
			final Actioner actioner, Vocabulary vocabulary) {
		this.framedGraph = framedGraph;
		this.actioner = actioner;
		this.vocabulary = vocabulary;
	}

	/**
	 * Tell the importer to simply skip invalid items rather than throwing an
	 * exception.
	 * 
	 * @param tolerant
	 */
	public void setTolerant(Boolean tolerant) {
		logger.debug("Setting importer to tolerant: " + tolerant);
		this.tolerant = tolerant;
	}

	/*** management part ***/

	/**
	 * 
	 * @param model
	 * @param logMessage
	 * @return
	 * @throws ValidationError
	 */
	public ImportLog importModel(Model model, String logMessage) throws ValidationError {
		try {
			// Create a new action for this import
			final EventContext eventContext = new ActionManager(framedGraph, vocabulary).logEvent(
					actioner, EventTypes.ingest, getLogMessage(logMessage));
			// Create a manifest to store the results of the import.
			final ImportLog log = new ImportLog(eventContext);

			// Do the import...
//			importFile(model, eventContext, log);
			createConcepts(model, eventContext, log);
			createVocabularyStructure(model, eventContext, log);
			// If nothing was imported, remove the action...
			commitOrRollback(log.hasDoneWork());
			return log;
		} catch (ValidationError e) {
			commitOrRollback(false);
			throw e;
		} catch (Exception e) {
			commitOrRollback(false);
			throw new RuntimeException(e);
		}
	}


	/*** End of management part, Vocabulary/Concept code below ***/


	/**
	 * Do the Concept data extraction and create all the Concepts and ConceptDescriptions.
	 * Concepts get properties: IDENTIFIER_KEY, CONCEPT_URL
	 * ConceptDefinitions get properties: name (= prefLabel), altLabel (= altLabel),
	 * 	definition, scopeNote, title, latitude, longitude
	 * 
	 * @param model
	 * @param action
	 * @param manifest
	 * @throws ValidationError
	 * @throws InvalidInputFormatError
	 * @throws IntegrityError
	 */
	private void createConcepts(Model model, final ActionManager.EventContext action,
			final ImportLog manifest) throws ValidationError,
			InvalidInputFormatError, IntegrityError {

		// Get skos:Concepts from Model
		ResIterator skosConcepts = model.listResourcesWithProperty(RDF.type, SKOS_CONCEPT);
		
		// For each skos:Concept ...
		for (Resource skosConcept : skosConcepts.toList()) {
			logger.debug("Concept: " + skosConcept.getURI());
			

			// ... create a Bundle consisting of Concept and related ConceptDescriptions
			Bundle unit = createBundleForConcept(model, skosConcept);//constructBundleForConcept(skosConcept);

			BundleDAO persister = new BundleDAO(framedGraph, vocabulary);
			Mutation<Concept> mutation = persister.createOrUpdate(unit,
					Concept.class);
			Concept frame = mutation.getNode();


			// Set the vocabulary/concept relationship
			handleCallbacks(mutation, manifest);

			if (mutation.created() || mutation.unchanged()) {
				// when concept was successfully persisted!
				action.addSubjects(frame);
			}




			// FIXME: Handle case where relationships have changed on update???
			if (mutation.created()) {
				frame.setVocabulary(vocabulary);
				frame.setPermissionScope(vocabulary);

				// Create and add a ConceptPlaceholder
				// for making the vocabulary (relation) structure in the next step
				List<String> broaderIds = getBroaderConceptIds(model, skosConcept);
				logger.debug("Concept has " + broaderIds.size()
						+ " broader ids: " + broaderIds.toString());
				List<String> relatedIds = getRelatedConceptIds(model, skosConcept);
				logger.debug("Concept has " + relatedIds.size()
						+ " related ids: " + relatedIds.toString());

				String storeId = unit.getId();//id;
				String skosId = (String)unit.getDataValue(CONCEPT_URL);
				// Referral
				logger.debug("Concept store id = " + storeId + ", skos id = " + skosId);
				conceptLookup.put(skosId, new ConceptPlaceholder(storeId, broaderIds, relatedIds, frame));
			}
		}
	}
		    	
	
	/**
	 * Get URIs of related concepts as Strings for a given concept
	 * 
	 * @param model The Model to find related concepts in
	 * @param skosConcept the concept to find related concepts for  
	 */
	private List<String> getRelatedConceptIds(Model model, Resource skosConcept) {
		List<String> ids = new ArrayList<String>();
		for (RDFNode node : model.listObjectsOfProperty(skosConcept, SKOS_RELATED).toList()) {
			ids.add(node.asResource().getURI());
		}
		return ids;
	}

	/**
	 * Get URIs of broader concepts as Strings for a given concept
	 * 
	 * @param model The Model to find broader concepts in
	 * @param skosConcept the concept to find broader concepts for  
	 */
	private List<String> getBroaderConceptIds(Model model, Resource skosConcept) {
		List<String> ids = new ArrayList<String>();
		for (RDFNode node : model.listObjectsOfProperty(skosConcept, SKOS_BROADER).toList()) {
			ids.add(node.asResource().getURI());
		}
		return ids;
	}

	private Bundle createBundleForConcept(Model model, Resource skosConcept) {
		// ... get statements about the concept 
		Model m2 = model.query(new SimpleSelector(skosConcept, null, (RDFNode)null));
		logger.debug("Number of statements: " + m2.size());

		// Create data map for Concept
		Map<String, Object> data = new HashMap<String, Object>();
		data.put(Ontology.IDENTIFIER_KEY, skosConcept.getLocalName());
		data.put(CONCEPT_URL, skosConcept.getURI());

		// ... count languages and create a Bundle for each ConceptDescription
		Map<String, Object> descriptionData = new HashMap<String, Object>();
		Map<String, Object> rel = new HashMap<String, Object>();
		
		Map<String, Object> descriptions = extractCvocConceptDescriptions(model, skosConcept);
		
//		StmtIterator stmnts = m2.listStatements();
//		while(stmnts.hasNext()) {
//			Statement s = stmnts.next();
//			logger.debug(s.toString());
//
//			//						extractDescriptions(descriptions)
//			// get a Map for the language
//			Map<String, Object> desc = getOrCreateDescriptionForLanguage(descriptionData, lang); 
//			if (descriptionData.containsKey(s.getLanguage())) {
//				desc = (Map<String, Object>) descriptionData.get(s.getLanguage());
//			} else {
//				desc = new HashMap<String, Object>();
//				desc.put(Ontology.LANGUAGE_OF_DESCRIPTION, s.getLanguage());
//				descriptionData.put(s.getLanguage(), desc);
//			}
//			
//			Property pred = s.getPredicate();
//			String value = s.getLiteral().getString();
//
//			if (pred.equals(SKOS_PREFLABEL)) 
//			{
//				logger.debug("skos:prefLabel found");
//				desc.put(Ontology.NAME_KEY, value);
//			} 
//			else if (pred.equals(SKOS_ALTLABEL)) 
//			{
//				logger.debug("skos:altLabel found");
//				desc.put(Ontology.CONCEPT_ALTLABEL, value);
//			} 
//			else if (pred.equals(SKOS_DEFINITION)) 
//			{
//				logger.debug("skos:definition found");
//				desc.put(Ontology.CONCEPT_DEFINITION, value);
//			} 
//			else if (pred.equals(SKOS_SCOPENOTE)) 
//			{
//				logger.debug("skos:definition found");
//				desc.put(Ontology.CONCEPT_SCOPENOTE, value);
//			} 
//			else if (pred.equals(DC.title)) 
//			{
//				logger.debug("dc:title found, put it as altLabel");
//				desc.put(Ontology.CONCEPT_ALTLABEL, value);
//			}
//			else 
//			{
//				logger.debug("undetermined property found: " + pred.getURI());
//				rel.put(pred.getURI(), s.getObject().toString());
//			}
//		}
		
		Bundle cBundle = new Bundle(EntityClass.CVOC_CONCEPT, data);
		
		for (String key : descriptionData.keySet()) {
			logger.debug("description for: " + key);
			Map<String, Object> d = (Map<String, Object>) descriptionData.get(key);
			logger.debug("languageCode = " + d.get(Ontology.LANGUAGE_OF_DESCRIPTION));

			Bundle descBundle = new Bundle(EntityClass.CVOC_CONCEPT_DESCRIPTION, d);
			// Get undetermined relationships
//			extractRelations(skosConcept, "owl:sameAs");
			if(!rel.isEmpty()) {
				descBundle = descBundle.withRelation(Ontology.HAS_ACCESS_POINT,
						new Bundle(EntityClass.UNDETERMINED_RELATIONSHIP, rel));
			}
			
			cBundle = cBundle.withRelation(Ontology.DESCRIPTION_FOR_ENTITY, descBundle);
		}
		
		return cBundle;
	}

//	/**
//	 * Extract data and construct the bundle for a new Concept
//	 * 
//	 * @param skosConcept
//	 * @throws ValidationError
//	 */
//	private Bundle constructBundleForConcept(Resource skosConcept) throws ValidationError {
//		Bundle unit = new Bundle(EntityClass.CVOC_CONCEPT,
//				extractCvocConcept(skosConcept));
//
//		// add the description data to the concept as relationship
//		Map<String, Object> descriptions = extractCvocConceptDescriptions(skosConcept);
//
//		for (String key : descriptions.keySet()) {
//			logger.debug("description for: " + key);
//			Map<String, Object> d = (Map<String, Object>) descriptions.get(key);
//			logger.debug("languageCode = " + d.get(Ontology.LANGUAGE_OF_DESCRIPTION));
//
//			Bundle descBundle = new Bundle(EntityClass.CVOC_CONCEPT_DESCRIPTION, d);
//			Map<String, Object> rel = extractRelations(skosConcept, "owl:sameAs");
//			if(!rel.isEmpty()) {
//				descBundle = descBundle.withRelation(Ontology.HAS_ACCESS_POINT,
//						new Bundle(EntityClass.UNDETERMINED_RELATIONSHIP, rel));
//			}
//
//			// NOTE maybe test if prefLabel is there?
//			unit = unit.withRelation(Ontology.DESCRIPTION_FOR_ENTITY, descBundle);
//		}
//
//		// get an ID for the GraphDB
//		String id = unit.getType().getIdgen()
//				.generateId(EntityClass.CVOC_CONCEPT, vocabulary, unit);
//		return unit.withId(id);
//	}



	/**
	 * Create the Vocabulary structure by creating all relations (BT/NT/RT) between the concepts
	 * 
	 * Note that we want this to be done in the same database 'transaction' 
	 * and this 'current' one is not finished. 
	 * Therefore the Concepts are not retrievable from the database yet 
	 * and we need to keep them in out lookup!
	 *  
	 * @param doc
	 * @param eventContext
	 * @param manifest
	 * @throws ValidationError
	 * @throws InvalidInputFormatError
	 * @throws IntegrityError
	 */
	private void createVocabularyStructure(Model doc, final EventContext eventContext,
			final ImportLog manifest) throws ValidationError,
			InvalidInputFormatError, IntegrityError {

		logger.debug("Number of concepts in lookup: " + conceptLookup.size());

		createBroaderNarrowerRelations(doc, eventContext, manifest);
		createNonspecificRelations(doc, eventContext, manifest);
	}

	/**
	 * Create the broader/narrower relations for all the concepts
	 * 
	 * @param doc
	 * @param eventContext
	 * @param manifest
	 * @throws ValidationError
	 * @throws InvalidInputFormatError
	 * @throws IntegrityError
	 */
	private void createBroaderNarrowerRelations(Model doc, final EventContext eventContext,
			final ImportLog manifest) throws ValidationError,
			InvalidInputFormatError, IntegrityError {

		// check the lookup and start making the BT relations (and narrower implicit)
		// visit all concepts an see if they have broader concepts
		for (String skosId : conceptLookup.keySet()) {
			ConceptPlaceholder conceptPlaceholder = conceptLookup.get(skosId);
			if (!conceptPlaceholder.broaderIds.isEmpty()) {
				logger.debug("Concept with skos id [" + skosId 
						+ "] has broader concepts of " + conceptPlaceholder.broaderIds.size());
				// find them in the lookup
				for (String bsId: conceptPlaceholder.broaderIds) {
					logger.debug(bsId);
					if (conceptLookup.containsKey(bsId)) {
						// found
						logger.debug("Found mapping from: " + bsId 
								+ " to: " + conceptLookup.get(bsId).storeId);

						createBroaderNarrowerRelation(conceptLookup.get(bsId), conceptPlaceholder);
					} else {
						// not found
						logger.debug("Found NO mapping for: " + bsId); 
						// NOTE What does this mean; refers to an External resource, not in this file?
					}
				}
			}
		}    	
	}

	/**
	 * Create the Broader to Narrower Concept relation
	 * Note that we cannot use the storeId's and retrieve them from the database. 
	 * we need to use the Concept objects )from the placeholders in the lookup. 
	 * 
	 * @param bcp
	 * @param ncp
	 */
	private void createBroaderNarrowerRelation(ConceptPlaceholder bcp, ConceptPlaceholder ncp)  {
		logger.debug("Creating Broader: " + bcp.storeId + " to Narrower: " + ncp.storeId);

		// An item CANNOT be a narrower version of itself!
		if (bcp.concept.equals(ncp.concept)) {
			logger.error("Ignoring cyclic narrower relationship on {}", bcp.concept.getId());
		} else {
			bcp.concept.addNarrowerConcept(ncp.concept);
		}
	}

	/**
	 * Create the 'non-specific' relations for all the concepts
	 * 
	 * @param doc
	 * @param eventContext
	 * @param manifest
	 * @throws ValidationError
	 * @throws InvalidInputFormatError
	 * @throws IntegrityError
	 */
	private void createNonspecificRelations(Model doc, final EventContext eventContext,
			final ImportLog manifest) throws ValidationError,
			InvalidInputFormatError, IntegrityError {

		// check the lookup and start making the relations
		// visit all concepts an see if they have a related concept    	
		for (String skosId : conceptLookup.keySet()) {
			ConceptPlaceholder conceptPlaceholder = conceptLookup.get(skosId);
			if (!conceptPlaceholder.relatedIds.isEmpty()) {
				logger.debug("Concept with skos id [" + skosId 
						+ "] has related concepts");
				// find them in the lookup
				for (String toCId: conceptPlaceholder.relatedIds) {
					if (conceptLookup.containsKey(toCId)) {
						// found
						logger.debug("Found mapping from: " + toCId 
								+ " to: " + conceptLookup.get(toCId).storeId);

						createNonspecificRelation(conceptPlaceholder, conceptLookup.get(toCId));
					} else {
						// not found
						logger.debug("Found NO mapping for: " + toCId); 
						// NOTE What does this mean; refers to an External resource, not in this file?
					}
				}
			}
		}    	
	}

	/**
	 * Create a non specific relation; this is the 'skos:related' on a concept 
	 * @param from
	 * @param to
	 */
	private void createNonspecificRelation(ConceptPlaceholder from, ConceptPlaceholder to)  {

		// Prevent creating the relation both ways
		// NOTE, optimization possible if we could prevent for checking everything twice!
		if (!reverseRelationExists(from, to)) {
			logger.debug("Creating relation from: " + from.storeId + " to: " + to.storeId);    	
			from.concept.addRelatedConcept(to.concept);
		}
	}

	/**
	 * Check if the 'reverse' relation is exists already, via relatedBy 
	 * 
	 * @param from
	 * @param to
	 */
	private boolean reverseRelationExists(ConceptPlaceholder from, ConceptPlaceholder to) {
		boolean result = false;
		for(Concept relatedBy: from.concept.getRelatedByConcepts()) {
			//logger.debug("Related By: " + relatedBy.asVertex().getProperty(EntityType.ID_KEY));
			String relatedByStoreId = (String) relatedBy.asVertex().getProperty(EntityType.ID_KEY);
			if (relatedByStoreId == to.storeId) {
				result = true;
				break; // found
			}
		}
		logger.debug("Relation exists: " + result + " for: " + to.storeId);
		return result;
	}

	

	/*** data extraction from XML below ***/



//	private Map<String, Object> extractRelations(Element element, String skosName) {
//		Map<String, Object> relationNode = new HashMap<String, Object>();
//
//		NodeList textNodeList = element.getElementsByTagName(skosName);
//		for (int i = 0; i < textNodeList.getLength(); i++) {
//			Node textNode = textNodeList.item(i);
//			// get value
//			String text = textNode.getTextContent();
//			logger.debug("text: \"" + text + "\", skos name: " + skosName);
//
//			// add to all descriptionData maps
//			relationNode.put(Ontology.ANNOTATION_TYPE, skosName);
//			relationNode.put(Ontology.NAME_KEY, text);
//		}
//		return relationNode;
//	}
	/**
	 * Extract the Descriptions information for a concept
	 * 
	 * @param skosConcept
	 */
	Map<String, Object> extractCvocConceptDescriptions(Model model, Resource skosConcept) {
		// extract and process the textual items (with a language)
		// one description for each language, so the languageCode serve as a key into the map
		Map<String, Object> descriptionData = new HashMap<String, Object>(); 

		// one and only one
		extractAndAddToLanguageMapSingleValuedTextToDescriptionData(descriptionData, Ontology.NAME_KEY, model, SKOS_PREFLABEL, skosConcept);
		// multiple alternatives is logical
		extractAndAddMultiValuedTextToDescriptionData(descriptionData, 
				Ontology.CONCEPT_ALTLABEL, model, SKOS_ALTLABEL, skosConcept);
		// just allow multiple, its not forbidden by Skos
		extractAndAddMultiValuedTextToDescriptionData(descriptionData, 
				Ontology.CONCEPT_SCOPENOTE, "skos:scopeNote", skosConcept);
		// just allow multiple, its not forbidden by Skos
		extractAndAddMultiValuedTextToDescriptionData(descriptionData, 
				Ontology.CONCEPT_DEFINITION, "skos:definition", skosConcept);
		//<geo:lat>52.43333333333333</geo:lat>
		extractAndAddToAllMapsSingleValuedTextToDescriptionData(descriptionData, "latitude", "geo:lat", skosConcept);
		//<geo:long>20.716666666666665</geo:long>
		extractAndAddToAllMapsSingleValuedTextToDescriptionData(descriptionData, "longitude", "geo:long", skosConcept);

		//<owl:sameAs>http://www.yadvashem.org/yv/he/research/ghettos_encyclopedia/ghetto_details.asp?cid=1</owl:sameAs>
		//TODO: must be an UndeterminedRelation, which can then later be resolved

		// NOTE we could try to also add everything else, using the skos tagname as a key?

		return descriptionData;
	}

//	private void extractAndAddToAllMapsSingleValuedTextToDescriptionData(Map<String, Object> descriptionData, 
//			String textName, String skosName, Element conceptElement) {
//		NodeList textNodeList = conceptElement.getElementsByTagName(skosName);
//		for(int i=0; i<textNodeList.getLength(); i++){
//			Node textNode = textNodeList.item(i);
//			// get value
//			String text = textNode.getTextContent();
//			logger.debug("text: \"" + text + "\", skos name: " + skosName + ", property: " + textName);
//
//			// add to all descriptionData maps
//			for(String key : descriptionData.keySet()){
//				Object map = descriptionData.get(key);
//				if(map instanceof Map){
//					((Map<String, Object>)map).put(textName, text);
//				}else{
//					logger.warn(key + " no description map found");
//				}
//			}
//		}    
//	}

	/**
	 * Extract a 'single valued' textual description property and add it to the data
	 * 
	 * @param descriptionData
	 * @param textName Graph node property label
	 * @param model 
	 * @param skosProperty DatatypeProperty to find values for
	 * @param skosConcept
	 */
	private void extractAndAddToLanguageMapSingleValuedTextToDescriptionData(Map<String, Object> descriptionData, 
			String textName, Model model, Property skosProperty, Resource skosConcept) {

		NodeIterator submodel = model.listObjectsOfProperty(skosConcept, skosProperty);
		
		while(submodel.hasNext()){
			Literal lit = (Literal) submodel.next();
			String lang = lit.getLanguage();
			// get value
			String text = lit.getString();
			logger.debug("text: \"" + text + "\" lang: \"" + lang + "\"" + ", skos name: " + skosProperty);

			// add to descriptionData
			Map<String, Object> d = getOrCreateDescriptionForLanguage(descriptionData, lang);
			d.put(textName, text); // only one item with this name per description
		}    	
	}

	/**
	 * Extract a 'multi valued' textual description property and add it to the data (list)
	 * 
	 * @param descriptionData
	 * @param textName
	 * @param skosName
	 * @param skosConcept
	 */
	private void extractAndAddMultiValuedTextToDescriptionData(Map<String, Object> descriptionData, 
			String textName, String skosName, Model model, Property skosProperty, Resource skosConcept) {

		NodeIterator submodel = model.listObjectsOfProperty(skosConcept, skosProperty);
		
		while(submodel.hasNext()){
			Literal lit = (Literal) submodel.next();
			String lang = lit.getLanguage();
			// get value
			String text = lit.getString();
//		NodeList textNodeList = skosConcept.getElementsByTagName(skosName);
//		for(int i=0; i<textNodeList.getLength(); i++){
//			Node textNode = textNodeList.item(i);
			// get lang attribute, we must have that!
//			Node langItem = textNode.getAttributes().getNamedItem("xml:lang");
//			String lang = langItem.getNodeValue();
			// get value
//			String text = textNode.getTextContent();
			logger.debug("text: \"" + text + "\" lang: \"" + lang + "\"" + ", skos name: " + skosName);
			// add to descriptionData
			Map<String, Object> d = getOrCreateDescriptionForLanguage(descriptionData, lang);
			// get the array if it is there, otherwise create it first
			if (d.containsKey(textName)) {
				// should be a list, add it
				((List<String>)d.get(textName)).add(text);
			} else {
				// create a list first
				List<String > textList = new ArrayList<String>();
				textList.add(text);
				d.put(textName, textList);
			}
		}    	
	}    

	/**
	 * Create a description for a specific language or return the one created before
	 * 
	 * @param descriptionData
	 * @param lang
	 */
	private Map<String, Object> getOrCreateDescriptionForLanguage(Map<String, Object> descriptionData, String lang) {
		Map<String, Object> d = null;
		if (descriptionData.containsKey(lang)) {
			d = (Map<String, Object>) descriptionData.get(lang);
		} else {
			// create one
			d = new HashMap<String, Object>();
			d.put("languageCode", lang); // initialize
			descriptionData.put(lang, d);
		}
		return d;
	}

	/*** ***/

	/**
	 * Used in the lookup which is needed for creating the Vocabulary structure
	 * 
	 * @author paulboon
	 *
	 */
	private class ConceptPlaceholder {
		public String storeId; // the identifier used for storage and referring in the repository
		List<String> broaderIds;
		List<String> relatedIds;
		Concept concept;

		public ConceptPlaceholder(String storeId,
				List<String> broaderIds,
				List<String> relatedIds,
				Concept concept) {
			this.storeId = storeId;
			this.broaderIds = broaderIds;
			this.relatedIds = relatedIds;

			// NOTE if we have the concept; why do we need those other members?
			this.concept = concept;
		}    	
	}

	/**
	 * Dummy resolver that does nothing. This is used to ensure that, in
	 * tolerant mode, the EAD parser does not lookup the DTD and validate the
	 * document, which is both slow and error prone.
	 */
	public class DummyEntityResolver implements EntityResolver {
		public InputSource resolveEntity(String publicID, String systemID)
				throws SAXException {

			return new InputSource(new StringReader(""));
		}
	}

	protected void handleCallbacks(Mutation<? extends AccessibleEntity> mutation,
			ImportLog manifest) {
		switch (mutation.getState()) {
		case CREATED:
			manifest.addCreated();
			break;
		case UPDATED:
			manifest.addUpdated();
			break;
		case UNCHANGED:
			manifest.addUnchanged();
			break;
		}
	}

	private void commitOrRollback(boolean okay) {
		TransactionalGraph baseGraph = framedGraph.getBaseGraph();
		if (baseGraph instanceof TxCheckedNeo4jGraph) {
			TxCheckedNeo4jGraph graph = (TxCheckedNeo4jGraph) baseGraph;
			if (!okay && graph.isInTransaction()) {
				graph.rollback();
			}
		} else {
			if (okay)
				baseGraph.commit();
			else
				baseGraph.rollback();
		}
	}
}


