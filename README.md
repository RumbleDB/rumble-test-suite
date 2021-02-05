# How to setup IntelliJ Project assuming all PRE-REQUIREMENTS are installed. 
0. Obtaining the rumble-test-suite repository and its structure 


```
git clone https://gitlab.inf.ethz.ch/gfourny/rumble-test-suite
cd rumble-test-suite
git checkout maven-framework-project
chmod u+x get-tests-repository.sh
git clone https://gitlab.inf.ethz.ch/gfourny/rumble
cd rumble
git checkout research-project-stevan-mihajlovic
```

1. Open IntelliJ idea and click import project.
2. Double click rumble-test-suite and wait for dependencies to be resolved
3. You will see a problem that Could not find artifact com.github.rumbledb:spark-rumble:pom:1.9.0 in central
4. Full screen IntelliJ idea and go to File->Project Structure (Command + ;)
5. Go to Module and Click on + sign (Command + N) and navigate to the rumble folder and click on it. 
6. Import it as Maven dependency and click next. spark-rumble should appear under rumble test suite
7. Click on rumble test suite and click on dependencies
8. At the end of the list there is another + sign. Click it and select Module dependency...
9. Select spark-rumble one and climb it on top of the list. After clicking okay, IntelliJ will resolve dependency
10. Click again on the + sign. Select JAR or directories.
11. Go to src->main->saxon->command select all 4 .jar files and click okay
12. Open Run.java class, Right-click Debug 'Run.main()'  

# 30.11.2020 How to setup PRE-REQUIREMENTS on BRAND NEW MacBook Pro M1 Chip
```
arch -x86_64 /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install.sh)"
arch -x86_64 brew install scala@2.11
arch -x86_64 brew install apache-spark will first install java 11 and then it will download spark 3.0.1 as I failed to specify the version
echo export SPARK_HOME=/usr/local/Cellar/apache-spark/3.0.1/libexec >> ~/.zshrc
echo export PATH="$SPARK_HOME/bin/:$PATH" >> ~/.zshrc
sudo ln -sfn /usr/local/opt/openjdk@11/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-11.jdk
export CPPFLAGS="-I/usr/local/opt/openjdk@11/include"
export JAVA_HOME=$(/usr/libexec/java_home) >> ~/.zshrc 
chmod +x /usr/local/Cellar/apache-spark/3.0.1/libexec/bin/*
echo 'export PATH="/usr/local/opt/scala@2.11/bin:$PATH"' >> ~/.zshrc 
echo export PATH="$JAVA_HOME/bin/:$PATH" >> ~/.zshrc
source ~/.zshrc
```

#### Java SDK and ZULU SDK
On the brand new Laptop, when running IntelliJ, you might need to setup SDK:
1. Go to [Oracle site for Java 1.8](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html) and select macOSx64 to get the .dmg file
2. Double click on it -> opens the window with package -> double click on it and install
3. Back in IntelliJ idea click Add SDK -> JDK and select the java version 1.8 one

It seems that M1 chip is not quite running fast with JAVA SDK. That is why we should download ZULU SDK from [here](https://cdn.azul.com/zulu/bin/zulu11.43.1021-ca-jdk11.0.9.1-macosx_aarch64.dmg). Once downloaded, it will be available in Intellij, just repeat same steps choosing the ZULU one

#### Maven and ANT
Rumble required us to install mvn and ant:
```
arch -x86_64 brew install maven
echo export PATH="/usr/local/Cellar/maven/3.6.3_1/bin/:$PATH" >> ~/.zshrc
arch -x86_64 brew install ant
echo export PATH="/usr/local/Cellar/ant/1.10.9/bin/:$PATH" >> ~/.zshrc
```

###### All the commands below need to be run within rumble directory (cd ~/Documents/rumble-test-suite/rumble)
In order to build grammar lexer classes using ant run:
```
ant -buildfile build_antlr_parser.xml generate-parser
cd src/main/java/org/rumbledb/parser
ant -buildfile build_antlr_parser.xml generate-xquery-parser
ant -buildfile build_antlr_parser.xml generate-xquery-lexer
```

In order to build .jar file using mvn run:
```
mvn clean compile assembly:single
```

In order to debug .jar file you need to:
First create the alias and run the command run_rumble_no_static_analysis. This will launch the Rumble in terminal
```
echo alias run_rumble_no_static_analysis="export SPARK_SUBMIT_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005; spark-submit target/spark-rumble-[0-9.]*-jar-with-dependencies.jar --shell yes" >> ~/.zshrc
run_rumble_no_static_analysis
```
In IntelliJ do following:
1. Click Edit Configuration
2. Command+N
3. From dropdown select Remote JVM Debug
4. It should be prefilled with Host:localhost, Port:5005, Command Line Arguments for remote JVM: -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
5. Just input the Name and click okay
6. Select the just created configuration and click debug
7. Input something in Rumble and it will hit the breakpoint!


# 07.10.2020 How to setup PRE-REQUIREMENTS on BRAND NEW Ubuntu 18.04 LTS
0. Download [spark-2.4.6-bin-hadoop2.7.tgz](https://archive.apache.org/dist/spark/spark-2.4.6/spark-2.4.6-bin-hadoop2.7.tgz) 
```
sudo apt install openjdk-8-jdk
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
echo $JAVA_HOME
export PATH=$PATH:$JAVA_HOME/bin
echo $PATH
java –version
cd ~/Downloads
tar -xvf spark-2.4.6-bin-hadoop2.7.tgz
mv spark-2.4.6-bin-hadoop2.7 ~/spark
export SPARK_HOME=$HOME/spark
export PATH=$PATH:$SPARK_HOME/bin
source ~/.bashrc
```

#### Personal decision and notes
- Java JDK is a greater set than JRE. JRE is Runtime environment needed for running the application, while JDK is Development Kit needed for developing
- Headless is used for servers and normal one is used for desktops
- Path variables are used when you want to run command. Java's bin should be added to the path in order to be able to use command java
- Some installations need to use Java and they look for it via JAVA_HOME variable, that is why we need to set that one as well
- I did not use sudo apt install default-jdk as this would install version 11 and I was using spark compatible with version 8
- Commands used:(PERFECT GUIDE [HERE](https://vitux.com/how-to-setup-java_home-path-in-ubuntu/))

- I decided to use the 2.4.6 spark and not the 3.0.0 version. Since 2.4.6 only works with java 8, this is why I did not opt out for the default java installation. Also I did not have to install scala as prerequirement, the downloaded installation does it for you.
- To run rumble from terminal (command line), download [spark-rumble-1.8.1.jar](https://github.com/RumbleDB/rumble/releases/download/v1.8.1/spark-rumble-1.8.1.jar) and execute command:
```
spark-submit spark-rumble-1.8.1.jar --shell yes
```
	



