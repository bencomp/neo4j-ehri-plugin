package eu.ehri.extension.test;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import eu.ehri.project.definitions.Entities;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import static com.sun.jersey.api.client.ClientResponse.Status.CREATED;
import static com.sun.jersey.api.client.ClientResponse.Status.OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GroupRestClientTest extends BaseRestClientTest {

    static final String UPDATED_NAME = "UpdatedNameTEST";
    static final String TEST_GROUP_NAME = "admin";
    static final String CURRENT_ADMIN_USER = "mike";
    static final String NON_ADMIN_USER = "reto";

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        initializeTestDb(GroupRestClientTest.class.getName());
    }

    @Test
    public void testCreateDeleteGroup() throws Exception {
        // Create
        String jsonGroupTestString = "{\"type\": \"group\", \"data\":{\"identifier\": \"jmp\", \"name\": \"JMP\"}}";
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.GROUP)).entity(jsonGroupTestString)
                .post(ClientResponse.class);

        assertStatus(CREATED, response);
        // Get created doc via the response location?
        URI location = response.getLocation();
        response = jsonCallAs(getAdminUserProfileId(), location)
                .get(ClientResponse.class);
        assertStatus(OK, response);
    }

    @Test
    public void testAddUser() throws Exception {
        // Create
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.GROUP, TEST_GROUP_NAME, NON_ADMIN_USER))
                .post(ClientResponse.class);
        assertStatus(OK, response);
    }

    @Test
    public void testRemoveUser() throws Exception {
        // Create
        WebResource resource = client.resource(
                ehriUri(Entities.GROUP, TEST_GROUP_NAME, CURRENT_ADMIN_USER));
        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.GROUP, TEST_GROUP_NAME, CURRENT_ADMIN_USER))
                .delete(ClientResponse.class);
        assertStatus(OK, response);
    }

    @Test
    public void testListGroupMembers() throws Exception {
        final String GROUP_ID = "kcl";
        WebResource resource = client.resource(ehriUri(Entities.GROUP, GROUP_ID, "list"));

        ClientResponse response = jsonCallAs(getAdminUserProfileId(),
                ehriUri(Entities.GROUP, GROUP_ID, "list"))
                .get(ClientResponse.class);

        assertStatus(OK, response);

        // check results
        //System.out.println(response.getEntity(String.class));
        Set<String> ids = getIdsFromEntityListJson(response.getEntity(String.class));
        // for 'kcl' it should be 'mike', 'reto' and nothing else
        assertTrue(ids.contains("mike"));
        assertTrue(ids.contains("reto"));
        assertEquals(2, ids.size());
    }

    /**
     * helpers **
     */

    private Set<String> getIdsFromEntityListJson(String jsonString) throws JSONException {
        JSONArray jsonArray = new JSONArray(jsonString);
        //System.out.println("id: " + obj.get("id"));
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject item = jsonArray.getJSONObject(i);
            //System.out.println("id: " + item.get("id"));
            ids.add(item.getString("id").toString());
        }
        return ids;
    }
}
