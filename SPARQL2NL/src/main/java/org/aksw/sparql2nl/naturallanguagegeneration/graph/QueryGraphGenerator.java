/**
 * 
 */
package org.aksw.sparql2nl.naturallanguagegeneration.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.swing.JFrame;

import org.aksw.sparql2nl.queryprocessing.TriplePatternExtractor;
import org.dllearner.algorithms.qtl.datastructures.rendering.Vertex;
import org.jgrapht.event.ConnectedComponentTraversalEvent;
import org.jgrapht.event.EdgeTraversalEvent;
import org.jgrapht.event.TraversalListener;
import org.jgrapht.event.VertexTraversalEvent;
import org.jgrapht.ext.JGraphXAdapter;
import org.jgrapht.graph.DirectedMultigraph;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.traverse.GraphIterator;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.sparql.core.Var;
import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.layout.mxIGraphLayout;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;



/**
 * @author Lorenz Buehmann
 *
 */
public class QueryGraphGenerator {
	
	private TriplePatternExtractor triplePatternExtractor = new TriplePatternExtractor();
	private boolean ignoreOptionalPatterns = false;
	
	public DirectedMultigraph<Node, Edge> generateQueryGraph(Query query){
		
		//extract triple patterns
		Set<Triple> triplePattern = triplePatternExtractor.extractTriplePattern(query, ignoreOptionalPatterns);
		
		//build graph
		DirectedMultigraph<Node, Edge> graph = new DirectedMultigraph<Node, Edge>(Edge.class);
		for (Triple tp : triplePattern) {
			graph.addVertex(tp.getSubject());
			graph.addVertex(tp.getObject());
			graph.addEdge(tp.getSubject(), tp.getObject(), new Edge(tp));
		}
		
		return graph;
	}
	
	public void reverse(DirectedMultigraph<Node, Edge> graph, Node rootNode) {
        System.out.println("reversing graph ... ");
        Set<Node> visited = new HashSet<Node>(graph.vertexSet().size() + 1);
        for (Edge edge : graph.incomingEdgesOf(rootNode)) {
            if (!visited.contains(graph.getEdgeSource(edge))) {
                reverse(graph, graph.getEdgeSource(edge), visited);
            }
        }
    }

    private void reverse(DirectedMultigraph<Node, Edge> graph, Node v, Set<Node> visited) {
        System.out.println("reversing " + v);
        visited.add(v);
        List<Node> neigbors = new ArrayList<Node>();
        List<Edge> toReverse = new ArrayList<Edge>();
        for (Edge edge : graph.incomingEdgesOf(v)) {
            Node source = graph.getEdgeSource(edge);
            if (!visited.contains(source)) {
                neigbors.add(source);
            }
            toReverse.add(edge);
        }

        for (Node n : neigbors) {
            reverse(graph, n, visited);
        }

        for (Edge edge : toReverse) {
        	graph.removeEdge(edge);
        	Triple reversedTriple = Triple.create(edge.asTriple().getObject(), edge.asTriple().getPredicate(), edge.asTriple().getSubject());
			graph.addEdge(edge.asTriple().getObject(), edge.asTriple().getSubject(), new Edge(reversedTriple, true));
        }

        System.out.println("reversed " + v);
    }
	
	public DirectedMultigraph<Node, Edge> transform(DirectedMultigraph<Node, Edge> graph, Node startNode){
		
		Set<Edge> revertedEdges = revertEdges(graph, startNode);
		for (Edge edge : revertedEdges) {
			graph.addEdge(edge.asTriple().getSubject(), edge.asTriple().getObject(), edge);
		}
		
		for (Edge edge : graph.edgeSet()) {
			System.out.println(edge);
		}
		
		return graph;
	}
	
	private Set<Edge> revertEdges(DirectedMultigraph<Node, Edge> graph, Node startNode){
		Set<Edge> incomingEdges = new HashSet<Edge>(graph.incomingEdgesOf(startNode));
		Set<Edge> newEdges = new HashSet<Edge>();
		for (Iterator<Edge> iterator = incomingEdges.iterator(); iterator.hasNext();) {
			Edge edge = iterator.next();
			
			newEdges.addAll(revertEdges(graph, graph.getEdgeSource(edge)));
			
			graph.removeEdge(edge);
			Triple reversedTriple = Triple.create(edge.asTriple().getObject(), edge.asTriple().getPredicate(), edge.asTriple().getSubject());
			newEdges.add(new Edge(reversedTriple, true));
		}
		return newEdges;
	}
	
	public static void showGraph(final DirectedMultigraph<Node, Edge> graph) {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {
	        public void run() {
	        	JFrame frame = new JFrame("DemoGraph");
	            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

	            JGraphXAdapter<Node, Edge> graphAdapter = 
	                    new JGraphXAdapter<Node, Edge>(graph);

	            mxIGraphLayout layout = new mxCircleLayout(graphAdapter);
	            layout = new mxHierarchicalLayout(graphAdapter);
	            layout.execute(graphAdapter.getDefaultParent());

	            frame.add(new mxGraphComponent(graphAdapter));

	            frame.pack();
	            frame.setLocationByPlatform(true);
	            frame.setVisible(true);
	        }
	    });
        
    }
	
	/**
	 * @param ignoreOptionalPatterns the ignoreOptionalPatterns to set
	 */
	public void setIgnoreOptionalPatterns(boolean ignoreOptionalPatterns) {
		this.ignoreOptionalPatterns = ignoreOptionalPatterns;
	}
	
	public static void main(String[] args) throws Exception {
		Query query = QueryFactory.create(
				"PREFIX : <http://sparql2nl.aksw.org/> "
				+ "SELECT ?state ?city WHERE {?city :locatedIn ?state. ?city a :City. ?state a :State.}"
						);
		QueryGraphGenerator graphGenerator = new QueryGraphGenerator();
		DirectedMultigraph<Node, Edge> graph = graphGenerator.generateQueryGraph(query);
		
		for (Var var : query.getProjectVars()) {System.out.println("Var:" + var);
			GraphIterator<Node, Edge> iterator = new DepthFirstIterator<Node, Edge>(graph, var.asNode());
			iterator.addTraversalListener(new TraversalListener<Node, Edge>() {
				
				@Override
				public void vertexTraversed(VertexTraversalEvent<Node> e) {
				}
				
				@Override
				public void vertexFinished(VertexTraversalEvent<Node> e) {
				}
				
				@Override
				public void edgeTraversed(EdgeTraversalEvent<Node, Edge> e) {
				}
				
				@Override
				public void connectedComponentStarted(ConnectedComponentTraversalEvent e) {
					System.out.println("Started");
				}
				
				@Override
				public void connectedComponentFinished(ConnectedComponentTraversalEvent e) {
					System.out.println("Finished");
				}
			});
	        while (iterator.hasNext()) {
	            System.out.println(iterator.next());
	        }
		}
	}
}
