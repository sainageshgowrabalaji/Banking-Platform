// The same pipeline as .github/workflows/ci.yml, written for Jenkins.
// Many banks still run Jenkins, so both are kept. GitHub Actions is the one that runs today.
pipeline {
    agent {
        docker { image 'eclipse-temurin:21-jdk' }
    }
    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }
    stages {
        stage('Build and test') {
            steps {
                sh './mvnw -B -ntp verify'
            }
        }
    }
    post {
        always {
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml'
        }
    }
}
