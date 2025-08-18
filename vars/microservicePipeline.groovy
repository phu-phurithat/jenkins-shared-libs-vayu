import devops.v1.ParamPreparer

def call(){
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
    echo 'Checkout code from repository...'
  }

  stage('Prepare Parameters') {
    echo 'Preparing parameters...'
    prep.validateParams()
  }

  stage('Prepare Agent') {
    echo 'Preparing agent for execution...'
  }
}

// ------------------- Run inside Kubernetes podTemplate -------------------
// podTemplate(yaml: podYaml) {
//   node(POD_LABEL) {

//     stage('Checkout') {
//       echo 'Checkout code from repository...'
//     }
//     stage('Build') {
//       echo 'Building Docker image...'
//     }
//     stage('Security Scan') {
//       echo 'Running security scan on Docker image...'
//     }
//     stage('Deploy') {
//       echo 'Deploying application to Kubernetes...'
//     }
//   }
// }
}