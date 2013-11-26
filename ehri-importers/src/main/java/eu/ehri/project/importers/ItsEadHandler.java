package eu.ehri.project.importers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.ehri.project.importers.properties.XmlImportProperties;

public class ItsEadHandler extends EadHandler {

	private static final Logger logger = LoggerFactory.getLogger(ItsEadHandler.class);

    // Default language to use in units without language
    protected String defaultLanguage = "de";

	private int itscount = 0;
	
	public ItsEadHandler(AbstractImporter<Map<String, Object>> importer) {
		super(importer); // add specific .properties?
		
	}

	
	/**
	 * Handler specific code for extraction of unit IDs
	 * @param currentGraph
	 */
	@Override
	protected void extractIdentifier(Map<String, Object> currentGraph) {
		//not all units have ids, and some have multiple, find the "bestellnummer"
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
						currentGraph.put("arta", identifiers.get(i));
					}
				}
				currentGraph.remove("objectIdentifierType");
			}
		} else {
			logger.error("no unitid found, setting " + ++itscount);
			currentGraph.put("objectIdentifier", "itsID"+itscount);

		}
	}

}