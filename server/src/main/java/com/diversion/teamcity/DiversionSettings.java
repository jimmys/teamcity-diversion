package com.diversion.teamcity;

/**
 * Constants for Diversion VCS root settings
 */
public class DiversionSettings {

    /**
     * Repository ID (e.g., dv.repo.f7445f1c-4508-4bdb-984e-fd430be37258)
     */
    public static final String REPOSITORY_ID = "repositoryId";

    /**
     * Branch name to monitor (default: main)
     */
    public static final String BRANCH_NAME = "branchName";

    /**
     * Path to Diversion executable (default: dv)
     */
    public static final String DV_EXECUTABLE_PATH = "dvExecutablePath";

    /**
     * Working directory for Diversion operations (optional)
     */
    public static final String WORKING_DIRECTORY = "workingDirectory";

    /**
     * Default branch name
     */
    public static final String DEFAULT_BRANCH_NAME = "main";

    /**
     * Default executable path
     */
    public static final String DEFAULT_DV_EXECUTABLE = "dv";

    /**
     * Maximum number of retries for checkout operations
     */
    public static final int MAX_CHECKOUT_RETRIES = 10;

    /**
     * Delay between checkout retries in milliseconds
     */
    public static final long CHECKOUT_RETRY_DELAY_MS = 1000L;

    /**
     * Extra commits to fetch when retrieving log (buffer to ensure we get all needed commits)
     */
    public static final int LOG_FETCH_BUFFER = 10;
}
