package com.diversion.teamcity;

import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * File content provider for Diversion VCS
 *
 * Retrieves file contents by checking out the specific revision in the workspace
 * and reading the file from the filesystem.
 */
public class DiversionFileContentProvider implements VcsFileContentProvider {

    private final DiversionVcsSupport myVcs;

    public DiversionFileContentProvider(@NotNull DiversionVcsSupport vcs) {
        this.myVcs = vcs;
    }

    @NotNull
    @Override
    public byte[] getContent(@NotNull String filePath, @NotNull VcsRoot root, @NotNull String version) throws VcsException {
        DiversionCommandExecutor executor = myVcs.createExecutorForRoot(root);

        // Checkout the specific version
        executor.checkout(version, true);

        // Read the file from the workspace
        String workingDir = root.getProperty(DiversionSettings.WORKING_DIRECTORY);
        if (workingDir == null || workingDir.trim().isEmpty()) {
            throw new VcsException("Working directory is not configured for VCS root");
        }

        File fileToRead = new File(workingDir, filePath);
        if (!fileToRead.exists()) {
            throw new VcsFileNotFoundException("File " + filePath + " not found in version " + version);
        }

        try {
            return Files.readAllBytes(fileToRead.toPath());
        } catch (IOException e) {
            throw new VcsException("Failed to read file " + filePath + " at version " + version + ": " + e.getMessage(), e);
        }
    }

    @NotNull
    @Override
    public byte[] getContent(@NotNull VcsModification vcsModification,
                              @NotNull VcsChangeInfo change,
                              @NotNull VcsChangeInfo.ContentType contentType,
                              @NotNull VcsRoot vcsRoot) throws VcsException {
        String version = contentType == VcsChangeInfo.ContentType.BEFORE_CHANGE
                         ? change.getBeforeChangeRevisionNumber()
                         : change.getAfterChangeRevisionNumber();

        if (version == null) {
            // For added files, before content is empty
            // For deleted files, after content is empty
            return new byte[0];
        }

        return getContent(change.getRelativeFileName(), vcsRoot, version);
    }
}
