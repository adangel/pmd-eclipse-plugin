/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */


package net.sourceforge.pmd.eclipse.runtime.cmd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;

import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PMDException;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.RulePriority;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleSetFactory;
import net.sourceforge.pmd.RuleSetWriter;
import net.sourceforge.pmd.RuleViolation;
import net.sourceforge.pmd.Report.ConfigurationError;
import net.sourceforge.pmd.Report.ProcessingError;
import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.PMDRuntimeConstants;
import net.sourceforge.pmd.eclipse.runtime.preferences.IPreferences;
import net.sourceforge.pmd.eclipse.runtime.properties.IProjectProperties;
import net.sourceforge.pmd.eclipse.runtime.properties.PropertiesException;
import net.sourceforge.pmd.eclipse.ui.actions.RuleSetUtil;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.renderers.AbstractRenderer;
import net.sourceforge.pmd.renderers.Renderer;
import net.sourceforge.pmd.util.ResourceLoader;
import net.sourceforge.pmd.util.datasource.DataSource;

import name.herlin.command.CommandException;
import name.herlin.command.Timer;

/**
 * @author andreas
 *
 */
public class ReviewProjectCmd extends AbstractDefaultCommand {

    private final IProject project;
    private final IProjectProperties projectProperties;
    private final PMDConfiguration pmdConfiguration;
    private final RuleSet ruleSet;
    
    public ReviewProjectCmd(IProject project) throws PropertiesException {
        super("Review Project", "Run PMD on a project");
        this.project = project;
        this.projectProperties = PMDPlugin.getDefault().loadProjectProperties(project);
        this.pmdConfiguration = new PMDConfiguration();
        
        boolean isJavaProject = false;
        try {
            isJavaProject = project.hasNature(JavaCore.NATURE_ID);
        } catch (CoreException e) {
            e.printStackTrace();
        }
        if (isJavaProject && PMDPlugin.getDefault().loadPreferences().isProjectBuildPathEnabled()) {
            this.pmdConfiguration.setClassLoader(this.projectProperties.getAuxClasspath());
        }

        if (isJavaProject) {
            LanguageVersion languageVersion = PMDPlugin.javaVersionFor(project);
            this.pmdConfiguration.setDefaultLanguageVersion(languageVersion);
        }
        
        try {
            String defaultCharset = project.getDefaultCharset(true);
            this.pmdConfiguration.setSourceEncoding(defaultCharset);
        } catch (CoreException e1) {
            throw new RuntimeException(e1);
        }
        
        
        try {
            ruleSet = filteredRuleSet(projectProperties);
            File tempRuleSet = File.createTempFile("pmd-eclipse-plugin-ruleset", "xml");
            tempRuleSet.deleteOnExit();
            
            RuleSetWriter writer = new RuleSetWriter(new FileOutputStream(tempRuleSet));
            writer.write(ruleSet);
            writer.close();
            
            this.pmdConfiguration.setRuleSets(tempRuleSet.getAbsolutePath());
            
        } catch (CommandException e) {
            throw new RuntimeException(e);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* (non-Javadoc)
     * @see net.sourceforge.pmd.eclipse.runtime.cmd.AbstractDefaultCommand#execute()
     */
    @Override
    public void execute() throws CommandException {
        PMDPlugin.getDefault().logInformation("Running ReviewProjectCmd for project " + project.getName());
        setTaskName("Review Project " + project.getName());

        PMDPlugin.getDefault().logInformation("Collecting files");
        List<DataSource> resources = collectResources();
        PMDPlugin.getDefault().logInformation("Collecting files: found " + resources.size() + " files");
        beginTask("PMD", resources.size());
        
        RuleSetFactory ruleSetFactory = new RuleSetFactory(new ResourceLoader(), RulePriority.LOW,
                false, false);
        RuleContext ctx = new RuleContext();
        
        Report collectingReport = new Report();
        Renderer renderer = new PmdEclipseRenderer(this, collectingReport);

        PMD.processFiles(pmdConfiguration, ruleSetFactory, resources, ctx, Arrays.asList(renderer));

        PMDPlugin.getDefault().logInformation("PMD found " + collectingReport.size() + " violations for project " + project.getName());

        if (collectingReport.hasConfigErrors()) {
            StringBuilder message = new StringBuilder("There were configuration errors!\n");
            Iterator<ConfigurationError> errors = collectingReport.configErrors();
            while (errors.hasNext()) {
                ConfigurationError error = errors.next();
                message.append(error.rule().getName()).append(": ").append(error.issue()).append('\n');
            }
            PMDPlugin.getDefault().logWarn(message.toString());
        }
        if (collectingReport.hasErrors()) {
            StringBuilder message = new StringBuilder("There were processing errors!\n");
            Iterator<ProcessingError> errors = collectingReport.errors();
            while (errors.hasNext()) {
                ProcessingError error = errors.next();
                message.append(error.getFile()).append(": ").append(error.getMsg()).append(' ')
                .append(error.getDetail())
                .append("\n");
            }
            PMDPlugin.getDefault().logWarn(message.toString());
            throw new RuntimeException(new PMDException(message.toString()));
        }

        applyMarkers(collectingReport);

        done();
    }
    
    private void applyMarkers(Report collectingReport) {
        int violationCount = 0;
        final Timer timer = new Timer();

        String currentFile = ""; // for logging

        beginTask("PMD Applying markers", collectingReport.size());

        try {
            Iterator<RuleViolation> iterator = collectingReport.iterator();
            while (iterator.hasNext()) {
                RuleViolation violation = iterator.next();
                
                IResource resource = project.findMember(violation.getFilename());
                IFile file = null;
                if (resource != null) {
                    file = resource.getAdapter(IFile.class);
                }
                
                if (file != null) {
                    MarkerInfo2 markerInfo = getMarkerInfo(violation, markerTypeFor(violation));
                    markerInfo.addAsMarkerTo(file);
                    violationCount++;
                }
                worked(1);
            }
        } catch (CoreException e) {
            PMDPlugin.getDefault().logWarn("CoreException when setting marker for file " + currentFile + " : " + e.getMessage());
        } catch (PropertiesException e) {
            PMDPlugin.getDefault().logWarn("PropertiesException when setting marker for file " + currentFile + " : " + e.getMessage());
        } finally {
            timer.stop();
            int count = collectingReport.size();
            PMDPlugin.getDefault().logInformation("" + violationCount + " markers applied on " + count + " files in " + timer.getDuration() + "ms.");
            PMDPlugin.getDefault().logInformation("End of processing marker directives. " + violationCount + " violations for " + count + " files.");
        }
    }

    private static String markerTypeFor(RuleViolation violation) {

        int priorityId = violation.getRule().getPriority().getPriority();

        switch (priorityId) {
        case 1:
            return PMDRuntimeConstants.PMD_MARKER_1;
        case 2:
            return PMDRuntimeConstants.PMD_MARKER_2;
        case 3:
            return PMDRuntimeConstants.PMD_MARKER_3;
        case 4:
            return PMDRuntimeConstants.PMD_MARKER_4;
        case 5:
            return PMDRuntimeConstants.PMD_MARKER_5;
        default:
            return PMDRuntimeConstants.PMD_MARKER;
        }
    }
    private MarkerInfo2 getMarkerInfo(RuleViolation violation, String type) throws PropertiesException {

        Rule rule = violation.getRule();

        MarkerInfo2 info = new MarkerInfo2(type, 7);

        info.add(IMarker.MESSAGE, violation.getDescription());
        info.add(IMarker.LINE_NUMBER, violation.getBeginLine());
        info.add(PMDRuntimeConstants.KEY_MARKERATT_LINE2, violation.getEndLine());
        info.add(PMDRuntimeConstants.KEY_MARKERATT_RULENAME, rule.getName());
        info.add(PMDRuntimeConstants.KEY_MARKERATT_PRIORITY, rule.getPriority().getPriority());

        switch (rule.getPriority().getPriority()) {
        case 1:
            info.add(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
            info.add(IMarker.SEVERITY,
                    projectProperties.violationsAsErrors() ? IMarker.SEVERITY_ERROR : IMarker.SEVERITY_WARNING);
            break;
        case 2:
            if (projectProperties.violationsAsErrors()) {
                info.add(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
            } else {
                info.add(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
                info.add(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
            }
            break;

        case 5:
            info.add(IMarker.SEVERITY, IMarker.SEVERITY_INFO);
            break;

        case 3:
            info.add(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
            info.add(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
            break;

        case 4:
        default:
            info.add(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
            info.add(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
            break;
        }

        return info;
    }
    
    private static class PmdEclipseRenderer extends AbstractRenderer {
        private ReviewProjectCmd cmd;
        private Report report;

        public PmdEclipseRenderer(ReviewProjectCmd reviewProjectCmd, Report report) {
            super("Eclipse Renderer", "Collects the violations and updates progress");
            this.cmd = reviewProjectCmd;
            this.report = report;
        }

        @Override
        public String defaultFileExtension() {
            return null;
        }

        @Override
        public void renderFileReport(Report report) throws IOException {
            cmd.worked(1); // one file finished
            for (RuleViolation v : report) {
                this.report.addRuleViolation(v);
            }
            for (Iterator<ProcessingError> it = report.errors(); it.hasNext();) {
                this.report.addError(it.next());
            }
            for (Iterator<ConfigurationError> it = report.configErrors(); it.hasNext();) {
                this.report.addConfigError(it.next());
            }
        }

        @Override
        public void start() throws IOException {
        }

        @Override
        public void startFileAnalysis(DataSource dataSource) {
        }

        @Override
        public void end() throws IOException {
        }
    }
    
    private List<DataSource> collectResources() {
        final List<DataSource> result = new ArrayList<>();

        IResourceVisitor visitor = new IResourceVisitor() {
            @Override
            public boolean visit(IResource resource) throws CoreException {
                IFile file = (IFile) resource.getAdapter(IFile.class);
                if (file != null && file.getFileExtension() != null
                        && ruleSet.applies(file.getRawLocation().toFile())) {
                    result.add(new EclipseDataSourceFile(file));
                }

                return true;
            }
        };
        try {
            project.accept(visitor);
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
        
        return result;
    }
    
    private static class EclipseDataSourceFile implements DataSource {
        private final IFile file;
        public EclipseDataSourceFile(IFile file) {
            this.file = file;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                return file.getContents();
            } catch (CoreException e) {
                throw new IOException(e);
            }
        }

        @Override
        public String getNiceFileName(boolean shortNames, String inputFileName) {
            return file.getProjectRelativePath().toString();
        }
    }

    private RuleSet filteredRuleSet(IProjectProperties properties) throws CommandException, PropertiesException {

        final RuleSet ruleSet = properties.getProjectRuleSet();
        IPreferences preferences = PMDPlugin.getDefault().getPreferencesManager().loadPreferences();
        Set<String> onlyActiveRuleNames = preferences.getActiveRuleNames();

        int rulesBefore = ruleSet.size();
        RuleSet filteredRuleSet = RuleSetUtil.newCopyOf(ruleSet);
        if (preferences.getGlobalRuleManagement()) {
            // TODO: active rules are not language aware... filter by rule
            // name...
            List<Rule> rulesToKeep = new ArrayList<Rule>();
            for (Rule rule : filteredRuleSet.getRules()) {
                if (onlyActiveRuleNames.contains(rule.getName())) {
                    rulesToKeep.add(rule);
                }
            }
            filteredRuleSet = RuleSetUtil.retainOnly(filteredRuleSet, rulesToKeep);
            int rulesAfter = filteredRuleSet.size();

            if (rulesAfter < rulesBefore) {
                PMDPlugin.getDefault()
                        .logWarn("Ruleset has been filtered as Global Rule Management is active. " + rulesAfter + " of "
                                + rulesBefore + " rules are active and are used. " + (rulesBefore - rulesAfter)
                                + " rules will be ignored.");
            }
        }
        filteredRuleSet = RuleSetUtil.addExcludePatterns(filteredRuleSet, preferences.activeExclusionPatterns(),
                properties.getBuildPathExcludePatterns());
        filteredRuleSet = RuleSetUtil.addIncludePatterns(filteredRuleSet, preferences.activeInclusionPatterns(),
                properties.getBuildPathIncludePatterns());

        return filteredRuleSet;
    }

    /* (non-Javadoc)
     * @see net.sourceforge.pmd.eclipse.runtime.cmd.AbstractDefaultCommand#reset()
     */
    @Override
    public void reset() {
        // TODO Auto-generated method stub
        
    }
}
