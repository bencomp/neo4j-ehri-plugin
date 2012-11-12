package eu.ehri.extension;

import java.net.URI;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Response.Status;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;

import eu.ehri.extension.errors.BadRequester;
import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.Agent;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.EntityTypes;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.persistance.EntityBundle;
import eu.ehri.project.views.ActionViews;
import eu.ehri.project.views.Query;

/**
 * Provides a RESTfull interface for the Agent
 */
@Path(EntityTypes.AGENT)
public class AgentResource extends EhriNeo4jFramedResource<Agent> {

    public AgentResource(@Context GraphDatabaseService database) {
        super(database, Agent.class);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:\\d+}")
    public Response getAgent(@PathParam("id") long id) throws PermissionDenied,
            BadRequester {
        return retrieve(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:.+}")
    public Response getAgent(@PathParam("id") String id) throws ItemNotFound,
            PermissionDenied, BadRequester {
        return retrieve(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/list")
    public StreamingOutput listAgents(
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("" + DEFAULT_LIST_LIMIT) int limit)
            throws ItemNotFound, BadRequester {
        return list(offset, limit);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createAgent(String json) throws PermissionDenied,
            ValidationError, IntegrityError, DeserializationError,
            ItemNotFound, BadRequester {
        return create(json);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAgent(String json) throws PermissionDenied,
            IntegrityError, ValidationError, DeserializationError,
            ItemNotFound, BadRequester {
        return update(json);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:.+}")
    public Response updateAgent(@PathParam("id") String id, String json)
            throws PermissionDenied, IntegrityError, ValidationError,
            DeserializationError, ItemNotFound, BadRequester {
        return update(id, json);
    }

    @DELETE
    @Path("/{id}")
    public Response deleteAgent(@PathParam("id") long id)
            throws PermissionDenied, ValidationError, ItemNotFound,
            BadRequester {
        return delete(id);
    }

    @DELETE
    @Path("/{id:.+}")
    public Response deleteAgent(@PathParam("id") String id)
            throws PermissionDenied, ItemNotFound, ValidationError,
            BadRequester {
        return delete(id);
    }

    /**
     * Create an instance of the 'entity' in the database
     * 
     * @param json
     *            The json representation of the entity to create (no vertex
     *            'id' fields)
     * @return The response of the create request, the 'location' will contain
     *         the url of the newly created instance.
     * @throws PermissionDenied
     * @throws IntegrityError
     * @throws ValidationError
     * @throws DeserializationError
     * @throws SerializationError
     * @throws BadRequester
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:\\d+}")
    public Response createAgentDocumentaryUnit(@PathParam("id") long id,
            String json) throws PermissionDenied, ValidationError,
            IntegrityError, DeserializationError, SerializationError,
            BadRequester {
        Agent agent = views.detail(graph.getVertex(id, cls),
                getRequesterUserProfile());
        DocumentaryUnit doc = createDocumentaryUnit(json, agent);
        return buildResponseFromDocumentaryUnit(doc);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id:.+}/" + EntityTypes.DOCUMENTARY_UNIT)
    public Response createAgentDocumentaryUnit(@PathParam("id") String id,
            String json) throws PermissionDenied, ValidationError,
            IntegrityError, DeserializationError, ItemNotFound, BadRequester {
        Transaction tx = graph.getBaseGraph().getRawGraph().beginTx();
        try {
            Agent agent = new Query<Agent>(graph, Agent.class).get(
                    AccessibleEntity.IDENTIFIER_KEY, id,
                    getRequesterUserProfile());
            DocumentaryUnit doc = createDocumentaryUnit(json, agent);
            tx.success();
            return buildResponseFromDocumentaryUnit(doc);
        } catch (SerializationError e) {
            tx.failure();
            throw new WebApplicationException(e);
        } finally {
            tx.finish();
        }
    }

    // Helpers

    private Response buildResponseFromDocumentaryUnit(DocumentaryUnit doc)
            throws SerializationError {
        String jsonStr = converter.vertexFrameToJson(doc);
        URI docUri = UriBuilder.fromUri(uriInfo.getBaseUri())
                .segment(EntityTypes.DOCUMENTARY_UNIT)
                .segment(doc.getIdentifier()).build();

        return Response.status(Status.CREATED).location(docUri)
                .entity((jsonStr).getBytes()).build();
    }

    private DocumentaryUnit createDocumentaryUnit(String json, Agent agent)
            throws DeserializationError, PermissionDenied, ValidationError,
            IntegrityError, BadRequester {
        EntityBundle<DocumentaryUnit> entityBundle = converter
                .jsonToBundle(json);

        DocumentaryUnit doc = new ActionViews<DocumentaryUnit>(graph,
                DocumentaryUnit.class)
                .create(converter.bundleToData(entityBundle),
                        getRequesterUserProfile());
        // Add it to this agent's collections
        agent.addCollection(doc);
        return doc;
    }
}
