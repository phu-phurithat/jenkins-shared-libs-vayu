package devops.v1
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