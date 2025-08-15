# Jenkins Shared Library – Folder Structure

## Overview
This repository contains reusable Jenkins pipeline code packaged as a **Jenkins Shared Library**.  
It follows the recommended structure from Jenkins documentation, with additional project-specific folders for configuration, templates, and documentation.

---

## Folder Structure
```plaintext
.
├── vars/
│   ├── examplePipeline.groovy
│   ├── examplePipeline.md
│   └── ...
├── src/
│   └── devops/
│       └── v1/
│           ├── DockerBuilder.groovy
│           ├── SecurityScanner.groovy
│           ├── DeployManager.groovy
│           ├── ParamPreparer.groovy
│           ├── SummaryPrinter.groovy
│           └── GitHelper.groovy
├── resources/
│   └── templates/
│       ├── podTemplate.yaml
│       ├── deployment.yaml
│       └── ...
├── docs/
│   ├── folder-structure.md
│   └── usage-guide.md
├── test/
│   └── devops/
│       └── v1/
│           └── DockerBuilderTest.groovy
├── build.gradle / pom.xml   # Optional if using build tools
├── README.md
└── Jenkinsfile              # Optional for testing the library itself
```

---

## Folder Details

### `vars/`

* **Purpose:** Contains **global pipeline steps** (functions) callable directly in Jenkins pipelines.
* **Naming:**

  * Each `.groovy` file defines one global variable (function name = file name).
  * Add a `.md` file with the same name for usage documentation.
* **Example:**

  * `dockerBuildAndPush.groovy` → callable as `dockerBuildAndPush()` in a Jenkinsfile.

---

### `src/`

* **Purpose:** Holds **Groovy classes** for complex logic and reusability.
* **Structure:** Organized by feature/module and version (e.g., `devops.v1`).
* **Example Classes:**

  * `DockerBuilder` → Builds and pushes Docker images.
  * `SecurityScanner` → Integrates with tools like SonarQube or Trivy.
  * `DeployManager` → Deploys apps to Kubernetes or Helm.
  * `ParamPreparer` → Centralizes pipeline parameter handling.
  * `SummaryPrinter` → Prints pipeline summaries.

---

### `resources/`

* **Purpose:** Stores **static resources** (YAML, JSON, scripts) to be loaded in Groovy.
* **Usage Example:**

  ```groovy
  libraryResource('templates/podTemplate.yaml')
  ```
* **Examples:**

  * `templates/podTemplate.yaml` → Kubernetes pod definitions.
  * `templates/deployment.yaml` → Deployment manifests.

---

### `docs/`

* **Purpose:** Project documentation.
* **Examples:**

  * `folder-structure.md` → This file.
  * `usage-guide.md` → How to import and use the shared library.
  * `coding-guidelines.md` → Best practices for writing steps/classes.

---

### `test/`

* **Purpose:** Unit & integration tests for the library.
* **Recommendation:** Use [Jenkins Pipeline Unit](https://github.com/jenkinsci/JenkinsPipelineUnit) for Groovy tests.
* **Example:**

  * `DockerBuilderTest.groovy` tests the `DockerBuilder` class.

---

### Root Files

* **`README.md`** – Overview & quick start.
* **`Jenkinsfile`** – For testing the library in CI (optional).
* **`build.gradle` / `pom.xml`** – Build automation if needed.

---

## Best Practices

* Keep **business logic** in `src/` and **orchestration** in `vars/`.
* Document each global step in a `.md` file.
* Use semantic versioning in package names (`devops.v1`, `devops.v2`).
* Keep `resources/` templates generic for reuse.
* Write tests to prevent regressions.


