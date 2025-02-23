package org.aksw.triple2nl.nlp.relation;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BinaryRequestWriter;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import com.google.common.base.Joiner;

/**
 * 
 * @author Daniel Gerber <dgerber@informatik.uni-leipzig.de>
 */
public class BoaPatternSelector {

    private static SolrServer server;
    private static Double WORDNET_DISTANCE_BOOST_FACTOR = 300000D;
    private static Double BOA_SCORE_BOOST_FACTOR = 10000D;
    private static Double REVERB_BOOST_FACTOR = 1000000D;

    // 300 / 10 / 10
    private static final List<String> BE_TOKENS = Arrays.asList("am", "are", "is", "was", "were");
    private static final String SOLR_INDEX = "sparql2nl";//"sparql2nl";//"boa_detail";

    static {
    	server = new HttpSolrServer("http://dbpedia.aksw.org:8080/solr/" + SOLR_INDEX);
    }

    /**
     * Returns an ordered list of natural language representations for a given
     * property uri. The list is ordered from highest first to lowest.
     * 
     * @param propertyUri
     * @return
     */
    public static List<Pattern> getNaturalLanguageRepresentation(String propertyUri, int maxResults) {

        // query the index to get all useful patterns
        List<Pattern> patterns = new ArrayList<Pattern>(BoaPatternSelector.querySolrIndex(propertyUri));

        // sort them by the score
        Collections.sort(patterns, new Comparator<Pattern>() {

            public int compare(Pattern pattern1, Pattern pattern2) {

                double x = (pattern2.naturalLanguageScore - pattern1.naturalLanguageScore);
                if (x < 0)
                    return -1;
                if (x == 0)
                    return 0;
                return 1;
            }
        });

        int i = 50;
        Set<Pattern> preResults = new LinkedHashSet<Pattern>();
        for (Pattern pattern : patterns) {

            if (preResults.size() >= i)
                break;
            if (pattern.naturalLanguageScore > 0)
                preResults.add(pattern);
        }

        List<Pattern> results = new ArrayList<Pattern>(preResults);
        Collections.sort(results, new Comparator<Pattern>() {

            public int compare(Pattern pattern1, Pattern pattern2) {

                double x = (pattern2.features.get("SUPPORT_NUMBER_OF_PAIRS_LEARNED_FROM") - pattern1.features.get("SUPPORT_NUMBER_OF_PAIRS_LEARNED_FROM"));
                if (x < 0)
                    return -1;
                if (x == 0)
                    return 0;
                return 1;
            }
        });

        return results.size() > maxResults ? results.subList(0, maxResults) : results;
    }

    private static boolean isSuitable(Pattern pattern) {

        List<String> wordTokensList = new ArrayList<String>(Arrays.asList(pattern.naturalLanguageRepresentation.split(" ")));
        List<String> posTagTokens = new ArrayList<String>(Arrays.asList(pattern.posTags.split(" ")));

        String[] wordTokens = pattern.naturalLanguageRepresentation.split(" ");
        String[] tagTokens = pattern.posTags.split(" ");

        // we want to remove the be forms and the corresponding pos tags
        for (int i = 0; i < tagTokens.length; i++) {

            if (wordTokens[i + 1].matches("(^\\p{Upper}.*|and)") || tagTokens[i].matches("(''|``|,|-RRB-|-LRB-|WP)")) {
                wordTokensList.set(i + 1, null);
                posTagTokens.set(i, null);
            }
        }
        if (wordTokens[wordTokens.length - 2].equals("the"))
            wordTokens[wordTokens.length - 2] = null;

        wordTokensList.removeAll(Arrays.asList("", null));
        posTagTokens.removeAll(Arrays.asList("", null));

        pattern.naturalLanguageRepresentation = Joiner.on(" ").join(wordTokensList);
        pattern.posTags = Joiner.on(" ").join(posTagTokens);

        wordTokensList.removeAll(BE_TOKENS);
        wordTokensList.remove("a");
        wordTokensList.remove("?D?");
        wordTokensList.remove("?R?");

        // check if the patterns contains a verb other than be verbs
        if ((posTagTokens.contains("VB") || posTagTokens.contains("VBZ") || posTagTokens.contains("VBP") || posTagTokens.contains("VBN") || posTagTokens.contains("VBD") || posTagTokens
                .contains("VBG")) && wordTokensList.size() > 0)
            return true;

        return false;
    }

    /**
     * 
     * @param pattern
     * @return
     */
    private static Double calculateNaturalLanguageScore(Pattern pattern) {

        return REVERB_BOOST_FACTOR * pattern.features.get("REVERB") + WORDNET_DISTANCE_BOOST_FACTOR * pattern.features.get("WORDNET_DISTANCE")
        // + LEARNED_FROM_BOOST_FACTOR *
        // pattern.features.get("SUPPORT_NUMBER_OF_PAIRS_LEARNED_FROM")
                + BOA_SCORE_BOOST_FACTOR * pattern.boaScore;
    }

    /**
     * Returns all patterns from the index and their features for reverb and the
     * wordnet distance and the overall boa-boaScore.
     * 
     * @param propertyUri
     * @return a list of patterns
     */
    private static Set<Pattern> querySolrIndex(String propertyUri) {

        Map<Integer, Pattern> patterns = new HashMap<Integer, Pattern>();

        try {

            SolrQuery query = new SolrQuery("uri:\"" + propertyUri + "\"");
            query.addField("REVERB");
            query.addField("WORDNET_DISTANCE");
            query.addField("SUPPORT_NUMBER_OF_PAIRS_LEARNED_FROM");
            query.addField("pos");
            query.addField("boa-score");
            query.addField("nlr-var");
            query.addField("nlr-no-var");
            query.setRows(10000);
            QueryResponse response = server.query(query);
            SolrDocumentList docList = response.getResults();

            // return the first list of types
            for (SolrDocument d : docList) {

                Pattern pattern = new Pattern();
                pattern.naturalLanguageRepresentation = (String) d.get("nlr-var");
                pattern.naturalLanguageRepresentationWithoutVariables = (String) d.get("nlr-no-var");
                pattern.features.put("REVERB", Double.valueOf((String) d.get("REVERB")));
                pattern.features.put("WORDNET_DISTANCE", Double.valueOf((String) d.get("WORDNET_DISTANCE")));
                pattern.features.put("SUPPORT_NUMBER_OF_PAIRS_LEARNED_FROM", Double.valueOf((String) d.get("SUPPORT_NUMBER_OF_PAIRS_LEARNED_FROM")));
                pattern.posTags = (String) d.get("pos");
                pattern.boaScore = Double.valueOf((String) d.get("boa-score"));
                pattern.naturalLanguageScore = calculateNaturalLanguageScore(pattern);

                // since ?D? and ?R? are removed so two patterns might look the
                // same
                if (isSuitable(pattern)) {

                    // merge the pattern
                    if (patterns.containsKey(pattern.hashCode())) {

                        Pattern p = patterns.get(pattern.hashCode());
                        p.features.put("REVERB", pattern.features.get("REVERB") + p.features.get("REVERB"));
                        p.features.put("WORDNET_DISTANCE", pattern.features.get("WORDNET_DISTANCE") + p.features.get("WORDNET_DISTANCE"));
                        p.features.put("SUPPORT_NUMBER_OF_PAIRS_LEARNED_FROM", pattern.features.get("SUPPORT_NUMBER_OF_PAIRS_LEARNED_FROM") + p.features.get("SUPPORT_NUMBER_OF_PAIRS_LEARNED_FROM"));
                        p.boaScore += pattern.boaScore;
                        p.naturalLanguageScore += pattern.naturalLanguageScore;
                        patterns.put(pattern.hashCode(), p);
                    }
                    else {

                        patterns.put(pattern.hashCode(), pattern);
                    }
                }
            }
        }
        catch (SolrServerException e) {

            System.out.println("Could not execute query: " + e);
            e.printStackTrace();
        }
        return new HashSet<Pattern>(patterns.values());
    }

    public static void main(String[] args) throws IOException {

        // createPropertyDistribution();

        List<String> uris = Arrays.asList("http://dbpedia.org/property/accessioneudate", "http://dbpedia.org/property/awards", "http://dbpedia.org/property/borderingstates", "http://dbpedia.org/property/classis", "http://dbpedia.org/property/country", "http://dbpedia.org/property/currency", "http://dbpedia.org/property/currencyCode", "http://dbpedia.org/property/densityrank", "http://dbpedia.org/property/design", "http://dbpedia.org/property/designer", "http://dbpedia.org/property/elevationM", "http://dbpedia.org/property/foundation", "http://dbpedia.org/property/ground", "http://dbpedia.org/property/industry", "http://dbpedia.org/property/location", "http://dbpedia.org/property/locationCountry", "http://dbpedia.org/property/mineral", "http://dbpedia.org/property/museum", "http://dbpedia.org/property/numEmployees", "http://dbpedia.org/property/office", "http://dbpedia.org/property/officialLanguages", "http://dbpedia.org/property/populationTotal", "http://dbpedia.org/property/publisher", "http://dbpedia.org/property/rulingParty", "http://dbpedia.org/property/spouse", "http://dbpedia.org/property/starring", "http://dbpedia.org/property/title", "http://dbpedia.org/ontology/album", "http://dbpedia.org/ontology/areaCode", "http://dbpedia.org/ontology/author", "http://dbpedia.org/ontology/battle", "http://dbpedia.org/ontology/birthDate", "http://dbpedia.org/ontology/birthPlace", "http://dbpedia.org/ontology/capital", "http://dbpedia.org/ontology/child", "http://dbpedia.org/ontology/country", "http://dbpedia.org/ontology/creator", "http://dbpedia.org/ontology/crosses", "http://dbpedia.org/ontology/currency", "http://dbpedia.org/ontology/date", "http://dbpedia.org/ontology/deathCause", "http://dbpedia.org/ontology/deathDate", "http://dbpedia.org/ontology/deathPlace", "http://dbpedia.org/ontology/developer", "http://dbpedia.org/ontology/director", "http://dbpedia.org/ontology/elevation", "http://dbpedia.org/ontology/formationYear", "http://dbpedia.org/ontology/foundationPlace", "http://dbpedia.org/ontology/genre", "http://dbpedia.org/ontology/governmentType", "http://dbpedia.org/ontology/ground", "http://dbpedia.org/ontology/height", "http://dbpedia.org/ontology/highestPlace", "http://dbpedia.org/ontology/isPartOf", "http://dbpedia.org/ontology/keyPerson", "http://dbpedia.org/ontology/language", "http://dbpedia.org/ontology/largestCity", "http://dbpedia.org/ontology/leaderName", "http://dbpedia.org/ontology/league", "http://dbpedia.org/ontology/locatedInArea", "http://dbpedia.org/ontology/location", "http://dbpedia.org/ontology/numberOfEmployees", "http://dbpedia.org/ontology/numberOfEntrances", "http://dbpedia.org/ontology/officialLanguage", "http://dbpedia.org/ontology/orderInOffice", "http://dbpedia.org/ontology/owner", "http://dbpedia.org/ontology/producer", "http://dbpedia.org/ontology/programmingLanguage", "http://dbpedia.org/ontology/publisher", "http://dbpedia.org/ontology/seasonNumber", "http://dbpedia.org/ontology/series", "http://dbpedia.org/ontology/sourceCountry", "http://dbpedia.org/ontology/spokenIn", "http://dbpedia.org/ontology/spouse", "http://dbpedia.org/ontology/starring");
        
        for (String uri : uris) {
        	
            List<Pattern> patterns = BoaPatternSelector.getNaturalLanguageRepresentation(uri, 1);

            if (patterns.size() > 0) {

                System.out.print(uri + ": ");
                for (Pattern p : patterns)
                    System.out.println(p.naturalLanguageRepresentation);
            }
            else System.out.println(uri + ": ---------------------------------");
        }
    }

    private static void createPropertyDistribution() throws IOException {

        String filePath = "resources/qald2-dbpedia-train.xml";
        byte[] buffer = new byte[(int) new File(filePath).length()];
        BufferedInputStream f = null;
        try {
            f = new BufferedInputStream(new FileInputStream(filePath));
            f.read(buffer);
        }
        finally {
            if (f != null)
                try {
                    f.close();
                }
                catch (IOException ignored) {
                }
        }
        String queryString = new String(buffer);

        Map<String, Integer> distribution = new HashMap<String, Integer>();
        Matcher matcher = java.util.regex.Pattern.compile("db[op]:\\p{Lower}\\w+\\s").matcher(queryString);
        while (matcher.find()) {

            String property = matcher.group();
            if (distribution.containsKey(property))
                distribution.put(property, distribution.get(property) + 1);
            else
                distribution.put(property, 1);
        }
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {

            result.add(entry.getValue() + ": " + entry.getKey());
        }
        Collections.sort(result);
        for (String s : result)
            System.out.println(s);
    }
}
