package eu.ehri.project.views.impl;

import java.util.Map;

import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph;
import com.tinkerpop.frames.FramedGraph;

import eu.ehri.project.acl.AclManager;
import eu.ehri.project.acl.PermissionType;
import eu.ehri.project.acl.SystemScope;
import eu.ehri.project.core.GraphManager;
import eu.ehri.project.core.GraphManagerFactory;
import eu.ehri.project.exceptions.DeserializationError;
import eu.ehri.project.exceptions.IntegrityError;
import eu.ehri.project.exceptions.ItemNotFound;
import eu.ehri.project.exceptions.PermissionDenied;
import eu.ehri.project.exceptions.SerializationError;
import eu.ehri.project.exceptions.ValidationError;
import eu.ehri.project.models.base.AccessibleEntity;
import eu.ehri.project.models.base.Accessor;
import eu.ehri.project.models.base.PermissionScope;
import eu.ehri.project.persistance.BundleDAO;
import eu.ehri.project.persistance.Converter;
import eu.ehri.project.persistance.Bundle;
import eu.ehri.project.views.Crud;
import eu.ehri.project.views.ViewHelper;

public final class CrudViews<E extends AccessibleEntity> implements Crud<E> {
    private final FramedGraph<Neo4jGraph> graph;
    private final Class<E> cls;
    private final ViewHelper helper;
    private final GraphManager manager;
    private final Converter converter;
    private final PermissionScope scope;
    private final AclManager acl;

    /**
     * Scoped Constructor.
     * 
     * @param graph
     * @param cls
     */
    public CrudViews(FramedGraph<Neo4jGraph> graph, Class<E> cls,
            PermissionScope scope) {
        this.graph = graph;
        this.cls = cls;
        this.scope = scope;
        helper = new ViewHelper(graph, cls, scope);
        converter = new Converter();
        manager = GraphManagerFactory.getInstance(graph);
        acl = new AclManager(graph);
    }

    /**
     * Constructor.
     * 
     * @param graph
     * @param cls
     */
    public CrudViews(FramedGraph<Neo4jGraph> graph, Class<E> cls) {
        this(graph, cls, SystemScope.getInstance());
    }

    /**
     * Return a string representation of the given item.
     * 
     * @param item
     * @param user
     * @return The given framed vertex
     * @throws PermissionDenied
     */
    public E detail(E entity, Accessor user) throws PermissionDenied {
        helper.checkReadAccess(entity, user);
        return entity;
    }

    /**
     * Update an object bundle, also updating dependent items.
     * 
     * @param data
     * @param user
     * @return The updated framed vertex
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws IntegrityError
     * @throws ItemNotFound
     */
    public E update(Map<String, Object> data, Accessor user)
            throws PermissionDenied, ValidationError, DeserializationError,
            IntegrityError, ItemNotFound {
        Bundle bundle = converter.dataToBundle(data);
        E entity = graph.frame(manager.getVertex(bundle.getId()), cls);
        helper.checkEntityPermission(entity, user, PermissionType.UPDATE);
        return new BundleDAO(graph, scope).update(bundle, cls);
    }

    /**
     * Create a new object of type `E` from the given data, within the scope of
     * `scope`.
     * 
     * @param data
     * @param user
     * @return The created framed vertex
     * @throws DeserializationError
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws IntegrityError
     */
    public E create(Map<String, Object> data, Accessor user)
            throws PermissionDenied, ValidationError, DeserializationError,
            IntegrityError {
        helper.checkPermission(user, PermissionType.CREATE);
        Bundle bundle = converter.dataToBundle(data);
        E item = new BundleDAO(graph, scope).create(bundle, cls);
        // If a user creates an item, grant them OWNER perms on it.
        acl.grantPermissions(user, item, PermissionType.OWNER);
        return item;
    }

    /**
     * Create or update a new object of type `E` from the given data, within the
     * scope of `scope`.
     * 
     * @param data
     * @param user
     * @return The created framed vertex
     * @throws DeserializationError
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws IntegrityError
     */
    public E createOrUpdate(Map<String, Object> data, Accessor user)
            throws PermissionDenied, ValidationError, DeserializationError,
            IntegrityError {
        helper.checkPermission(user, PermissionType.CREATE);
        helper.checkPermission(user, PermissionType.UPDATE);
        Bundle bundle = converter.dataToBundle(data);
        return new BundleDAO(graph, scope).createOrUpdate(bundle, cls);
    }

    /**
     * Delete an object bundle, following dependency cascades, within the scope
     * of item `scope`.
     * 
     * @param item
     * @param user
     * @return The number of vertices deleted.
     * @throws PermissionDenied
     * @throws ValidationError
     * @throws SerializationError
     */
    public Integer delete(E item, Accessor user) throws PermissionDenied,
            ValidationError, SerializationError {
        helper.checkEntityPermission(item, user, PermissionType.DELETE);
        return new BundleDAO(graph, scope).delete(converter
                .vertexFrameToBundle(item));
    }

    public Crud<E> setScope(PermissionScope scope) {
        return new CrudViews<E>(graph, cls, scope);
    }
}