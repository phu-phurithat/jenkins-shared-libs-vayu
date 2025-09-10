package devops.v1

def Compile(SONAR_TOKEN, SONAR_HOST, SONAR_PROJECT_KEY, build_tool) {
    withCredentials([string(credentialsId: SONAR_TOKEN, variable: 'SONAR_TOKEN')]) {
        if (build_tool == 'maven') {
            container('maven') {
                sh '''
                    mvn clean install verify
                '''
            }
        } else if (build_tool == 'npm' ) {
            container('nodejs') {
                    sh '''
                        npm install \
                        npm run test
                    '''
            }
        }else if (build_tool == 'pip') {
            container('python') {
                sh '''
                    pip install -r requirements.txt
                '''
            }
        }else if (build_tool == 'go' ) {
                container('golang') {
                    sh '''
            go mod tidy
            go build -buildvcs=false

            '''
                }
        }else if (build_tool == 'gradle' ) {
                container('gradle') {
                    sh '''
            gradle build
            '''
                }
        }
    }
}

def BuildImage(BUILDKIT_ADDR, FULL_IMAGE, DOCKER_CONFIG) {
    String REGISTRY_HOST = FULL_IMAGE.tokenize('/')[0]
    sh """
            buildctl \
              --addr ${BUILDKIT_ADDR} \
              build \
              prune \
              --frontend dockerfile.v0 \
              --local context=. \
              --local dockerfile=. \
              --output type=image,name=${FULL_IMAGE},push=true,registry.config=${DOCKER_CONFIG} \
              --export-cache type=inline \
              --import-cache type=registry,ref=${REGISTRY_HOST}
          """
}
