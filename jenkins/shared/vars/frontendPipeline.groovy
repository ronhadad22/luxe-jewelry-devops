#!/usr/bin/env groovy

/**
 * Frontend Pipeline Helper - vars wrapper
 * Provides convenience methods for frontend CI/CD pipelines
 */

def getPodTemplate() {
    return FrontendPipeline.getPodTemplate()
}

def isPullRequest(env) {
    return FrontendPipeline.isPullRequest(env)
}

def isMainBranch(env) {
    return FrontendPipeline.isMainBranch(env)
}

def shouldRunQualityChecks(env) {
    return FrontendPipeline.shouldRunQualityChecks(env)
}

def determineReleaseVersion(env) {
    return FrontendPipeline.determineReleaseVersion(this, env)
}

def getLatestFrontendTag() {
    return FrontendPipeline.getLatestFrontendTag(this)
}

def setupNodeEnvironment() {
    FrontendPipeline.setupNodeEnvironment(this)
}

def runLinting() {
    FrontendPipeline.runLinting(this)
}

def runTests() {
    FrontendPipeline.runTests(this)
}

def runSecurityAudit() {
    FrontendPipeline.runSecurityAudit(this)
}

def runBundleAnalysis() {
    FrontendPipeline.runBundleAnalysis(this)
}

def buildApplication() {
    FrontendPipeline.buildApplication(this)
}

def buildValidationImage(env, ecrRegistry, ecrRepository) {
    FrontendPipeline.buildValidationImage(this, env, ecrRegistry, ecrRepository)
}

def buildAndTagReleaseImage(env, releaseVersion, ecrRegistry, ecrRepository) {
    return FrontendPipeline.buildAndTagReleaseImage(this, env, releaseVersion, ecrRegistry, ecrRepository)
}

def pushTaggedImages(ecrRegistry, ecrRepository, releaseTags, awsRegion) {
    FrontendPipeline.pushTaggedImages(this, ecrRegistry, ecrRepository, releaseTags, awsRegion)
}

def createGitTag(imageTag) {
    FrontendPipeline.createGitTag(this, imageTag)
}

def getEnvironmentVariables() {
    return FrontendPipeline.getEnvironmentVariables()
}
