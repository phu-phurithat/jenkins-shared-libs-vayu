package devops.v1

def getConfigSummary(args, config) {

  String appName = args.DEPLOYMENT_REPO.tokenize('/').last().replace('.git','')
  String microserviceRepo = config.kinds.deployments[args.MICROSERVICE_NAME]
  echo """
#> ═════════════════════ Ⓘ Configuration Summary ═══════════════════════════════════
|  ❯ Application : ${appName}
|  ❯ App ID     : ${config.app_id}
|  ❯ Microservice : ${args.MICROSERVICE_NAME}
|  ❯ Microservice Repo : ${microserviceRepo}
#> ──────────────────────────────────────────────────────────────────────────────────
"""
  }

def validateArguments(args) {
  def errors = []

  if (!args?.DEPLOYMENT_REPO) {
    errors << 'DEPLOYMENT_REPO is required.'
  }
  if (!args?.TRIGGER_TOKEN) {
    errors << 'TRIGGER_TOKEN is required.'
  }
  if (!args?.MICROSERVICE_NAME) {
    errors << 'MICROSERVICE_NAME is required.'
  }
  
  if (errors) {
    error "Invalid arguments: \n - " + errors.join("\n - ")
  }
}

def validateConfig(config) {
    def errors = []

    // app_id
    if (!(config.app_id instanceof Integer)) {
        errors << "app_id must be an integer"
    }

    // deployments
    if (!(config.kinds?.deployments instanceof Map)) {
        errors << "kinds.deployments must exist and be a map"
    } else {
        config.kinds.deployments.each { name, url ->
            if (!(url instanceof String)) {
                errors << "Deployment '${name}' must have a Git URL string"
            } else if (!url.startsWith("https://github.com/")) {
                errors << "Deployment '${name}' has invalid repo URL: ${url}"
            }
        }
    }

    // environments
    if (!(config.environments instanceof Map)) {
        errors << "environments must exist and be a map"
    } else {
        config.environments.each { env, details ->
            if (!(details instanceof Map)) {
                errors << "Environment '${env}' must be a map"
            } else {
                if (details.cluster && !details.namespace) {
                    errors << "Environment '${env}' must have namespace when cluster is defined"
                }
                if (details.endpoint && !details.credential) {
                    errors << "Environment '${env}' with endpoint must also define credential"
                }
            }
        }
    }

    // registry
    if (!(config.registry instanceof Map)) {
        errors << "registry must exist and be a map"
    } else {
        ["nonprod", "prod"].each { key ->
            if (!(config.registry[key] instanceof String)) {
                errors << "Registry '${key}' must exist"
            } else if (!config.registry[key].startsWith("harbor.")) {
                errors << "Registry '${key}' must point to Harbor: ${config.registry[key]}"
            }
        }
    }

    // helm
    if (!(config.helm instanceof Map)) {
        errors << "helm must exist and be a map"
    } else {
        if (!(config.helm.version ==~ /\d+\.\d+\.\d+/)) {
            errors << "helm.version must be semantic version (e.g., 1.0.0)"
        }
    }

    // Final check
    if (errors) {
        error "❌ Config validation failed:\n - " + errors.join("\n - ")
    } else {
        echo "✅ Config validation passed!"
    }
}
