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



# 30.11.2020 Commands used to setup on MacBook Pro
```
git clone https://gitlab.inf.ethz.ch/gfourny/rumble-test-suite
cd rumble-test-suite
git checkout maven-framework-project
chmod u+x get-tests-repository.sh
git clone https://gitlab.inf.ethz.ch/gfourny/rumble
cd rumble
git checkout research-project-stevan-mihajlovic
```

1. Open intellij idea and click import project.
2. Double click rumble-test-suite and wait for dependencies to be resolved
3. You will see a problem that Could not find artifact com.github.rumbledb:spark-rumble:pom:1.9.0 in central
4. Full screen intellij idea and go to File->Project Structure (Command + ;)
5. Click on + sign (Command + N) and navigate to the rumble folder and click on it. 
6. Import it as Maven dependency and click next. spark-rumble should appear under rumble test suite
7. Click on rumble test suite and click on dependencies
8. In the list find the underlined spark rumble one. Select and click delete
9. At the end of the list there is another + sign. Click it and select Module dependency...
10. Select spark-rumble one and climb it on top of the list. After clicking okay, intellij will resolve dependency
11. Go to [Oracle site for Java 1.8](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html) and select macOSx64 to get the .dmg file
12. Double click on it -> opens the window with package -> double click on it and install
13. Back in intellij idea click Add SDK -> JDK and select the java version 1.8 one
14. Click again on the + sign. Select JAR or directories.
15. Go to src->main->saxon->command select all 4 .jar files and click okay
16. Right click debug Run  

# Added as I could not RunQuery
1. arch -x86_64 /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install.sh)"
2. arch -x86_64 brew install scala@2.11
3. arch -x86_64 brew install apache-spark will first install java 11 and then it will download spark 3.0.1 as I failed to specify the version
4. echo export SPARK_HOME=/usr/local/Cellar/apache-spark/3.0.1/libexec >> ~/.zshrc
5. echo export PATH="$SPARK_HOME/bin/:$PATH" >> ~/.zshrc
6. echo export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home >> ~/.zshrc
7. echo export PATH="$JAVA_HOME/bin/:$PATH" >> ~/.zshrc
8. source ~/.zshrc 
9. chmod +x /usr/local/Cellar/apache-spark/3.0.1/libexec/bin/*
10. arch -x86_64 brew install emacs  - NOT NEEDED
11. emacs .profile
12. export SPARK_HOME=/usr/local/Cellar/apache-spark/3.0.1/libexec
13. export PATH="$SPARK_HOME/bin/:$PATH"
14. ctrl-x, ctrl-s, ctrl-x, ctrl-c - UNTIL HERE NOT NEEDED
15. nano ~/.zshrc - verify that last line is export PATH=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home/bin/:/usr/local/Cellar/apache-spark/3.0.1/libexec/bin/:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin
16. ctrl-x, y, return

# More additions
sudo ln -sfn /usr/local/opt/openjdk@11/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-11.jdk
export CPPFLAGS="-I/usr/local/opt/openjdk@11/include"
export JAVA_HOME=$(/usr/libexec/java_home) >> ~/.zshrc
echo 'export PATH="/usr/local/opt/scala@2.11/bin:$PATH"' >> ~/.zshrc 

# More additions
It seems that M1 chip is not quite running fast with JAVA SDK. That is why we should download ZULU SDK from [here](https://cdn.azul.com/zulu/bin/zulu11.43.1021-ca-jdk11.0.9.1-macosx_aarch64.dmg)
Once downloaded, it will be available in Intellij.
Go to Project Structure and add it as SDK for all modules!
