package com.open.spring.mvc.assignments;

/**
 * One definition of what a lesson page's contentUrl looks like once stored.
 *
 * Two callers reach /api/assignments/auto-create for the same page: the browser sends
 * Jekyll's {@code page.url} (always leading-slashed, e.g. "/csa/home-page-game-feedback")
 * and the Pages sync script derives the same URL from the source file. Without a shared
 * rule those two spellings produce two separate assignment rows, and submissions end up
 * on one while frontmatter-driven ownership lands on the other.
 *
 * The Python side (scripts/create_assignments_from_frontmatter.py, canonicalize_content_url)
 * applies these exact three rules, so keep the two implementations in step.
 */
public final class AssignmentContentUrls {

    private AssignmentContentUrls() {
    }

    /**
     * Collapses a raw contentUrl to its stored form: trimmed, no repeated slashes, and
     * no leading or trailing slash.
     *
     * The file extension is deliberately preserved. Jekyll serves posts without an
     * explicit permalink at "/CSH/2026/07/27/csh-team-formation.html", and the sync
     * script reproduces that URL verbatim, so stripping ".html" here would only make
     * the stored key differ from the page people actually visit.
     *
     * @return the canonical form, or null when there is nothing left to store
     */
    public static String canonicalize(String rawContentUrl) {
        if (rawContentUrl == null) {
            return null;
        }

        String trimmed = rawContentUrl.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String collapsed = trimmed.replaceAll("/{2,}", "/");

        int start = 0;
        int end = collapsed.length();
        while (start < end && collapsed.charAt(start) == '/') {
            start++;
        }
        while (end > start && collapsed.charAt(end - 1) == '/') {
            end--;
        }

        String canonical = collapsed.substring(start, end);
        return canonical.isEmpty() ? null : canonical;
    }
}
