package com.open.spring.mvc.assignments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Covers both halves of one contract: how a contentUrl is canonicalized before it is
 * stored, and how the legacy "[CONTENT_URL: ...]" description marker is read back out of
 * rows that predate the content_url column.
 */
class AssignmentContentUrlsTest {

    @Test
    void leadingSlashIsStripped() {
        assertEquals("csa/home-page-game-feedback",
            AssignmentContentUrls.canonicalize("/csa/home-page-game-feedback"));
    }

    @Test
    void trailingSlashIsStripped() {
        assertEquals("java/spring/hacks",
            AssignmentContentUrls.canonicalize("/java/spring/hacks/"));
    }

    @Test
    void repeatedSlashesAreCollapsed() {
        assertEquals("csa/lesson", AssignmentContentUrls.canonicalize("//csa//lesson//"));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertEquals("csa/lesson", AssignmentContentUrls.canonicalize("  /csa/lesson/  "));
    }

    @Test
    void theHtmlExtensionIsPreserved() {
        // Jekyll serves dateless-permalink posts at this exact URL, and the Pages sync
        // script reproduces it; dropping .html would make the stored key differ from the
        // page people actually visit.
        assertEquals("CSH/2026/07/27/csh-team-formation.html",
            AssignmentContentUrls.canonicalize("/CSH/2026/07/27/csh-team-formation.html"));
    }

    @Test
    void caseIsPreserved() {
        assertEquals("CSH/2026/07/27/x.html",
            AssignmentContentUrls.canonicalize("/CSH/2026/07/27/x.html"));
    }

    @Test
    void nothingUsefulCanonicalizesToNull() {
        assertNull(AssignmentContentUrls.canonicalize(null));
        assertNull(AssignmentContentUrls.canonicalize(""));
        assertNull(AssignmentContentUrls.canonicalize("   "));
        assertNull(AssignmentContentUrls.canonicalize("/"));
        assertNull(AssignmentContentUrls.canonicalize("///"));
    }

    /**
     * The reason this class exists: the browser posts Jekyll's page.url and the sync script
     * posts the same URL derived from the source file. Both must reach one assignment row.
     */
    @Test
    void browserAndSyncScriptSpellingsCollapseToOneKey() {
        String fromBrowser = "/csa/home-page-game-feedback";
        String fromSyncScript = "csa/home-page-game-feedback";

        assertEquals(AssignmentContentUrls.canonicalize(fromBrowser),
            AssignmentContentUrls.canonicalize(fromSyncScript));
    }

    @Test
    void theLegacyMarkerYieldsTheUrlItWrapped() {
        assertEquals("csa/lesson",
            AssignmentContentUrlMigration.extractMarkedContentUrl("[CONTENT_URL: csa/lesson]"));
    }

    @Test
    void theLegacyMarkerIsReadEvenWithADescriptionAfterIt() {
        assertEquals("csa/lesson",
            AssignmentContentUrlMigration.extractMarkedContentUrl(
                "[CONTENT_URL: csa/lesson] Play the game and open an issue"));
    }

    @Test
    void anUnmarkedOrTruncatedDescriptionYieldsNothing() {
        assertNull(AssignmentContentUrlMigration.extractMarkedContentUrl(null));
        assertNull(AssignmentContentUrlMigration.extractMarkedContentUrl("Just a description"));
        assertNull(AssignmentContentUrlMigration.extractMarkedContentUrl("[CONTENT_URL: csa/lesson"));
    }
}
