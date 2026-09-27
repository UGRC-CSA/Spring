package com.open.spring.mvc.assignments;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Gives the assignment table a real, indexed content_url column and fills it in from the
 * legacy description marker.
 *
 * Auto-created assignments used to be identified by writing "[CONTENT_URL: &lt;url&gt;]" into the
 * description and dedup-matching on that prefix, which meant every auto-create request read
 * the whole assignment table. The column replaces that with a single indexed lookup.
 *
 * Hibernate runs with ddl-auto=none, so entity annotations never reach a live database.
 * The established pattern for schema changes here is hand-written idempotent SQL with
 * swallowed exceptions (see ModelInit.run() and
 * AssignmentsApiController.normalizeAssignmentSequenceForSqlite()), which is what this does.
 * Identifiers are quoted to match hibernate.globally_quoted_identifiers=true.
 *
 * Every step is safe to repeat: it runs on each boot and is a no-op once applied.
 */
@Component
@RequiredArgsConstructor
public class AssignmentContentUrlMigration {

    /** Prefix the old dedup scheme wrote into description. Read here, never written again. */
    static final String LEGACY_MARKER_PREFIX = "[CONTENT_URL: ";

    private static final Logger logger = LoggerFactory.getLogger(AssignmentContentUrlMigration.class);

    private final JdbcTemplate jdbcTemplate;

    @Bean
    public ApplicationRunner migrateAssignmentContentUrls() {
        return args -> {
            addContentUrlColumn();
            createContentUrlIndex();
            backfillContentUrlsFromDescription();
        };
    }

    private void addContentUrlColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE \"assignment\" ADD COLUMN \"content_url\" varchar(255)");
            logger.info("Added 'content_url' column to 'assignment' table");
        } catch (DataAccessException ignored) {
            // Column already exists on every boot after the first.
        }
    }

    private void createContentUrlIndex() {
        // Kept separate from the ALTER so that a databases rejecting one statement still
        // gets the other. MySQL has no IF NOT EXISTS for CREATE INDEX, so it lands here
        // as a swallowed duplicate-key error rather than a startup failure.
        try {
            jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS \"idx_assignment_content_url\" "
                    + "ON \"assignment\" (\"content_url\")");
        } catch (DataAccessException ignored) {
            // Index already exists, or the dialect refuses IF NOT EXISTS.
        }
    }

    /**
     * Copies the URL out of the legacy description marker into the new column.
     *
     * Descriptions are intentionally left untouched: the marker is redundant once the
     * column is populated, but rewriting live rows buys nothing and cannot be undone.
     */
    private void backfillContentUrlsFromDescription() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                "SELECT \"id\", \"description\" FROM \"assignment\" "
                    + "WHERE \"content_url\" IS NULL AND \"description\" LIKE '[CONTENT_URL: %'");
        } catch (DataAccessException e) {
            logger.warn("Skipped assignment content_url backfill: {}", e.getMessage());
            return;
        }

        int updated = 0;
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            Object description = row.get("description");
            if (id == null || description == null) {
                continue;
            }

            String contentUrl = AssignmentContentUrls.canonicalize(
                extractMarkedContentUrl(description.toString()));
            if (contentUrl == null) {
                continue;
            }

            try {
                updated += jdbcTemplate.update(
                    "UPDATE \"assignment\" SET \"content_url\" = ? WHERE \"id\" = ?",
                    contentUrl, id);
            } catch (DataAccessException e) {
                logger.warn("Could not backfill content_url for assignment {}: {}", id, e.getMessage());
            }
        }

        if (updated > 0) {
            logger.info("Backfilled content_url for {} assignment(s) from the legacy description marker", updated);
        }
    }

    /**
     * @return the URL inside "[CONTENT_URL: &lt;url&gt;]", or null when the description is not
     *         actually marked or the marker was truncated.
     */
    static String extractMarkedContentUrl(String description) {
        if (description == null || !description.startsWith(LEGACY_MARKER_PREFIX)) {
            return null;
        }
        int close = description.indexOf(']', LEGACY_MARKER_PREFIX.length());
        if (close < 0) {
            return null;
        }
        return description.substring(LEGACY_MARKER_PREFIX.length(), close);
    }
}
