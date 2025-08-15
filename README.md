# Jenkins Shared Library

## Overview
This repository contains a Jenkins Shared Library that provides reusable pipeline steps, classes, and templates for DevOps automation.  
It is designed to standardize CI/CD processes across projects.

## **CI/CD Pipeline Features**

### **1. Continuous Integration**

1. **Commit**

   * Commit source code to the repository (e.g., GitLab).
2. **Security Scan**

   * Code Scan (static code analysis with SonarQube).
   * Dependency Scan (vulnerability scanning of dependencies with tools like Trivy).
   * Secret Scan (check for exposed secrets).
3. **Build Scan**

   * Build container image (using BuildKit or Docker).
   * Scan image for vulnerabilities (e.g., with Trivy, DefectDojo).

---

### **2. Continuous Delivery & Deployment**

4. **Push**

   * Push built image to registry (e.g., Harbor, Nexus).
   * Push library artifacts.
5. **Update Manifest**

   * Update Helm values and Kubernetes manifest files.
6. **Deploy**

   * Deploy application to Kubernetes cluster using Helm/Kubectl.

---

### **Tool Integrations**

* **Source Control:** GitLab
* **Static Code Analysis:** SonarQube
* **Secret Scan:** GitLeaks
* **Dependency & Container Scan:** Trivy
* **Security Testing Management:** DefectDojo
* **Container Build:** BuildKit, Docker
* **Artifact Registries:** Harbor, Nexus
* **Deployment:** Helm, Kubernetes

---

## How to Use

### 1️⃣ Load the Library in Jenkins
In Jenkins, go to:
**Manage Jenkins → Configure System → Global Pipeline Libraries**
- **Name:** `shared-libs`
- **Default Version:** `main`
- **Retrieval Method:** Modern SCM
- **Source Code Management:** Git
- **Project Repository:** `https://github.com/your-org/shared-libs.git`

---

### 2️⃣ Import in a Jenkinsfile
```groovy
@Library('shared-libs') _
examplePipeline()
````

---

### 3️⃣ Example Usage

```groovy
@Library('shared-libs') _
pipeline {
    agent any
    stages {
        stage('Build & Push') {
            steps {
                dockerBuildAndPush(imageName: 'my-app', imageTag: '1.0.0')
            }
        }
        stage('Security Scan') {
            steps {
                securityScan(tool: 'sonarqube')
            }
        }
    }
}
```

---

## Documentation

* [Folder Structure](docs/folder-structure.md)
* [Usage Guide](docs/usage-guide.md)
* [Coding Guidelines](docs/coding-guidelines.md)

---

## Development & Testing

### Run Unit Tests

```bash
./gradlew test
```

or

```bash
mvn test
```

(using Jenkins Pipeline Unit)

### Folder Layout

See [Folder Structure](docs/folder-structure.md) for details.

---

## Contributing

1. Fork the repo.
2. Create a feature branch.
3. Commit changes.
4. Submit a Pull Request.

---

## License

[MIT](LICENSE)
