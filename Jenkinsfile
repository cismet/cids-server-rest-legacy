pipeline {
    agent any
    
    options {
        buildDiscarder(logRotator(numToKeepStr:'5'))
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('checkout') {
            steps {
                checkout([$class: 'GitSCM', branches: [[name: '*/dev-uba']], 
                        extensions: [[$class: 'CleanCheckout'], 
                            [$class: 'LocalBranch', localBranch: 'dev-uba']]])
            }
        }  
        
        stage('build') {
            
            when {
                branch 'dev-uba'
            }
            
            steps {
                withMaven(
                    maven: 'default', // Tools declared in the Jenkins "Global Tool Configuration"
                    jdk: 'default'){
                    sh 'mvn -U clean deploy'
                } // withMaven will discover the generated Maven artifacts, JUnit reports and FindBugs reports
            }
        }
    } 
    
    post {
        success {
			sh '$JENKINS_HOME/scripts/updateGithubIssue.sh'
        }
        failure {
            emailext attachLog: true, 
				to: "pascal@cismet.de", 
				subject: "Build failed in Jenkins: ${currentBuild.fullDisplayName}",
                body: """<p>FAILED: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
                <p>Check console output at &QUOT;<a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;</p>"""
        }
        unstable {
            emailext attachLog: true, 
				to: "pascal@cismet.de", 
				subject: "Jenkins build became unstable: ${currentBuild.fullDisplayName}",
                body: """<p>UNSTABLE: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
                <p>Check console output at &QUOT;<a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;</p>"""
        }
        changed {
            emailext attachLog: true, 
                to: "dev@cismet.de", 
                subject: "Jenkins build passed: ${currentBuild.fullDisplayName}",
                body: """<p>SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]':</p>
                <p>Check console output at &QUOT;<a href='${env.BUILD_URL}'>${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>&QUOT;</p>"""
        }
    }
}