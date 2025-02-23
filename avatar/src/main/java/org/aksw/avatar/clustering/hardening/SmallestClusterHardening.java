/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.avatar.clustering.hardening;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aksw.avatar.clustering.Node;
import org.aksw.avatar.clustering.WeightedGraph;

/**
 * Hardening that prefers clusters with smaller weights
 * @author ngonga
 */
public class SmallestClusterHardening extends LargestClusterHardening{
     public List<Set<Node>> harden(Set<Set<Node>> clusters, WeightedGraph wg) {
        Set<Node> nodes = new HashSet<Node>(wg.getNodes().keySet());
        double min, weight;
        Set<Node> bestCluster;
        List<Set<Node>> result = new ArrayList<Set<Node>>();
        while (!nodes.isEmpty()) {
            min = Double.MAX_VALUE;
            bestCluster = null;
            //first get weights            
            for (Set<Node> c : clusters) {
                if (!result.contains(c)) {
                    weight = getWeight(c, wg, nodes);
                    if (weight < min) {
                        min = weight;
                        bestCluster = c;
                    }
                }
            }
            // no more clusters available
            if (bestCluster == null) {
                return result;
            }
            //in all other cases       
            clusters.remove(bestCluster);
            bestCluster.retainAll(nodes);
            result.add(bestCluster);
            nodes.removeAll(bestCluster);
        }
        result = Lists.reverse(result);
        return result;
    }
}
