import devops.v1.*

def call(args) {
  // args only contain DEPLOYMENT_REPO, TRIGGER_TOKEN, MICROSERVICE_NAME, BRANCH (optional), TARGET_ENV

  // Constants
  final String SONAR_HOST        = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
  final String SONAR_PROJECT_KEY = 'java'
  final String BUILDKIT_ADDR     = 'tcp://buildkit-buildkit-service.buildkit.svc.cluster.local:1234'
  final String REGISTRY          = 'harbor.phurithat.site'
  final String DOCKER_CONFIG     = '/root/.docker/config.json'

  // ENV
  env.TRIVY_BASE_URL = 'http://trivy.trivy-system.svc.cluster.local:4954'
  env.DEFECTDOJO_BASE_URL = 'http://defectdojo-django.defectdojo.svc.cluster.local'
  env.DOJO_KEY = 'defect-dojo-key'
  env.HARBOR_CRED = 'harbor-cred'
  env.JENKINS_CRED = 'jenkins-cred'
  env.GITLAB_NONPROD_KEY = 'gitlab-nonprod-key'
  env.GITLAB_PROD_KEY = 'gitlab-prod-key'
  env.HELM_PATH = 'vayu-helm'
  env.HELM_NONPROD_REPO = 'https://gitlab.devopsnonprd.vayuktbcs/api/v4/projects/7410/packages/helm/stable'
  env.OCP_NONPROD_AGENT = 'ocp-nonprod-agent'
  env.OCP_PROD_AGENT = 'ocp-prod-agent'

  // Helper classes
  def pt = new PodTemplate()
  def prep = new Preparer()
  def dm = new DeployManager()
  def config = [:]
  def properties = [:]
  String microserviceRepo = ''
  String fullImageName = ''

  // ------------------- Prep on a controller/agent -------------------
  node('master') { // change label as needed
    // Basic input validation
    prep.validateArguments(args)

    dir('deployment') {
      stage('Checkout Deployment Repository') {
        git url: args.DEPLOYMENT_REPO, branch: args.BRANCH ?: 'main'
      }

      stage('Read Configuration from "config.yaml"') {
        String configPath = './config.yaml'
        String configContent = readFile(file: configPath, encoding: 'UTF-8')

        if (configContent?.trim()) {
          config = readYaml(text: configContent)
        // echo config.toString()
        } else {
          error "Configuration file not found or empty at ${configPath}"
        }

        prep.validateConfig(config)
        prep.getConfigSummary(args, config)
      }
    }

    dir('src') {
      stage('Checkout Microservice Code for Preparation') {
        microserviceRepo = config.kinds.deployments[args.MICROSERVICE_NAME]
        if (!microserviceRepo) {
          error "Microservice '${args.MICROSERVICE_NAME}' not found in configuration."
        }

        echo "Cloning microservice repository: ${microserviceRepo}"
        git url: microserviceRepo, branch: 'main'
      }

      stage('Read Properties from "devops.properties.yaml"') {
        String propertiesPath = './devops.properties.yaml'
        if (fileExists(propertiesPath)) {
          def fileContents = readFile(file: propertiesPath, encoding: 'UTF-8')
          properties = readYaml(text: fileContents)
        } else {
          error "Properties file not found at ${propertiesPath}"
        }
      }
    }

      stage('Prepare Agent') {
        pt.injectConfig(properties, args)
      }
  }

  // ------------------- Run inside Kubernetes podTemplate -------------------
  podTemplate(yaml: pt.toString()) {
    node(POD_LABEL) {
      dir('deployment') {

        stage('Checkout Deployment Repository') {
          // Re-clone to ensure clean state inside pod
          git url: args.DEPLOYMENT_REPO, branch: args.BRANCH
        }

        String kubeconfigCred = config.environments[args.TARGET_ENV].cluster.toLowerCase()
        String namespace    = config.environments[args.TARGET_ENV].namespace.toLowerCase()
        String helmPath   = './helm-chart'   // path where your Helm chart lives
        String helmRelease = args.DEPLOYMENT_REPO.tokenize('/').last().replace('.git', '').toLowerCase()

        dm.deployHelm(
          kubeconfigCred: kubeconfigCred,
          namespace: namespace,
          helmPath: helmPath,
          helmRelease: helmRelease
        )
      }
      
    }
  }
}
