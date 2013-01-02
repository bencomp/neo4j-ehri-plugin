package eu.ehri.project.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Maps;

import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.DatePeriod;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.TemporalEntity;
import eu.ehri.project.persistance.BundleDAO;
import eu.ehri.project.persistance.Converter;
import eu.ehri.project.persistance.Bundle;

public class BundleTest extends ModelTestBase {

    private static final String ID = "c1";
    
    private Converter converter;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        converter = new Converter(graph);
    }

    @Test
    public void testSerialisation() throws SerializationError,
            DeserializationError, ItemNotFound {
        DocumentaryUnit c1 = manager.getFrame(ID, DocumentaryUnit.class);
        String json = converter.vertexFrameToJson(c1);
        Bundle bundle = converter.jsonToBundle(json);
        assertEquals(ID, bundle.getId());
    }

    public void testSaving() throws SerializationError, ValidationError,
            IntegrityError, ItemNotFound {
        DocumentaryUnit c1 = manager.getFrame(ID, DocumentaryUnit.class);
        assertEquals(1, toList(c1.getDescriptions()).size());

        Bundle bundle = converter.vertexFrameToBundle(c1);
        BundleDAO persister = new BundleDAO(graph);
        DocumentaryUnit c1redux = persister.update(bundle,
                DocumentaryUnit.class);

        assertEquals(toList(c1.getDescriptions()),
                toList(c1redux.getDescriptions()));
    }

    @Test
    public void testSavingWithDependentChanges() throws SerializationError,
            DeserializationError, ValidationError, IntegrityError, ItemNotFound {
        DocumentaryUnit c1 = manager.getFrame(ID, DocumentaryUnit.class);
        assertEquals(1, toList(c1.getDescriptions()).size());
        String json = converter.vertexFrameToJson(c1);

        Description desc = toList(c1.getDescriptions()).get(0);
        c1.removeDescription(desc);
        assertEquals(0, toList(c1.getDescriptions()).size());

        // Restore the item from JSON
        Bundle bundle = converter.jsonToBundle(json);
        BundleDAO persister = new BundleDAO(graph);
        persister.update(bundle, DocumentaryUnit.class);

        // Our deleted description should have come back...
        assertEquals(1, toList(c1.getDescriptions()).size());
    }

    @Test
    public void testDeletingDependents() throws SerializationError,
            ValidationError, IntegrityError, ItemNotFound {
        DocumentaryUnit c1 = manager.getFrame(ID, DocumentaryUnit.class);
        Bundle bundle = converter.vertexFrameToBundle(c1);
        assertEquals(2, toList(c1.getDatePeriods()).size());
        String dpid = "c1-dp2";
        try {
            manager.getFrame(dpid, DatePeriod.class);
        } catch (ItemNotFound e) {
            fail("Date period '" + dpid
                    + "' not found in index before delete test.");
        }

        List<Bundle> dates = bundle.getRelations(TemporalEntity.HAS_DATE);
        Bundle newBundle = bundle.removeRelations(TemporalEntity.HAS_DATE)
                    .withRelation(TemporalEntity.HAS_DATE, dates.get(0));
        BundleDAO persister = new BundleDAO(graph);
        persister.update(newBundle, DocumentaryUnit.class);
        assertEquals(1, toList(c1.getDatePeriods()).size());

        // The second date period should be gone from the index
        try {
            manager.getFrame(dpid, DatePeriod.class);
            fail("Date period '" + dpid + "' found in index AFTER delete test.");
        } catch (ItemNotFound e) {
        }

        // It should also not exist as a node...
        try {
            graph.getVertices(EntityType.ID_KEY, dpid).iterator().next();
            fail("Date period '" + dpid + "' found in index AFTER delete test.");
        } catch (NoSuchElementException e) {
        }
    }

    @Test(expected = ItemNotFound.class)
    public void testDeletingWholeBundle() throws SerializationError,
            ValidationError, ItemNotFound {
        DocumentaryUnit c1 = manager.getFrame(ID, DocumentaryUnit.class);
        Bundle bundle = converter.vertexFrameToBundle(c1);
        assertEquals(2, toList(c1.getDatePeriods()).size());
        List<DatePeriod> dates = toList(manager.getFrames(
                EntityClass.DATE_PERIOD, DatePeriod.class));

        BundleDAO persister = new BundleDAO(graph);
        Integer numDeleted = persister.delete(bundle);
        assertTrue(numDeleted > 0);
        assertEquals(
                dates.size() - 2,
                toList(
                        manager.getFrames(EntityClass.DATE_PERIOD,
                                DatePeriod.class)).size());
        // Should raise NoSuchElementException
        manager.getFrame(ID, DocumentaryUnit.class);
    }
    
    @Test(expected = ValidationError.class)
    public void testValidationError() throws SerializationError,
            ValidationError, ItemNotFound, IntegrityError {
        DocumentaryUnit c1 = manager.getFrame(ID, DocumentaryUnit.class);
        Bundle bundle = converter.vertexFrameToBundle(c1);

        List<Bundle> dates = bundle.getRelations(TemporalEntity.HAS_DATE);
        // remove the start date key from a date
        Bundle invalidDate = dates.get(0).withData(Maps.<String,Object>newHashMap());
        Bundle newBundle = bundle.withRelation(TemporalEntity.HAS_DATE, invalidDate);

        BundleDAO persister = new BundleDAO(graph);
        persister.update(newBundle, DocumentaryUnit.class);
        fail("Bundle with invalid dates did not throw a ValidationError");
    }
}
