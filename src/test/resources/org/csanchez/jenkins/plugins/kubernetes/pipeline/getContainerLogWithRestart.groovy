//noinspection GrPackage
podTemplate()
{
    node(POD_LABEL) {
        stage('container log') {
            sh 'for i in `seq 1 5`; do echo $i; sleep 5; done'
            containerLog 'jnlp'
        }
    }
}
