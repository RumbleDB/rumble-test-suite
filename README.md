# rumble-test-suite

After cloning the repository, give the necessary permissions to get-tests-repository.sh. This script will ensure you obtain the necessary tests repository!
```
chmod u+x get-tests-repository.sh 
```

To generate jar file, simply run the following command:
```
ant -f build.xml 
```

You are ready to execute the Java application by runnning:
```
java -jar rumble-test-suite.jar 
```