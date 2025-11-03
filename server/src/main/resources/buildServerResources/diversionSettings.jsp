<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<style type="text/css">
    .diversionSettings {
        margin-bottom: 1em;
    }
    .diversionSettings label {
        font-weight: bold;
        display: block;
        margin-bottom: 0.3em;
    }
    .diversionSettings .description {
        color: #888;
        font-size: 0.9em;
        margin-top: 0.2em;
    }
</style>

<div class="diversionSettings">
    <table class="runnerFormTable">
        <tr>
            <th><label for="repositoryId">Repository ID:<span class="mandatoryAsterix" title="Mandatory field">*</span></label></th>
            <td>
                <props:textProperty name="repositoryId" className="longField" maxlength="256"/>
                <span class="smallNote">
                    The Diversion repository ID (e.g., dv.repo.f7445f1c-4508-4bdb-984e-fd430be37258)
                    <br/>You can find this by running <code>dv repo</code> command
                </span>
                <span class="error" id="error_repositoryId"></span>
            </td>
        </tr>

        <tr>
            <th><label for="branchName">Branch Name:<span class="mandatoryAsterix" title="Mandatory field">*</span></label></th>
            <td>
                <props:textProperty name="branchName" className="longField" maxlength="256"/>
                <span class="smallNote">
                    The branch to monitor for changes (default: main)
                </span>
                <span class="error" id="error_branchName"></span>
            </td>
        </tr>

        <tr>
            <th><label for="dvExecutablePath">Diversion Executable:</label></th>
            <td>
                <props:textProperty name="dvExecutablePath" className="longField" maxlength="512"/>
                <span class="smallNote">
                    Path to the Diversion executable (default: dv)
                    <br/>Leave as 'dv' if it's in your system PATH
                </span>
                <span class="error" id="error_dvExecutablePath"></span>
            </td>
        </tr>

        <tr>
            <th><label for="workingDirectory">Working Directory:</label></th>
            <td>
                <props:textProperty name="workingDirectory" className="longField" maxlength="512"/>
                <span class="smallNote">
                    Working directory for Diversion operations
                    <br/><strong>Required for server-side checkout</strong>. Leave empty for agent-side checkout.
                </span>
                <span class="error" id="error_workingDirectory"></span>
            </td>
        </tr>

    </table>
</div>

<div class="diversionSettings">
    <h3>Important Notes:</h3>
    <ul>
        <li>Both <strong>server-side</strong> and <strong>agent-side</strong> checkout modes are supported</li>
        <li><strong>Server-side checkout</strong>: Requires <strong>Working Directory</strong> to be configured. Agents don't need Diversion CLI.</li>
        <li><strong>Agent-side checkout</strong>: Requires Diversion CLI (<code>dv</code>) installed on all build agents</li>
        <li>The Diversion CLI (<code>dv</code>) must be installed on the TeamCity server (for change detection)</li>
        <li><strong style="color: #d00;">REQUIRED:</strong> Run <code>dv login</code> on the TeamCity server before configuring this VCS root</li>
        <li><strong>For agent-side checkout:</strong> Run <code>dv login</code> on all build agents</li>
        <li>Personal builds are not supported</li>
    </ul>
</div>
