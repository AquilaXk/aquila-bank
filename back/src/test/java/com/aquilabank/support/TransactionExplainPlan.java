package com.aquilabank.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
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
    if (indexNames.contains(indexName)) {
      return true;
    }
    PartitionedIndexPattern pattern = PARTITIONED_INDEX_PATTERNS.get(indexName);
    return pattern != null && indexNames.stream().anyMatch(pattern::matches);
  }

  private static void collect(JsonNode node, Set<String> nodeTypes, Set<String> indexNames) {
    String nodeType = node.path("Node Type").asText(null);
    if (nodeType != null && shouldCollectNodeType(node, nodeType)) {
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

  private static final Map<String, PartitionedIndexPattern> PARTITIONED_INDEX_PATTERNS =
      Map.of(
          "idx_transaction_read_model_account_cursor",
              new PartitionedIndexPattern(
                  Set.of("account_id", "booked_at", "id"),
                  Set.of("transaction_statu", "transaction_ref")),
          "idx_transaction_read_model_account_status_cursor",
              new PartitionedIndexPattern(Set.of("account_id", "transaction_statu"), Set.of()),
          "idx_transaction_read_model_account_reference_cursor",
              new PartitionedIndexPattern(Set.of("account_id", "transaction_ref"), Set.of()),
          "idx_transaction_read_model_archive_account_cursor",
              new PartitionedIndexPattern(
                  Set.of("account_id", "booked_at", "id"),
                  Set.of("transaction_statu", "transaction_ref")),
          "idx_transaction_read_model_archive_account_status_cursor",
              new PartitionedIndexPattern(Set.of("account_id", "transaction_statu"), Set.of()),
          "idx_transaction_read_model_archive_account_reference_cursor",
              new PartitionedIndexPattern(Set.of("account_id", "transaction_ref"), Set.of()));

  private record PartitionedIndexPattern(Set<String> requiredTokens, Set<String> forbiddenTokens) {

    private boolean matches(String indexName) {
      // PostgreSQL은 partitioned index를 leaf partition별 자동 이름으로 EXPLAIN에 노출합니다.
      return requiredTokens.stream().allMatch(indexName::contains)
          && forbiddenTokens.stream().noneMatch(indexName::contains);
    }
  }

  private static boolean shouldCollectNodeType(JsonNode node, String nodeType) {
    if ("Seq Scan".equals(nodeType) && isEmptyDefaultPartitionScan(node)) {
      return false;
    }
    if ("Sort".equals(nodeType) && isBoundedDefaultPartitionSafetySort(node)) {
      return false;
    }
    return true;
  }

  private static boolean isEmptyDefaultPartitionScan(JsonNode node) {
    return node.path("Relation Name").asText("").endsWith("_default")
        && node.path("Actual Rows").asDouble(Double.MAX_VALUE) == 0.0d
        && node.path("Total Cost").asDouble(Double.MAX_VALUE) == 0.0d;
  }

  private static boolean isBoundedDefaultPartitionSafetySort(JsonNode node) {
    return node.path("Actual Rows").asDouble(Double.MAX_VALUE) <= 128.0d
        && containsEmptyDefaultPartitionScan(node)
        && containsIndexAccess(node);
  }

  private static boolean containsEmptyDefaultPartitionScan(JsonNode node) {
    String nodeType = node.path("Node Type").asText(null);
    if ("Seq Scan".equals(nodeType) && isEmptyDefaultPartitionScan(node)) {
      return true;
    }
    for (JsonNode child : node.path("Plans")) {
      if (containsEmptyDefaultPartitionScan(child)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsIndexAccess(JsonNode node) {
    String indexName = node.path("Index Name").asText(null);
    if (indexName != null) {
      return true;
    }
    for (JsonNode child : node.path("Plans")) {
      if (containsIndexAccess(child)) {
        return true;
      }
    }
    return false;
  }
}
