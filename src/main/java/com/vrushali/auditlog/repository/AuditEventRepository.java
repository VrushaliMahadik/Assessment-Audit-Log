package com.vrushali.auditlog.repository;

import com.vrushali.auditlog.dto.AuditEventFilter;
import com.vrushali.auditlog.model.AuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AuditEventRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<AuditEvent> ROW_MAPPER = (rs, rowNum) -> {
        AuditEvent e = new AuditEvent();
        e.setId(rs.getObject("id", UUID.class));
        e.setEventType(rs.getString("event_type"));
        e.setActorId(rs.getString("actor_id"));
        e.setResourceType(rs.getString("resource_type"));
        e.setResourceId(rs.getString("resource_id"));
        e.setPayload(rs.getString("payload"));
        e.setOriginalPayload(rs.getString("original_payload"));
        Timestamp ts = rs.getTimestamp("timestamp");
        e.setTimestamp(ts != null ? ts.toInstant() : null);
        e.setContentHash(rs.getString("content_hash"));
        e.setPreviousHash(rs.getString("previous_hash"));
        e.setSequenceNumber(rs.getLong("sequence_number"));
        Timestamp cat = rs.getTimestamp("created_at");
        e.setCreatedAt(cat != null ? cat.toInstant() : null);
        // Scenario B fields
        Timestamp aat = rs.getTimestamp("archived_at");
        e.setArchivedAt(aat != null ? aat.toInstant() : null);
        e.setRedacted(rs.getBoolean("is_redacted"));
        Timestamp rat = rs.getTimestamp("redacted_at");
        e.setRedactedAt(rat != null ? rat.toInstant() : null);
        Array rfArr = rs.getArray("redacted_fields");
        e.setRedactedFields(rfArr != null ? (String[]) rfArr.getArray() : null);
        e.setRedactedContentHash(rs.getString("redacted_content_hash"));
        return e;
    };

    public AuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuditEvent save(AuditEvent event) {
        jdbcTemplate.update(
            "INSERT INTO audit_event (id, event_type, actor_id, resource_type, resource_id, " +
            "payload, original_payload, timestamp, content_hash, previous_hash) " +
            "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)",
            event.getId(),
            event.getEventType(),
            event.getActorId(),
            event.getResourceType(),
            event.getResourceId(),
            event.getPayload(),
            event.getOriginalPayload(),
            Timestamp.from(event.getTimestamp()),
            event.getContentHash(),
            event.getPreviousHash()
        );
        return findById(event.getId()).orElseThrow();
    }

    public Optional<AuditEvent> findById(UUID id) {
        List<AuditEvent> results = jdbcTemplate.query(
            "SELECT * FROM audit_event WHERE id = ?",
            ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Returns the contentHash of the last inserted record, for chain linking. */
    public Optional<String> findLatestContentHash() {
        List<String> results = jdbcTemplate.queryForList(
            "SELECT content_hash FROM audit_event ORDER BY sequence_number DESC LIMIT 1",
            String.class);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Returns all records ordered by sequence_number ASC for chain verification. */
    public List<AuditEvent> findAllForVerification() {
        return jdbcTemplate.query(
            "SELECT * FROM audit_event ORDER BY sequence_number ASC",
            ROW_MAPPER);
    }

    public List<AuditEvent> findFiltered(AuditEventFilter filter) {
        WhereClause wc = buildWhere(filter, true); // excludes archived
        String sql = "SELECT * FROM audit_event" + wc.sql() +
                     " ORDER BY sequence_number ASC" +
                     " LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(wc.params());
        params.add(filter.getSize());
        params.add((long) filter.getPage() * filter.getSize());
        return jdbcTemplate.query(sql, ROW_MAPPER, params.toArray());
    }

    public long countFiltered(AuditEventFilter filter) {
        WhereClause wc = buildWhere(filter, true); // excludes archived
        String sql = "SELECT COUNT(*) FROM audit_event" + wc.sql();
        Long count = jdbcTemplate.queryForObject(sql, Long.class, wc.params().toArray());
        return count != null ? count : 0L;
    }

    /** Soft-deletes records older than the cutoff by setting archived_at = NOW(). */
    public int archiveOlderThan(Instant cutoff) {
        return jdbcTemplate.update(
            "UPDATE audit_event SET archived_at = NOW() " +
            "WHERE timestamp < ? AND archived_at IS NULL",
            Timestamp.from(cutoff));
    }

    /**
    * Updates only the caller-visible payload and redaction metadata. The original
    * payload and contentHash remain immutable.
     */
    public void redactPayloadFields(UUID id, String redactedPayload,
                             String redactedContentHash, String[] redactedFields,
                             Instant now) throws Exception {
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                "UPDATE audit_event " +
                "SET payload = ?::jsonb, redacted_content_hash = ?, is_redacted = TRUE, " +
                "redacted_at = ?, redacted_fields = ? " +
                "WHERE id = ?");
            ps.setString(1, redactedPayload);
            ps.setString(2, redactedContentHash);
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setArray(4, connection.createArrayOf("TEXT", redactedFields));
            ps.setObject(5, id);
            return ps;
        });
    }

    /** Returns all records for export, including archived, matching the given filter (no pagination). */
    public List<AuditEvent> findAllForExport(AuditEventFilter filter) {
        WhereClause wc = buildWhere(filter, false); // includes archived
        String sql = "SELECT * FROM audit_event" + wc.sql() +
                     " ORDER BY sequence_number ASC LIMIT ?";
        List<Object> params = new ArrayList<>(wc.params());
        params.add(filter.getSize());
        return jdbcTemplate.query(sql, ROW_MAPPER, params.toArray());
    }

    private WhereClause buildWhere(AuditEventFilter filter, boolean excludeArchived) {
        StringBuilder sb = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (excludeArchived) {
            sb.append(" AND archived_at IS NULL");
        }
        if (filter.getActorId() != null) {
            sb.append(" AND actor_id = ?");
            params.add(filter.getActorId());
        }
        if (filter.getResourceType() != null) {
            sb.append(" AND resource_type = ?");
            params.add(filter.getResourceType());
        }
        if (filter.getResourceId() != null) {
            sb.append(" AND resource_id = ?");
            params.add(filter.getResourceId());
        }
        if (filter.getEventType() != null) {
            sb.append(" AND event_type = ?");
            params.add(filter.getEventType());
        }
        if (filter.getFrom() != null) {
            sb.append(" AND timestamp >= ?");
            params.add(Timestamp.from(filter.getFrom()));
        }
        if (filter.getTo() != null) {
            sb.append(" AND timestamp <= ?");
            params.add(Timestamp.from(filter.getTo()));
        }

        String where = sb.isEmpty() ? "" : " WHERE 1=1" + sb;
        return new WhereClause(where, params);
    }

    private record WhereClause(String sql, List<Object> params) {}
}
