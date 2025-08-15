import devops.v1.ParamPreparer

// ------- Define UI Parameters (Scripted uses `properties(...)`) -------
properties([
  parameters([
    // Core
    string(name: 'REPO_URL', defaultValue: '', description: 'Git repository URL'),
    choice(name: 'ENV', choices: ['dev', 'staging', 'prod'], description: 'Target environment'),
    choice(
      name: 'PIPELINE_MODE',
      choices: ['build-only', 'deploy-only', 'full-cicd'],
      description: 'Pipeline mode'
    )
])

// Will hold prepared config across nodes
def prep = new ParamPreparer()
def podYaml = ''

// ------------------- Prep on a controller/agent -------------------
node('master') { // change label as needed
  stage('Checkout') {
    
  }

  stage('Prepare Parameters') {
    
  }

  stage('Prepare Agent') {

  }
}

// ------------------- Run inside Kubernetes podTemplate -------------------
podTemplate(yaml: podYaml) {
  node(POD_LABEL) {

    stage('Checkout') {
    }
    stage('Build') {
    }
    stage('Security Scan') {
    }
    stage('Deploy') {
    }
  }
}