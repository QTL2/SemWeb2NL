/**
 * 
 */
package org.aksw.avatar;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.aksw.avatar.clustering.Node;
import org.aksw.avatar.clustering.WeightedGraph;
import org.aksw.avatar.dump.Controller;
import org.aksw.avatar.dump.LogEntry;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.reasoning.SPARQLReasoner;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLProperty;

import uk.ac.manchester.cs.owl.owlapi.OWLObjectPropertyImpl;

/**
 * @author Lorenz Buehmann
 *
 */
public class EntitySummarizationModelGenerator {

	private SPARQLQueryProcessor processor;
	private SparqlEndpointKS ks;

	public EntitySummarizationModelGenerator(SparqlEndpointKS ks) {
		this.ks = ks;
		processor = new SPARQLQueryProcessor(ks);
	}
	
	
	/**
	 * Generates a entity summarization model given a collection of SPARQL query log entries. 
	 * @param logEntries
	 * @return the entity summarization model
	 */
	public EntitySummarizationModel generateModel(Collection<LogEntry> logEntries){
		Set<EntitySummarizationTemplate> templates = new HashSet<EntitySummarizationTemplate>();
        
        //process the log entries
        Collection<Map<OWLClass, Set<OWLProperty>>> result = processor.processEntries(logEntries);
        
        //generate for each class in the knowledge base a summarization template
        for(OWLClass nc : new SPARQLReasoner(ks).getOWLClasses()){
        	//generate the weighted graph
       	 	WeightedGraph wg = Controller.generateGraphMultithreaded(nc, result);
       	 	//generate the entity summarization template
       	 	Set<OWLProperty> properties = new HashSet<OWLProperty>();
       	 	for (Entry<Node, Double> entry : wg.getNodes().entrySet()) {
				properties.add(new OWLObjectPropertyImpl(IRI.create(entry.getKey().label)));
			}
       	 	templates.add(new EntitySummarizationTemplate(nc, properties));
        }
        return new EntitySummarizationModel(templates);
	}

}
