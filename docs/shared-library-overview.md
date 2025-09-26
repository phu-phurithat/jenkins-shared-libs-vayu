# Jenkins Shared Library Reference

This document explains how the Vayu Jenkins shared library is organized, how its microservice pipeline works, and what teams need to provide in order to reuse it across projects.

---

## 1. Library Purpose
- Provide a single reusable pipeline (`microservicePipeline`) that standardizes CI/CD workflows for containerized microservices.
- Encapsulate tooling integrations (BuildKit, Harbor, SonarQube, Trivy, GitLeaks, Helm, Kubernetes) so application pipelines only pass high-level parameters.
- Ensure every deployment follows the same validation, security scanning, image build, and Helm release process.

---

## 2. High-Level Architecture
```
jenkins-shared-libs-vayu/
├── vars/                    # Global pipeline steps callable from Jenkinsfiles
│   └── microservicePipeline.groovy
├── src/devops/v1/           # Groovy support classes
│   ├── PodTemplate.groovy    # Dynamically builds Kubernetes agent pod specs
│   ├── Preparer.groovy       # Validates inputs & prints run summary
│   ├── DeployManager.groovy  # Handles Helm lint + deploy stages
│   └── CredManager.groovy    # Central place for shared credential IDs
├── docs/                    # Reference documentation (this file, usage, structure)
└── Jenkinsfile               # Example for testing the library itself
```

Key points:
- Only code in `vars/` is exposed directly to pipelines. Everything else should live in `src/` classes for reuse and testability.
- `devops.v1` namespaces allow versioning; breaking changes can go into `devops.v2` without affecting existing jobs.

---

## 3. Entry Point: `microservicePipeline`
Defined in `vars/microservicePipeline.groovy:1`, this step orchestrates the full release flow. Parameters are passed as a map when invoking the library:

```groovy
microservicePipeline(
  DEPLOYMENT_REPO: 'https://gitlab.example.com/app/deployment.git',
  TRIGGER_TOKEN: 'github-token-id',
  MICROSERVICE_NAME: 'catalog',
  BRANCH: 'feature/my-change',
  AUTO_DEPLOY: true,
  TARGET_ENV: 'uat'
)
```

Parameter expectations:
- `DEPLOYMENT_REPO` *(required)* – Git repo containing the Helm chart plus `config.yaml`.
- `TRIGGER_TOKEN` *(required)* – Token used when downstream jobs need to be triggered.
- `MICROSERVICE_NAME` *(required)* – Must match a key under `kinds.deployments` in the deployment repo `config.yaml`.
- `BRANCH` *(required)* – Microservice git branch to build; pipeline enforces it is provided as a string.
- `AUTO_DEPLOY` *(boolean-ish)* – values such as `true`, `'true'`, or `1` enable post-build deployment and Helm stages.
- `TARGET_ENV` *(required when AUTO_DEPLOY)* – Environment key (e.g., `dev`, `uat`, `prod`) used to resolve credentials and namespaces from `config.yaml`.

Validation and summary output are handled by `Preparer.validateArguments` and `Preparer.getConfigSummary` (`src/devops/v1/Preparer.groovy:39`).

---

## 4. Required Repository Layouts

### 4.1 Deployment Repository
The deployment repo referenced by `DEPLOYMENT_REPO` must include a `config.yaml` similar to:

```yaml
app_id: 101
registry:
  nonprod: harbor.phurithat.site/nonprod
  prod: harbor.phurithat.site/prod
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

> The preparer enforces the presence of the registry map, Helm semantic version, and Harbor-based registries (`Preparer.validateConfig`).

### 4.2 Application Repository
Each microservice repo is expected to contain `devops.properties.yaml` to describe its build toolchain and security posture. Example:

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

The preparer reads this file to drive pod composition, scanning stages, and runtime summary output (`Preparer.getConfigSummary`).

---

## 5. Pipeline Flow

1. **Controller preparation (`node('master')`):**
   - Validates input arguments and config (`Preparer.validateArguments`, `validateConfig`).
   - Clones the deployment repo and reads `config.yaml`.
   - Clones the target microservice repo (`config.kinds.deployments[MICROSERVICE_NAME]`).
   - Renders a configuration summary banner for visibility.
   - Calls `PodTemplate.injectConfig` to build the Kubernetes agent spec.

2. **Ephemeral build pod (`podTemplate { node(POD_LABEL) { ... } }`):**
   - Re-checks out the deployment repository inside the pod to ensure a clean workspace.
   - If `AUTO_DEPLOY` is enabled, resolves environment-specific credentials/namespace and hands them to `DeployManager.deployHelm` for linting, dry run, and deployment.

Stages for code build, security scans, and image pushes are expected to be inserted using containers auto-attached to the pod (see section 6). The current library scaffolds the pod with the right tooling, letting project-specific steps run inside dedicated containers.

---

## 6. Tooling & Container Injection (`PodTemplate.injectConfig`)
`src/devops/v1/PodTemplate.groovy` dynamically adds containers and volumes based on `devops.properties.yaml` values:
- `build_tool`: Adds one of `maven`, `nodejs`, `golang`, or `python` containers with the toolchain installed.
- `build_image: true`: Adds a privileged `buildkit` container plus Harbor docker config secret mount.
- `security.image` or `security.dependency`: Adds a `trivy` scanner container.
- `security.code`: Adds a `sonarqube` scanner container.
- `security.secret`: Adds a `gitleaks` container.
- `AUTO_DEPLOY: true`: Adds `kubectl` and `helm` containers for release automation.

Shared volumes (`shared`, `harbor-secret`) are mounted automatically where required. The generated YAML is converted to a pod template using SnakeYAML (`PodTemplate.toString`).

---

## 7. Deployment with Helm
`DeployManager.deployHelm` (`src/devops/v1/DeployManager.groovy:1`) runs two guarded stages inside the `helm` container:
1. **Helm Lint & Dry-Run** – executes `helm lint` and client-side `helm upgrade --install ... --dry-run`. Any failure aborts the pipeline early.
2. **Deploy via Helm** – performs the real `helm upgrade --install` with `--wait --timeout 5m`, using the environment-specific kubeconfig credential.

Both stages use `withCredentials` to load kubeconfig files securely, protecting cluster access.

---

## 8. Shared Credentials & Environment Variables
`CredManager.globalENV` (`src/devops/v1/CredManager.groovy:3`) documents the Jenkins credential IDs and URLs the library expects:
- `HARBOR_CRED`, `JENKINS_CRED`, `GITLAB_NONPROD_KEY`, `GITLAB_PROD_KEY`
- `TRIVY_BASE_URL`, `DEFECTDOJO_BASE_URL`, `DOJO_KEY`
- `HELM_PATH`, `HELM_NONPROD_REPO`, `OCP_NONPROD_AGENT`, `OCP_PROD_AGENT`

Currently `microservicePipeline` sets these environment variables inline; consider invoking `new CredManager().globalENV()` at the start of your pipelines to keep one source of truth.

Ensure all referenced credentials exist in Jenkins before adopting the library.

---

## 9. Extending the Library
- Add new reusable steps under `vars/` (and document them). Each Groovy file exposes a global variable matching its filename.
- Place complex logic in `src/devops/<version>/` classes. Favor small, testable methods.
- To introduce new container tooling, extend `PodTemplate` with another `add<Thing>()` helper and toggle it from `devops.properties.yaml`.
- Keep version compatibility by creating a new package (`devops.v2`) when making breaking changes.

---

## 10. Testing & Validation
- Use [Jenkins Pipeline Unit](https://github.com/jenkinsci/JenkinsPipelineUnit) to unit test shared library steps. Test scaffolding should live under `test/` mirroring the `src/` package structure.
- Smoke-test changes with the included `Jenkinsfile` by pointing it at a sandbox job and invoking `@Library('shared-libs@your-branch') _`.
- Validate pod template output locally by calling `new PodTemplate().injectConfig(config, args).toString()` in a Groovy console and inspecting the YAML.

---

## 11. Adoption Checklist
- [ ] Register the shared library in Jenkins Global Pipeline Libraries (`name: shared-libs`, default branch `main`).
- [ ] Ensure deployment repos provide `config.yaml` with correct registries, environments, and Helm settings.
- [ ] Ensure each microservice repo provides `devops.properties.yaml` with accurate build + security settings.
- [ ] Provision all Jenkins credential IDs referenced by the library (Harbor, GitLab, kubeconfig files, etc.).
- [ ] Update project Jenkinsfiles to call `microservicePipeline(...)` with the correct arguments.

Following this checklist allows teams across projects to reuse the shared library confidently while preserving consistent CI/CD behavior.
