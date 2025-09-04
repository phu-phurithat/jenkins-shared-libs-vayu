package devops.v1

class Builder {
    def config
    def Builder(config){
        this.config = config
    }
   def credManager = new CredManager()
    
 
    def Compile(SONAR_HOST,SONAR_PROJECT_KEY){
        def build_tool= config.build_tool
        def language
        if (build_tool != null) {
       language = buildTool.toLowerCase()
       echo "LANGUAGE = ${language}"
       } else {
       echo "build_tool is null!"
          }
        if(build_tool.equalsIgnoreCase("maven")){
            sh """
               mvn clean install verify sonar:sonar \
                 -Dsonar.host.url=${SONAR_HOST} \
                 -Dsonar.login=${SONAR_TOKEN} \
                 -Dsonar.projectKey=${SONAR_PROJECT_KEY}

              curl -s -u "${SONAR_TOKEN}:" \
                       "${SONAR_HOST}/api/issues/search?projectKey=${SONAR_PROJECT_KEY}" \
                       -o sonarqube-report.json
             """
        }
        else if(config.build_tool.equalsIgnoreCase("node.js")){

        }
    }
}