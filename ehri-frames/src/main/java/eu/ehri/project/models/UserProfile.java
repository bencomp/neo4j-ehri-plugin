package eu.ehri.project.models;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.frames.Adjacency;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.annotations.EntityType;
import eu.ehri.project.models.annotations.Fetch;
import eu.ehri.project.models.base.*;

@EntityType(EntityClass.USER_PROFILE)
public interface UserProfile extends Accessor, AccessibleEntity, IdentifiableEntity,
        Annotator, Actioner, NamedEntity {

    @Fetch(Ontology.ACCESSOR_BELONGS_TO_GROUP)
    @Adjacency(label = Ontology.ACCESSOR_BELONGS_TO_GROUP)
    public Iterable<Group> getGroups();

    @Adjacency(label = Ontology.USER_FOLLOWS_USER, direction = Direction.OUT)
    public Iterable<UserProfile> getFollowing();

    @Adjacency(label = Ontology.USER_FOLLOWS_USER, direction = Direction.OUT)
    public void addFollowing(final UserProfile user);

    @Adjacency(label = Ontology.USER_FOLLOWS_USER, direction = Direction.OUT)
    public void removeFollowing(final UserProfile user);
}
