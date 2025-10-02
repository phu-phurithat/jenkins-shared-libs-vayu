package devops.v1

def sourceCodeScan(sonarProjectKey, sonarProjectName, language) {
  withCredentials([string(credentialsId: SONAR_TOKEN, variable: 'SONAR_TOKEN')]) {
    def responseContent = sh(
      script: "curl -u ${SONAR_TOKEN}: ${SONAR_BASE_URL}/api/projects/search?projects=${sonarProjectKey}",
      returnStdout: true
    )
    def response = readJSON text: responseContent
    def projectExists = response.components

    if (!projectExists) {
      echo "SonarQube Project not found, creating SonarQube project..."
      sh """
        curl -u  ${SONAR_TOKEN}: -X POST \
        "${SONAR_BASE_URL}/api/projects/create?project=${sonarProjectKey}&name=${sonarProjectName}"
      """
      echo "SonarQube project '${sonarProjectName}' created."
    } else {
      echo "SonarQube project '${sonarProjectKey}' already exists."
    }

    def sonar_param = [
      maven : [src: 'src/main/java', binaries: 'target/classes'],
      gradle: [src: 'src', binaries: 'build/classes/java/main/'],
      other : [src: '.', binaries: '']
    ]
    if (language != 'maven' && language != 'gradle') {
      language = 'other'
    }

    container('sonarqube') {
      sh """
        sonar-scanner \
          -Dsonar.projectKey=${sonarProjectKey} \
          -Dsonar.sources=${sonar_param[language].src} \
          -Dsonar.java.binaries=${sonar_param[language].binaries} \
          -Dsonar.host.url=${SONAR_BASE_URL} \
          -Dsonar.login=${SONAR_TOKEN}
      """
    }

    sh """
      curl -s -u "${SONAR_TOKEN}:" \
        "${SONAR_BASE_URL}/api/issues/search?projectKey=${sonarProjectKey}" \
        -o sonarqube-report.json
    """
    return
  }
}

def dependenciesScan() {
  sh """
    trivy fs . \
      --server ${TRIVY_BASE_URL} \
      --timeout 10m \
      --skip-db-update \
      --severity CRITICAL,HIGH,MEDIUM \
      --ignore-unfixed \
      --scanners vuln \
      --format cyclonedx \
      -o trivy_deps.json
  """
}

def imageScan(FULL_IMAGE) {
  sh """
    trivy image ${FULL_IMAGE} \
      --server ${TRIVY_BASE_URL} \
      --timeout 10m \
      --skip-db-update \
      --severity CRITICAL,HIGH,MEDIUM \
      --ignore-unfixed \
      --scanners vuln \
      --format cyclonedx \
      -o trivy_image.json
  """
}

/**
 * Secret scanning with gitleaks
 * - Runs gitleaks in a container
 * - Mounts workspace
 * - Saves JSON report
 * - Fails build if secrets are found
 */
def secretScan() {
  try {
    sh '''
      gitleaks detect --source=. \
             --report-path=gitleaks-report.json \
             --redact 
    '''
    def report = readJSON file: 'gitleaks-report.json'
    if (report.leaks && report.leaks.size() > 0) {
      echo "❌ ${report.leaks.size()} secrets detected:"
      report.leaks.each { leak ->
        echo "- ${leak.Description} in ${leak.File}:${leak.StartLine}"
      }
      error("Gitleaks scan failed. Secret found")
    } else {
      echo "✅ No secrets detected by Gitleaks."
    }
  } catch (Exception e) {
    error("${e}")
  }
}
