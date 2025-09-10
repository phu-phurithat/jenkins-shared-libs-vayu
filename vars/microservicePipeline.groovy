import devops.v1.*

def call(args) {
  // args only contain DEPLOYMENT_REPO, TRIGGER_TOKEN

  // Constants
  final String SONAR_HOST        = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
  final String SONAR_PROJECT_KEY = 'java'
  final String BUILDKIT_ADDR     = 'tcp://buildkit-buildkit-service.buildkit.svc.cluster.local:1234'
  final String REGISTRY          = 'harbor.phurithat.site'
  final String DOCKER_CONFIG     = '/root/.docker/config.json'
  final String REPO              = 'boardgame_1'
  //  final String APP_REPO          = args.DEPLOYMENT_REPO.replace('-helm-charts.git', '-app.git')
  final String COMPONENT_NAME    = args.DEPLOYMENT_REPO.tokenize('/').last().replace('-helm-charts.git', '')
  final String IMAGE_TAG         = env.BUILD_ID
  final String FULL_IMAGE        = "${REGISTRY}/${REPO}/${COMPONENT_NAME}:${IMAGE_TAG}"

  // ENV
  // env.TRIVY_BASE_URL = 'http://trivy.trivy.svc.cluster.local:4954'
  // env.DEFECTDOJO_BASE_URL = 'https://defectdojo.phurithat.site'
  // env.DOJO_KEY = 'defectdojo_api_key'
  // env.SONAR_TOKEN =  'sonar_token'
  // env.HARBOR_CRED = 'harbor-cred'
  // env.JENKINS_CRED = 'jenkins-cred'
  // env.GITLAB_NONPROD_KEY = 'gitlab-nonprod-key'
  // env.GITLAB_PROD_KEY = 'gitlab-prod-key'
  // env.HELM_PATH = 'vayu-helm'
  // env.HELM_NONPROD_REPO = 'https://gitlab.devopsnonprd.vayuktbcs/api/v4/projects/7410/packages/helm/stable'
  // env.OCP_NONPROD_AGENT = 'ocp-nonprod-agent'
  // env.OCP_PROD_AGENT = 'ocp-prod-agent'
  def config      = [:]
  // Helper classes
  def pt = new PodTemplate()
  def prep = new Preparer(args)
  def credManager = new CredManager()
  def builder = new Builder()
  def scanner = new SecurityScanner()
  def defectdojo = new DefectDojoManager()

  // ------------------- Prep on a controller/agent -------------------
  node('master') { // change label as needed
    // Basic input validation
    if (!args?.DEPLOYMENT_REPO) {
      error 'DEPLOYMENT_REPO is required'
    }

    stage('Checkout') {
      echo 'Checkout code from repository...'
      git url: args.DEPLOYMENT_REPO, branch: 'main'
    }

    stage('Read Configuration from /"config.yaml/"') {
      echo 'Getting Configuration'
      String configPath = "${env.WORKSPACE}/config.yaml"
      String configContent = readFile(file: configPath, encoding: 'UTF-8')

      if (configContent?.trim()) {
        config = readYaml(text: configContent)
      } else {
        error "Configuration file not found or empty at ${configPath}"
      }
      prep.injectConfig(config)
      credManager.globalENV()
      echo prep.getConfigSummary()
    }

    stage('Prepare Agent') {
      pt.injectConfig(config)
    }
  }

  // ------------------- Run inside Kubernetes podTemplate -------------------
  podTemplate(yaml: pt.toString()) {
    node(POD_LABEL) {
      stage('Checkout') {
        echo 'Checkout code from repository...'
        String appRepo = args.DEPLOYMENT_REPO.replace('-helm-charts.git', '-app.git')
        git url: appRepo, branch: 'main'
      }

      String language = config.build_tool.toLowerCase()
      echo "LANGUAGE = ${language}"

      stage('Build'){
        if(language=="maven"){
          container('maven'){
            builder.Compile(SONAR_TOKEN,SONAR_HOST,SONAR_PROJECT_KEY,language)
          }
        } else {
          if(language=='node.js' || language=='nodejs' || language=='node'){
            container('nodejs'){
              builder.Compile(SONAR_TOKEN,SONAR_HOST,SONAR_PROJECT_KEY,language)
            }
          }else if(language=='python'){
            container('python'){
              builder.Compile(SONAR_TOKEN,SONAR_HOST,SONAR_PROJECT_KEY,language)
            }
          }else if(language=='go' || language=='golang'){
            container('golang'){
              builder.Compile(SONAR_TOKEN,SONAR_HOST,SONAR_PROJECT_KEY,language)
            }
          }
        }
      }
      stage('Sorce Code Scan') {
        container('sonarscanner') {
          scanner.SorceCodeScan(SONAR_TOKEN, SONAR_HOST, SONAR_PROJECT_KEY, language)
        }
      }

      stage('Build Docker Image') {
        container('buildkit') {

          builder.BuildImage(BUILDKIT_ADDR,FULL_IMAGE,DOCKER_CONFIG,REGISTRY)
        }
      }
      stage('Dependencies Scan') {
        container('trivy') {

          scanner.DependenciesScan()
        }
      }
      stage('Image Scan') {
        container('trivy') {

          scanner.ImageScan(FULL_IMAGE)
        }
      }

      stage('Import report') {

        defectdojo.ImportReport()
      }

    }
  }
}

