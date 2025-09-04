package devops.v1

    def Compile(SONAR_TOKEN,SONAR_HOST,SONAR_PROJECT_KEY,language){
        withCredentials([string(credentialsId: SONAR_TOKEN, variable: 'defectdojo_api_key')]){
            if(language=="maven"){
            sh """
               mvn clean install verify sonar:sonar \
                 -Dsonar.host.url=${SONAR_HOST} \
                 -Dsonar.login=${env.SONAR_TOKEN} \
                 -Dsonar.projectKey=${SONAR_PROJECT_KEY}

              curl -s -u "${env.SONAR_TOKEN}:" \
                       "${SONAR_HOST}/api/issues/search?projectKey=${SONAR_PROJECT_KEY}" \
                       -o sonarqube-report.json
             """
        }
        else if(config.build_tool.equalsIgnoreCase("node.js")){

        }}

    }
