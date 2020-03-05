package rocks.patch.plugins.jira.mailhandlerdefault;

import com.atlassian.configurable.ObjectConfigurationException;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.plugins.mail.webwork.AbstractEditHandlerDetailsWebAction;
import com.atlassian.jira.service.JiraServiceContainer;
import com.atlassian.jira.service.services.file.AbstractMessageHandlingService;
import com.atlassian.jira.service.util.ServiceUtils;
import com.atlassian.jira.util.collect.MapBuilder;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

import java.util.Map;

public class EditHandlerDetailsWebAction extends AbstractEditHandlerDetailsWebAction {

    public EditHandlerDetailsWebAction(@ComponentImport PluginAccessor pluginAccessor, @ComponentImport IssueManager issueManager) {
        super(pluginAccessor);
    }

    private String projectKey;
    private String issueTypeName;
    private String reporterDefaultName;

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getIssueTypeName() {
        return issueTypeName;
    }

    public void setIssueTypeName(String issueTypeName) {
        this.issueTypeName = issueTypeName;
    }

    public String getReporterDefaultName() {
        return reporterDefaultName;
    }

    public void setReporterDefaultName(String reporterDefaultName) {
        this.reporterDefaultName = reporterDefaultName;
    }

    // this method is called to let us populate our variables (or action state)
    // with current handler settings managed by associated service (file or mail).
    @Override
    protected void copyServiceSettings(JiraServiceContainer jiraServiceContainer) throws ObjectConfigurationException {

        final String params = jiraServiceContainer.getProperty(AbstractMessageHandlingService.KEY_HANDLER_PARAMS);
        final Map<String, String> parameterMap = ServiceUtils.getParameterMap(params);

        projectKey = parameterMap.get(Handler.KEY_PROJECT_KEY);
        issueTypeName = parameterMap.get(Handler.KEY_ISSUE_TYPE_NAME);
        reporterDefaultName = parameterMap.get(Handler.KEY_REPORTER_DEFAULT_NAME);

    }

    @Override
    protected Map<String, String> getHandlerParams() {

        MapBuilder mapBuilder = MapBuilder.newBuilder();
        mapBuilder.add(Handler.KEY_PROJECT_KEY, projectKey);
        mapBuilder.add(Handler.KEY_ISSUE_TYPE_NAME, issueTypeName);
        mapBuilder.add(Handler.KEY_REPORTER_DEFAULT_NAME, reporterDefaultName);

        return mapBuilder.toMap();

    }

    @Override
    protected void doValidation() {

        if (configuration == null) {
            return;
        }

        super.doValidation();

    }

}