package devops.v1

def getConfigSummary(args, config, properties, fullImageName) {
    String appName = args.DEPLOYMENT_REPO.tokenize('/').last().replace('.git', '')
    String microserviceRepo = config.kinds.deployments[args.MICROSERVICE_NAME]
    echo """
#> ══════════════════════════ Ⓘ CONFIG SUMMARY ═════════════════════════════════════
|
|  ❯ Application       : ${appName}
|  ❯ App ID            : ${config.app_id}
|  ❯ Microservice      : ${args.MICROSERVICE_NAME}
|  ❯ Microservice Repo : ${microserviceRepo}
|  ❯ Branch            : ${args.BRANCH}
|  ❯ Auto Deploy       : ${args.AUTO_DEPLOY in [true, 'true'] ? '✅' : '❌'}
|
#> ───────────────────────────────── BUILD ──────────────────────────────────────────
|
|  ❯ Enabled     : ${properties.enable in [true, 'true'] ? '✅' : '❌'}
|  ❯ Build Tool  : ${properties.build_tool}
|  ❯ Language    : ${properties.language}
|  ❯ Version     : ${properties.language_version}
|  ❯ Build Image : ${properties.build_image in [true, 'true'] ? '✅' : '❌'}
|  ❯ Full Image  : ${fullImageName}
|
#> ────────────────────────────── SECURITY SCAN ─────────────────────────────────────
|
|  ❯ Code Scan  : ${properties.security?.code in [true, 'true'] ? '✅' : '❌'}
|  ❯ Secret Scan: ${properties.security?.secret in [true, 'true'] ? '✅' : '❌'}
|  ❯ Dependency : ${properties.security?.dependency in [true, 'true'] ? '✅' : '❌'}
|  ❯ Image Scan : ${properties.security?.image in [true, 'true'] ? '✅' : '❌'}
|  ❯ EOL Check  : ${properties.security?.eol in [true, 'true'] ? '✅' : '❌'}
|  ❯ DAST       : ${properties.security?.dast.enable in [true, 'true'] ? '✅' : '❌'}
|     ❯ DAST Port : ${properties.security?.dast.port ?: 'N/A'}
|     ❯ DAST Paths: ${properties.security?.dast.paths ?: 'N/A'}
|
#> ───────────────────────────────── REGISTRY ───────────────────────────────────────
|
|  ❯ Non-Prod Registry : ${config.registry?.nonprod ?: 'N/A'}
|  ❯ Prod Registry     : ${config.registry?.prod ?: 'N/A'}
|
#> ──────────────────────────────── DEPLOYMENT ──────────────────────────────────────
|
|  ❯ Helm Version : ${config.helm?.version ?: 'N/A'}
|  ❯ Target Env   : ${args.TARGET_ENV ?: 'N/A'}
|  ❯ Namespace    : ${config.environments[args.TARGET_ENV]?.namespace ?: 'N/A'}
|  ❯ Credential   : ${config.environments[args.TARGET_ENV]?.cluster ?: 'N/A'}
|
#> ══════════════════════════════════════════════════════════════════════════════════

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
    if (!args.BRANCH || !(args.BRANCH instanceof String)) {
        errors << 'Invalid BRANCH or missing; must be a string.'
    }
    if (args.AUTO_DEPLOY in [true, 'true'] && !args?.TARGET_ENV) {
        errors << 'TARGET_ENV is required when AUTO_DEPLOY is enabled.'
    }

    if (errors) {
        error 'Invalid arguments: \n - ' + errors.join('\n - ')
    }
}

def validateConfig(config) {
    def errors = []

    // app_id
    if (!(config.app_id instanceof Integer)) {
        errors << 'app_id must be an integer'
    }

    // deployments
    if (!(config.kinds?.deployments instanceof Map)) {
        errors << 'kinds.deployments must exist and be a map'
    } else {
        config.kinds.deployments.each { name, url ->
            if (!(url instanceof String)) {
                errors << "Deployment '${name}' must have a Git URL string"
            } else if (!url.startsWith('https://github.com/')) {
                errors << "Deployment '${name}' has invalid repo URL: ${url}"
            }
        }
    }

    // environments
    if (!(config.environments instanceof Map)) {
        errors << 'environments must exist and be a map'
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
        errors << 'registry must exist and be a map'
    } else {
        ['nonprod', 'prod'].each { key ->
            if (!(config.registry[key] instanceof String)) {
                errors << "Registry '${key}' must exist"
            } else if (!config.registry[key].startsWith('harbor.')) {
                errors << "Registry '${key}' must point to Harbor: ${config.registry[key]}"
            }
        }
    }

    // helm
    if (!(config.helm instanceof Map)) {
        errors << 'helm must exist and be a map'
    } else {
        if (!(config.helm.version ==~ /\d+\.\d+\.\d+/)) {
            errors << 'helm.version must be semantic version (e.g., 1.0.0)'
        }
    }

    // Final check
    if (errors) {
        error '❌ Config validation failed:\n - ' + errors.join('\n - ')
    } else {
        echo '✅ Config validation passed!'
    }
}

def validateProperties(properties) {
    def errors = []

    def allowedProperties = [
        enable: [true, false, 'true', 'false'],
        build_tool: ['maven', 'gradle', 'npm', 'go', 'pip'],
        language: [
            java: ['8', '11', '17', '21'],
            node: ['14', '16', '18', '20', '22', '24'],
            go: ['1.18', '1.19', '1.20', '1.21', '1.22', '1.23', '1.24', '1.25'],
            python: ['3.11.4', '3.12.0'],
        ]
        build_image: [true, false, 'true', 'false'],
        security: [
            code: [true, false, 'true', 'false', null],
            secret: [true, false, 'true', 'false', null],
            dependency: [true, false, 'true', 'false', null],
            image: [true, false, 'true', 'false', null],
            eol: [true, false, 'true', 'false', null],
            dast: [
                enable: [true, false, 'true', 'false', null],
                port: Integer,
                paths: String
            ]
        ]

    ]
    // Validate top-level properties
    allowedProperties.each { key, allowedValues ->
        if (properties[key] == null) {
            errors << "Property '${key}' is required."
        } else if (key == 'language') {
            def lang = properties.language.toLowerCase()
            if (!allowedValues.containsKey(lang)) {
                errors << "Unsupported language '${properties.language}'. Supported: ${allowedValues.keySet().join(', ')}"
            } else if (!allowedValues[lang].contains(properties.language_version?.toString())) {
                errors << "Unsupported language_version '${properties.language_version}' for language '${lang}'. Supported: ${allowedValues[lang].join(', ')}"
            }
        } else if (key == 'security') {
            if (!(properties.security instanceof Map)) {
                errors << "Property 'security' must be a map."
            } else {
                allowedValues.each { secKey, secAllowedValues ->
                    if (secKey == 'dast') {
                        if (properties.security.dast) {
                            if (properties.security.dast.enable == null) {
                                errors << "Property 'security.dast.enable' is required."
                            } else if (!secAllowedValues.enable.contains(properties.security.dast.enable)) {
                                errors << "Property 'security.dast.enable' must be boolean."
                            } else if (properties.security.dast.enable in [true, 'true']) {
                                if (properties.security.dast.port == null) {
                                    errors << "Property 'security.dast.port' is required when DAST is enabled."
                                } else if (!(properties.security.dast.port instanceof Integer) || properties.security.dast.port <= 0) {
                                    errors << "Property 'security.dast.port' must be a positive integer."
                                }
                                if (!properties.security.dast.paths) {
                                    errors << "Property 'security.dast.paths' is required when DAST is enabled."
                                } else if (!(properties.security.dast.paths instanceof String)) {
                                    errors << "Property 'security.dast.paths' must be a string."
                                }
                            }
                        }
                    } else {
                        if (properties.security[secKey] == null) {
                            errors << "Property 'security.${secKey}' is required."
                        } else if (!secAllowedValues.contains(properties.security[secKey])) {
                            errors << "Property 'security.${secKey}' must be boolean."
                        }
                    }
                }
            }
        } else {
            if (!allowedValues.contains(properties[key])) {
                errors << "Property '${key}' has invalid value '${properties[key]}'. Allowed: ${allowedValues.join(', ')}"
            }
        }
    }
    if (errors) {
        error '❌ Properties validation failed:\n - ' + errors.join('\n - ')
    } else {
        echo '✅ Properties validation passed!'
    }
}
