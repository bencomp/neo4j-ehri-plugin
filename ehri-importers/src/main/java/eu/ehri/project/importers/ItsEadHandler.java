package eu.ehri.project.importers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.XMLPropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.importers.properties.XmlImportProperties;

public class ItsEadHandler extends EadHandler {

	private static final Logger logger = LoggerFactory.getLogger(ItsEadHandler.class);


    private final String UNKNOWN_TITLE = "UNKNOWN title";
	private int itscount = 0;
	private String archID;
	
	public ItsEadHandler(AbstractImporter<Map<String, Object>> importer) {
		super(importer, new XmlImportProperties("its.properties"));
		this.defaultLanguage = "deu";
	}

	 @Override
	 public void endElement(String uri, String localName, String qName) throws SAXException {
		 //the child closes, add the new DocUnit to the list, establish some relations
		 super.endElement(uri, localName, qName);
		 if (qName.equals("eadid")) {
			 archID = (String) currentGraphPath.peek().get("eadIdentifier");
			 logger.debug("archID set: " + archID);
		 }
//		 else if (qName.equals("unitid")) {
//			 String uid = (String) currentGraphPath.peek().get("objectIdentifier");
//			 logger.debug("unitid at depth " + depth +": " + uid);
//		 }
	 }
	 
	/**
	 * Handler specific code for extraction of unit IDs
	 * @param currentGraph
	 */
	@Override
	protected void extractIdentifier(Map<String, Object> currentGraph) {
		//not all units have ids, and some have multiple, find the "bestellnummer"
		if (this.archID == null && currentGraph.containsKey("archIdentifier")) {
			this.archID = (String) currentGraph.get("archIdentifier");
		}
		if (currentGraph.containsKey("objectIdentifier")) {
			if (currentGraph.get("objectIdentifier") instanceof List) {
				logger.debug("class of identifier: " + currentGraph.get("objectIdentifier").getClass());
				ArrayList<String> identifiers = (ArrayList<String>) currentGraph.get("objectIdentifier");
				ArrayList<String> identifiertypes = (ArrayList<String>) currentGraph.get("objectIdentifierType");
				for (int i = 0; i < identifiers.size(); i++) {
					if (identifiertypes.get(i).equals("bestellnummer")) {
						logger.debug("found official id: " + identifiers.get(i));
						currentGraph.put("objectIdentifier", identifiers.get(i));
					} else {
						logger.debug("found other form of identifier: " + identifiers.get(i));
						addOtherIdentifier(currentGraph, identifiers.get(i));
					}
				}
				currentGraph.remove("objectIdentifierType");
			}
		} else {
			logger.error("no unitid found, setting " + ++itscount);
			currentGraph.put("objectIdentifier", this.archID+"-itsID"+itscount);

		}
	}

	/**
	 * Not all ITS units have a title; generate one for those.
	 * 
	 * @param currentGraph the unit representation to extract a title from 
	 * 		or generate a title for
	 */
	@Override
	protected void extractTitle(Map<String, Object> currentGraph) {
		if (!currentGraph.containsKey(Ontology.NAME_KEY)) {
			String title = UNKNOWN_TITLE;
			if (currentGraph.containsKey(Ontology.IDENTIFIER_KEY)) {
				title += " (" + (String)currentGraph.get(Ontology.IDENTIFIER_KEY) + ")";
			}
			logger.info("Setting default title: " + title);
			currentGraph.put(Ontology.NAME_KEY, title);
		}
		
	}
	
	

}
