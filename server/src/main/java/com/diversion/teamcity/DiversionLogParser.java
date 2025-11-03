package com.diversion.teamcity;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import jetbrains.buildServer.vcs.ModificationData;
import jetbrains.buildServer.vcs.VcsChange;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Diversion log output into TeamCity ModificationData objects
 */
public class DiversionLogParser {

    private static final Logger LOG = Logger.getInstance(DiversionLogParser.class.getName());

    private final DiversionCommandExecutor executor;
    private final Gson gson;
    private final SimpleDateFormat dateFormat;

    // Pattern: commit dv.commit.7 (dv.branch.1)
    private static final Pattern COMMIT_PATTERN = Pattern.compile("^commit\\s+(dv\\.commit\\.\\d+)\\s+\\(([^)]+)\\)");

    // Pattern: Author: Chris Ashworth <cashworth@gmail.com>
    private static final Pattern AUTHOR_PATTERN = Pattern.compile("^Author:\\s+([^<]+)<([^>]+)>");

    // Pattern: Date:   10-31-2025 09:36:28
    private static final Pattern DATE_PATTERN = Pattern.compile("^Date:\\s+(.+)");

    // Status values from dv ls JSON output
    private static final int STATUS_INTACT = 1;
    private static final int STATUS_ADDED = 2;
    private static final int STATUS_MODIFIED = 3;
    private static final int STATUS_DELETED = 4;

    /**
     * Constructor
     * @param executor Command executor for running dv ls to get file changes
     */
    public DiversionLogParser(@Nullable DiversionCommandExecutor executor) {
        this.executor = executor;
        this.gson = new Gson();
        this.dateFormat = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
    }

    /**
     * Parse log output into list of modifications
     *
     * @param logOutput Output from 'dv log' command
     * @param root VCS root for the modifications
     * @return List of modifications
     * @throws VcsException if parsing fails
     */
    @NotNull
    public List<ModificationData> parse(@NotNull String logOutput, @NotNull VcsRoot root) throws VcsException {
        List<ModificationData> modifications = new ArrayList<>();
        String[] lines = logOutput.split("\n");

        String commitId = null;
        String branchId = null;
        String authorName = null;
        String authorEmail = null;
        Date date = null;
        StringBuilder message = new StringBuilder();
        boolean inMessage = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Check for commit line
            Matcher commitMatcher = COMMIT_PATTERN.matcher(line);
            if (commitMatcher.matches()) {
                // Save previous commit if exists
                if (commitId != null) {
                    modifications.add(createModification(commitId, authorName, authorEmail, date, message.toString().trim(), root));
                }

                // Start new commit
                commitId = commitMatcher.group(1);
                branchId = commitMatcher.group(2);
                authorName = null;
                authorEmail = null;
                date = null;
                message = new StringBuilder();
                inMessage = false;
                continue;
            }

            // Check for author line
            Matcher authorMatcher = AUTHOR_PATTERN.matcher(line);
            if (authorMatcher.matches()) {
                authorName = authorMatcher.group(1).trim();
                authorEmail = authorMatcher.group(2).trim();
                continue;
            }

            // Check for date line
            Matcher dateMatcher = DATE_PATTERN.matcher(line);
            if (dateMatcher.matches()) {
                String dateStr = dateMatcher.group(1).trim();
                try {
                    date = dateFormat.parse(dateStr);
                } catch (ParseException e) {
                    throw new VcsException("Failed to parse date: " + dateStr, e);
                }
                inMessage = true; // Next non-empty lines are the message
                continue;
            }

            // Collect commit message (after date line, usually indented)
            if (inMessage && !line.trim().isEmpty()) {
                if (message.length() > 0) {
                    message.append("\n");
                }
                message.append(line.trim());
            }
        }

        // Don't forget the last commit
        if (commitId != null) {
            modifications.add(createModification(commitId, authorName, authorEmail, date, message.toString().trim(), root));
        }

        return modifications;
    }

    @NotNull
    private ModificationData createModification(
            @NotNull String commitId,
            @NotNull String authorName,
            @NotNull String authorEmail,
            @NotNull Date date,
            @NotNull String message,
            @NotNull VcsRoot root) {

        // TeamCity 2023.11 ModificationData constructor signature:
        // ModificationData(Date vcsDate, List<VcsChange> changes, String description,
        //                  String userName, VcsRoot root, String version, String displayVersion)

        // Try to get actual file changes using dv ls
        List<VcsChange> changes = new ArrayList<>();
        if (executor != null) {
            try {
                // Get file list with status from dv ls <commitId> <tempfile>
                String filesJson = executor.getCommitFiles(commitId);
                JsonObject filesObject = gson.fromJson(filesJson, JsonObject.class);

                // Parse each file entry
                for (Map.Entry<String, JsonElement> entry : filesObject.entrySet()) {
                    String path = entry.getKey();
                    JsonObject fileInfo = entry.getValue().getAsJsonObject();

                    // Get status (1=INTACT, 2=ADDED, 3=MODIFIED, 4=DELETED)
                    int status = fileInfo.has("status") ? fileInfo.get("status").getAsInt() : STATUS_INTACT;

                    // Only include changed files (not INTACT)
                    if (status != STATUS_INTACT) {
                        // Convert Diversion status to TeamCity VcsChange.Type
                        VcsChange.Type changeType;
                        switch (status) {
                            case STATUS_ADDED:
                                changeType = VcsChange.Type.ADDED;
                                break;
                            case STATUS_DELETED:
                                changeType = VcsChange.Type.REMOVED;
                                break;
                            case STATUS_MODIFIED:
                            default:
                                changeType = VcsChange.Type.CHANGED;
                                break;
                        }

                        // Calculate parent commit ID (commitNumber - 1) for before/after revisions
                        int commitNum = extractCommitNumber(commitId);
                        String parentCommitId = commitNum > 1 ? "dv.commit." + (commitNum - 1) : null;

                        // For ADDED files, there is no before revision
                        // For DELETED files, there is no after revision
                        // For MODIFIED files, both revisions exist
                        String beforeRev = (status == STATUS_ADDED) ? null : parentCommitId;
                        String afterRev = (status == STATUS_DELETED) ? null : commitId;

                        changes.add(new VcsChange(
                                changeType,
                                changeType.name().toLowerCase() + ": " + path,  // description
                                path,  // file name
                                path,  // relative file name
                                beforeRev,  // before revision
                                afterRev    // after revision
                        ));
                    }
                }
            } catch (Exception e) {
                // If dv ls fails, fall back to generic message
                LOG.warn("Failed to get file changes for commit " + commitId + ": " + e.getMessage(), e);

                String parentCommitId = null;
                try {
                    int commitNum = extractCommitNumber(commitId);
                    parentCommitId = commitNum > 1 ? "dv.commit." + (commitNum - 1) : null;
                } catch (VcsException ex) {
                    // Ignore, leave parentCommitId as null
                }

                changes.add(new VcsChange(
                        VcsChange.Type.CHANGED,
                        "Changes detected (file list unavailable: " + e.getMessage() + ")",
                        "(changes detected)",
                        "(changes detected)",
                        parentCommitId,
                        commitId
                ));
            }
        }

        // If no executor or no changes found, add generic entry
        if (changes.isEmpty()) {
            String parentCommitId = null;
            try {
                int commitNum = extractCommitNumber(commitId);
                parentCommitId = commitNum > 1 ? "dv.commit." + (commitNum - 1) : null;
            } catch (VcsException ex) {
                // Ignore, leave parentCommitId as null
            }

            changes.add(new VcsChange(
                    VcsChange.Type.CHANGED,
                    "Changes have been detected, specific changes are not available at present",
                    "(changes detected)",
                    "(changes detected)",
                    parentCommitId,
                    commitId
            ));
        }

        return new ModificationData(
                date,                                    // vcsDate
                changes,                                 // changes
                message,                                 // description
                authorName + " <" + authorEmail + ">",  // userName
                root,                                    // VcsRoot
                commitId,                                // version
                commitId                                 // displayVersion
        );
    }

    /**
     * Extract numeric commit number from commit ID (e.g., "dv.commit.7" -> 7)
     */
    public static int extractCommitNumber(@NotNull String commitId) throws VcsException {
        Pattern pattern = Pattern.compile("dv\\.commit\\.(\\d+)");
        Matcher matcher = pattern.matcher(commitId);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        throw new VcsException("Invalid commit ID format: " + commitId);
    }
}
