/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.runtime.cmd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IPerspectiveRegistry;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.PMDRuntimeConstants;
import net.sourceforge.pmd.eclipse.runtime.builder.MarkerUtil;
import net.sourceforge.pmd.eclipse.runtime.preferences.IPreferences;
import net.sourceforge.pmd.eclipse.runtime.properties.IProjectProperties;
import net.sourceforge.pmd.eclipse.runtime.properties.PropertiesException;
import net.sourceforge.pmd.eclipse.ui.actions.RuleSetUtil;
import net.sourceforge.pmd.eclipse.ui.actions.internal.InternalRuleSetUtil;
import net.sourceforge.pmd.lang.Language;

/**
 * This command executes the PMD engine on a specified resource.
 * As resource might be a single selected file, multiple selected files
 * or one or more complete projects.
 *
 * @author Philippe Herlin
 *
 */
public class ReviewCodeCmd extends AbstractDefaultCommand {
    private static final Logger LOG = LoggerFactory.getLogger(ReviewCodeCmd.class);

    /**
     * Maximum count of changed resources, that are considered to be not a full build. If more than these resources are
     * changed, PMD will only be executed, if full build option is enabled.
     */
    private static final int MAXIMUM_RESOURCE_COUNT = 5;

    private final List<IResource> resources = new ArrayList<IResource>();
    private IResourceDelta resourceDelta;
    private Map<IFile, Set<MarkerInfo2>> markersByFile = new HashMap<IFile, Set<MarkerInfo2>>();
    private boolean taskMarker;
    private boolean openPmdPerspective;
    private boolean openPmdViolationsOverviewView;
    private boolean openPmdViolationsOutlineView;
    private int ruleCount;
    private int fileCount;
    private long pmdDuration;

    /**
     * Whether to run the review command, even if PMD is disabled in the project settings.
     * This allows to run PMD via the context menu "PMD --&gt; Check Code" manually.
     */
    private boolean runAlways = false;

    private IProjectProperties propertyCache = null;

    public ReviewCodeCmd() {
        super("ReviewCode", "Run PMD on a list of workbench resources");

        setOutputProperties(true);
        setReadOnly(true);
        setTerminated(false);
    }

    public Set<IFile> markedFiles() {
        return markersByFile.keySet();
    }

    /**
     * Easy way to refresh a set of files.
     *
     * @param files the selected files to run PMD on.
     */
    public static void runCodeReviewOnFiles(Set<IFile> files) {
        ReviewCodeCmd cmd = new ReviewCodeCmd();
        cmd.setStepCount(files.size());
        cmd.setTaskMarker(true);
        cmd.setOpenPmdPerspective(PMDPlugin.getDefault().loadPreferences().isPmdPerspectiveEnabled());
        cmd.setOpenPmdViolationsOverviewView(PMDPlugin.getDefault().loadPreferences().isPmdViolationsOverviewEnabled());
        cmd.setOpenPmdViolationsOutlineView(PMDPlugin.getDefault().loadPreferences().isPmdViolationsOutlineEnabled());
        cmd.setUserInitiated(true);
        cmd.setRunAlways(true);
        for (IResource file : files) {
            cmd.addResource(file);
        }
        cmd.performExecute();
    }

    private RuleSet currentRules() {
        // FIXME - this always returns a empty rule set
        return RuleSetUtil.newEmpty(RuleSetUtil.DEFAULT_RULESET_NAME, RuleSetUtil.DEFAULT_RULESET_DESCRIPTION);
    }

    private Map<Rule, String> misconfiguredRulesIn() {

        RuleSet ruleSet = currentRules();

        Map<Rule, String> faultsByRule = new HashMap<Rule, String>();
        for (Rule rule : ruleSet.getRules()) {
            String fault = rule.dysfunctionReason();
            if (StringUtils.isNotEmpty(fault)) {
                faultsByRule.put(rule, fault);
            }
        }

        return faultsByRule;
    }

    /**
     * Checks whether there are any misconfigured rules. If there are, a confirmation
     * dialog will ask the user, whether to continue or abort the PMD run.
     *
     * @return <code>true</code> if PMD should be executed, <code>false</code> if it should be aborted.
     */
    private boolean checkForMisconfiguredRules() {
        RuleSet ruleSet = currentRules();

        boolean runPMD = true;

        if (!ruleSet.getRules().isEmpty()) {
            Map<Rule, String> faultsByRule = misconfiguredRulesIn();
            if (!faultsByRule.isEmpty()) {
                runPMD = MessageDialog.openConfirm(Display.getDefault().getActiveShell(), "Rule configuration problem",
                        "Continue anyways?");
            }
        }
        return runPMD;
    }

    @Override
    public void execute() {
        boolean doReview = checkForMisconfiguredRules();
        if (!doReview) {
            return;
        }

        LOG.debug("ReviewCode command starting.");
        try {
            fileCount = 0;
            ruleCount = 0;
            pmdDuration = 0;

            String projectList = determineProjectList();
            int totalWork = determineTotalWork();
            LOG.info("Found {} resources in projects {}", totalWork, projectList);
            setStepCount(totalWork); // mostly for unit tests

            StringBuilder mainTaskName = new StringBuilder(projectList.length() + 20);
            mainTaskName.append("Executing PMD for ").append(projectList).append(" ...");
            beginTask(mainTaskName.toString(), totalWork);

            // Lancer PMD
            // PMDPlugin fills resources if it's a full build and
            // resourcesDelta if it is incremental or auto
            if (resources.isEmpty()) {
                processResourceDelta();
            } else {
                processResources();
            }

            // do we really need to do any of the rest of this if
            // fileCount and ruleCount are both 0?

            // skip the marking processing if the markersByFile set is empty
            // (avoids grabbing the "run" lock for nothing)
            if (!markersByFile.isEmpty()) {
                // Appliquer les marqueurs
                IWorkspaceRunnable action = new IWorkspaceRunnable() {
                    public void run(IProgressMonitor monitor) throws CoreException {
                        applyMarkers();
                    }
                };

                // clear the markers here. The call to Resource.deleteMarkers
                // will
                // also call the Workspace.prepareOperation so do that
                // outside the larger "applyMarkers" call to avoid doubly
                // holding locks
                // for too long
                for (IFile file : markersByFile.keySet()) {
                    if (isCanceled()) {
                        break;
                    }
                    MarkerUtil.deleteAllMarkersIn(file);
                }

                final IWorkspace workspace = ResourcesPlugin.getWorkspace();
                workspace.run(action, getSchedulingRule(), IWorkspace.AVOID_UPDATE, getMonitor());
            }

            // Switch to the PMD perspective if required
            if (openPmdPerspective) {
                Display.getDefault().asyncExec(new Runnable() {
                    @Override
                    public void run() {
                        switchToPmdPerspective();
                    }
                });
            }

            if (openPmdViolationsOverviewView) {
                PMDPlugin.getDefault().showView(PMDPlugin.VIOLATIONS_OVERVIEW_ID);
            }

            if (openPmdViolationsOutlineView) {
                PMDPlugin.getDefault().showView(PMDPlugin.VIOLATIONS_OUTLINE_ID);
            }

        } catch (CoreException e) {
            throw new RuntimeException("Core exception when reviewing code", e);
        } finally {
            LOG.debug("ReviewCode command has ended.");
            setTerminated(true);
            done();

            // Log performance information
            if (fileCount > 0 && ruleCount > 0) {
                LOG.info("Review code command finished. {} rules were executed against {} files.\n"
                        + "Actual PMD duration is about {}ms, that is about {}ms/file, {}ms/rule, {}ms/filerule",
                        ruleCount, fileCount, pmdDuration, (float) pmdDuration / fileCount,
                        (float) pmdDuration / ruleCount, (float) pmdDuration / ((long) fileCount * (long) ruleCount));
            } else {
                LOG.info("Review code command finished. {} rules were executed against {} files. "
                        + "PMD has not been executed.", ruleCount, fileCount);
            }
        }

        PMDPlugin.getDefault().changedFiles(markedFiles());
    }

    private int determineTotalWork() {
        boolean useFileExtensions = PMDPlugin.getDefault().loadPreferences().isDetermineFiletypesAutomatically();
        Map<IProject, Set<String>> fileExtensionsPerProject = new HashMap<>();
        CountVisitor2 visitor = new CountVisitor2(useFileExtensions, fileExtensionsPerProject);

        for (IResource resource : resources) {
            determineFileExtensions(fileExtensionsPerProject, resource);

            try {
                if (resource instanceof IProject && ((IProject) resource).hasNature(JavaCore.NATURE_ID)) {
                    for (IResource sourceFolder : getJavaProjectSourceFolders((IProject) resource)) {
                        sourceFolder.accept(visitor);
                    }
                } else {
                    resource.accept(visitor);
                }
            } catch (CoreException e) {
                LOG.warn("Error while counting resources for {}", resource, e);
            }
        }
        if (resourceDelta != null) {
            IResource resource = resourceDelta.getResource();
            determineFileExtensions(fileExtensionsPerProject, resource);
            try {
                resource.accept(visitor);
            } catch (CoreException e) {
                LOG.warn("Error while counting resources for {} (delta)", resource, e);
            }
        }
        return visitor.getCount();
    }

    private void determineFileExtensions(Map<IProject, Set<String>> fileExtensionsPerProject, IResource resource) {
        IProject project = resource.getProject();
        if (project != null && !fileExtensionsPerProject.containsKey(project)) {
            try {
                List<RuleSet> rulesets = rulesetsFrom(resource);
                Set<String> fileExtensions = determineFileExtensions(rulesets);
                fileExtensionsPerProject.put(project, fileExtensions);
            } catch (PropertiesException e) {
                LOG.warn("Error while determining file extensions for project {}", project, e);
                fileExtensionsPerProject.put(project, Collections.<String>emptySet());
            }
        }
    }

    private static class CountVisitor2 implements IResourceVisitor {
        private final boolean useFileExtensions;
        private final Map<IProject, Set<String>> fileExtensionsPerProject;
        private int count;

        CountVisitor2(boolean useFileExtensions, Map<IProject, Set<String>> fileExtensionsPerProject) {
            this.useFileExtensions = useFileExtensions;
            this.fileExtensionsPerProject = fileExtensionsPerProject;
        }

        CountVisitor2(boolean useFileExtensions, IProject project, Set<String> fileExtensions) {
            this.useFileExtensions = useFileExtensions;
            this.fileExtensionsPerProject = new HashMap<>();
            this.fileExtensionsPerProject.put(project, fileExtensions);
        }

        @Override
        public boolean visit(IResource resource) throws CoreException {
            if (resource instanceof IFile) {
                if (useFileExtensions) {
                    Set<String> extensions = fileExtensionsPerProject.get(resource.getProject());
                    String extension = resource.getFileExtension();
                    if (extensions != null && extension != null
                            && extensions.contains(extension.toLowerCase(Locale.ROOT))) {
                        count++;
                    }
                } else {
                    // count all files
                    count++;
                }
            }
            return true;
        }

        public int getCount() {
            return count;
        }
    }

    private String determineProjectList() {
        Set<IProject> projects = new HashSet<>();
        for (IResource resource : resources) {
            IProject project = resource.getProject();
            if (project != null) {
                projects.add(project);
            }
        }
        if (resourceDelta != null) {
            IProject project = resourceDelta.getResource().getProject();
            if (project != null) {
                projects.add(project);
            }
        }
        StringBuilder projectList = new StringBuilder(projects.size() * 20);
        projectList.append('[');
        for (IProject project : projects) {
            projectList.append(project.getName());
            projectList.append(", ");
        }
        projectList.delete(projectList.length() - 2, projectList.length());
        projectList.append(']');
        return projectList.toString();
    }

    public Map<IFile, Set<MarkerInfo2>> getMarkers() {
        return markersByFile;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setResources(Collection<ISchedulingRule> resources) {
        resources.clear();
        resources.addAll(resources);
    }

    /**
     * Add a resource to the list of resources to be reviewed.
     *
     * @param resource
     *            a workbench resource
     */
    public void addResource(IResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Resource parameter can not be null");
        }

        resources.add(resource);
    }

    public void setResourceDelta(IResourceDelta resourceDelta) {
        this.resourceDelta = resourceDelta;
    }

    public void setTaskMarker(boolean taskMarker) {
        this.taskMarker = taskMarker;
    }

    public void setRunAlways(boolean runAlways) {
        this.runAlways = runAlways;
    }

    /**
     * @param openPmdPerspective
     *            Tell whether the PMD perspective should be opened after processing.
     */
    public void setOpenPmdPerspective(boolean openPmdPerspective) {
        this.openPmdPerspective = openPmdPerspective;
    }

    /**
     * Set the open violations view to run after code review.
     *
     * @param openPmdViolationsView
     *            should open
     */
    public void setOpenPmdViolationsOverviewView(boolean openPmdViolationsView) {
        this.openPmdViolationsOverviewView = openPmdViolationsView;
    }

    /**
     * Set the open violations outline view to run after code review.
     *
     * @param openPmdViolationsOutlineView
     *            should open
     */
    public void setOpenPmdViolationsOutlineView(boolean openPmdViolationsOutlineView) {
        this.openPmdViolationsOutlineView = openPmdViolationsOutlineView;
    }

    @Override
    public void reset() {
        resources.clear();
        markersByFile = new HashMap<IFile, Set<MarkerInfo2>>();
        setTerminated(false);
        openPmdPerspective = false;
        openPmdViolationsOverviewView = false;
        openPmdViolationsOutlineView = false;
        runAlways = false;
    }

    @Override
    public boolean isReadyToExecute() {
        return !resources.isEmpty() || resourceDelta != null;
    }

    /**
     * @return the scheduling rule needed to apply markers
     */
    private ISchedulingRule getSchedulingRule() {
        final IWorkspace workspace = ResourcesPlugin.getWorkspace();
        final IResourceRuleFactory ruleFactory = workspace.getRuleFactory();
        ISchedulingRule rule;

        if (resources.isEmpty()) {
            rule = ruleFactory.markerRule(resourceDelta.getResource().getProject());
        } else {
            ISchedulingRule[] rules = new ISchedulingRule[resources.size()];
            for (int i = 0; i < rules.length; i++) {
                rules[i] = ruleFactory.markerRule((IResource) resources.get(i));
            }
            rule = new MultiRule(resources.toArray(rules));
        }

        return rule;
    }

    /**
     * Process the list of workbench resources
     */
    private void processResources() {
        for (IResource resource : resources) {
            if (!isCanceled()) {
                // if resource is a project, visit only its source folders
                if (resource instanceof IProject) {
                    processProject((IProject) resource);
                } else {
                    processResource(resource);
                }
            }
        }
    }

    private IProjectProperties getProjectProperties(IProject project) throws PropertiesException {
        if (propertyCache == null || !propertyCache.getProject().getName().equals(project.getName())) {
            propertyCache = PMDPlugin.getDefault().loadProjectProperties(project);
        }
        return propertyCache;
    }

    private List<RuleSet> rulesetsFrom(IResource resource) throws PropertiesException {
        IProject project = resource.getProject();
        IProjectProperties properties = getProjectProperties(project);

        return filteredRuleSets(properties); // properties.getProjectRuleSet();
    }

    /**
     * Review a single resource. The given resource might be a directory, though.
     */
    private void processResource(IResource resource) {
        try {

            final IProject project = resource.getProject();
            final IProjectProperties properties = getProjectProperties(project);
            if (!runAlways && !properties.isPmdEnabled()) {
                return;
            }

            List<RuleSet> ruleSets = rulesetsFrom(resource);
            Set<String> fileExtensions = determineFileExtensions(ruleSets);
            // final PMDEngine pmdEngine = getPmdEngineForProject(project);
            int targetCount = 0;
            if (resource.exists()) {
                targetCount = countResourceElement(resource, fileExtensions);
            }
            // Could add a property that lets us set the max number to analyze
            if (properties.isFullBuildEnabled() || isUserInitiated() || targetCount <= MAXIMUM_RESOURCE_COUNT) {
                setStepCount(targetCount);
                LOG.debug("Visiting resource {}: {}", resource.getName(), getStepCount());
                if (resource.exists()) {
                    final ResourceVisitor visitor = new ResourceVisitor();
                    visitor.setMonitor(getMonitor());
                    visitor.setRuleSetList(ruleSets);
                    visitor.setFileExtensions(fileExtensions);
                    // visitor.setPmdEngine(pmdEngine);
                    visitor.setAccumulator(markersByFile);
                    visitor.setUseTaskMarker(taskMarker);
                    visitor.setProjectProperties(properties);
                    resource.accept(visitor);

                    ruleCount = InternalRuleSetUtil.countRules(ruleSets);
                    fileCount += visitor.getProcessedFilesCount();
                    pmdDuration += visitor.getActualPmdDuration();
                } else {
                    LOG.debug("Skipping resource {} because it doesn't exist.", resource.getName());
                }
            } else {
                LOG.info("Skipping resource {} because of fullBuildEnabled flag and "
                        + "targetCount is {}. This is more than {}. "
                        + "If you want to execute PMD, please check \"Full build enabled\" in the project settings.",
                        resource.getName(), targetCount, MAXIMUM_RESOURCE_COUNT);
            }

        } catch (PropertiesException e) {
            throw new RuntimeException(e);
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<String> determineFileExtensions(List<RuleSet> ruleSets) {
        Set<Language> languages = new HashSet<Language>();
        for (RuleSet ruleset : ruleSets) {
            for (Rule rule : ruleset.getRules()) {
                languages.add(rule.getLanguage());
            }
        }
        Set<String> fileExtensions = new HashSet<String>();
        for (Language language : languages) {
            for (String extension : language.getExtensions()) {
                fileExtensions.add(extension.toLowerCase(Locale.ROOT));
            }
        }
        LOG.debug("Determined applicable file extensions: {}", fileExtensions);
        return fileExtensions;
    }

    /**
     * Review an entire project
     */
    private void processProject(IProject project) {
        try {
            subTask("Review " + project);

            if (project.hasNature(JavaCore.NATURE_ID)) {
                processJavaProject(project);
            } else {
                processResource(project);
            }

        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
    }

    private void processJavaProject(IProject project) throws CoreException {
        for (IResource sourceFolder : getJavaProjectSourceFolders(project)) {
            processResource(sourceFolder);
        }
    }

    private List<IResource> getJavaProjectSourceFolders(IProject project) throws CoreException {
        List<IResource> sourceFolders = new ArrayList<>();
        final IJavaProject javaProject = JavaCore.create(project);
        final IClasspathEntry[] entries = javaProject.getRawClasspath();
        final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        for (IClasspathEntry entrie : entries) {
            if (entrie.getEntryKind() == IClasspathEntry.CPE_SOURCE) {

                // phherlin note: this code is ugly but I don't how to do
                // otherwise.
                // The IWorkspaceRoot getContainerLocation(IPath) always
                // return null.
                // Catching the IllegalArgumentException on getFolder is the
                // only way I found
                // to know if the entry is a folder or a project !
                IContainer sourceContainer = null;
                try {
                    sourceContainer = root.getFolder(entrie.getPath());
                } catch (IllegalArgumentException e) {
                    sourceContainer = root.getProject(entrie.getPath().toString());
                }
                if (sourceContainer == null) {
                    LOG.warn("Source container {} for project {} is not valid", entrie.getPath(), project);
                } else {
                    sourceFolders.add(sourceContainer);
                }
            }
        }
        return sourceFolders;
    }

    private void taskScope(int activeRuleCount, int totalRuleCount) {
        setTaskName("Checking with " + Integer.toString(activeRuleCount) + " out of " + Integer.toString(totalRuleCount)
                + " rules");
    }

    private List<RuleSet> filteredRuleSets(IProjectProperties properties) throws PropertiesException {
        final List<RuleSet> projectRuleSets = properties.getProjectRuleSetList();
        IPreferences preferences = PMDPlugin.getDefault().getPreferencesManager().loadPreferences();
        Set<String> onlyActiveRuleNames = preferences.getActiveRuleNames();

        List<RuleSet> filteredRuleSets = new ArrayList<>();

        for (RuleSet ruleSet : projectRuleSets) {
            int rulesBefore = ruleSet.size();
            RuleSet filteredRuleSet = RuleSetUtil.newCopyOf(ruleSet);
            if (preferences.getGlobalRuleManagement()) {
                // TODO: active rules are not language aware... filter by rule name...
                List<Rule> rulesToKeep = new ArrayList<Rule>();
                for (Rule rule : filteredRuleSet.getRules()) {
                    if (onlyActiveRuleNames.contains(rule.getName())) {
                        rulesToKeep.add(rule);
                    }
                }
                filteredRuleSet = RuleSetUtil.retainOnly(filteredRuleSet, rulesToKeep);
                int rulesAfter = filteredRuleSet.size();

                if (rulesAfter < rulesBefore) {
                    LOG.warn("Ruleset has been filtered as Global Rule Management is active. "
                            + "{} of {} rules are active and are used. {} rules will be ignored.",
                            rulesAfter, rulesBefore, rulesBefore - rulesAfter);
                }
            }
            filteredRuleSet = InternalRuleSetUtil.addExcludePatterns(filteredRuleSet,
                    InternalRuleSetUtil.convertStringPatterns(preferences.activeExclusionPatterns()),
                    InternalRuleSetUtil.convertStringPatterns(properties.getBuildPathExcludePatterns()));
            filteredRuleSet = InternalRuleSetUtil.addIncludePatterns(filteredRuleSet,
                    InternalRuleSetUtil.convertStringPatterns(preferences.activeInclusionPatterns()),
                    InternalRuleSetUtil.convertStringPatterns(properties.getBuildPathIncludePatterns()));
            filteredRuleSets.add(filteredRuleSet);
        }

        taskScope(InternalRuleSetUtil.countRules(filteredRuleSets), InternalRuleSetUtil.countRules(projectRuleSets));
        return filteredRuleSets;
    }

    private List<RuleSet> rulesetsFromResourceDelta() throws PropertiesException {

        IResource resource = resourceDelta.getResource();
        final IProject project = resource.getProject();
        final IProjectProperties properties = getProjectProperties(project);

        return filteredRuleSets(properties); // properties.getProjectRuleSet();
    }

    /**
     * Review a resource delta.
     */
    private void processResourceDelta() {
        try {
            IResource resource = resourceDelta.getResource();
            final IProject project = resource.getProject();
            final IProjectProperties properties = getProjectProperties(project);
            LOG.info("ReviewCodeCmd started on resource delta {} in {}", resource.getName(), project);

            final List<RuleSet> ruleSets = rulesetsFromResourceDelta();
            Set<String> fileExtensions = determineFileExtensions(ruleSets);

            // PMDEngine pmdEngine = getPmdEngineForProject(project);
            int targetCount = countDeltaElement(resourceDelta);
            // Could add a property that lets us set the max number to analyze
            if (properties.isFullBuildEnabled() || isUserInitiated() || targetCount <= MAXIMUM_RESOURCE_COUNT) {
                setStepCount(targetCount);
                LOG.debug("Visiting delta of resource {}: {}", resource.getName(), getStepCount());

                DeltaVisitor visitor = new DeltaVisitor();
                visitor.setMonitor(getMonitor());
                visitor.setRuleSetList(ruleSets);
                visitor.setFileExtensions(fileExtensions);
                // visitor.setPmdEngine(pmdEngine);
                visitor.setAccumulator(markersByFile);
                visitor.setUseTaskMarker(taskMarker);
                visitor.setProjectProperties(properties);
                resourceDelta.accept(visitor);

                ruleCount = InternalRuleSetUtil.countRules(ruleSets);
                fileCount += visitor.getProcessedFilesCount();
                pmdDuration += visitor.getActualPmdDuration();
            } else {
                LOG.info("Skipping resourceDelta {} because of fullBuildEnabled flag and targetCount is {}. "
                        + "This is more than {}. If you want to execute PMD, please check \"Full build enabled\" "
                        + "in the project settings.", resource.getName(), targetCount, MAXIMUM_RESOURCE_COUNT);
            }
        } catch (PropertiesException e) {
            throw new RuntimeException(e);
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Apply PMD markers after the review.
     */
    private void applyMarkers() {
        LOG.debug("Processing marker directives");
        int violationCount = 0;
        long start = System.currentTimeMillis();

        String currentFile = ""; // for logging

        beginTask("PMD Applying markers", markersByFile.size());

        try {
            for (IFile file : markersByFile.keySet()) {
                if (isCanceled()) {
                    break;
                }
                currentFile = file.getName();
                Set<MarkerInfo2> markerInfoSet = markersByFile.get(file);
                for (MarkerInfo2 markerInfo : markerInfoSet) {
                    markerInfo.addAsMarkerTo(file);
                    violationCount++;
                }

                worked(1);
            }
        } catch (CoreException e) {
            // TODO: NLS
            LOG.warn("CoreException when setting marker for file {}: {}", currentFile, e.toString(), e);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int count = markersByFile.size();
            LOG.debug("applyMarkers: {} markers applied on {} files in {} ms.", violationCount, count, duration);
            LOG.info("End of processing marker directives. " + violationCount + " violations for " + count + " files.");
        }
    }

    /**
     * Count the number of sub-resources of a resource.
     *
     * @param resource a project
     * @param fileExtensions the file extensions that should match
     * @return the element count
     */
    private int countResourceElement(IResource resource, Set<String> fileExtensions) {
        boolean checkFileExtensions = PMDPlugin.getDefault().loadPreferences().isDetermineFiletypesAutomatically();

        if (resource instanceof IFile) {
            if (checkFileExtensions && fileExtensions != null) {
                String extension = resource.getFileExtension();
                if (extension != null && fileExtensions.contains(extension.toLowerCase(Locale.ROOT))) {
                    return 1;
                } else {
                    return 0;
                }
            }
            return 1;
        }

        final CountVisitor2 visitor = new CountVisitor2(checkFileExtensions, resource.getProject(), fileExtensions);

        try {
            resource.accept(visitor);
        } catch (CoreException e) {
            LOG.error("Exception when counting elements of a project: {}", e.toString(), e);
        }

        return visitor.getCount();
    }

    /**
     * Count the number of sub-resources of a delta.
     *
     * @param delta
     *            a resource delta
     * @return the element count
     */
    private int countDeltaElement(IResourceDelta delta) {
        final CountVisitor visitor = new CountVisitor();

        try {
            delta.accept(visitor);
        } catch (CoreException e) {
            LOG.error("Exception counting elements in a delta selection: {}", e.toString(), e);
        }

        return visitor.count;
    }

    /**
     * opens the PMD perspective.
     *
     * @author SebastianRaffel ( 07.05.2005 )
     */
    private static void switchToPmdPerspective() {
        final IWorkbench workbench = PlatformUI.getWorkbench();
        final IPerspectiveRegistry reg = workbench.getPerspectiveRegistry();
        final IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        window.getActivePage().setPerspective(reg.findPerspectiveWithId(PMDRuntimeConstants.ID_PERSPECTIVE));
    }

    /**
     * Private inner class to count the number of resources or delta elements. Only files are counted.
     */
    private final class CountVisitor implements IResourceVisitor, IResourceDeltaVisitor {
        public int count = 0;

        public boolean visit(IResource resource) {
            if (resource instanceof IFile) {
                count++;
            }
            return true;
        }

        public boolean visit(IResourceDelta delta) {
            IResource resource = delta.getResource();
            return visit(resource);
        }
    }

}
