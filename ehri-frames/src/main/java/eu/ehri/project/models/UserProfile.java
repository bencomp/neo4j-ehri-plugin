package eu.ehri.project.models;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.frames.Adjacency;
import com.tinkerpop.frames.modules.javahandler.JavaHandler;
import com.tinkerpop.frames.modules.javahandler.JavaHandlerContext;
import com.tinkerpop.pipes.PipeFunction;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.annotations.Fetch;
import eu.ehri.project.models.base.*;
import eu.ehri.project.models.utils.JavaHandlerUtils;

import static eu.ehri.project.definitions.Ontology.*;

@EntityType(EntityClass.USER_PROFILE)
public interface UserProfile extends Accessor, AccessibleEntity, IdentifiableEntity,
        Annotator, Actioner, NamedEntity {

    public static String FOLLOWER_COUNT = "_followers";
    public static String FOLLOWING_COUNT = "_following";
    public static String WATCHING_COUNT = "_watching";
    public static String WATCHED_COUNT = "_watchedBy";


    @Fetch(Ontology.ACCESSOR_BELONGS_TO_GROUP)
    @Adjacency(label = ACCESSOR_BELONGS_TO_GROUP)
    public Iterable<Group> getGroups();

    @Adjacency(label = USER_FOLLOWS_USER, direction = Direction.IN)
    public Iterable<UserProfile> getFollowers();

    @Adjacency(label = USER_FOLLOWS_USER, direction = Direction.OUT)
    public Iterable<UserProfile> getFollowing();

    @JavaHandler
    public void addFollowing(final UserProfile user);

    @JavaHandler
    public void removeFollowing(final UserProfile user);

    @JavaHandler
    public boolean isFollowing(final UserProfile otherUser);

    @JavaHandler
    public boolean isFollower(final UserProfile otherUser);

    @Adjacency(label = USER_WATCHING_ITEM, direction = Direction.OUT)
    public Iterable<Watchable> getWatching();

    @Adjacency(label = Ontology.LINK_HAS_LINKER, direction = Direction.IN)
    public Iterable<Link> getLinks();

    @Adjacency(label = Ontology.ANNOTATOR_HAS_ANNOTATION)
    public Iterable<Annotation> getAnnotations();

    @JavaHandler
    public void addWatching(final Watchable item);

    @JavaHandler
    public void removeWatching(final Watchable item);

    /**
     * Users who share groups with this user.
     */
    @JavaHandler
    public Iterable<UserProfile> coGroupMembers();

    @JavaHandler
    public boolean isWatching(final Watchable item);

    abstract class Impl implements JavaHandlerContext<Vertex>, UserProfile {

        private void updateFollowCounts(Vertex user, Vertex other) {
            JavaHandlerUtils.cacheCount(
                    user, gremlin().out(USER_FOLLOWS_USER), FOLLOWING_COUNT);
            JavaHandlerUtils.cacheCount(
                    other,
                    gremlin().start(other).in(USER_FOLLOWS_USER), FOLLOWER_COUNT);
        }

        private void updateWatchCount(Vertex user, Vertex item) {
            JavaHandlerUtils.cacheCount(
                    user, gremlin().out(USER_WATCHING_ITEM), WATCHING_COUNT);
            JavaHandlerUtils.cacheCount(
                    item,
                    gremlin().start(item).in(USER_WATCHING_ITEM), WATCHED_COUNT);
        }

        public void addFollowing(final UserProfile user) {
            if (!isFollowing(user)) {
                it().addEdge(USER_FOLLOWS_USER, user.asVertex());
                updateFollowCounts(it(), user.asVertex());
            }
        }

        public void removeFollowing(final UserProfile user) {
            for (Edge e : it().getEdges(Direction.OUT, USER_FOLLOWS_USER)) {
                if (e.getVertex(Direction.IN).equals(user.asVertex())) {
                    e.remove();
                }
            }
            updateFollowCounts(it(), user.asVertex());
        }

        public boolean isFollowing(final UserProfile otherUser) {
            return gremlin().out(USER_FOLLOWS_USER).filter(new PipeFunction<Vertex, Boolean>() {
                @Override
                public Boolean compute(Vertex vertex) {
                    return vertex.equals(otherUser.asVertex());
                }
            }).hasNext();
        }

        public boolean isFollower(final UserProfile otherUser) {
            return gremlin().in(USER_FOLLOWS_USER).filter(new PipeFunction<Vertex, Boolean>() {
                @Override
                public Boolean compute(Vertex vertex) {
                    return vertex.equals(otherUser.asVertex());
                }
            }).hasNext();
        }

        public void addWatching(final Watchable item) {
            if (!isWatching(item)) {
                it().addEdge(USER_WATCHING_ITEM, item.asVertex());
                updateWatchCount(it(), item.asVertex());
            }
        }

        public void removeWatching(final Watchable item) {
            for (Edge e : it().getEdges(Direction.OUT, USER_WATCHING_ITEM)) {
                if (e.getVertex(Direction.IN).equals(item.asVertex())) {
                    e.remove();
                }
            }
            updateWatchCount(it(), item.asVertex());
        }

        public boolean isWatching(final Watchable item) {
            return gremlin().out(USER_WATCHING_ITEM).filter(new PipeFunction<Vertex, Boolean>() {
                @Override
                public Boolean compute(Vertex vertex) {
                    return vertex.equals(item.asVertex());
                }
            }).hasNext();
        }

        public Iterable<UserProfile> coGroupMembers() {
            return frameVertices(gremlin().as("n")
                    .out(Ontology.ACCESSOR_BELONGS_TO_GROUP)
                    .loop("n", JavaHandlerUtils.defaultMaxLoops, JavaHandlerUtils.noopLoopFunc)
                    .in(Ontology.ACCESSOR_BELONGS_TO_GROUP).filter(new PipeFunction<Vertex, Boolean>() {
                @Override
                public Boolean compute(Vertex vertex) {
                    // Exclude the current user...
                    if (it().equals(vertex)) {
                        return false;
                    }
                    // Exclude other groups...
                    String type = vertex.getProperty(EntityType.TYPE_KEY);
                    if (type == null || !type.equals(Entities.USER_PROFILE)) {
                        return false;
                    }
                    return true;
                }
            }));
        }
    }
}
