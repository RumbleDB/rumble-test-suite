# NEW README
## Installation
1. git clone this repo
2. git clone rumble repo inside this one
3. maven build something
4. run test converter
5. run test driver


# OLD README START
# Introduction
This README is written as a top-down breakdown document. At the beginning you will find the **core description** on how to actually use the Project. This assumes that you have already setup your IDE and all the PRE-REQUIREMENTS needed for it. In case that you did not setup anything yet, you should read this document backwards - from end until beginning such that you can follow a story.

# How to use Test Driver and Test Convertor
Due to time factor, the Project could not be refactored. Meaning that there is **no command line arguments** that could be passed to command executing a JAVA .jar file were not created. Also, **no configuration .json file** (or other format) was created. Instead, **Constants.java** class will be used in combination with **private fields** of JAVA classes. **For each scenario we will describe how to setup all fields in order to obtain desired results. In addition, usage of outputs will be described.**

#### 1. Understanding the Output directory structure of Test Driver
The outputs can be enabled by setting **public static final boolean PRODUCE_LOGS = true** in **src/main/java/ch/ethz/Constants.java**

The outputs will be stored under **rumble-test-suite/logDirectoryName directory**.

**logDirectoryName** is declared and assigned as **private static String logDirectoryName** in **src/main/java/ch/ethz/Run.java**

**Each execution** of src/main/java/ch/ethz/Run.java **will create a subdirectory** rumble-test-suite/logDirectoryName/timestamp containing:
1. Success.txt - List of test cases that **were not modified** by hard-coded conversion, where **Rumble result matches expected result of test case**
2. Managed.txt - List of test cases that **were modified** by hard-coded conversion, where **Rumble result matches expected result of test case**
3. Fails.txt - List of test cases that where **Rumble result does not match expected result of test case**
4. Crashes.txt - List of test cases that caused **unhandled exception when run in Rumble**
5. UnsupportedTypes.txt - List of test cases that are causing **internal Test Driver exception** as they **contain Types that are not supported in Rumble yet**.
6. UnsupportedErrorCodes.txt - List of test cases that are causing **internal Test Driver exception** as their **expected result contains Error codes that are not supported in Rumble yet**. To Edit this list please check below for **How to Edit UnsupportedErrorCodes.txt**
7. Dependencies.txt - List of test cases that are causing **internal Test Driver exception** as they **contain dependency tags that are not supported in Rumble yet**. To Edit this list please check below for **How to Edit Dependencies.txt**
8. Skipped.txt - List of test cases that are **omitted from being executed in Rumble** as they would fail since **they are not supported in Rumble yet**. To Edit this list please check below for **How to Edit Skipped.txt**
9. Statistics.csv - **aggregated sum per test set** of above mentioned 8 categories
10. BrokenWithLatestImplementation.txt - List of test cases that were **passing before** but not anymore and List of Tests that were **not crashing before**, but are now and not in previous List. **Before means in comparison to previous TestDriver execution.** To use this List refer to **How to verify Rumble bugfix**

#### 2. Understanding the Output directory structure of Test Converter
The outputs can be enabled by setting **public static final boolean PRODUCE_OUTPUT = true** in **src/main/java/converter/Constants.java**

The outputs will be stored under **rumble-test-suite/OUTPUT_TEST_SUITE_DIRECTORY directory**.

**OUTPUT_TEST_SUITE_DIRECTORY** is declared and assigned as public static final String OUTPUT_TEST_SUITE_DIRECTORY = "Output_Test_Suite" in **src/main/java/converter/Constants.java**

**Each execution** of src/main/java/converter/Run.java **will create a subdirectory** rumble-test-suite/OUTPUT_TEST_SUITE_DIRECTORY/timestamp with the **directory structure same as the qt3tests directory.**

Similar to the Test Driver, we can **omit from the Output test sets and test cases** since they are not JSONiq and **should not be converted to JSONiq. It will never be supported**. The list of test cases that will be are all the test cases that are contained within test sets in **rumble-test-suite/TestSetsToSkip_Item1.txt** .txt file.

This list is loaded by **src/main/java/converter/TestConverter.java** using **public static final String TEST_SETS_TO_SKIP_FILENAME = "TestSetsToSkip_Item1.txt"** in **src/main/java/converter/Constants.java**

In addition, list can be complemented by some specific test cases from other test sets that might be troublesome to convert. The list of specific test cases are contained in **rumble-test-suite/TestCasesToSkip.txt**.txt file.

This list is loaded by **src/main/java/converter/TestConverter.java** using **public static final String TEST_CASES_TO_SKIP_FILENAME = "TestCasesToSkip.txt"** in **src/main/java/converter/Constants.java**

#### 3. Test Driver and Test Converter connection
First we must understand that we can test Rumble with either XQuery or JSONiq Parser implementation. The Rumble implementation can be tested using the original qt3tests test suite. In case Rumble is using XQuery parser, we can run the whole test suite as is. In case Rumble is using JSONiq parser, the test cases must be hard-code converted as otherwise they would not be meaningful. The Rumble implementation can be tested using the qt3tests test suite that is converted to JSONiq in a smiliar fashion. To support different scenarios, we are using **public static final boolean TO_CONVERT** and **public static final boolean USE_CONVERTED_TEST_SUITE** in **src/main/java/ch/ethz/Constants.java**. Here we will describe use cases and values that corresponding fields should have:
1. Preferred way of testing Rumble implementation as is now: TO_CONVERT = true; USE_CONVERTED_TEST_SUITE = false;
2. Verify implementation of XQuery Parser: TO_CONVERT = false; USE_CONVERTED_TEST_SUITE = false;
3. Future way of testing Rumble implementation once Test Converter is stable: TO_CONVERT = false; USE_CONVERTED_TEST_SUITE = true;
4. Rarely used (only for advanced users): TO_CONVERT = true; USE_CONVERTED_TEST_SUITE = true;

#### 4. How to verify Rumble bugfix
As mentioned in **Test Driver and Test Converter connection**, preferred way of testing Rumble implementation for now is to use the original qt3tests test suite wtih test cases that are hard-code converted:
1. We need a branch (research-project-stevan-mihajlovic) where bugfix implemented in master branch will be verified:
```
cd rumble
git checkout master
git pull
git checkout research-project-stevan-mihajlovic
git merge master
For Ubuntu: CTRL X (to exit the menu)
For MAC:
	1. press "esc" (escape)
	2. write ":wq" (write & quit)
	3. then press enter.
git push
```
2. Execute **src/main/java/ch/ethz/Run.java** to obtain new output of the results as explained in **Understanding the Output directory structure of Test Driver**
3. Open generated BrokenWithLatestImplementation.txt file. Ideally it should be empty as new implementation should not break previous one.
4. Verify that test cases that were failing due to a bug before are now either in generated Success.txt or Managed.txt
5. If steps 3 and 4 were verified successfully, close the issue on GitHub otherwise debug (check **How to debug Test Driver**)

#### 5. How to debug Test Driver
The Test Driver uses 3 fields enabling to debug specific test sets or cases or simply specific query. They are all declared and assigned in **src/main/java/ch/ethz/TestDriver.java** as fields:
1. private String testCaseToTest - Set this field if you want to run a specific test set with name that starts with this string
2. private String testSetToTest -  Set this field if you want to run a specific test case with name that starts with this string
3. private String queryToTest - For running a specific query as string usually when testing the XQuery Parser for Rumble (check **How to debug Test Converter**)

#### 6. How to debug XQuery Parser
As mentioned in **Test Driver and Test Converter connection**, verifying implementation of XQuery Parser is done with original qt3tests test suite without any hard-coded conersion. The current implementation of Rumble does not support both XQuery and JSONiq Parser running simultaneously. Therefore, it is first required to change to the branch with desired XQuery Parser declared and assigned **parseMainModule** and **parseLibraryModule** methods in **rumble-test-suite/rumble/src/main/java/org/rumbledb/compiler/VisitorHelpers.java**. Building the rumble .jar file and debugging it is explained in **Maven and ANT**

#### 7. How to output converted Test Suite
1. As explained in **How to debug XQuery Parser**: The current implementation of Rumble does not support both XQuery and JSONiq Parser running simultaneously. Therefore, it is first required to change to the branch with desired XQuery Parser declared and assigned **parseMainModule** and **parseLibraryModule** methods in **rumble-test-suite/rumble/src/main/java/org/rumbledb/compiler/VisitorHelpers.java**.
2. Execute **src/main/java/converter/Run.java ** to obtain new output of the results as explained in **Understanding the Output directory structure of Test Converter**
3. DONE

#### 8. How to Edit UnsupportedTypes.txt
The list of Types that will cause the exception **mentiond in Understanding the Output folder structure of Test Driver** can be found in **ConvertAtomicTypes** and **ConvertNonAtomicTypes** methods in **src/main/java/ch/ethz/TestDriver.java**. This list was compiled **according to official Rumble documentation Supported Types list** available [here](https://rumble.readthedocs.io/en/latest/JSONiq/). In Future, any change happening in Rumble should be reflected by changing these two methods.

#### 9. How to Edit UnsupportedErrorCodes.txt
The list of Errors that will not cause the exception **mentiond in Understanding the Output folder structure of Test Driver** can be found in **supportedErrorCodes** field in **src/main/java/ch/ethz/TestDriver.java**. This list was compiled **according to official Rumble documentation Error Codes list** available [here](https://rumble.readthedocs.io/en/latest/Error%20codes/). In Future, any change happening in Rumble should be reflected by changing this field.

#### 10. How to Edit Dependencies.txt
The list of Dependencies that will not cause the exception **mentiond in Understanding the Output folder structure of Test Driver** can be found at beginning of **processTestCase** method in **src/main/java/ch/ethz/TestDriver.java**. This list was compiled **according to communication between Dr Ghislain Fourny and Stevan Mihajlovic** and is available in thesis report and will be coppied here:

schemaValidation,                    schemaImport,                        advanced-uca-fallback,               non_empty_sequence_collection,       collection-stability,                directory-as-collection-uri,         non_unicode_codepoint_collation,     staticTyping,                        simple-uca-fallback,                 olson-timezone,                      fn-format-integer-CLDR,              xpath-1.0-compatibility,             fn-load-xquery-module,               fn-transform-XSLT,                   namespace-axis,                      infoset-dtd,                         serialization,                       fn-transform-XSLT30,                 remote_http,                         typedData,                           schema-location-hint    		
calendar,                            format-integer-sequence,             limits.

In Future, any change happening in Rumble should be reflected by changing this method.

#### 11. How to Edit Skipped.txt
The list of test cases that will be omitted as **mentiond in Understanding the Output folder structure of Test Driver** are all the test cases that are contained within test sets in **rumble-test-suite/TestSetsToSkip_Item2.txt** .txt file.

This list is loaded by **src/main/java/ch/ethz/TestDriver.java** using **public static final String TEST_SETS_TO_SKIP_FILENAME = "TestSetsToSkip_Item2.txt"** in **src/main/java/ch/ethz/Constants.java**

In addition, list contains some test cases might be troublesome to execute (example too deep recursion for assert-permutation). This list can be found in **skipTestCaseList** field in **src/main/java/ch/ethz/TestDriver.java**.

# How to setup your IDE - IntelliJ Project assuming all PRE-REQUIREMENTS are installed.
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
	
