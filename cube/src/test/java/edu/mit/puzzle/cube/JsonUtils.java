package edu.mit.puzzle.cube;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class JsonUtils {

    public static List<JsonNode> getElementsForPredicate(JsonNode node, Predicate<JsonNode> predicate) {
        return ImmutableList.copyOf(node.elements()).stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public static JsonNode getOnlyElementForPredicate(JsonNode node, Predicate<JsonNode> predicate) {
        List<JsonNode> nodeList = ImmutableList.copyOf(node.elements()).stream()
                .filter(predicate)
                .collect(Collectors.toList());
        assertEquals(1, nodeList.size());
        return nodeList.get(0);
    }

}
