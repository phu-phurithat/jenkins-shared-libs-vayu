package devops.v1

class Builder {
    def config
    def Builder(config){
        this.config = config
    }
    String language = config.build_tool

  
 
    def Compile(SONAR_HOST,SONAR_PROJECT_KEY,language){
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

        }


        }
       
    }
