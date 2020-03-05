package rocks.patch.plugins.jira.mailhandlerdefault;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.project.ProjectService;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.service.util.handler.MessageHandler;
import com.atlassian.jira.service.util.handler.MessageHandlerContext;
import com.atlassian.jira.service.util.handler.MessageHandlerErrorCollector;
import com.atlassian.jira.service.util.handler.MessageUserProcessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.Map;

public class Handler implements MessageHandler {

    private static final Logger log = LogManager.getLogger("atlassian.plugin");

    public static final String KEY_PROJECT_KEY = "projectKey";
    public static final String KEY_ISSUE_TYPE_NAME = "issueTypeName";
    public static final String KEY_REPORTER_DEFAULT_NAME = "reporterDefaultName";

    @ComponentImport
    private final IssueManager issueManager;
    @ComponentImport
    private final MessageUserProcessor messageUserProcessor;
    @ComponentImport
    private final ProjectService projectService;
    @ComponentImport
    private final JiraAuthenticationContext authenticationContext;
    @ComponentImport
    private final ConstantsManager constantsManager;
    @ComponentImport
    private final IssueService issueService;
    @ComponentImport
    private final UserManager userManager;

    private String projectKey;
    private String issueTypeName;
    private String reporterDefaultName;

    // we can use dependency injection here too!
    public Handler(MessageUserProcessor messageUserProcessor,
                   IssueManager issuesManager,
                   ProjectService projectService,
                   JiraAuthenticationContext authenticationContext,
                   ConstantsManager constantsManager,
                   IssueService issueService,
                   UserManager userManager) {
        this.messageUserProcessor = messageUserProcessor;
        this.issueManager = issuesManager;
        this.projectService = projectService;
        this.authenticationContext = authenticationContext;
        this.constantsManager = constantsManager;
        this.issueService = issueService;
        this.userManager = userManager;
    }

    @Override
    public void init(Map<String, String> params, MessageHandlerErrorCollector monitor) {

        // getting configuration parameters

        projectKey = params.get(KEY_PROJECT_KEY);
        if (StringUtils.isBlank(projectKey)) {
            monitor.error("Project key has not been specified ('" + KEY_PROJECT_KEY + "' parameter). This handler will not work correctly.");
        }

        issueTypeName = params.get(KEY_ISSUE_TYPE_NAME);
        if (StringUtils.isBlank(issueTypeName)) {
            monitor.error("Issue type name has not been specified ('" + KEY_ISSUE_TYPE_NAME + "' parameter). This handler will not work correctly.");
        }

        reporterDefaultName = params.get(KEY_REPORTER_DEFAULT_NAME);
        if (StringUtils.isBlank(reporterDefaultName)) {
            monitor.error("Reporter default name has not been specified ('" + KEY_REPORTER_DEFAULT_NAME + "' parameter). This handler will not work correctly.");
        }

    }

    @Override
    public boolean handleMessage(Message message, MessageHandlerContext context) throws MessagingException {
        return createNewIssue(message, context);
    }

    private boolean createNewIssue(Message message, MessageHandlerContext context) throws MessagingException {

        // ApplicationUser user = authenticationContext.getLoggedInUser();
        ApplicationUser user = userManager.getUserByKey("admin");
        log.info("Incoming mail: using application user " + user.getName() + " / " + user.getEmailAddress());
        Project project = projectService.getProjectByKey(user, projectKey).getProject();
        log.info("Incoming mail: using project " + project.getName() + " / " + project.getId());

        IssueType taskIssueType = project.getIssueTypes().stream().filter(
                issueType -> issueType.getName().equalsIgnoreCase(issueTypeName)).findFirst().orElse(null);
        log.info("Incoming mail: using issue type " + taskIssueType.getName() + " / " + taskIssueType.getId());

        ApplicationUser reporter = getReporter(message);

        IssueInputParameters issueInputParameters = issueService.newIssueInputParameters();
        issueInputParameters.setSummary(message.getSubject())
                .setDescription(message.getDescription())
                .setReporterId(reporter.getName())
                .setProjectId(project.getId())
                .setIssueTypeId(taskIssueType.getId());

        authenticationContext.setLoggedInUser(user);
        IssueService.CreateValidationResult result = issueService.validateCreate(user, issueInputParameters);

        if (!result.getErrorCollection().hasAnyErrors()) {
            issueService.create(user, result);
            return true;
        } else {
            log.warn(result.getErrorCollection());
            log.warn("Errors: " + result.getErrorCollection().hasAnyErrors());
            log.warn("# Errors: " + result.getErrorCollection().getErrorMessages().size());
            log.warn("# Warnings: " + result.getWarningCollection().getWarnings().size());
            result.getErrorCollection().getErrorMessages().forEach(
                    error -> log.warn("Error: " + error.toString())
            );
        }

        return false;

    }

    private ApplicationUser getReporter(Message message) throws MessagingException {

        InternetAddress sender = (InternetAddress) message.getFrom()[0];
        ApplicationUser reporter = userManager.getUserByName(sender.getAddress());
        log.info("Incoming mail: using reporter " + reporter.getName());

        return reporter;

    }

}