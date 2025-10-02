import devops.v1.*

def call(args) {
  // args only contain:
  // DEPLOYMENT_REPO, TRIGGER_TOKEN, MICROSERVICE_NAME, BRANCH (optional),
  // AUTO_DEPLOY, TARGET_ENV, IMAGE_TAG (optional)

  // Helper classes
  def pt          = new PodTemplate()
  def credManager = new CredManager()
  def builder     = new Builder()
  def scanner     = new SecurityScanner()
  def defectdojo  = new DefectDojoManager()
  def prep        = new Preparer()
  def dm          = new DeployManager()
  def gitm        = new GitManager()

  // Vars
  def config            = [:]
  def properties        = [:]
  String appName        = ''
  String microserviceRepo = ''
  String fullImageName  = ''
  String imageTag       = ''
  def fullPath          = ''
  def component         = ''
  def productName       = '' // "${fullPath}-${component}"
  def engagementName    = '' // "${component}:${imageTag}"
  def sonarProjectKey   = '' // "{fullPath}-{Component}"
  def sonarProjectName  = ''


  // ------------------- Prep on a controller/agent -------------------
  node('master') {
    prep.validateArguments(args)
    credManager.globalENV()

    appName = args.DEPLOYMENT_REPO.tokenize('/').last().replace('.git', '').toLowerCase()

    dir('deployment') {
      stage('Checkout Deployment Repository') {
        git url: args.DEPLOYMENT_REPO, branch: targetBranch
      }

      stage('Read Configuration from "config.yaml"') {
        String configPath    = './config.yaml'
        String configContent = readFile(file: configPath, encoding: 'UTF-8')
        if (configContent?.trim()) {
          config = readYaml(text: configContent)
        } else {
          error "Configuration file not found or empty at ${configPath}"
        }
        prep.validateConfig(config)
      }
    }

    dir('src') {
      stage('Checkout Microservice Code for Preparation') {
        // Null-safe path to repo in config
        microserviceRepo = config.kinds?.deployments?.get(args.MICROSERVICE_NAME)
        if (!microserviceRepo) {
          error("Microservice '${args.MICROSERVICE_NAME}' not found in configuration.")
        }

        def matcher = (microserviceRepo =~ /github\.com\/(.*)\.git/)
        if (!matcher.find()) {
          error("Could not parse repo URL: ${microserviceRepo}")
        }

        fullPath         = matcher.group(1)
        component        = fullPath.tokenize('/')[-1].replace('.git', '')
        fullPath         = fullPath.replace('/', '-')
        // imageTag placeholder until read properties (we'll set real tag after)
        imageTag         = args.IMAGE_TAG ?: 'latest'
        productName      = "${fullPath}-${component}"
        engagementName   = "${component}:${imageTag}"
        sonarProjectKey  = "${fullPath}-${component}"
        sonarProjectName = sonarProjectKey

        echo "fullPath:${fullPath} component:${component} Product:${productName}"
        echo "Engagement:${engagementName}"

        echo "Cloning microservice repository: ${microserviceRepo}"
        git url: microserviceRepo, branch: targetBranch
      }

      stage('Read Properties from "devops.properties.yaml"') {
        String propertiesPath = './devops.properties.yaml'
        if (!fileExists(propertiesPath)) {
          error "Properties file not found at ${propertiesPath}"
        }
        properties = readYaml(text: readFile(file: propertiesPath, encoding: 'UTF-8'))

        // Prefer explicit IMAGE_TAG, else git short SHA, else 'latest'
        imageTag = args.IMAGE_TAG ?: (
          sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
        ) ?: 'latest'

        fullImageName = (args.TARGET_ENV in ['prod', 'production']) ?
          "${config.registry.prod}/${args.MICROSERVICE_NAME}:${imageTag}" :
          "${config.registry.nonprod}/${args.MICROSERVICE_NAME}:${imageTag}"

        // refresh engagement now that tag is final
        engagementName = "${component}:${imageTag}"

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
        git url: microserviceRepo, branch: targetBranch
      }

      String build_tool = properties.build_tool?.toLowerCase()
      String language   = properties.language?.toLowerCase() ?: 'generic'
      echo "Using ${build_tool} as Builder"

      stage('Build') {
        builder.Compile(build_tool)
      }

      // Dynamic parallel stages (conditioned by properties.security.*)
      def parallelStages = getParallelStages(
        properties.security ?: [:],
        scanner,
        builder,
        sonarProjectKey,
        sonarProjectName,
        language,
        fullImageName
      )

      if (parallelStages && !parallelStages.isEmpty()) {
        parallel parallelStages, failFast: true
      } else {
        echo "No security stages enabled"
      }

      stage('Image Scan') {
        container('trivy') {
          scanner.ImageScan(fullImageName)
        }
      }

      stage('Import report') {
        defectdojo.ImportReport(productName, engagementName)
      }

      if (args.AUTO_DEPLOY in [true, 'true']) {
        dir('deployment') {
          stage('Checkout Deployment Repository') {
            git url: args.DEPLOYMENT_REPO, branch: targetBranch
          }

          String kubeconfigCred = config.environments[args.TARGET_ENV].cluster.toLowerCase()
          String namespace      = config.environments[args.TARGET_ENV].namespace.toLowerCase()
          String valuePath      = "./deployments/${args.TARGET_ENV}/${args.MICROSERVICE_NAME}.yaml"
          String helmRelease    = args.MICROSERVICE_NAME

          dm.updateHelmValuesFile(valuePath, fullImageName)
          gitm.setupHelm()

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
            error "Deployment to ${args.TARGET_ENV} failed."
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

private def getParallelStages(
  def security,
  def scanner,
  def builder,
  String sonarProjectKey,
  String sonarProjectName,
  String language,
  String fullImageName
) {
  def stages = [:]

  if (security?.code in [true, 'true']) {
    stages["Source Code Scan"] = {
      container('sonarqube') {
        // NOTE: If your method name is actually SourceCodeScan, rename below.
        scanner.sourceCodeScan(sonarProjectKey, sonarProjectName, language)
      }
    }
  }

  if (security?.dependency in [true, 'true']) {
    stages["Dependencies Scan"] = {
      container('trivy') {
        scanner.dependenciesScan()
      }
    }
  }

  if (security?.image in [true, 'true']) {
    stages["Build Docker Image"] = {
      container('buildkit') {
        builder.buildImage(fullImageName)
      }
    }
  }

  return stages
}
