package com.lightbot.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Knowledge;
import com.lightbot.enums.KnowledgeType;
import com.lightbot.service.DifyDatasetClient;
import com.lightbot.service.EmbeddingService;
import com.lightbot.service.KnowledgeRetrievalService;
import com.lightbot.util.DifySecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统一知识库检索服务实现。 */
@Service
@RequiredArgsConstructor
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private final EmbeddingService embeddingService;
    private final EmbeddingModel embeddingModel;
    private final DifyDatasetClient difyDatasetClient;
    private final DifySecretCipher difySecretCipher;
    private final ObjectMapper objectMapper;

    @Override
    public List<Map<String, Object>> retrieve(Knowledge knowledge, String query, int topK,
                                               double threshold, Map<String, Object> queryParams) {
        if (knowledge.getType() == KnowledgeType.DIFY) {
            return retrieveDify(knowledge, query, topK, threshold, queryParams);
        }
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(query), null));
        return embeddingService.searchSimilarSql(knowledge.getId(), response.getResult().getOutput(),
                topK, threshold, queryParams);
    }

    private List<Map<String, Object>> retrieveDify(Knowledge knowledge, String query, int topK,
                                                    double threshold, Map<String, Object> queryParams) {
        Map<String, Object> config = parseConfig(knowledge.getConfig());
        String apiUrl = stringValue(config.get("apiUrl"));
        String datasetId = stringValue(config.get("datasetId"));
        String token = difySecretCipher.decrypt(stringValue(config.get("tokenCiphertext")));
        String searchMode = queryParams != null ? stringValue(queryParams.get("search_mode")) : "vector";
        List<Map<String, Object>> records = difyDatasetClient.retrieve(apiUrl, datasetId, token, query,
                Math.min(Math.max(topK, 1), 20), threshold, searchMode);
        List<Map<String, Object>> results = new ArrayList<>(records.size());
        for (Map<String, Object> record : records) {
            Map<String, Object> segment = valueAsMap(record.get("segment"));
            if (segment.isEmpty() || !segment.containsKey("content")) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("content", stringValue(segment.get("content")));
            row.put("score", numberValue(record.get("score")));
            row.put("document_name", stringValue(segment.get("document_name")));
            row.put("document_id", null);
            row.put("external_document_id", segment.get("document_id"));
            row.put("external_chunk_id", segment.get("id"));
            row.put("chunk_index", segment.get("position"));
            results.add(row);
        }
        return results;
    }

    private Map<String, Object> parseConfig(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> valueAsMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Double numberValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
