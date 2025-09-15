package devops.v1

def SorceCodeScan(SONAR_TOKEN, SONAR_HOST, SONAR_PROJECT_KEY, language) {
     withCredentials([string(credentialsId: SONAR_TOKEN, variable: 'SONAR_TOKEN')]) {
          def sonar_param = [
                    maven:[src: 'src/main/java',
                         binaries: 'target/classes'],
                    gradle:[src: 'src',
                         binaries: 'build/classes'],
                         
                    other:[src: '.',
                         binaries: '']
                         
               ]
               if(language!='maven' && language!='gradle'){
                    language = 'other'
               }
               container('sonarqube') {
                    sh """
                    sonar-scanner \
                             -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                             -Dsonar.sources=${sonar_param[language].src} \
                             -Dsonar.java.binaries=${sonar_param[language].binaries} \
                             -Dsonar.host.url=${SONAR_HOST} \
                             -Dsonar.login=${SONAR_TOKEN}
                    """
               }
               
               
               
               sh """
                    curl -s -u "${SONAR_TOKEN}:" \
                               "${SONAR_HOST}/api/issues/search?projectKey=${SONAR_PROJECT_KEY}" \
                               -o sonarqube-report.json
                """
               return
     
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
