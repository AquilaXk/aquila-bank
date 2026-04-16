package com.aquilabank.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** PostgreSQL EXPLAIN JSON에서 baseline 비교용 핵심 정보만 추립니다. */
public record TransactionExplainPlan(
    String rootNodeType,
    double actualRows,
    long sharedHitBlocks,
    long sharedReadBlocks,
    Set<String> nodeTypes,
    Set<String> indexNames) {

  public static TransactionExplainPlan fromJson(ObjectMapper objectMapper, String planJson) {
    try {
      JsonNode rootArray = objectMapper.readTree(planJson);
      JsonNode rootPlan = rootArray.path(0).path("Plan");
      if (rootPlan.isMissingNode()) {
        throw new IllegalArgumentException("EXPLAIN JSON root plan is missing");
      }

      LinkedHashSet<String> nodeTypes = new LinkedHashSet<>();
      LinkedHashSet<String> indexNames = new LinkedHashSet<>();
      collect(rootPlan, nodeTypes, indexNames);

      return new TransactionExplainPlan(
          rootPlan.path("Node Type").asText(),
          rootPlan.path("Actual Rows").asDouble(),
          rootPlan.path("Shared Hit Blocks").asLong(),
          rootPlan.path("Shared Read Blocks").asLong(),
          nodeTypes,
          indexNames);
    } catch (IOException ex) {
      throw new IllegalArgumentException("EXPLAIN JSON parse failed", ex);
    }
  }

  public boolean hasNodeType(String nodeType) {
    return nodeTypes.contains(nodeType);
  }

  public boolean usesIndex(String indexName) {
    return indexNames.contains(indexName);
  }

  private static void collect(JsonNode node, Set<String> nodeTypes, Set<String> indexNames) {
    String nodeType = node.path("Node Type").asText(null);
    if (nodeType != null) {
      nodeTypes.add(nodeType);
    }

    String indexName = node.path("Index Name").asText(null);
    if (indexName != null) {
      indexNames.add(indexName);
    }

    for (JsonNode child : node.path("Plans")) {
      collect(child, nodeTypes, indexNames);
    }
  }
}
