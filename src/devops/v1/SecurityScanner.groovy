package devops.v1

def SorceCodeScan(SONAR_TOKEN, SONAR_HOST, SONAR_PROJECT_KEY, language) {
     withCredentials([string(credentialsId: SONAR_TOKEN, variable: 'SONAR_TOKEN')]) {
          String param = """
               sonar-scanner \
                          -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                          -Dsonar.sources=. \
                          -Dsonar.host.url=${SONAR_HOST} \
                          -Dsonar.login=${SONAR_TOKEN} -Dsonar.java.binaries=target/ 
          """

          if (language == 'maven'){
               param = param + " -Dsonar.java.binaries=target/"
          }
          sh """
               ${param}
                 curl -s -u "${SONAR_TOKEN}:" \
                            "${SONAR_HOST}/api/issues/search?projectKey=${SONAR_PROJECT_KEY}" \
                            -o sonarqube-report.json
             """
     }
}
def DependenciesScan() {
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
def ImageScan(FULL_IMAGE) {
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
