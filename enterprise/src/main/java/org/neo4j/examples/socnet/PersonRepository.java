package org.neo4j.examples.socnet;

import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.IterableWrapper;
import org.neo4j.index.IndexService;

import static org.neo4j.examples.socnet.RelTypes.A_PERSON;
import static org.neo4j.examples.socnet.RelTypes.REF_PERSONS;

public class PersonRepository
{
    private final GraphDatabaseService graphDb;
    private final IndexService index;
    private final Node personRefNode;

    public PersonRepository( GraphDatabaseService graphDb, IndexService index )
    {
        this.graphDb = graphDb;
        this.index = index;
        Relationship rel = graphDb.getReferenceNode().getSingleRelationship(
                REF_PERSONS, Direction.OUTGOING );
        if ( rel != null )
        {
            personRefNode = rel.getEndNode();
        }
        else
        {
            personRefNode = createPersonReferenceNode();
        }
    }

    private Node createPersonReferenceNode()
    {
        Transaction tx = graphDb.beginTx();
        try
        {
            Node refNode = graphDb.createNode();
            graphDb.getReferenceNode().createRelationshipTo( refNode,
                    REF_PERSONS );
            tx.success();
            return refNode;
        }
        finally
        {
            tx.finish();
        }
    }

    public Person createPerson( String name )
    {
        // to guard against duplications we use the lock grabbed on ref node
        // when
        // creating a relationship and are optimistic about person not existing
        Transaction tx = graphDb.beginTx();
        try
        {
            Node newPersonNode = graphDb.createNode();
            Relationship rel = personRefNode.createRelationshipTo(
                    newPersonNode, A_PERSON );
            // lock now taken, we can check if already exist in index
            Node alreadyExist = index.getSingleNode( Person.NAME, name );
            if ( alreadyExist != null )
            {
                // clean up and return existing
                rel.delete();
                newPersonNode.delete();
                tx.success();
                return new Person( alreadyExist );
            }
            newPersonNode.setProperty( Person.NAME, name );
            index.index( newPersonNode, Person.NAME, name );
            tx.success();
            return new Person( newPersonNode );
        }
        finally
        {
            tx.finish();
        }
    }

    public Person getPersonByName( String name )
    {
        Node personNode = index.getSingleNode( Person.NAME, name );
        if ( personNode == null )
        {
            throw new IllegalArgumentException( "Person[" + name
                                                + "] not found" );
        }
        return new Person( personNode );
    }

    public void deletePerson( Person person )
    {
        Transaction tx = graphDb.beginTx();
        try
        {
            Node personNode = person.getUnderlyingNode();
            index.removeIndex( personNode, Person.NAME, person.getName() );
            for ( Person friend : person.getFriends() )
            {
                person.removeFriend( friend );
            }
            personNode.getSingleRelationship( A_PERSON, Direction.INCOMING ).delete();

            for ( StatusUpdate status : person.getStatus() )
            {
                Node statusNode = status.getUnderlyingNode();
                for ( Relationship r : statusNode.getRelationships() )
                {
                    r.delete();
                }
                statusNode.delete();
            }

            personNode.delete();
            tx.success();
        }
        finally
        {
            tx.finish();
        }
    }

    public Iterable<Person> getAllPersons()
    {
        return new IterableWrapper<Person, Relationship>(
                personRefNode.getRelationships( A_PERSON ) )
        {
            @Override
            protected Person underlyingObjectToObject( Relationship personRel )
            {
                return new Person( personRel.getEndNode() );
            }
        };
    }
}
