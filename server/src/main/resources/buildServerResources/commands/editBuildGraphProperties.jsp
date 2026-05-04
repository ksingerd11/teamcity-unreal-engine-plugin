<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri ="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>
<%@ taglib prefix="bs" tagdir="/WEB-INF/tags" %>

<jsp:useBean id="component" class="com.jetbrains.teamcity.plugins.unrealengine.server.runner.ui.BuildGraphComponent"/>

<c:set var="parameter" value="${component.script}"/>
<%@ include file="../common/textField.jspf"%>

<c:set var="noBorder" value="${true}"/>

<c:set var="parameter" value="${component.target}"/>
<%@ include file="../common/textField.jspf"%>

<tr id="BuildGraph">
    <th class="noBorder">
        <label for="${component.options.name}">
            ${component.options.displayName}:
        </label>
    </th>
    <td class="noBorder">
        <props:multilineProperty expanded="true" name="${component.options.name}" rows="6" cols="4" className="longField" linkTitle=""/>
        <span class="error" id="error_${component.options.name}"></span>
        <span class="smallNote" id="${component.options.name}-hint">
            ${component.options.description}
        </span>
    </td>
</tr>

<c:set var="parameter" value="${component.mode}"/>
<c:set var="onChange" value="BS.UnrealRunner.updateContentBasedOnSelect('${component.mode.name}', ${component.mode.optionNamesAsJsArray})"/>
<%@ include file="../common/selectField.jspf"%>

<tbody id="${component.mode.distributed.name}">
    <c:set var="parameter" value="${component.postBadges}"/>
    <c:set var="onclick" value="BS.UnrealRunner.updateContentBasedOnCheckbox('${component.postBadges.name}', '.post-badges-settings')"/>
    <%@ include file="../common/checkbox.jspf"%>
    <c:set var="onclick" value=""/>

    <c:set var="cssClass" value="post-badges-settings"/>
    <c:set var="parameter" value="${component.ugsMetadataServer}"/>
    <%@ include file="../common/textField.jspf"%>
    <c:set var="cssClass" value=""/>
</tbody>

<c:set var="parameter" value="${component.retriesEnabled}"/>
<c:set var="onclick" value="BS.UnrealRunner.updateContentBasedOnCheckbox('${component.retriesEnabled.name}', '.build-graph-retry-settings')"/>
<%@ include file="../common/checkbox.jspf"%>
<c:set var="onclick" value=""/>

<c:set var="cssClass" value="build-graph-retry-settings"/>
<c:set var="parameter" value="${component.setupMaxAttempts}"/>
<%@ include file="../common/textField.jspf"%>

<c:set var="parameter" value="${component.retryDelaySeconds}"/>
<%@ include file="../common/textField.jspf"%>

<c:set var="parameter" value="${component.retryCondition}"/>
<c:set var="onChange" value=""/>
<%@ include file="../common/selectField.jspf"%>

<tr class="unreal-parameter build-graph-retry-settings advancedSetting">
    <th class="noBorder">
        <label for="${component.retryFailurePatterns.name}">
            ${component.retryFailurePatterns.displayName}:
        </label>
    </th>
    <td class="noBorder">
        <props:multilineProperty expanded="true" name="${component.retryFailurePatterns.name}" rows="5" cols="4" className="longField" linkTitle=""/>
        <span class="error" id="error_${component.retryFailurePatterns.name}"></span>
        <span class="smallNote preserve-line-breaks" id="${component.retryFailurePatterns.name}-hint">
            ${component.retryFailurePatterns.description}
        </span>
    </td>
</tr>

<tr class="unreal-parameter build-graph-retry-settings advancedSetting">
    <th class="noBorder">
        <label for="${component.nodeRetryRules.name}">
            ${component.nodeRetryRules.displayName}:
        </label>
    </th>
    <td class="noBorder">
        <props:multilineProperty expanded="true" name="${component.nodeRetryRules.name}" rows="5" cols="4" className="longField" linkTitle=""/>
        <span class="error" id="error_${component.nodeRetryRules.name}"></span>
        <span class="smallNote preserve-line-breaks" id="${component.nodeRetryRules.name}-hint">
            ${component.nodeRetryRules.description}
        </span>
    </td>
</tr>

<c:set var="parameter" value="${component.logRetrySummaries}"/>
<%@ include file="../common/checkbox.jspf"%>

<c:set var="parameter" value="${component.warnUnmatchedRetryRules}"/>
<%@ include file="../common/checkbox.jspf"%>
<c:set var="cssClass" value=""/>

<c:set var="parameter" value="${component.setupTimeout}"/>
<%@ include file="../common/textField.jspf"%>

<tr class="unreal-parameter advancedSetting">
    <th class="noBorder">
        <label for="${component.nodeTimeoutRules.name}">
            ${component.nodeTimeoutRules.displayName}:
        </label>
    </th>
    <td class="noBorder">
        <props:multilineProperty expanded="true" name="${component.nodeTimeoutRules.name}" rows="5" cols="4" className="longField" linkTitle=""/>
        <span class="error" id="error_${component.nodeTimeoutRules.name}"></span>
        <span class="smallNote preserve-line-breaks" id="${component.nodeTimeoutRules.name}-hint">
            ${component.nodeTimeoutRules.description}
        </span>
    </td>
</tr>

<c:set var="parameter" value="${component.publishSetupDiagnostics}"/>
<%@ include file="../common/checkbox.jspf"%>

<c:set var="parameter" value="${component.traceGeneratedBuilds}"/>
<%@ include file="../common/checkbox.jspf"%>

<c:set var="parameter" value="${component.bootstrapMode}"/>
<c:set var="onChange" value="BS.UnrealRunner.updateBuildGraphBootstrap()"/>
<%@ include file="../common/selectField.jspf"%>
<c:set var="onChange" value=""/>

<c:set var="cssClass" value="build-graph-bootstrap-settings"/>
<c:set var="parameter" value="${component.bootstrapApplyTo}"/>
<%@ include file="../common/selectField.jspf"%>
<c:set var="cssClass" value=""/>

<tr class="unreal-parameter build-graph-bootstrap-settings build-graph-bootstrap-selected-settings advancedSetting">
    <th class="noBorder">
        <label for="${component.bootstrapStepRefs.name}">
            ${component.bootstrapStepRefs.displayName}:
        </label>
    </th>
    <td class="noBorder">
        <props:multilineProperty expanded="true" name="${component.bootstrapStepRefs.name}" rows="5" cols="4" className="longField" linkTitle=""/>
        <span class="error" id="error_${component.bootstrapStepRefs.name}"></span>
        <span class="smallNote preserve-line-breaks" id="${component.bootstrapStepRefs.name}-hint">
            ${component.bootstrapStepRefs.description}
        </span>
    </td>
</tr>

<c:set var="parameter" value="${component.logSink}"/>
<c:set var="onChange" value="BS.UnrealRunner.updateBuildGraphLogSink()"/>
<%@ include file="../common/selectField.jspf"%>
<c:set var="onChange" value=""/>

<c:set var="cssClass" value="build-graph-local-log-settings"/>
<c:set var="parameter" value="${component.logDirectory}"/>
<%@ include file="../common/textField.jspf"%>

<c:set var="parameter" value="${component.publishLogArtifacts}"/>
<%@ include file="../common/checkbox.jspf"%>
<c:set var="cssClass" value=""/>

<c:set var="cssClass" value="build-graph-graylog-settings"/>
<c:set var="parameter" value="${component.graylogEndpoint}"/>
<%@ include file="../common/textField.jspf"%>

<c:set var="parameter" value="${component.graylogSource}"/>
<%@ include file="../common/textField.jspf"%>

<c:set var="parameter" value="${component.graylogTimeoutSeconds}"/>
<%@ include file="../common/textField.jspf"%>

<tr class="unreal-parameter build-graph-graylog-settings advancedSetting">
    <th class="noBorder">
        <label for="${component.graylogExtraFields.name}">
            ${component.graylogExtraFields.displayName}:
        </label>
    </th>
    <td class="noBorder">
        <props:multilineProperty expanded="true" name="${component.graylogExtraFields.name}" rows="5" cols="4" className="longField" linkTitle=""/>
        <span class="error" id="error_${component.graylogExtraFields.name}"></span>
        <span class="smallNote preserve-line-breaks" id="${component.graylogExtraFields.name}-hint">
            ${component.graylogExtraFields.description}
        </span>
    </td>
</tr>

<tr class="unreal-parameter build-graph-graylog-settings advancedSetting">
    <th class="noBorder"></th>
    <td class="noBorder">
        <input type="button" class="btn btn_mini" value="Test Graylog" onclick="BS.UnrealRunner.testBuildGraphGraylog(); return false;"/>
        <span class="smallNote" id="build-graph-graylog-test-status"></span>
    </td>
</tr>
<c:set var="cssClass" value=""/>

<script type="text/javascript">
    BS.UnrealRunner.updateBuildGraphLogSink = function() {
        const selectedValue = $j(BS.Util.escapeId('${component.logSink.name}')).val();
        const localEnabled =
            selectedValue === '${component.logSink.localFile.name}' ||
            selectedValue === '${component.logSink.localFileAndGraylog.name}';
        const graylogEnabled =
            selectedValue === '${component.logSink.graylog.name}' ||
            selectedValue === '${component.logSink.localFileAndGraylog.name}';

        $j('.build-graph-local-log-settings').toggle(localEnabled);
        $j('.build-graph-graylog-settings').toggle(graylogEnabled);
        BS.MultilineProperties.updateVisible();
    };

    BS.UnrealRunner.updateBuildGraphBootstrap = function() {
        const selectedValue = $j(BS.Util.escapeId('${component.bootstrapMode.name}')).val();
        const enabled = selectedValue !== '${component.bootstrapMode.disabled.name}';
        const selectedOnly = selectedValue === '${component.bootstrapMode.selected.name}';

        $j('.build-graph-bootstrap-settings').toggle(enabled);
        $j('.build-graph-bootstrap-selected-settings').toggle(selectedOnly);
        BS.MultilineProperties.updateVisible();
    };

    BS.UnrealRunner.testBuildGraphGraylog = function() {
        const status = $j('#build-graph-graylog-test-status');
        status.text('Testing Graylog endpoint...');

        BS.ajaxRequest('<c:url value="/unrealEngine/buildGraph/testGraylog.html"/>', {
            method: 'post',
            parameters: {
                endpoint: $j(BS.Util.escapeId('${component.graylogEndpoint.name}')).val(),
                source: $j(BS.Util.escapeId('${component.graylogSource.name}')).val(),
                timeoutSeconds: $j(BS.Util.escapeId('${component.graylogTimeoutSeconds.name}')).val(),
                extraFields: $j(BS.Util.escapeId('${component.graylogExtraFields.name}')).val()
            },
            onComplete: function(transport) {
                let response = {};
                try {
                    response = JSON.parse(transport.responseText || '{}');
                } catch (e) {
                    response = {success: false, message: 'Unexpected response from TeamCity server.'};
                }

                status
                    .toggleClass('error', !response.success)
                    .text(response.message || (response.success ? 'Graylog test message sent.' : 'Graylog test failed.'));
            }
        });
    };

    BS.UnrealRunner.updateContentBasedOnSelect('${component.mode.name}', ${component.mode.optionNamesAsJsArray})
    BS.UnrealRunner.updateContentBasedOnCheckbox('${component.postBadges.name}', '.post-badges-settings');
    BS.UnrealRunner.updateContentBasedOnCheckbox('${component.retriesEnabled.name}', '.build-graph-retry-settings');
    BS.UnrealRunner.updateBuildGraphBootstrap();
    BS.UnrealRunner.updateBuildGraphLogSink();
</script>
