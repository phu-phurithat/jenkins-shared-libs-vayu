# Jenkins Shared Library Reference

This guide targets developers maintaining or adopting the Vayu Jenkins shared library. It covers the repository layout, expected configuration, pipeline lifecycle, and the helper classes that power each stage.

---

## 1. Quick Facts
- Library name in Jenkins: `shared-libs` (default branch `main`).
- Primary entrypoint: `vars/microservicePipeline.groovy`.
- Designed for containerised microservices deployed to Kubernetes via Helm.
- Integrates BuildKit, Harbor, SonarQube, Trivy, GitLeaks, and DefectDojo out of the box.

---

## 2. Repository Layout
```
jenkins-shared-libs-vayu/
├── vars/
│   └── microservicePipeline.groovy   # Pipeline entrypoint exposed to Jenkins jobs
├── src/devops/v1/
│   ├── Builder.groovy                # Build orchestration & image publishing
│   ├── CredManager.groovy            # Shared environment/credential defaults
│   ├── DefectDojoManager.groovy      # Pushes scan reports into DefectDojo
│   ├── DeployManager.groovy          # Helm lint/dry-run/deploy helpers
│   ├── GitManager.groovy             # Git commit/push utilities
│   ├── PodTemplate.groovy            # Dynamically assembles Kubernetes agent pods
│   ├── Preparer.groovy               # Validates inputs & renders summaries
│   └── SecurityScanner.groovy        # SonarQube and Trivy wrappers
├── docs/                             # Project documentation (this file, guides)
└── Jenkinsfile                       # Smoke test harness for the library
```

The `devops.v1` namespace signals the first stable API surface. Ship breaking changes in a new package (for example, `devops.v2`) so existing jobs remain unaffected.

---

## 3. Getting Started
1. **Register the library** under *Manage Jenkins → Configure System → Global Pipeline Libraries* with name `shared-libs` and default version `main`.
2. **Prepare a deployment repository** containing Helm charts and `config.yaml` (see §5.1).
3. **Ensure each microservice repository** contains `devops.properties.yaml` (see §5.2).
4. **Provision Jenkins credentials** referenced by `CredManager` (Harbor, GitLab, kubeconfigs, scan tokens, etc.).
5. **Call the pipeline** from an application Jenkinsfile:
   ```groovy
   @Library('shared-libs') _
   microservicePipeline(
     DEPLOYMENT_REPO: 'https://gitlab.example.com/app/deployment.git',
     TRIGGER_TOKEN: 'github-token-id',
     MICROSERVICE_NAME: 'catalog',
     BRANCH: 'feature/my-change',
     AUTO_DEPLOY: true,
     TARGET_ENV: 'uat'
   )
   ```

---

## 4. Pipeline Lifecycle
The entrypoint at `vars/microservicePipeline.groovy` orchestrates the entire flow:

1. **Validate arguments** (`Preparer.validateArguments`).
2. **Clone the deployment repo** and parse `config.yaml` (`Preparer.validateConfig`).
3. **Clone the microservice repo** and read `devops.properties.yaml` (`Preparer.getConfigSummary`).
4. **Generate a Kubernetes pod template** tailored to the stack (`PodTemplate.injectConfig`).
5. **Execute stages inside the pod**. Build, scan, and (optionally) deploy via Helm using the injected containers.

```groovy
import devops.v1.*

def call(args) {
  node('master') {
    prep.validateArguments(args)
    dir('deployment') {
      git url: args.DEPLOYMENT_REPO, branch: args.BRANCH ?: 'main'
      config = readYaml text: readFile('config.yaml')
      prep.validateConfig(config)
    }
    dir('src') {
      git url: config.kinds.deployments[args.MICROSERVICE_NAME], branch: 'main'
      properties = readYaml text: readFile('devops.properties.yaml')
      prep.getConfigSummary(args, config, properties)
    }
    pt.injectConfig(properties, args)
  }

  podTemplate(yaml: pt.toString()) {
    node(POD_LABEL) {
      git url: args.DEPLOYMENT_REPO, branch: args.BRANCH
      dm.deployHelm(...)
    }
  }
}
```

---

## 5. Required Configuration Files

### 5.1 Deployment Repository (`config.yaml`)
```yaml
app_id: 101
registry:
  nonprod: harbor.example.com/nonprod
  prod: harbor.example.com/prod
helm:
  version: 3.15.4
kinds:
  deployments:
    catalog: https://github.com/your-org/catalog-service.git
    user: https://github.com/your-org/user-service.git
environments:
  dev:
    cluster: ocp-nonprod-agent
    namespace: app-dev
  prod:
    cluster: ocp-prod-agent
    namespace: app-prod
```
`Preparer.validateConfig` enforces Harbor registries, semantic Helm versions, and well-formed environment maps before any workload executes.

### 5.2 Microservice Repository (`devops.properties.yaml`)
```yaml
enable: true
build_tool: maven
language: java
language_version: 17
build_image: true
security:
  code: true
  dependency: true
  image: true
  secret: true
  dast:
    enable: false
```
These values drive tool selection in `PodTemplate` and populate the summary banner printed by `Preparer.getConfigSummary`.

---

## 6. Component Catalogue (devops.v1)
Each helper class keeps the pipeline modular. The summaries below highlight the methods most teams interact with.

### 6.1 `Preparer` – Validation & Run Summary
```groovy
def validateArguments(args) {
  if (!args?.DEPLOYMENT_REPO) errors << 'DEPLOYMENT_REPO is required.'
  if (!args?.TRIGGER_TOKEN)   errors << 'TRIGGER_TOKEN is required.'
  if (!args?.MICROSERVICE_NAME) errors << 'MICROSERVICE_NAME is required.'
  if (!args.BRANCH || !(args.BRANCH instanceof String)) errors << 'Invalid BRANCH or missing; must be a string.'
  if (args.AUTO_DEPLOY in [true, 'true'] && !args?.TARGET_ENV) errors << 'TARGET_ENV is required when AUTO_DEPLOY is enabled.'
  ...
}
```
`validateConfig` sanity-checks `config.yaml`, and `getConfigSummary` prints the consolidated build/scan/deploy configuration.

### 6.2 `PodTemplate` – Dynamic Agent Construction
```groovy
PodTemplate injectConfig(config, args) {
  switch ((config.build_tool ?: '').toLowerCase()) {
    case 'maven':  addMaven();  break
    case 'nodejs': addNode();   break
    case 'go':     addGo();     break
    case 'python': addPython(); break
  }
  if (config.build_image in [true, 'true']) {
    addBuildkit()
    addHarborSecretVolume()
  }
  if (config.security?.code in [true, 'true'])   addSonarScanner()
  if (config.security?.secret in [true, 'true']) addGitLeaks()
  if (args.AUTO_DEPLOY in [true, 'true']) {
    addKubectl()
    addHelm()
  }
  this
}
```
The generated YAML is serialised via SnakeYAML and fed into Jenkins’ `podTemplate` step.

### 6.3 `Builder` – Compilation & Image Publishing
```groovy
def Compile(build_tool) {
  if (build_tool == 'maven') {
    container('maven') { sh 'mvn clean install verify' }
  } else if (build_tool == 'npm') {
    container('nodejs') {
      sh '''
        npm install
        npm run build
        npm test
      '''
    }
  } else if (build_tool == 'pip') {
    container('python') { sh 'pip install -r requirements.txt' }
  } else if (build_tool == 'go') {
    container('golang') {
      sh '''
        go mod tidy
        go build -buildvcs=false
      '''
    }
  } else if (build_tool == 'gradle') {
    container('gradle') {
      sh 'chmod +x gradlew'
      sh '''
        ./gradlew googleJavaFormat
        ./gradlew build --scan downloadRepos installDist
      '''
    }
  }
}

def BuildImage(fullImage) {
  String registryHost = fullImage.tokenize('/')[0]
  sh '''
    buildctl \
      --addr ${BUILDKIT_ADDR} \
      build \
      prune \
      --frontend dockerfile.v0 \
      --local context=. \
      --local dockerfile=. \
      --output type=image,name=${fullImage},push=true,registry.config=${DOCKER_CONFIG} \
      --export-cache type=inline \
      --import-cache type=registry,ref=${registryHost}
  '''
}
```
`Compile` selects the container matching the declared build tool, while `BuildImage` runs BuildKit and pushes to the target registry.

### 6.4 `SecurityScanner` – Quality & Vulnerability Checks
```groovy
def SorceCodeScan(key, name, language) {
  withCredentials([string(credentialsId: SONAR_TOKEN, variable: 'SONAR_TOKEN')]) {
    def response = readJSON text: sh(
      script: "curl -u ${SONAR_TOKEN}: ${SONAR_BASE_URL}/api/projects/search?projects=${key}",
      returnStdout: true
    )
    if (!response.components) {
      sh "curl -u ${SONAR_TOKEN}: -X POST \"${SONAR_BASE_URL}/api/projects/create?project=${key}&name=${name}\""
    }
    container('sonarqube') {
      sh '''
        sonar-scanner \
          -Dsonar.projectKey=${key} \
          -Dsonar.sources=... \
          -Dsonar.host.url=${SONAR_BASE_URL} \
          -Dsonar.login=${SONAR_TOKEN}
      '''
    }
    sh "curl -s -u \"${SONAR_TOKEN}:\" \"${SONAR_BASE_URL}/api/issues/search?projectKey=${key}\" -o sonarqube-report.json"
  }
}
```
`DependenciesScan` and `ImageScan` call Trivy in CycloneDX mode against the workspace or container image respectively.

### 6.5 `DefectDojoManager` – Findings Aggregation
```groovy
def ImportReport(productName, engagementName) {
  withCredentials([string(credentialsId: DOJO_KEY, variable: 'DOJO_KEY')]) {
    def productJson = readJSON text: sh("curl .../api/v2/products/?name=${productName} ...", returnStdout: true)
    def productId = productJson?.count ? productJson.results[0].id : null
    if (!productId) {
      productId = readJSON(text: sh("curl .../api/v2/products/ (POST payload)", returnStdout: true)).id
    }

    def engagementJson = readJSON text: sh("curl .../api/v2/engagements/?name=${engagementName}&product=${productId} ...", returnStdout: true)
    def engagementId = engagementJson?.count ? engagementJson.results[0].id : null
    if (!engagementId) {
      engagementId = readJSON(text: sh("curl .../api/v2/engagements/ (POST payload)", returnStdout: true)).id
    }

    ['sonarqube-report.json', 'trivy_deps.json', 'trivy_image.json'].each { report ->
      sh "curl .../api/v2/reimport-scan/ -F file=@${report} -F product_name=${productName} -F engagement_name=${engagementName}"
    }
  }
}
```
The helper wraps the DefectDojo REST API, ensuring products and engagements exist before uploading SonarQube and Trivy reports.

### 6.6 `DeployManager` – Helm Operations
```groovy
def deployHelm(args) {
  container('helm') {
    stage('Helm Lint & Dry-Run') {
      sh "helm lint ${args.helmPath}"
      withCredentials([file(credentialsId: args.kubeconfigCred, variable: 'KUBECONFIG_FILE')]) {
        sh '''
          export KUBECONFIG=${KUBECONFIG_FILE}
          helm upgrade --install ${args.helmRelease} ${args.helmPath} \
            --namespace ${args.namespace} \
            --create-namespace --dry-run=client
        '''
      }
    }
    stage('Deploy via Helm') {
      withCredentials([file(credentialsId: args.kubeconfigCred, variable: 'KUBECONFIG_FILE')]) {
        sh '''
          export KUBECONFIG=${KUBECONFIG_FILE}
          helm upgrade --install ${args.helmRelease} ${args.helmPath} \
            --namespace ${args.namespace} \
            --create-namespace --wait --timeout 5m
        '''
      }
    }
  }
}
```
Both stages provide clear failure messages to simplify troubleshooting.

### 6.7 `GitManager` – Commit & Push Utilities
```groovy
def pushChanges(String message, String repoUrl, String credentialsId) {
  withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
    sh '''
      git config --global user.email "auto@pipeline.jenkins.io"
      git config --global user.name "Jenkins CI"
      git remote set-url origin https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/phu-phurithat/microservices-demo.git
      git add .
      git commit -m "${message}" || echo "No changes to commit"
      git push origin main
    '''
  }
}
```
> ⚠️ The remote URL is currently hard-coded. Consider using the `repoUrl` argument when adapting this helper for other repositories.

`setupHelm()` (same file) refreshes Helm repositories inside the `helm` container, pointing `stable` at `HELM_NONPROD_REPO`.

### 6.8 `CredManager` – Shared Environment Defaults
`CredManager.globalENV()` documents the environment variables and credential IDs the library expects (`HARBOR_CRED`, `GITLAB_NONPROD_KEY`, `OCP_NONPROD_AGENT`, etc.). Calling it at pipeline start keeps these defaults in one place.

---

## 7. Execution Timeline
1. **Controller node (`node('master')`)** – validate inputs, clone repositories, print the configuration banner, and build the pod template.
2. **Ephemeral Kubernetes pod** – re-checkout the deployment repo, execute build/test/scan stages, and deploy via Helm when enabled.
3. **Post stages (optional)** – use `GitManager.pushChanges` to update manifests or `DefectDojoManager.ImportReport` to upload findings.

---

## 8. Adoption Checklist
- [ ] Register the shared library in Jenkins (`name: shared-libs`, default `main`).
- [ ] Populate deployment repositories with compliant `config.yaml` files and Helm charts.
- [ ] Populate microservice repositories with accurate `devops.properties.yaml` files.
- [ ] Provision Jenkins credentials referenced by `CredManager` and `SecurityScanner`.
- [ ] Verify DefectDojo projects exist or allow the pipeline to create them via `DefectDojoManager`.
- [ ] Update application Jenkinsfiles to call `microservicePipeline(...)` with correct arguments.

With these pieces in place, teams gain a consistent, security-aware CI/CD pipeline without duplicating orchestration logic across services.
