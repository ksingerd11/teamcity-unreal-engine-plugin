<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri ="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>
<jsp:useBean id="buildGraphComponent" class="com.jetbrains.teamcity.plugins.unrealengine.server.runner.ui.BuildGraphComponent"/>

<div class="parameter">
    ${buildGraphComponent.script.displayName}: <props:displayValue name="${buildGraphComponent.script.name}"/>
</div>

<div class="parameter">
    ${buildGraphComponent.target.displayName}: <props:displayValue name="${buildGraphComponent.target.name}"/>
</div>

<c:if test="${not empty propertiesBean.properties[buildGraphComponent.options.name]}">
    <div class="parameter">
        ${buildGraphComponent.options.displayName}: <props:displayValue name="${buildGraphComponent.options.name}"/>
    </div>
</c:if>

<div class="parameter">
    ${buildGraphComponent.mode.displayName}: <props:displayValue name="${buildGraphComponent.mode.name}"/>
</div>

<c:set var="buildGraphMode" value="${propertiesBean.properties[buildGraphComponent.mode.name]}"/>
<c:choose>
    <c:when test="${buildGraphMode == buildGraphComponent.mode.distributed.name}">
        <c:set var="postBadges" value="${propertiesBean.properties[buildGraphComponent.postBadges.name]}"/>
        <c:if test="${postBadges}">
            <div class="parameter">
                ${buildGraphComponent.postBadges.displayName}: <strong>Yes</strong>
            </div>

            <div class="parameter">
                ${buildGraphComponent.ugsMetadataServer.displayName}: <props:displayValue name="${buildGraphComponent.ugsMetadataServer.name}"/>
            </div>
        </c:if>
    </c:when>
</c:choose>

<c:if test="${propertiesBean.properties[buildGraphComponent.retriesEnabled.name]}">
    <div class="parameter">
        ${buildGraphComponent.retriesEnabled.displayName}: <strong>Yes</strong>
    </div>

    <div class="parameter">
        ${buildGraphComponent.setupMaxAttempts.displayName}: <props:displayValue name="${buildGraphComponent.setupMaxAttempts.name}"/>
    </div>

    <div class="parameter">
        ${buildGraphComponent.retryDelaySeconds.displayName}: <props:displayValue name="${buildGraphComponent.retryDelaySeconds.name}"/>
    </div>

    <div class="parameter">
        ${buildGraphComponent.retryCondition.displayName}: <props:displayValue name="${buildGraphComponent.retryCondition.name}"/>
    </div>

    <c:if test="${not empty propertiesBean.properties[buildGraphComponent.retryFailurePatterns.name]}">
        <div class="parameter">
            ${buildGraphComponent.retryFailurePatterns.displayName}: <props:displayValue name="${buildGraphComponent.retryFailurePatterns.name}"/>
        </div>
    </c:if>

    <c:if test="${not empty propertiesBean.properties[buildGraphComponent.nodeRetryRules.name]}">
        <div class="parameter">
            ${buildGraphComponent.nodeRetryRules.displayName}: <props:displayValue name="${buildGraphComponent.nodeRetryRules.name}"/>
        </div>
    </c:if>

    <c:if test="${propertiesBean.properties[buildGraphComponent.logRetrySummaries.name]}">
        <div class="parameter">
            ${buildGraphComponent.logRetrySummaries.displayName}: <strong>Yes</strong>
        </div>
    </c:if>

    <c:if test="${propertiesBean.properties[buildGraphComponent.warnUnmatchedRetryRules.name]}">
        <div class="parameter">
            ${buildGraphComponent.warnUnmatchedRetryRules.displayName}: <strong>Yes</strong>
        </div>
    </c:if>
</c:if>

<c:if test="${not empty propertiesBean.properties[buildGraphComponent.setupTimeout.name] && propertiesBean.properties[buildGraphComponent.setupTimeout.name] != '0'}">
    <div class="parameter">
        ${buildGraphComponent.setupTimeout.displayName}: <props:displayValue name="${buildGraphComponent.setupTimeout.name}"/>
    </div>
</c:if>

<c:if test="${not empty propertiesBean.properties[buildGraphComponent.nodeTimeoutRules.name]}">
    <div class="parameter">
        ${buildGraphComponent.nodeTimeoutRules.displayName}: <props:displayValue name="${buildGraphComponent.nodeTimeoutRules.name}"/>
    </div>
</c:if>

<c:if test="${propertiesBean.properties[buildGraphComponent.publishSetupDiagnostics.name]}">
    <div class="parameter">
        ${buildGraphComponent.publishSetupDiagnostics.displayName}: <strong>Yes</strong>
    </div>
</c:if>

<c:if test="${propertiesBean.properties[buildGraphComponent.traceGeneratedBuilds.name]}">
    <div class="parameter">
        ${buildGraphComponent.traceGeneratedBuilds.displayName}: <strong>Yes</strong>
    </div>
</c:if>

<c:set var="bootstrapMode" value="${propertiesBean.properties[buildGraphComponent.bootstrapMode.name]}"/>
<c:if test="${not empty bootstrapMode && bootstrapMode != buildGraphComponent.bootstrapMode.disabled.name}">
    <div class="parameter">
        ${buildGraphComponent.bootstrapMode.displayName}: <props:displayValue name="${buildGraphComponent.bootstrapMode.name}"/>
    </div>

    <div class="parameter">
        ${buildGraphComponent.bootstrapApplyTo.displayName}: <props:displayValue name="${buildGraphComponent.bootstrapApplyTo.name}"/>
    </div>

    <c:if test="${not empty propertiesBean.properties[buildGraphComponent.bootstrapStepRefs.name]}">
        <div class="parameter">
            ${buildGraphComponent.bootstrapStepRefs.displayName}: <props:displayValue name="${buildGraphComponent.bootstrapStepRefs.name}"/>
        </div>
    </c:if>
</c:if>

<c:set var="logSink" value="${propertiesBean.properties[buildGraphComponent.logSink.name]}"/>
<c:if test="${not empty logSink && logSink != buildGraphComponent.logSink.teamCityOnly.name}">
    <div class="parameter">
        ${buildGraphComponent.logSink.displayName}: <props:displayValue name="${buildGraphComponent.logSink.name}"/>
    </div>

    <c:if test="${logSink == buildGraphComponent.logSink.localFile.name || logSink == buildGraphComponent.logSink.localFileAndGraylog.name}">
        <c:if test="${not empty propertiesBean.properties[buildGraphComponent.logDirectory.name]}">
            <div class="parameter">
                ${buildGraphComponent.logDirectory.displayName}: <props:displayValue name="${buildGraphComponent.logDirectory.name}"/>
            </div>
        </c:if>

        <c:if test="${propertiesBean.properties[buildGraphComponent.publishLogArtifacts.name]}">
            <div class="parameter">
                ${buildGraphComponent.publishLogArtifacts.displayName}: <strong>Yes</strong>
            </div>
        </c:if>
    </c:if>

    <c:if test="${logSink == buildGraphComponent.logSink.graylog.name || logSink == buildGraphComponent.logSink.localFileAndGraylog.name}">
        <div class="parameter">
            ${buildGraphComponent.graylogEndpoint.displayName}: <props:displayValue name="${buildGraphComponent.graylogEndpoint.name}"/>
        </div>

        <c:if test="${not empty propertiesBean.properties[buildGraphComponent.graylogSource.name]}">
            <div class="parameter">
                ${buildGraphComponent.graylogSource.displayName}: <props:displayValue name="${buildGraphComponent.graylogSource.name}"/>
            </div>
        </c:if>

        <div class="parameter">
            ${buildGraphComponent.graylogTimeoutSeconds.displayName}: <props:displayValue name="${buildGraphComponent.graylogTimeoutSeconds.name}"/>
        </div>

        <c:if test="${not empty propertiesBean.properties[buildGraphComponent.graylogExtraFields.name]}">
            <div class="parameter">
                ${buildGraphComponent.graylogExtraFields.displayName}: <props:displayValue name="${buildGraphComponent.graylogExtraFields.name}"/>
            </div>
        </c:if>
    </c:if>
</c:if>
