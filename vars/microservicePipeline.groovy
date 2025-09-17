import devops.v1.*

def call(args) {
  // args only contain DEPLOYMENT_REPO, TRIGGER_TOKEN, MICROSERVICE_NAME, BRANCH (optional), AUTO_DEPLOY, TARGET_ENV

  // Constants
  final String SONAR_HOST        = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
  final String SONAR_PROJECT_KEY = 'java'
  final String BUILDKIT_ADDR     = 'tcp://buildkit-buildkit-service.buildkit.svc.cluster.local:1234'
  final String DOCKER_CONFIG     = '/root/.docker/config.json'
  

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
  // Helper classes
  def pt = new PodTemplate()
  def credManager = new CredManager()
  def builder = new Builder()
  def scanner = new SecurityScanner()
  def defectdojo = new DefectDojoManager()
  def prep = new Preparer()
  def dm = new DeployManager()
  def config = [:]
  def properties = [:]
  String microserviceRepo = ''
  String fullImageName = ''
  String imageTag = 'latest'
  def fullPath = ''
  def component = ''

  // ------------------- Prep on a controller/agent -------------------
  node('master') { // change label as needed
    // Basic input validation
    prep.validateArguments(args)
    credManager.globalENV()

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
      }
    }

    dir('src') {
      stage('Checkout Microservice Code for Preparation') {
        microserviceRepo = config.kinds.deployments[args.MICROSERVICE_NAME]
         if (microserviceRepo) {
        def matcher = (microserviceRepo =~ /github\.com\/(.*)/)
        if (matcher.find()) {
        fullPath = matcher.group(1)
        component = fullPath.tokenize('/')[-1].replace('.git', '')
        echo "fullPath:${fullPath} component: ${component} Product: ${fullPath}+"-"+" "${component}"
        echo  "and Engagement: ${component}"+":"+"${imageTag}"
    } else {
        error("Could not parse repo URL: ${microserviceRepo}")
    }
} else {
    error("Microservice '${args.MICROSERVICE_NAME}' not found in configuration.")
}
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

        fullImageName = args.TARGET_ENV in ['prod', 'production'] ?
        "${config.registry.prod}/${args.MICROSERVICE_NAME}:${imageTag}" :
        "${config.registry.nonprod}/${args.MICROSERVICE_NAME}:${imageTag}"
        prep.validateProperties(properties)
        prep.getConfigSummary(args, config, properties, fullImageName)
      }
    }

      stage('Prepare Agent') {
        pt.injectConfig(properties, args)
      }
  }

  // ------------------- Run inside Kubernetes podTemplate -------------------
  podTemplate(yaml: pt.toString()) {
    node(POD_LABEL) {
      stage('Checkout Source Code Repository') {
        // Re-clone to ensure clean state inside pod
        git url: microserviceRepo, branch: args.BRANCH
      }

      String build_tool = properties.build_tool.toLowerCase()
      String language = properties.language.toLowerCase()
      echo "build_tool = ${build_tool}"

      stage('Build') {
        builder.Compile(SONAR_TOKEN, SONAR_HOST, SONAR_PROJECT_KEY, build_tool)
      }

      stage('Sorce Code Scan') {
        container('sonarqube') {
          scanner.SorceCodeScan(SONAR_TOKEN, SONAR_HOST, SONAR_PROJECT_KEY, language)
        }
      }

      stage('Build Docker Image') {
        container('buildkit') {
          builder.BuildImage(BUILDKIT_ADDR, fullImageName, DOCKER_CONFIG)
        }
      }
      stage('Dependencies Scan') {
        container('trivy') {
          scanner.DependenciesScan()
        }
      }
      stage('Image Scan') {
        container('trivy') {
          scanner.ImageScan(fullImageName)
        }
      }

      stage('Import report') {
        defectdojo.ImportReport(fullPath, imageTag,component)
      }

      if (args.AUTO_DEPLOY in [true, 'true']) {
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
}
