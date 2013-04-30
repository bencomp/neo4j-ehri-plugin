package eu.ehri.project.views;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.FramedGraph;
import com.tinkerpop.pipes.PipeFunction;
import eu.ehri.project.acl.AclManager;
import eu.ehri.project.acl.PermissionType;
import eu.ehri.project.acl.SystemScope;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.Annotation;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.Link;
import eu.ehri.project.models.UndeterminedRelationship;
import eu.ehri.project.models.base.*;
import eu.ehri.project.persistance.*;

import java.util.List;

/**
 * View class for handling annotation-related operations.
 *
 * @author mike
 */
public final class LinkViews {

    private final FramedGraph<?> graph;
    private final AclManager acl;
    private final ViewHelper helper;
    private final GraphManager manager;

    /**
     * Scoped constructor.
     *
     * @param graph
     * @param scope
     */
    public LinkViews(FramedGraph<?> graph, PermissionScope scope) {
        this.graph = graph;
        helper = new ViewHelper(graph, scope);
        acl = helper.getAclManager();
        manager = GraphManagerFactory.getInstance(graph);
    }

    /**
     * Constructor with system scope.
     *
     * @param graph
     */
    public LinkViews(FramedGraph<?> graph) {
        this(graph, SystemScope.getInstance());
    }

    /**
     * Create a link between two items.
     *
     * @param targetId1 the identifier of a AccessibleEntity target of this Annotation
     * @param targetId2 the identifier of a Annotator source of this Annotation
     * @param bundle    the annotation itself
     * @param user
     * @return
     * @throws eu.ehri.project.exceptions.ItemNotFound
     *
     * @throws eu.ehri.project.exceptions.ValidationError
     *
     * @throws eu.ehri.project.exceptions.PermissionDenied
     *
     */
    public Link createLink(String targetId1, String targetId2, List<String> bodies, Bundle bundle,
            Accessor user) throws ItemNotFound, ValidationError, PermissionDenied {
        LinkableEntity t1 = manager.getFrame(targetId1, LinkableEntity.class);
        LinkableEntity t2 = manager.getFrame(targetId2, LinkableEntity.class);
        helper.checkEntityPermission(t1, user, PermissionType.ANNOTATE);
        helper.checkEntityPermission(t2, user, PermissionType.ANNOTATE);
        Link link = new BundleDAO(graph).create(bundle, Link.class);
        link.addLinkTarget(t1);
        link.addLinkTarget(t2);
        link.setLinker(user);
        ActionManager.EventContext eventContext = new ActionManager(graph).logEvent(t1, graph.frame(user.asVertex(), Actioner.class),
                "Added link");
        eventContext.addSubjects(link).addSubjects(t2);
        for (String body : bodies) {
            System.out.println("Adding annotation body: " + body);
            AccessibleEntity item = manager.getFrame(body, AccessibleEntity.class);
            link.addLinkBody(item);
            eventContext.addSubjects(item);
        }
        return link;
    }

    /**
     * Create a link between two items, along with an access point on the given description.
     *
     * @param targetId1       the identifier of a AccessibleEntity target of this Annotation
     * @param targetId2 the identifier of a Annotator source of this Annotation
     * @param bundle   the annotation itself
     * @param user
     * @return
     * @throws ItemNotFound
     * @throws ValidationError
     * @throws PermissionDenied
     */
    public Link createAccessPointLink(String targetId1, String targetId2, String descriptionId, String bodyName,
            String bodyType, Bundle bundle, Accessor user) throws ItemNotFound, ValidationError, PermissionDenied {
        LinkableEntity t1 = manager.getFrame(targetId1, LinkableEntity.class);
        LinkableEntity t2 = manager.getFrame(targetId2, LinkableEntity.class);
        Description description = manager.getFrame(descriptionId, Description.class);
        helper.checkEntityPermission(t1, user, PermissionType.ANNOTATE);
        helper.checkEntityPermission(t2, user, PermissionType.ANNOTATE);
        helper.checkEntityPermission(description.getEntity(), user, PermissionType.UPDATE);
        Link link = new BundleDAO(graph).create(bundle, Link.class);
        Bundle relBundle = new Bundle(EntityClass.UNDETERMINED_RELATIONSHIP)
                .withDataValue(UndeterminedRelationship.NAME, bodyName)
                .withDataValue(UndeterminedRelationship.RELATIONSHIP_TYPE, bodyType)
                .withDataValue(Link.LINK_DESCRIPTION, link.getDescription());
        UndeterminedRelationship rel = new BundleDAO(graph).create(relBundle, UndeterminedRelationship.class);
        description.addUndeterminedRelationship(rel);
        link.addLinkTarget(t1);
        link.addLinkTarget(t2);
        link.setLinker(user);
        link.addLinkBody(rel);
        ActionManager.EventContext eventContext = new ActionManager(graph).logEvent(t1, graph.frame(user.asVertex(), Actioner.class),
                "Added access-point link");
        eventContext.addSubjects(link).addSubjects(t2).addSubjects(rel);
        return link;
    }
}
