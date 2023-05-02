/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.eclipse.ui.views.ast;

import java.util.List;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleViolation;
import net.sourceforge.pmd.eclipse.ui.actions.RuleSetUtil;
import net.sourceforge.pmd.eclipse.util.internal.SpyingRule;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.JavaLanguageModule;
import net.sourceforge.pmd.lang.rule.XPathRule;
import net.sourceforge.pmd.lang.rule.xpath.XPathVersion;

/**
 * 
 * @author Brian Remedios
 */
public final class XPathEvaluator {
    private static final String SNIPPET_FILENAME = "snippet.java";

    public static final XPathEvaluator INSTANCE = new XPathEvaluator();

    private XPathEvaluator() {
    }

    public Node getCompilationUnit(String source) {
        PMDConfiguration configuration = new PMDConfiguration();
        configuration.setIgnoreIncrementalAnalysis(true);
        configuration.setForceLanguageVersion(getLanguageVersion());

        SpyingRule rule = new SpyingRule();
        rule.setLanguage(getLanguageVersion().getLanguage());
        RuleSet ruleset = RuleSetUtil.newSingle(rule);

        try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
            pmd.addRuleSet(ruleset);
            pmd.files().addSourceFile(source, SNIPPET_FILENAME);
            pmd.performAnalysis();
        }

        return rule.getRootNode();
    }

    private LanguageVersion getLanguageVersion() {
        return LanguageRegistry.PMD.getLanguageByFullName(JavaLanguageModule.NAME).getDefaultVersion();
    }

    /**
     * Builds a temporary XPathRule using the query provided and executes it
     * against the source. Returns a list of nodes detailing any issues found
     * with it.
     * 
     * @param source
     * @param xpathQuery
     * @param xpathVersion
     * @return
     */
    public List<RuleViolation> evaluate(String source, String xpathQuery, String xpathVersion) {
        XPathRule xpathRule = new XPathRule(XPathVersion.ofId(xpathVersion), xpathQuery);
        RuleSet ruleSet = RuleSet.forSingleRule(xpathRule);

        PMDConfiguration configuration = new PMDConfiguration();
        configuration.setIgnoreIncrementalAnalysis(true);
        configuration.setForceLanguageVersion(getLanguageVersion());
        try (PmdAnalysis pmd = PmdAnalysis.create(configuration)) {
            pmd.addRuleSet(ruleSet);
            pmd.files().addSourceFile(source, SNIPPET_FILENAME);
            Report report = pmd.performAnalysisAndCollectReport();
            return report.getViolations();
        }
    }
}
