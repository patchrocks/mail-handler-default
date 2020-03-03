package rocks.patch.plugins.jira.mailhandlerdefault;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.project.ProjectService;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.Issue;
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
import com.atlassian.mail.MailUtils;
import com.atlassian.plugin.Application;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.util.List;
import java.util.Map;

public class Handler implements MessageHandler {

    private static final Logger log = LogManager.getLogger("atlassian.plugin");
    public static final String KEY_ISSUE_KEY = "issueKey";

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

    private final IssueKeyValidator issueKeyValidator;
    private String issueKey;

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
        this.issueKeyValidator = new IssueKeyValidator(issuesManager);
        this.constantsManager = constantsManager;
        this.issueService = issueService;
        this.userManager = userManager;
    }

    @Override
    public void init(Map<String, String> params, MessageHandlerErrorCollector monitor) {
        // getting here issue key configured by the user
        issueKey = params.get(KEY_ISSUE_KEY);
        if (StringUtils.isBlank(issueKey)) {
            // this message will be either logged or displayed to the user (if the handler is tested from web UI)
            monitor.error("Issue key has not been specified ('" + KEY_ISSUE_KEY + "' parameter). This handler will not work correctly.");
        }
        issueKeyValidator.validateIssue(issueKey, monitor);
    }

    @Override
    public boolean handleMessage(Message message, MessageHandlerContext context) throws MessagingException {
        // return createIssueComment(message, context);
        return createNewIssue(message, context);
    }

    private boolean createNewIssue(Message message, MessageHandlerContext context) throws MessagingException {

        // ApplicationUser user = authenticationContext.getLoggedInUser();
        ApplicationUser user = userManager.getUserByKey("admin");
        log.info("Incoming mail: using application user " + user.getName() + " / " + user.getEmailAddress());
        Project project = projectService.getProjectByKey(user, "TEST").getProject();
        log.info("Incoming mail: using project " + project.getName() + " / " + project.getId());

        IssueType taskIssueType = project.getIssueTypes().stream().filter(
                issueType -> issueType.getName().equalsIgnoreCase("task")).findFirst().orElse(null);
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

    private boolean createIssueComment(Message message, MessageHandlerContext context) throws MessagingException {

        // let's again validate the issue key - meanwhile issue could have been deleted, closed, etc..
        final Issue issue = issueKeyValidator.validateIssue(issueKey, context.getMonitor());
        if (issue == null) {
            return false; // returning false means that we were unable to handle this message. It may be either
            // forwarded to specified address or left in the mail queue (if forwarding not enabled)
        }
        // this is a small util method JIRA API provides for us, let's use it.
        final ApplicationUser sender = messageUserProcessor.getAuthorFromSender(message);
        if (sender == null) {
            context.getMonitor().error("Message sender(s) '" + StringUtils.join(MailUtils.getSenders(message), ",")
                    + "' do not have corresponding users in JIRA. Message will be ignored");
            return false;
        }
        final String body = MailUtils.getBody(message);
        final StringBuilder commentBody = new StringBuilder(message.getSubject());
        if (body != null) {
            commentBody.append("\n").append(StringUtils.abbreviate(body, 100000)); // let trim too long bodies
        }
        // thanks to using passed context we don't need to worry about normal run vs. test run - our call
        // will be dispatched accordingly
        context.createComment(issue, sender, commentBody.toString(), false);
        return true; // returning true means that we have handled the message successfully. It means it will be deleted next.

    }

}