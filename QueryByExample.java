import java.io.*;
import java.util.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;
import org.json.simple.JSONArray;


public class Solution {
    // reader, writer, and jsonParser 
    // public Scanner in;
    public BufferedReader in;

    public PrintWriter out;
    public JSONParser parser;

    // use Trie as data structure to store the documents for faster indexing
    private Trie trie; // Trie structure to store the json hierarchy structure
    private Map<JSONObject, String> jsonStringMap; // map json doc to string for EXACT output
    private Map<JSONObject, Integer> jsonOrderMap; // map json to order int for output sequence
    private int entryCount; // counter to assign order numbers to each newly added document

    public Solution() {
        // initialization utils
        // in = new Scanner(new BufferedReader(new InputStreamReader(System.in)));
        in = new BufferedReader(new InputStreamReader(System.in));

        out = new PrintWriter(System.out);
        parser = new JSONParser();

        // initialization of data structures
        trie = new Trie();
        jsonStringMap = new HashMap<>();
        jsonOrderMap = new HashMap<>();
        entryCount = 0;
    }


    /**
    * cmd execution
    * 
    * @param cmd    cmd string, could be ["add", "get", "delete"]
    * @param arg    arg string, the string representation of the document
    * @param doc    JSONObject representation of the document
    */
    public void execute(String cmd, String arg, JSONObject doc) {
        switch (cmd) {
            case "add":
                add(arg, doc);
                break;
            
            case "get":
                get(doc);
                break;
            
            case "delete":
                delete(doc);
                break;
        }
    }

    // add json doc to the trie structure, add record to maps
    private void add(String arg, JSONObject doc) {
        entryCount++;
        jsonStringMap.put(doc, arg);
        jsonOrderMap.put(doc, entryCount);
        trie.addEntry(doc);
    }

    // print string represent of all matching Json doc to stdout
    private void get(JSONObject doc) {
        Set<JSONObject> getResSet;

        // if doc is an empty json object, return every thing
        if (doc.keySet().isEmpty()) {
            getResSet = new HashSet<>(jsonStringMap.keySet());
        } else {
            getResSet = trie.query(doc);
        }

        // output if the query result is not empty
        if (!getResSet.isEmpty()) {
            List<JSONObject> resList = new ArrayList<>(getResSet);

            // sort the list according to the creation order of the doc
            Collections.sort(resList, (a, b) -> (jsonOrderMap.get(a) - jsonOrderMap.get(b)));

            for (JSONObject entry : resList) {
                // print the string representation, instead of the json object itself
                out.println(jsonStringMap.get(entry));
            }
        }
    }

    // delete all matching document
    private void delete(JSONObject doc) {
        // query to get a set of all matching documents
        Set<JSONObject> toBeRemovedSet = new HashSet<>(trie.query(doc));

        // delete matching documents one by one
        for (JSONObject entry : toBeRemovedSet) {
            // remove from trie
            trie.removeEntry(entry);
            
            // remove from maps
            jsonOrderMap.remove(entry);
            jsonStringMap.remove(entry);
        }
    }

    private class Trie {
        private TrieNode trieRoot;
        public Trie() {
            trieRoot = new TrieNode();
        }

        // add the json representation of the document to the trie structure
        public void addEntry(JSONObject doc) {
            // add the json structrue recursively
            addEntry(trieRoot, doc, doc);
        }

        // add curDoc(JSONObject) to current root(TrieNode)
        // at the bottom level, put originalDoc (the whole json structure) to the set for later retrieval
        private void addEntry(TrieNode root, JSONObject curDoc, JSONObject originalDoc) {
            for (Object key : curDoc.keySet()) {
                root.next.putIfAbsent(key, new TrieNode());
                Object value = curDoc.get(key);
                TrieNode childNode = root.next.get(key);
                // if current entry is a JSONObject itself, add recursively
                if (value instanceof JSONObject) {
                    addEntry(childNode, (JSONObject) value, originalDoc);
                } else {
                    // at bottom level of json structure
                    // if (childNode.entries == null) {childNode.entries = new HashMap<>();}
                    childNode.entries.putIfAbsent(value, new HashSet<>());
                    childNode.entries.get(value).add(originalDoc);
                }
            }
        }

        // find and return set of all matching json object
        public Set<JSONObject> query(JSONObject queryDoc) {
            // query the json structure against trie recursively
            Set<JSONObject> queryRes = query(trieRoot, queryDoc);
            if (queryRes == null) {return new HashSet<>();}
            return queryRes;
        }

        // query the TrieNode recursively with doc (JSONObject)
        private Set<JSONObject> query(TrieNode root, JSONObject doc) {
            Set<JSONObject> getRes = new HashSet<>();
            // flag to indicate whether it is the first "sub-query"
            boolean firstQuery = true;
            for (Object key : doc.keySet()) {
                Set<JSONObject> curQueryRes = query(root, key, doc.get(key));

                // if curQueryRes is null, return empty result immediately
                if (curQueryRes == null) {return new HashSet<JSONObject>();}

                // if it is the first "sub-query", 
                // set getRes to be the result of the "sub-query", and unset the flag
                if (firstQuery) {
                    getRes = curQueryRes;
                    firstQuery = false;
                } else {
                    // if not the first "sub-query", get the intersection of the result
                    getRes.retainAll(curQueryRes);
                }

                // if the intersection (current result) is already empty, return empty result early
                if (getRes.isEmpty()) {return getRes;}
            }
            return getRes;
        }

        // check the cur TrieNode for specific key and return matching (to value) json object
        private Set<JSONObject> query(TrieNode root, Object key, Object value) {

            // if current node has no this key, return empty result
            if (!root.next.containsKey(key)) {return new HashSet<JSONObject>();}
            TrieNode childNode = root.next.get(key);

            // if value is a json object itself, query recursively
            if (value instanceof JSONObject) {
                return query(childNode, (JSONObject) value);
            } else {

                // if value is not an instance of JSONArray, directly get value's corresponding set
                if (!(value instanceof JSONArray)) {
                    if (childNode.entries.containsKey(value)) {
                        return new HashSet<>(childNode.entries.get(value));
                    } else {
                        return new HashSet<JSONObject>();
                    }
                } 

                // if value is an instance of JSONArray, check if "contains all"
                else {
                    Set<JSONObject> curRes = new HashSet<>();
                    // check each of the JSONArray in the keyset of the node
                    for (Object array : childNode.entries.keySet()) {
                        if (((JSONArray) array).containsAll((JSONArray) value)) {
                            curRes.addAll(childNode.entries.get(array));
                        }
                    }
                    return curRes;
                }
            }
        }

        // remove cur json object from trieRoot
        public void removeEntry(JSONObject doc) {
            // remove recursively if json object is nested
            removeEntry(trieRoot, doc, doc);
        }

        // very similar to addEntry process, but instead of adding, do removing here
        private void removeEntry(TrieNode root, JSONObject curDoc, JSONObject originalDoc) {
            for (Object key : curDoc.keySet()) {
                Object value = curDoc.get(key);
                TrieNode childNode = root.next.get(key);
                // if value itself is a json object, remove recursively
                if (value instanceof JSONObject) {
                    removeEntry(childNode, (JSONObject) value, originalDoc);
                } else {
                    childNode.entries.get(value).remove(originalDoc);

                    // if after removal of the current element, the set is already empty,
                    // remove the key to save time for future quries
                    if (childNode.entries.get(value).isEmpty()) {
                        childNode.entries.remove(value);
                    }
                }
            }
        }


        private class TrieNode {
            // next is a Map for the (next level) nested json structure
            // keys     <- name of the subfield (keys in json doc)
            // values   <- TrieNode for subfields
            public Map<Object, TrieNode> next;

            // entries is a map from json values to json object
            // keys     <- values of the subfield(values in json doc for a specific key)
            // values   <- set of matching json objects 
            public Map<Object, Set<JSONObject>> entries;

            public TrieNode() {
                next = new HashMap<>();
                entries = new HashMap<>();
            }  
        }
    }

    public static void main(String args[] ) throws Exception {
        // create a new Solution instance
        Solution s = new Solution();
        int count = 0;
        // read in data
        // while (s.in.hasNext()) {
        //     if (count++ % 1000 == 0) System.err.println(count);
        //     String cmd = s.in.next(); // read in cmd string
        //     String arg = s.in.nextLine().trim(); // read in Json string
            
        //     // try parse the arg string as Json doc, throw eceptions if met
        //     JSONObject doc;
        //     try {
        //         doc = (JSONObject) s.parser.parse(arg);
        //         // if parsed successfully, move on to command execution
        //         s.execute(cmd, arg, doc);
        //     } catch (ParseException e) {
        //         e.printStackTrace();
        //     }
        // }
        
        String line;
        while ((line = s.in.readLine()) != null) {
            if (count++ % 1000 == 0) System.err.println(count);
            int gap = line.indexOf(' ');
            String cmd = line.substring(0, gap); // read in cmd string
            String arg = line.substring(gap + 1); // read in Json string
            
            // try parse the arg string as Json doc, throw eceptions if met
            JSONObject doc;
            try {
                doc = (JSONObject) s.parser.parse(arg);
                // if parsed successfully, move on to command execution
                s.execute(cmd, arg, doc);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        // upon finishing, flush and close stdout
        s.out.flush();
        s.out.close();
    }
}