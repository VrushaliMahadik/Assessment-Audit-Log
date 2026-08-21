package com.vrushali.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vrushali.auditlog.model.AuditEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * Computes SHA-256 content hashes over a deterministic canonical representation.
 *
 * Resolved decisions (docs/DATABASE-DESIGN.md §7-8):
 *   OD-02: SHA-256
 *   OD-03: Sorted-key JSON over {actorId, eventType, payload, resourceId, resourceType, timestamp}
 *   OD-04: Genesis value = 64 zero characters
 *   Timestamp canonical form: ISO-8601 UTC microseconds (yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z')
 */
@Service
public class HashService {

    /** previousHash of the first record — 64 hex zeros (no predecessor). */
    public static final String GENESIS =
        "0000000000000000000000000000000000000000000000000000000000000000";

    private static final DateTimeFormatter TIMESTAMP_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");

    // Dedicated mapper — ORDER_MAP_ENTRIES_BY_KEYS ensures TreeMap order is preserved
    private static final ObjectMapper CANONICAL_MAPPER =
        new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final ObjectMapper objectMapper;

    public HashService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Computes the SHA-256 hash of the event's auditable fields in canonical form.
     * The same field values always produce the same hash (deterministic).
     */
    public String computeContentHash(AuditEvent event) {
        try {
            String canonical = buildCanonical(event);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute content hash", e);
        }
    }

    /**
     * Builds the deterministic canonical JSON string for hashing.
     * Keys are alphabetically sorted; payload is compact normalized JSON or "" if null.
     */
    String buildCanonical(AuditEvent event) throws Exception {
        TreeMap<String, String> fields = new TreeMap<>();
        fields.put("actorId",       event.getActorId());
        fields.put("eventType",     event.getEventType());
        fields.put("payload",       normalizePayload(event.getPayload()));
        fields.put("resourceId",    event.getResourceId());
        fields.put("resourceType",  event.getResourceType());
        fields.put("timestamp",     event.getTimestamp().atZone(ZoneOffset.UTC).format(TIMESTAMP_FMT));
        return CANONICAL_MAPPER.writeValueAsString(fields);
    }

    /** Re-serializes a JSON string with sorted keys for canonical representation. */
    public String normalizePayload(String payload) {
        if (payload == null) return "";
        try {
            Object parsed = objectMapper.readValue(payload, Object.class);
            return CANONICAL_MAPPER.writeValueAsString(parsed);
        } catch (Exception e) {
            return payload; // return as-is if not valid JSON
        }
    }

    /** Serializes an arbitrary object (from request body) to compact canonical JSON. */
    public String serializePayload(Object payload) {
        if (payload == null) return null;
        try {
            return CANONICAL_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }
}
