package com.conceptualware.bdd;

import org.junit.platform.suite.api.*;

/**
 * Concept #19 — BDD: ATDD (Acceptance Test-Driven Development)
 * Cucumber JUnit Platform Suite runner — discovers and runs all .feature files.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = "cucumber.plugin",
    value = "pretty, html:target/cucumber-report.html, json:target/cucumber-report.json")
@ConfigurationParameter(key = "cucumber.glue", value = "com.conceptualware.bdd")
@ConfigurationParameter(key = "cucumber.publish.quiet", value = "true")
public class CucumberRunner {
    // Entry point — JUnit Platform discovers this via @Suite
}
