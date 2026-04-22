package com.aquilabank.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** PostgreSQL EXPLAIN JSON에서 notification baseline 비교용 핵심 정보만 추립니다. */
public record NotificationExplainPlan(
    String rootNodeType,
    Set<String> nodeTypes,
    Set<String> indexNames,
    Set<String> seqScanRelations) {

  public static NotificationExplainPlan fromJson(ObjectMapper objectMapper, String planJson) {
    try {
      JsonNode rootArray = objectMapper.readTree(planJson);
      JsonNode rootPlan = rootArray.path(0).path("Plan");
      if (rootPlan.isMissingNode()) {
        throw new IllegalArgumentException("EXPLAIN JSON root plan is missing");
      }

      LinkedHashSet<String> nodeTypes = new LinkedHashSet<>();
      LinkedHashSet<String> indexNames = new LinkedHashSet<>();
      LinkedHashSet<String> seqScanRelations = new LinkedHashSet<>();
      collect(rootPlan, nodeTypes, indexNames, seqScanRelations);
      return new NotificationExplainPlan(
          rootPlan.path("Node Type").asText(), nodeTypes, indexNames, seqScanRelations);
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

  private static void collect(
      JsonNode node, Set<String> nodeTypes, Set<String> indexNames, Set<String> seqScanRelations) {
    String nodeType = node.path("Node Type").asText(null);
    if (nodeType != null) {
      nodeTypes.add(nodeType);
    }
    if ("Seq Scan".equals(nodeType)) {
      String relationName = node.path("Relation Name").asText(null);
      if (relationName != null) {
        seqScanRelations.add(relationName);
      }
    }

    String indexName = node.path("Index Name").asText(null);
    if (indexName != null) {
      indexNames.add(indexName);
    }

    for (JsonNode child : node.path("Plans")) {
      collect(child, nodeTypes, indexNames, seqScanRelations);
    }
  }
}
