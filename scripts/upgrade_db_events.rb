#!/usr/bin/env jruby

# NB: Things to do before running this script:
#  - make sure jruby is on the PATH
#  - make sure the CLASSPATH env var is set for the given NEO4J
#  - make sure the NEO4J_HOME env var is set

# Abort if we don't have NEO4J_HOME
if ENV['NEO4J_HOME'].nil? or ENV['NEO4J_HOME'].empty? then
    abort "Error: NEO4J_HOME environment variable must be defined."
end

# Abort if we don't have NEO4J_HOME
if ENV['CLASSPATH'].nil? or ENV['CLASSPATH'].empty? then
    abort "Error: CLASSPATH environment variable must be defined."
end

# The magic necessary to do Java stuff...
require "java"

# Import Java classes like so...
java_import "com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph"
java_import "com.tinkerpop.frames.FramedGraph"
java_import "eu.ehri.project.core.GraphManagerFactory"
java_import "eu.ehri.project.models.EntityClass"
java_import "eu.ehri.project.models.base.Frame"
java_import "eu.ehri.project.models.events.SystemEvent"
java_import "eu.ehri.project.persistance.Bundle"
java_import "eu.ehri.project.persistance.BundleDAO"
java_import "eu.ehri.project.persistance.Serializer"
java_import "eu.ehri.project.exceptions.SerializationError"
java_import "java.lang.IllegalArgumentException"
java_import "eu.ehri.project.definitions.EventTypes"

# Wildcard import of a package like so. Note, the stuff
# imported will be used with the given module namespace, i.e.
# EhriModels::DocumentaryUnit
module EhriModels
    include_package "eu.ehri.project.models"
end

# Use the default if NEO4J_DB isn't set...
DB_PATH = ENV['NEO4J_DB'] ||= "#{ENV['NEO4J_HOME']}/data/graph.db"

# Initialise a graph and the manager.
# Note: the graph must not be being used elsewhere (i.e. by the server)
graph = FramedGraph.new(Neo4jGraph.new DB_PATH)
manager = GraphManagerFactory.get_instance graph

# Test the serializer. Note that the CamelCase Java methods
# can be converted to the snake_case Ruby style automatically.
serializer = Serializer.new graph

# List the types of events and their log message...
tx = graph.get_base_graph.get_raw_graph.begin_tx
begin
    manager.get_frames(EntityClass::SYSTEM_EVENT, SystemEvent.java_class).each do |event|
        begin    
            if event.get_event_type.nil? then
                type = case event.get_log_message
                    when /item-level permissions/i
                        EventTypes::setItemPermissions
                    when /permissions with scope '(\w)'/i
                        EventTypes::setGlobalPermissions
                    when /visibility/i
                        EventTypes::setVisibility
                    when /annotate|annotation/i
                        EventTypes::annotation
                    when /link/i
                        EventTypes::link
                    when /(:?access point|update|updating|created description|copying description)/i
                        EventTypes::modification
                    when /created|creating/i
                        EventTypes::creation
                    when /import/i
                        EventTypes::ingest
                    when /remove.+group/i
                        EventTypes::removeGroup
                    when /add.+group/i
                        EventTypes::addGroup
                    when /delete|deleting/i
                        EventTypes::deletion
                    else
                        nil
                end
                 
                puts "#{event} #{event.get_log_message} => #{type}"
                bundle = serializer.vertex_frame_to_bundle(event).with_data_value(
                            SystemEvent::EVENT_TYPE, type.to_string)
                
                BundleDAO.new(graph).update(bundle, SystemEvent.java_class)
                if type.nil?
                   puts "No type found: #{msg}"
                end
            end
        rescue Exception => msg
            puts "ERROR #{event} - #{msg}"
        end
    end
    tx.success
rescue
    tx.failure
else
   tx.finish
end

