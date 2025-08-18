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
      defaultValue: 'build-only',
      description: 'Pipeline mode'
    ),

    // Build
    string(name: 'REGISTRY_URL', defaultValue: '', description: 'Harbor registry URL'),
    string(name: 'IMAGE_NAME', defaultValue: '', description: 'Image name'),
    string(name: 'IMAGE_TAG', defaultValue: '', description: 'Image tag'),
    string(name: 'DOCKERFILE_PATH', defaultValue: 'Dockerfile', description: 'Path to Dockerfile(Default: Dockerfile)'),

    // Deploy
    
  ])
])

// Will hold prepared config across nodes
def prep = new ParamPreparer()

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