package devops.v1

    def Compile(SONAR_TOKEN,SONAR_HOST,SONAR_PROJECT_KEY,language){
        withCredentials([string(credentialsId: SONAR_TOKEN, variable: 'SONAR_TOKEN')]) {
            if(language=="maven"){
            sh """
               mvn clean install verify sonar:sonar \
                 -Dsonar.host.url=${SONAR_HOST} \
                 -Dsonar.login=${SONAR_TOKEN} \
                 -Dsonar.projectKey=${SONAR_PROJECT_KEY}

              curl -s -u "${env.SONAR_TOKEN}:" \
                       "${SONAR_HOST}/api/issues/search?projectKey=${SONAR_PROJECT_KEY}" \
                       -o sonarqube-report.json
             """
        }
        else if(config.build_tool.equalsIgnoreCase("node.js")){

        }}

    }
def BuildImage(BUILDKIT_ADDR,FULL_IMAGE,DOCKER_CONFIG,REGISTRY){
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
              --import-cache type=registry,ref=${REGISTRY}
          """
}