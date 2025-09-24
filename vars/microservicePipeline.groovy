import devops.v1.*

def call(args) {
  // args only contain
  // DEPLOYMENT_REPO, TRIGGER_TOKEN, MICROSERVICE_NAME, BRANCH (optional), AUTO_DEPLOY, TARGET_ENV
  // IMAGE_TAG (optional)

  // Constants
  final String SONAR_HOST        = 'http://sonarqube-sonarqube.sonarqube.svc.cluster.local:9000'
  final String SONAR_PROJECT_KEY = 'java'
  final String BUILDKIT_ADDR     = 'tcp://buildkit-buildkit-service.buildkit.svc.cluster.local:1234'
  final String DOCKER_CONFIG     = '/root/.docker/config.json'
  

  // Helper classes
  def pt = new PodTemplate()
  def credManager = new CredManager()
  def builder = new Builder()
  def scanner = new SecurityScanner()
  def defectdojo = new DefectDojoManager()
  def prep = new Preparer()
  def dm = new DeployManager()
  def gitm = new GitManager()

  // Variables
  def config = [:]
  def properties = [:]
  String appName = ''
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

    appName = args.DEPLOYMENT_REPO.tokenize('/').last().replace('.git', '').toLowerCase()

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
        def matcher = (microserviceRepo =~ /github\.com\/(.*)\.git/)
        if (matcher.find()) {
        fullPath = matcher.group(1)
        component = fullPath.tokenize('/')[-1].replace('.git', '')
        echo "fullPath:${fullPath} component: ${component} Product: ${fullPath}-${component}"
        echo  "and Engagement: ${component}:${imageTag}"
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

        // imageTag = args.IMAGE_TAG ?: sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
        imageTag = 'latest'
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
        builder.Compile(build_tool)
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

      if (args.AUTO_DEPLOY in [true, 'true'] ) {
        dir('deployment') {
          stage('Checkout Deployment Repository') {
            // Re-clone to ensure clean state inside pod
            git url: args.DEPLOYMENT_REPO, branch: args.BRANCH
          }

          String kubeconfigCred = config.environments[args.TARGET_ENV].cluster.toLowerCase()
          String namespace    = config.environments[args.TARGET_ENV].namespace.toLowerCase()
          String valuePath   = "./deployments/${args.TARGET_ENV}/${args.MICROSERVICE_NAME}.yaml"   // path where your Helm chart lives
          String helmRelease = args.DEPLOYMENT_REPO.tokenize('/').last().replace('.git', '').toLowerCase()

          // Update Helm values with the new image
          dm.updateHelmValuesFile(valuePath, fullImageName)

          // Setup Helm (remove default stable repo and add ours)
          gitm.setupHelm()

          // Deploy via Helm if auto-deploy is enabled
          boolean isDeploySuccess = false
          try {
            dm.deployHelm(
              kubeconfigCred: kubeconfigCred,
              namespace: namespace,
              valuePath: valuePath,
              helmRelease: helmRelease
            )
            isDeploySuccess = true
          } catch (Exception e) {
            isDeploySuccess = false
            echo "Deployment to ${args.TARGET_ENV} failed. Attempting rollback..."
            dm.rollbackHelm(helmRelease, namespace, kubeconfigCred)
          }

          if (isDeploySuccess) {
            echo "Deployment to ${args.TARGET_ENV} successful."
            gitm.pushChanges(
              "Update ${args.MICROSERVICE_NAME} manifest for ${args.TARGET_ENV}",
              args.DEPLOYMENT_REPO,
              env.GITHUB_CRED
            )
          }
        }
      } else {
        echo 'Auto-deploy is disabled. Skipping deployment step.'
      }
    }
  }
// ------------------- End of podTemplate -------------------
}
