package devops.v1

class Builder {
    def config
    def Builder(config){
        this.config = config
    }
   
    

    def Compile(SONAR_TOKEN,SONAR_HOST,SONAR_PROJECT_KEY){
        if(config.build_tool.equalsIgnoreCase("maven")){

        }
        else if(config.build_tool.equalsIgnoreCase("node.js")){

        }
    }
}